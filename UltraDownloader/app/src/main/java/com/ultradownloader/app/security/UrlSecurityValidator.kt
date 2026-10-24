package com.ultradownloader.app.security

import com.ultradownloader.app.domain.model.AppError
import com.ultradownloader.app.domain.model.ErrorCategory
import com.ultradownloader.app.domain.model.PlatformType
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hardened URL & Network Security Validator.
 * - Enforces HTTPS-only connections.
 * - Prevents Server-Side Request Forgery (SSRF) by blocking loopback, link-local,
 *   RFC1918 private networks, carrier-grade NAT (100.64.0.0/10), cloud metadata endpoints (169.254.169.254),
 *   and IPv6 unique local / site-local ranges.
 * - Rejects embedded credentials (user:pass@host) and dangerous schemes (file://, javascript://, intent://, data://).
 */
@Singleton
class UrlSecurityValidator @Inject constructor() {

    companion object {
        private const val MAX_URL_LENGTH = 2048

        private val YOUTUBE_HOSTS = setOf(
            "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be", "youtube-nocookie.com"
        )
        private val TIKTOK_HOSTS = setOf(
            "tiktok.com", "www.tiktok.com", "vm.tiktok.com", "vt.tiktok.com", "m.tiktok.com"
        )
        private val INSTAGRAM_HOSTS = setOf(
            "instagram.com", "www.instagram.com", "instagr.am"
        )
        private val DIRECT_MEDIA_EXTENSIONS = setOf(
            "mp4", "m4a", "mp3", "webm", "mov"
        )
        private val FORBIDDEN_HOSTNAMES = setOf(
            "localhost", "localhost.localdomain", "ip6-localhost", "ip6-loopback",
            "metadata.google.internal", "metadata", "instance-data"
        )
    }

    /**
     * Validates a user-supplied URL for syntax, HTTPS enforcement, and SSRF safety.
     * Throws [AppError] with user-friendly Arabic and English diagnostic steps if unsafe or malformed.
     */
    fun validateAndNormalize(
        rawInput: String,
        resolveDns: Boolean = true
    ): URI {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            throw buildInvalidUrlError("الرابط فارغ. يرجى لصق رابط فيديو صالح.", "The link is empty. Please paste a valid video URL.")
        }
        if (trimmed.length > MAX_URL_LENGTH) {
            throw buildInvalidUrlError("الرابط طويل جداً ويتجاوز الحد الآمن المسموح به.", "The URL exceeds the maximum safe length.")
        }

        val uri = try {
            URI(trimmed)
        } catch (e: Exception) {
            throw buildInvalidUrlError("صيغة الرابط غير صحيحة. تأكد من نسخ الرابط بالكامل.", "Malformed URL syntax. Please copy the full link.")
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT)
            ?: throw buildInvalidUrlError("الرابط يفتقد لبروتوكول الاتصال (https://).", "The URL is missing the https:// scheme.")

        if (scheme != "https") {
            throw AppError(
                category = ErrorCategory.INSECURE_HTTP_PROTOCOL,
                titleAr = "بروتوكول اتصال غير آمن",
                titleEn = "Insecure Protocol Blocked",
                descriptionAr = "يسمح تطبيق Ultra Downloader بروابط HTTPS المشفرة فقط لحماية خصوصيتك وأمان جهازك. البروتوكول ($scheme://) غير مسموح.",
                descriptionEn = "Ultra Downloader strictly enforces encrypted HTTPS connections. The scheme ($scheme://) is blocked.",
                suggestedFixesAr = listOf(
                    "استبدل http:// بـ https:// في بداية الرابط.",
                    "لا تستخدم روابط محلية (file:// أو content://) في حقل التحليل."
                ),
                suggestedFixesEn = listOf(
                    "Use an https:// URL instead of http://.",
                    "Do not paste local file:// or javascript:// links."
                )
            )
        }

        if (!uri.userInfo.isNullOrEmpty()) {
            throw buildSsrfError(
                "تم حظر الرابط لاحتوائه على بيانات اعتماد مضمنة (userInfo).",
                "URL rejected because it contains embedded credentials (userInfo)."
            )
        }

        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.')
            ?: throw buildInvalidUrlError("الرابط لا يحتوي على اسم نطاق صالح.", "The URL does not contain a valid hostname.")

        if (host in FORBIDDEN_HOSTNAMES ||
            host.endsWith(".local") ||
            host.endsWith(".internal") ||
            host.endsWith(".lan") ||
            host.endsWith(".home") ||
            host.endsWith(".corp")
        ) {
            throw buildSsrfError(
                "تم حظر الاتصال بنطاق داخلي أو محلي ($host) لحماية الشبكة الداخلية.",
                "Access to internal or local hostname ($host) is blocked by SSRF protection."
            )
        }

        // Check literal IP or DNS-resolved IPs
        if (resolveDns) {
            val addresses = try {
                InetAddress.getAllByName(host)
            } catch (e: Exception) {
                // If offline or literal check needed, validate literal if host looks like IP
                emptyArray()
            }
            for (addr in addresses) {
                if (isPrivateOrReservedAddress(addr)) {
                    throw buildSsrfError(
                        "العنوان ($host) يشير إلى شبكة داخلية خاصة ومحمية.",
                        "Host ($host) resolves to a private or reserved internal IP address."
                    )
                }
            }
        }

        // Always check if the host string itself is a literal private IPv4/IPv6 even without DNS
        if (isLiteralPrivateIp(host)) {
            throw buildSsrfError(
                "لا يُسمح بالاتصال بعناوين الشبكة الداخلية أو المحلية ($host).",
                "Direct connections to private or loopback IP addresses ($host) are prohibited."
            )
        }

        return uri.normalize()
    }

    /**
     * Checks whether an [InetAddress] belongs to loopback, private RFC1918, link-local,
     * carrier-grade NAT (100.64.0.0/10), multicast, cloud metadata, or IPv6 ULA ranges.
     */
    fun isPrivateOrReservedAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) {
            return true
        }

        val raw = address.address
        if (address is Inet4Address && raw.size == 4) {
            val b0 = raw[0].toInt() and 0xFF
            val b1 = raw[1].toInt() and 0xFF
            val b2 = raw[2].toInt() and 0xFF

            // 0.0.0.0/8 (Current network)
            if (b0 == 0) return true
            // 10.0.0.0/8 (Private network)
            if (b0 == 10) return true
            // 100.64.0.0/10 (Carrier-Grade NAT RFC 6598)
            if (b0 == 100 && (b1 in 64..127)) return true
            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) return true
            // 169.254.0.0/16 (Link-local & Cloud Metadata 169.254.169.254)
            if (b0 == 169 && b1 == 254) return true
            // 172.16.0.0/12 (Private network)
            if (b0 == 172 && (b1 in 16..31)) return true
            // 192.0.0.0/24 and 192.0.2.0/24 (TEST-NET-1)
            if (b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2)) return true
            // 192.168.0.0/16 (Private network)
            if (b0 == 192 && b1 == 168) return true
            // 198.18.0.0/15 (Benchmarking)
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return true
            // 198.51.100.0/24 (TEST-NET-2)
            if (b0 == 198 && b1 == 51 && b2 == 100) return true
            // 203.0.113.0/24 (TEST-NET-3)
            if (b0 == 203 && b1 == 0 && b2 == 113) return true
            // 224.0.0.0/4 (Multicast) & 240.0.0.0/4 (Reserved)
            if (b0 >= 224) return true
        } else if (address is Inet6Address && raw.size == 16) {
            val b0 = raw[0].toInt() and 0xFF
            val b1 = raw[1].toInt() and 0xFF
            // fc00::/7 Unique Local Addresses (ULA)
            if ((b0 and 0xFE) == 0xFC) return true
            // fe80::/10 Link-local
            if (b0 == 0xFE && (b1 and 0xC0) == 0x80) return true
        }
        return false
    }

    private fun isLiteralPrivateIp(host: String): Boolean {
        val cleanHost = host.removePrefix("[").removeSuffix("]")
        if (cleanHost == "::1" || cleanHost == "0:0:0:0:0:0:0:1" || cleanHost == "::") return true
        if (cleanHost.startsWith("fc", ignoreCase = true) ||
            cleanHost.startsWith("fd", ignoreCase = true) ||
            cleanHost.startsWith("fe80:", ignoreCase = true)
        ) {
            return true
        }
        val ipv4Parts = cleanHost.split(".")
        if (ipv4Parts.size == 4 && ipv4Parts.all { it.toIntOrNull() in 0..255 }) {
            val b0 = ipv4Parts[0].toInt()
            val b1 = ipv4Parts[1].toInt()
            val b2 = ipv4Parts[2].toInt()
            if (b0 == 0 || b0 == 10 || b0 == 127 || b0 >= 224) return true
            if (b0 == 100 && b1 in 64..127) return true
            if (b0 == 169 && b1 == 254) return true
            if (b0 == 172 && b1 in 16..31) return true
            if (b0 == 192 && b1 == 168) return true
            if (b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2)) return true
            if (b0 == 198 && (b1 == 18 || b1 == 19)) return true
        }
        return false
    }

    /**
     * Automatically detects which platform a URL belongs to.
     */
    fun detectPlatform(rawUrl: String): PlatformType {
        val uri = try {
            URI(rawUrl.trim())
        } catch (e: Exception) {
            return PlatformType.UNKNOWN
        }
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return PlatformType.UNKNOWN
        val path = uri.path?.lowercase(Locale.ROOT).orEmpty()

        return when {
            host in YOUTUBE_HOSTS || host.endsWith(".youtube.com") -> PlatformType.YOUTUBE
            host in TIKTOK_HOSTS || host.endsWith(".tiktok.com") -> PlatformType.TIKTOK
            host in INSTAGRAM_HOSTS || host.endsWith(".instagram.com") -> PlatformType.INSTAGRAM
            DIRECT_MEDIA_EXTENSIONS.any { ext -> path.endsWith(".$ext") } -> PlatformType.DIRECT_MEDIA
            else -> PlatformType.UNKNOWN
        }
    }

    private fun buildInvalidUrlError(descAr: String, descEn: String): AppError {
        return AppError(
            category = ErrorCategory.INVALID_URL,
            titleAr = "رابط غير صالح",
            titleEn = "Invalid URL",
            descriptionAr = descAr,
            descriptionEn = descEn,
            suggestedFixesAr = listOf(
                "تأكد من نسخ الرابط الكامل من تطبيق YouTube أو TikTok أو Instagram.",
                "تأكد من أن الرابط يبدأ بـ https:// ولا يحتوي على مسافات أو نصوص إضافية."
            ),
            suggestedFixesEn = listOf(
                "Make sure you copied the complete link from YouTube, TikTok, or Instagram.",
                "Ensure the link starts with https:// and contains no extra spaces."
            )
        )
    }

    private fun buildSsrfError(descAr: String, descEn: String): AppError {
        return AppError(
            category = ErrorCategory.SSRF_SECURITY_BLOCK,
            titleAr = "تم حظر الرابط بواسطة نظام الحماية (SSRF)",
            titleEn = "Blocked by SSRF Network Protection",
            descriptionAr = descAr,
            descriptionEn = descEn,
            suggestedFixesAr = listOf(
                "يُمنع استخدام روابط الشبكات المحلية (مثل 127.0.0.1 أو 192.168.x.x أو localhost).",
                "أدخل رابطاً عاماً من منصة خارجية موثوقة عبر HTTPS."
            ),
            suggestedFixesEn = listOf(
                "Internal, loopback, and private network addresses (127.0.0.1, 192.168.x.x, localhost) are prohibited.",
                "Provide a public HTTPS link from a supported external platform."
            )
        )
    }
}
