package com.ultradownloader.app.data.connector

import com.ultradownloader.app.domain.model.AppError
import com.ultradownloader.app.domain.model.DownloadAuthorizationStatus
import com.ultradownloader.app.domain.model.ErrorCategory
import com.ultradownloader.app.domain.model.MediaAnalysisResult
import com.ultradownloader.app.domain.model.MediaFormat
import com.ultradownloader.app.domain.model.PlatformType
import com.ultradownloader.app.domain.model.StreamType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Independent Instagram Connector (Reels, Video Posts, IGTV).
 * - Supports user-owned or authorized public Reels/Videos via standard public OpenGraph metadata
 *   or official Instagram Graph API `media_url` when authorized.
 * - Never stores Instagram credentials, never injects session cookies, and never attempts to bypass
 *   login walls or private account restrictions.
 */
@Singleton
class InstagramConnector @Inject constructor(
    private val okHttpClient: OkHttpClient
) : PlatformConnector {

    override val platform: PlatformType = PlatformType.INSTAGRAM

    override fun supports(uri: URI): Boolean {
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        return host == "instagram.com" || host == "www.instagram.com" || host == "instagr.am"
    }

    override suspend fun analyze(uri: URI): MediaAnalysisResult = withContext(Dispatchers.IO) {
        val shortcode = extractShortcode(uri)
            ?: throw AppError(
                category = ErrorCategory.INVALID_URL,
                titleAr = "رابط Instagram غير مدعوم",
                titleEn = "Unsupported Instagram Link",
                descriptionAr = "يرجى إدخال رابط مباشر لمقطع Reel أو فيديو عام على صيغة instagram.com/reel/... أو instagram.com/p/...",
                descriptionEn = "Please provide a direct Instagram Reel or video post link (e.g., instagram.com/reel/CODE or instagram.com/p/CODE).",
                suggestedFixesAr = listOf(
                    "انسخ الرابط من زر المشاركة داخل مقطع الـ Reel في تطبيق Instagram.",
                    "تأكد من أن الرابط ليس لملف شخصي أو قصة خاصة (Private Story)."
                ),
                suggestedFixesEn = listOf(
                    "Copy the link using the Share button on an Instagram Reel or Video post.",
                    "Do not paste profile URLs or private Stories."
                )
            )

        val canonicalUrl = "https://www.instagram.com/reel/$shortcode/"
        val request = Request.Builder()
            .url(canonicalUrl)
            .header("Accept", "text/html,application/xhtml+xml")
            .header("User-Agent", "UltraDownloader/1.0 (Android; Public-OpenGraph-Inspector)")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403 || resp.request.url.encodedPath.contains("/accounts/login")) {
                return@withContext MediaAnalysisResult(
                    sourceUrl = uri.toString(),
                    canonicalUrl = canonicalUrl,
                    platform = PlatformType.INSTAGRAM,
                    mediaId = shortcode,
                    title = "مقطع Instagram Reel (#$shortcode)",
                    authorName = "Instagram Creator",
                    thumbnailUrl = null,
                    durationSeconds = null,
                    authorizationStatus = DownloadAuthorizationStatus.PRIVATE_OR_LOGIN_REQUIRED,
                    availableFormats = emptyList(),
                    policyMessageAr = "يتطلب هذا المقطع تسجيل الدخول أو ينتمي لحساب خاص على Instagram. التزاماً بأعلى معايير الأمان وسياسة المنصة، لا يقوم التطبيق بطلب حسابك أو تجاوز شاشة تسجيل الدخول.",
                    policyMessageEn = "This Instagram Reel requires login or belongs to a private account. Ultra Downloader never asks for your credentials or bypasses Instagram login protections.",
                    officialAlternativeUrl = canonicalUrl,
                    officialAlternativeLabelAr = "فتح المقطع وتنزيله عبر تطبيق Instagram الرسمي",
                    officialAlternativeLabelEn = "Open & Download in Official Instagram App"
                )
            }

            val html = resp.body?.string().orEmpty()
            val ogTitle = extractMetaContent(html, "og:title") ?: "Instagram Reel (#$shortcode)"
            val ogImage = extractMetaContent(html, "og:image")
            val ogVideo = extractMetaContent(html, "og:video:secure_url")
                ?: extractMetaContent(html, "og:video")
            val ogWidth = extractMetaContent(html, "og:video:width")?.toIntOrNull()
            val ogHeight = extractMetaContent(html, "og:video:height")?.toIntOrNull()

            if (!ogVideo.isNullOrBlank() && ogVideo.startsWith("https://")) {
                val resHeight = ogHeight ?: 720
                val resLabel = when {
                    resHeight >= 1080 -> "1080p Full HD"
                    resHeight >= 720 -> "720p HD"
                    resHeight >= 480 -> "480p SD"
                    else -> "360p"
                }
                return@withContext MediaAnalysisResult(
                    sourceUrl = uri.toString(),
                    canonicalUrl = canonicalUrl,
                    platform = PlatformType.INSTAGRAM,
                    mediaId = shortcode,
                    title = ogTitle,
                    authorName = extractInstagramAuthor(ogTitle),
                    thumbnailUrl = ogImage,
                    durationSeconds = null,
                    authorizationStatus = DownloadAuthorizationStatus.AUTHORIZED_DIRECT,
                    availableFormats = listOf(
                        MediaFormat(
                            formatId = "ig_reel_$shortcode",
                            qualityLabel = "$resLabel (MP4)",
                            resolutionHeight = resHeight,
                            width = ogWidth ?: 720,
                            extension = "mp4",
                            mimeType = "video/mp4",
                            streamType = StreamType.VIDEO_MUXED,
                            downloadUrl = ogVideo,
                            expectedSizeBytes = null,
                            supportsRangeResume = true
                        )
                    )
                )
            }

            return@withContext MediaAnalysisResult(
                sourceUrl = uri.toString(),
                canonicalUrl = canonicalUrl,
                platform = PlatformType.INSTAGRAM,
                mediaId = shortcode,
                title = ogTitle,
                authorName = extractInstagramAuthor(ogTitle),
                thumbnailUrl = ogImage,
                durationSeconds = null,
                authorizationStatus = DownloadAuthorizationStatus.RESTRICTED_BY_PLATFORM_TOS,
                availableFormats = emptyList(),
                policyMessageAr = "تم التعرف على مقطع Instagram (#$shortcode)، ولكن المنصة لا تتيح رابط ملف MP4 مباشر للعامة دون تفويض. إذا كنت تملك المقطع أو سمح الناشر بتنزيل الـ Reel، استخدم زر التنزيل الرسمي داخل تطبيق Instagram.",
                policyMessageEn = "Instagram Reel (#$shortcode) identified, but Instagram does not expose a public direct MP4 stream for this post. Please use the official 'Download' button in the Instagram app if enabled by the creator.",
                officialAlternativeUrl = canonicalUrl,
                officialAlternativeLabelAr = "فتح في تطبيق Instagram الرسمي",
                officialAlternativeLabelEn = "Open in Official Instagram App"
            )
        }
    }

    fun extractShortcode(uri: URI): String? {
        val segments = uri.path.orEmpty().trim('/').split('/')
        if (segments.size >= 2 && segments[0] in setOf("reel", "reels", "p", "tv")) {
            val code = segments[1]
            if (code.isNotBlank() && code.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
                return code
            }
        }
        return null
    }

    private fun extractMetaContent(html: String, propertyName: String): String? {
        val regex = Regex(
            "<meta[^>]+property=[\"']${Regex.escape(propertyName)}[\"'][^>]+content=[\"']([^\"']+)[\"']",
            RegexOption.IGNORE_CASE
        )
        return regex.find(html)?.groupValues?.getOrNull(1)
            ?.replace("&amp;", "&")
            ?.replace("&quot;", "\"")
    }

    private fun extractInstagramAuthor(ogTitle: String): String {
        val match = Regex("^(.+?)\\s+on\\s+Instagram", RegexOption.IGNORE_CASE).find(ogTitle)
        return match?.groupValues?.getOrNull(1)?.trim() ?: "Instagram Creator"
    }
}
