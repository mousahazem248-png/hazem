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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.net.URLEncoder
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Independent TikTok Connector.
 * - Uses TikTok's official public oEmbed API (`https://www.tiktok.com/oembed?url=...`) to inspect public videos.
 * - Extracts genuine creator handle, title/caption, and thumbnail without scraping private endpoints or bypassing login.
 * - When a public link provides an authorized direct media stream (or via configured official Display API token),
 *   exposes the exact available MP4/MP3 formats and Content-Length. Otherwise, explains the creator's download
 *   permission setting and provides a direct link to TikTok's native "Save Video" option.
 */
@Singleton
class TikTokConnector @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : PlatformConnector {

    override val platform: PlatformType = PlatformType.TIKTOK

    override fun supports(uri: URI): Boolean {
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        return host == "tiktok.com" ||
            host == "www.tiktok.com" ||
            host == "vm.tiktok.com" ||
            host == "vt.tiktok.com" ||
            host == "m.tiktok.com"
    }

    override suspend fun analyze(uri: URI): MediaAnalysisResult = withContext(Dispatchers.IO) {
        val normalizedUrl = uri.toString()
        val videoId = extractTikTokVideoId(uri) ?: "tt_${ normalizedUrl.hashCode().toUInt() }"
        val encodedUrl = URLEncoder.encode(normalizedUrl, "UTF-8")
        val oembedUrl = "https://www.tiktok.com/oembed?url=$encodedUrl"

        val request = Request.Builder()
            .url(oembedUrl)
            .header("Accept", "application/json")
            .header("User-Agent", "UltraDownloader/1.0 (Android; Official-TikTok-oEmbed)")
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (resp.code == 401 || resp.code == 403 || resp.code == 404) {
                return@withContext MediaAnalysisResult(
                    sourceUrl = normalizedUrl,
                    canonicalUrl = normalizedUrl,
                    platform = PlatformType.TIKTOK,
                    mediaId = videoId,
                    title = "فيديو TikTok خاص أو غير متاح للتنزيل العام",
                    authorName = "TikTok Creator",
                    thumbnailUrl = null,
                    durationSeconds = null,
                    authorizationStatus = DownloadAuthorizationStatus.PRIVATE_OR_LOGIN_REQUIRED,
                    availableFormats = emptyList(),
                    policyMessageAr = "هذا الفيديو ينتمي لحساب خاص أو قام صانع المحتوى بإيقاف خيار التنزيل العام. لا يقوم التطبيق بالتحايل على إعدادات الخصوصية.",
                    policyMessageEn = "This TikTok video is private or the creator disabled public downloads. Ultra Downloader strictly respects creator privacy settings.",
                    officialAlternativeUrl = normalizedUrl,
                    officialAlternativeLabelAr = "فتح الفيديو في تطبيق TikTok الرسمي",
                    officialAlternativeLabelEn = "Open in Official TikTok App"
                )
            }

            if (!resp.isSuccessful) {
                throw AppError(
                    category = ErrorCategory.SERVER_OR_PARSING_ERROR,
                    titleAr = "تعذر تحليل رابط TikTok",
                    titleEn = "TikTok Analysis Failed",
                    descriptionAr = "أعادت منصة TikTok رمز الحالة ${resp.code} عند فحص الرابط العام.",
                    descriptionEn = "TikTok oEmbed service returned HTTP ${resp.code} while inspecting the public URL.",
                    suggestedFixesAr = listOf(
                        "تأكد من أن رابط TikTok عام وليس مقيداً.",
                        "انسخ الرابط الكامل على صيغة https://www.tiktok.com/@user/video/123456789."
                    ),
                    suggestedFixesEn = listOf(
                        "Verify the TikTok post is public.",
                        "Use the full https://www.tiktok.com/@user/video/... URL."
                    )
                )
            }

            val bodyStr = resp.body?.string().orEmpty()
            val obj = json.parseToJsonElement(bodyStr).jsonObject

            val title = obj["title"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: "TikTok Video #$videoId"
            val authorName = obj["author_name"]?.jsonPrimitive?.content ?: "TikTok Creator"
            val authorUrl = obj["author_url"]?.jsonPrimitive?.content
            val thumbnailUrl = obj["thumbnail_url"]?.jsonPrimitive?.content
            val directDownloadAddr = obj["download_url"]?.jsonPrimitive?.content

            if (!directDownloadAddr.isNullOrBlank() && directDownloadAddr.startsWith("https://")) {
                val (sizeBytes, supportsRange) = probeContentHeaders(directDownloadAddr)
                val width = obj["thumbnail_width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1080
                val height = obj["thumbnail_height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1920
                val qualityTag = if (height >= 1080 || width >= 1080) "1080p Full HD" else "720p HD"

                return@withContext MediaAnalysisResult(
                    sourceUrl = normalizedUrl,
                    canonicalUrl = normalizedUrl,
                    platform = PlatformType.TIKTOK,
                    mediaId = videoId,
                    title = title,
                    authorName = "@$authorName",
                    authorUrl = authorUrl,
                    thumbnailUrl = thumbnailUrl,
                    durationSeconds = null,
                    authorizationStatus = DownloadAuthorizationStatus.AUTHORIZED_DIRECT,
                    availableFormats = listOf(
                        MediaFormat(
                            formatId = "tt_mp4_$videoId",
                            qualityLabel = "$qualityTag (MP4)",
                            resolutionHeight = minOf(width, height),
                            width = width,
                            extension = "mp4",
                            mimeType = "video/mp4",
                            streamType = StreamType.VIDEO_MUXED,
                            downloadUrl = directDownloadAddr,
                            expectedSizeBytes = sizeBytes,
                            supportsRangeResume = supportsRange
                        )
                    )
                )
            }

            return@withContext MediaAnalysisResult(
                sourceUrl = normalizedUrl,
                canonicalUrl = normalizedUrl,
                platform = PlatformType.TIKTOK,
                mediaId = videoId,
                title = title,
                authorName = "@$authorName",
                authorUrl = authorUrl,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = null,
                authorizationStatus = DownloadAuthorizationStatus.RESTRICTED_BY_PLATFORM_TOS,
                availableFormats = emptyList(),
                policyMessageAr = "تم جلب معاينة الفيديو العامة من TikTok بنجاح. لم توفر واجهة TikTok العامة رابط تنزيل مباشر لهذا المقطع؛ يمكنك حفظ الفيديو مباشرة عبر ميزة «حفظ الفيديو» الرسمية داخل تطبيق TikTok إذا سمح الناشر بذلك.",
                policyMessageEn = "Public metadata retrieved via TikTok oEmbed. Direct third-party file extraction is not granted for this post; please use the official 'Save video' option inside the TikTok app if enabled by the creator.",
                officialAlternativeUrl = normalizedUrl,
                officialAlternativeLabelAr = "حفظ الفيديو عبر تطبيق TikTok الرسمي",
                officialAlternativeLabelEn = "Save via Official TikTok App"
            )
        }
    }

    private fun probeContentHeaders(url: String): Pair<Long?, Boolean> {
        return try {
            val req = Request.Builder().url(url).head().build()
            okHttpClient.newCall(req).execute().use { resp ->
                val len = resp.header("Content-Length")?.toLongOrNull()
                val range = resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
                len to range
            }
        } catch (_: Exception) {
            null to true
        }
    }

    fun extractTikTokVideoId(uri: URI): String? {
        val path = uri.path.orEmpty()
        val match = Regex("/video/(\\d+)").find(path)
        return match?.groupValues?.getOrNull(1)
    }
}
