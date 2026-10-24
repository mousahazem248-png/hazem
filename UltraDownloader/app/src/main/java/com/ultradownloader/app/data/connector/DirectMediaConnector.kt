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
 * Direct Media Connector for user-owned cloud media, Creative Commons videos,
 * and open HTTPS MP4/MP3/M4A/WebM links.
 * Performs a real HTTP HEAD/Range inspection to determine exact file size (`Content-Length`),
 * MIME type, and pause/resume capability (`Accept-Ranges: bytes`), without fabricating qualities
 * not actually served by the source.
 */
@Singleton
class DirectMediaConnector @Inject constructor(
    private val okHttpClient: OkHttpClient
) : PlatformConnector {

    override val platform: PlatformType = PlatformType.DIRECT_MEDIA

    override fun supports(uri: URI): Boolean {
        val path = uri.path?.lowercase(Locale.ROOT).orEmpty()
        return path.endsWith(".mp4") ||
            path.endsWith(".m4a") ||
            path.endsWith(".mp3") ||
            path.endsWith(".webm") ||
            path.endsWith(".mov")
    }

    override suspend fun analyze(uri: URI): MediaAnalysisResult = withContext(Dispatchers.IO) {
        val urlStr = uri.toString()
        val fileName = uri.path.substringAfterLast('/').ifBlank { "direct_media.mp4" }
        val ext = fileName.substringAfterLast('.', "mp4").lowercase(Locale.ROOT)
        val baseTitle = fileName.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')

        val request = Request.Builder()
            .url(urlStr)
            .header("User-Agent", "UltraDownloader/1.0 (Android; DirectMediaInspector)")
            .head()
            .build()

        okHttpClient.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) {
                throw AppError(
                    category = ErrorCategory.SERVER_OR_PARSING_ERROR,
                    titleAr = "الملف المباشر غير متاح (HTTP ${resp.code})",
                    titleEn = "Direct Media Unreachable (HTTP ${resp.code})",
                    descriptionAr = "تعذر الوصول إلى ملف الوسائط المباشر على الخادم البعيد.",
                    descriptionEn = "The remote server returned HTTP ${resp.code} for the direct media link.",
                    suggestedFixesAr = listOf(
                        "تأكد من أن الرابط يعمل ولا يتطلب صلاحيات دخول خاصة.",
                        "تحقق من اتصال الإنترنت لديك."
                    ),
                    suggestedFixesEn = listOf(
                        "Confirm the link is publicly accessible.",
                        "Check your internet connection."
                    )
                )
            }

            val contentLength = resp.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
            val acceptRanges = resp.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            val contentType = resp.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT)
                ?: defaultMimeForExtension(ext)

            // Infer true resolution only if explicitly indicated in filename/metadata; never claim 4K/8K if unverified
            val detectedHeight = inferResolutionFromName(fileName)
            val isAudio = ext == "mp3" || ext == "m4a" || contentType.startsWith("audio/")

            val qualityLabel = if (isAudio) {
                "Original Audio (${ext.uppercase(Locale.ROOT)})"
            } else if (detectedHeight != null) {
                "${detectedHeight}p (${ext.uppercase(Locale.ROOT)})"
            } else {
                "Original Source Quality (${ext.uppercase(Locale.ROOT)})"
            }

            val format = MediaFormat(
                formatId = "direct_${ext}_${detectedHeight ?: "orig"}",
                qualityLabel = qualityLabel,
                resolutionHeight = if (isAudio) null else detectedHeight,
                width = null,
                extension = if (isAudio && ext == "mp3") "mp3" else if (isAudio) "m4a" else "mp4",
                mimeType = contentType,
                streamType = if (isAudio) StreamType.AUDIO_ONLY else StreamType.VIDEO_MUXED,
                downloadUrl = urlStr,
                expectedSizeBytes = contentLength,
                supportsRangeResume = acceptRanges
            )

            return@withContext MediaAnalysisResult(
                sourceUrl = urlStr,
                canonicalUrl = urlStr,
                platform = PlatformType.DIRECT_MEDIA,
                mediaId = fileName.hashCode().toUInt().toString(),
                title = baseTitle.ifBlank { "Direct Media File" },
                authorName = uri.host ?: "Direct Source",
                thumbnailUrl = null,
                durationSeconds = null,
                authorizationStatus = DownloadAuthorizationStatus.AUTHORIZED_DIRECT,
                availableFormats = listOf(format)
            )
        }
    }

    private fun inferResolutionFromName(name: String): Int? {
        val match = Regex("(2160|1440|1080|720|480|360)p", RegexOption.IGNORE_CASE).find(name)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun defaultMimeForExtension(ext: String): String = when (ext) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        else -> "video/mp4"
    }
}
