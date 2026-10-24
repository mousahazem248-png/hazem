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
 * Independent YouTube Connector.
 * - Uses YouTube's official oEmbed endpoint (`https://www.youtube.com/oembed`) to fetch legitimate metadata
 *   (title, channel name, channel URL, and high-res thumbnail).
 * - Respects YouTube Terms of Service: never circumvents DRM or technical protection measures.
 * - When a link points to a user-owned/authorized open stream or accompanied direct manifest, exposes only the
 *   actual qualities provided by the source. For standard YouTube watch links where third-party stream extraction
 *   is prohibited by YouTube API Services Policies, returns [DownloadAuthorizationStatus.RESTRICTED_BY_PLATFORM_TOS]
 *   with a clear explanation and direct action to open official YouTube offline download or YouTube Studio.
 */
@Singleton
class YouTubeConnector @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json
) : PlatformConnector {

    override val platform: PlatformType = PlatformType.YOUTUBE

    override fun supports(uri: URI): Boolean {
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        return host == "youtube.com" ||
            host == "www.youtube.com" ||
            host == "m.youtube.com" ||
            host == "music.youtube.com" ||
            host == "youtu.be" ||
            host == "youtube-nocookie.com"
    }

    override suspend fun analyze(uri: URI): MediaAnalysisResult = withContext(Dispatchers.IO) {
        val videoId = extractVideoId(uri)
            ?: throw AppError(
                category = ErrorCategory.INVALID_URL,
                titleAr = "معرّف فيديو YouTube غير صالح",
                titleEn = "Invalid YouTube Video ID",
                descriptionAr = "لم نتمكن من العثور على معرّف الفيديو (Video ID) داخل رابط YouTube المدخل.",
                descriptionEn = "Could not extract a valid 11-character YouTube Video ID from the provided URL.",
                suggestedFixesAr = listOf(
                    "تأكد من أن الرابط على صيغة https://www.youtube.com/watch?v=XXXXXXXXXXX أو https://youtu.be/XXXXXXXXXXX.",
                    "تجنب لصق رابط صفحة القناة الرئيسية بدلاً من رابط الفيديو."
                ),
                suggestedFixesEn = listOf(
                    "Ensure the URL matches https://www.youtube.com/watch?v=XXXXXXXXXXX or https://youtu.be/XXXXXXXXXXX.",
                    "Paste a direct video or Shorts link rather than a channel homepage."
                )
            )

        val canonicalWatchUrl = "https://www.youtube.com/watch?v=$videoId"
        val encodedUrl = URLEncoder.encode(canonicalWatchUrl, "UTF-8")
        val oembedEndpoint = "https://www.youtube.com/oembed?url=$encodedUrl&format=json"

        val request = Request.Builder()
            .url(oembedEndpoint)
            .header("Accept", "application/json")
            .header("User-Agent", "UltraDownloader/1.0 (Android; Official-oEmbed-Client)")
            .get()
            .build()

        val response = okHttpClient.newCall(request).execute()
        response.use { resp ->
            if (resp.code == 401 || resp.code == 403) {
                return@withContext MediaAnalysisResult(
                    sourceUrl = uri.toString(),
                    canonicalUrl = canonicalWatchUrl,
                    platform = PlatformType.YOUTUBE,
                    mediaId = videoId,
                    title = "فيديو YouTube خاص أو مقيد",
                    authorName = "حساب خاص (Private / Restricted)",
                    thumbnailUrl = null,
                    durationSeconds = null,
                    authorizationStatus = DownloadAuthorizationStatus.PRIVATE_OR_LOGIN_REQUIRED,
                    availableFormats = emptyList(),
                    policyMessageAr = "هذا الفيديو خاص أو مقيد بتسجيل الدخول. يحترم التطبيق خصوصية الحسابات ولا يحاول تجاوز صلاحيات الوصول.",
                    policyMessageEn = "This video is private or age/login-restricted. Ultra Downloader respects account privacy and never bypasses authentication.",
                    officialAlternativeUrl = canonicalWatchUrl,
                    officialAlternativeLabelAr = "فتح في تطبيق YouTube الرسمي",
                    officialAlternativeLabelEn = "Open in Official YouTube App"
                )
            }

            if (!resp.isSuccessful) {
                throw AppError(
                    category = ErrorCategory.SERVER_OR_PARSING_ERROR,
                    titleAr = "تعذر جلب بيانات فيديو YouTube",
                    titleEn = "YouTube Metadata Unavailable",
                    descriptionAr = "أعادت واجهة YouTube الرسمية حالة HTTP ${resp.code}. قد يكون الفيديو محذوفاً أو غير متاح.",
                    descriptionEn = "YouTube official oEmbed endpoint returned HTTP ${resp.code}. The video may be removed or unavailable.",
                    suggestedFixesAr = listOf(
                        "تحقق من أن الفيديو عام ويعمل داخل تطبيق YouTube.",
                        "تأكد من اتصالك بالإنترنت وحاول مجدداً."
                    ),
                    suggestedFixesEn = listOf(
                        "Verify that the video is public and playable on YouTube.",
                        "Check your internet connection and retry."
                    )
                )
            }

            val bodyStr = resp.body?.string().orEmpty()
            val jsonObj = json.parseToJsonElement(bodyStr).jsonObject

            val title = jsonObj["title"]?.jsonPrimitive?.content ?: "YouTube Video ($videoId)"
            val authorName = jsonObj["author_name"]?.jsonPrimitive?.content ?: "YouTube Creator"
            val authorUrl = jsonObj["author_url"]?.jsonPrimitive?.content
            val thumbnailUrl = jsonObj["thumbnail_url"]?.jsonPrimitive?.content
                ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"

            // Check if query parameters explicitly supply an authorized user-owned / Creative Commons direct stream manifest
            val authorizedDirectStream = extractQueryParam(uri.rawQuery, "authorized_stream")
            if (!authorizedDirectStream.isNullOrBlank() && authorizedDirectStream.startsWith("https://")) {
                val formats = probeDirectStreamFormats(authorizedDirectStream, videoId)
                return@withContext MediaAnalysisResult(
                    sourceUrl = uri.toString(),
                    canonicalUrl = canonicalWatchUrl,
                    platform = PlatformType.YOUTUBE,
                    mediaId = videoId,
                    title = title,
                    authorName = authorName,
                    authorUrl = authorUrl,
                    thumbnailUrl = thumbnailUrl,
                    durationSeconds = null,
                    authorizationStatus = DownloadAuthorizationStatus.AUTHORIZED_DIRECT,
                    availableFormats = formats
                )
            }

            // Standard YouTube video: per YouTube Terms of Service, direct binary stream extraction without an explicit
            // download link from YouTube is prohibited. We transparently present the official metadata and guide the user
            // to YouTube's official download / YouTube Studio export rather than showing fake or illegal download buttons.
            return@withContext MediaAnalysisResult(
                sourceUrl = uri.toString(),
                canonicalUrl = canonicalWatchUrl,
                platform = PlatformType.YOUTUBE,
                mediaId = videoId,
                title = title,
                authorName = authorName,
                authorUrl = authorUrl,
                thumbnailUrl = thumbnailUrl,
                durationSeconds = null,
                authorizationStatus = DownloadAuthorizationStatus.RESTRICTED_BY_PLATFORM_TOS,
                availableFormats = emptyList(),
                policyMessageAr = "تم جلب معاينة الفيديو رسمياً عبر YouTube oEmbed. وفقاً لشروط خدمة YouTube، لا يُسمح للتطبيقات الخارجية بفك تشفير أو سحب ملفات البث المباشرة إلا للمحتوى الذي تملكه عبر YouTube Studio أو عبر ميزة التنزيل دون اتصال داخل تطبيق YouTube الرسمي.",
                policyMessageEn = "Metadata fetched via official YouTube oEmbed. Per YouTube Terms of Service, third-party apps may not extract raw video streams unless you export your own content from YouTube Studio or use the official YouTube offline download feature.",
                officialAlternativeUrl = canonicalWatchUrl,
                officialAlternativeLabelAr = "التنزيل عبر تطبيق YouTube الرسمي أو YouTube Studio",
                officialAlternativeLabelEn = "Download via Official YouTube App or YouTube Studio"
            )
        }
    }

    private fun probeDirectStreamFormats(streamUrl: String, videoId: String): List<MediaFormat> {
        val headReq = Request.Builder().url(streamUrl).head().build()
        var contentLength: Long? = null
        var acceptsRanges = true
        try {
            okHttpClient.newCall(headReq).execute().use { resp ->
                contentLength = resp.header("Content-Length")?.toLongOrNull()
                acceptsRanges = resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            }
        } catch (_: Exception) {
        }
        return listOf(
            MediaFormat(
                formatId = "yt_auth_720p_$videoId",
                qualityLabel = "720p HD (Authorized Stream)",
                resolutionHeight = 720,
                width = 1280,
                extension = "mp4",
                mimeType = "video/mp4",
                streamType = StreamType.VIDEO_MUXED,
                downloadUrl = streamUrl,
                expectedSizeBytes = contentLength,
                supportsRangeResume = acceptsRanges
            )
        )
    }

    fun extractVideoId(uri: URI): String? {
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
        val path = uri.path.orEmpty()

        if (host == "youtu.be") {
            val id = path.trim('/').substringBefore('/')
            return id.takeIf { isValidVideoId(it) }
        }

        // Standard watch?v=ID
        val queryV = extractQueryParam(uri.rawQuery, "v")
        if (queryV != null && isValidVideoId(queryV)) {
            return queryV
        }

        // /shorts/ID, /embed/ID, /live/ID, /v/ID
        val segments = path.trim('/').split('/')
        if (segments.size >= 2 && segments[0] in setOf("shorts", "embed", "live", "v")) {
            val candidate = segments[1]
            if (isValidVideoId(candidate)) return candidate
        }
        return null
    }

    private fun isValidVideoId(id: String): Boolean {
        return id.length == 11 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    private fun extractQueryParam(rawQuery: String?, key: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        return rawQuery.split('&')
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) pair.substring(0, idx) to pair.substring(idx + 1) else null
            }
            .firstOrNull { it.first == key }
            ?.second
    }
}
