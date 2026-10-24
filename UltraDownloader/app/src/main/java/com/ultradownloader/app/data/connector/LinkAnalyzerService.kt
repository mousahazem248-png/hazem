package com.ultradownloader.app.data.connector

import com.ultradownloader.app.domain.model.AppError
import com.ultradownloader.app.domain.model.ErrorCategory
import com.ultradownloader.app.domain.model.MediaAnalysisResult
import com.ultradownloader.app.security.UrlSecurityValidator
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates security validation (HTTPS + SSRF check) and dispatches the link
 * to the matching isolated [PlatformConnector].
 */
@Singleton
class LinkAnalyzerService @Inject constructor(
    private val urlSecurityValidator: UrlSecurityValidator,
    private val youTubeConnector: YouTubeConnector,
    private val tikTokConnector: TikTokConnector,
    private val instagramConnector: InstagramConnector,
    private val directMediaConnector: DirectMediaConnector
) {
    private val connectors: List<PlatformConnector>
        get() = listOf(
            youTubeConnector,
            tikTokConnector,
            instagramConnector,
            directMediaConnector
        )

    suspend fun analyzeUrl(rawUrl: String, resolveDns: Boolean = true): MediaAnalysisResult {
        val safeUri = urlSecurityValidator.validateAndNormalize(rawUrl, resolveDns = resolveDns)
        val connector = connectors.firstOrNull { it.supports(safeUri) }
            ?: throw AppError(
                category = ErrorCategory.UNSUPPORTED_PLATFORM,
                titleAr = "منصة غير مدعومة حالياً",
                titleEn = "Unsupported Platform",
                descriptionAr = "النطاق (${safeUri.host}) غير مدعوم. يدعم التطبيق روابط YouTube وTikTok وInstagram والروابط المباشرة لملفات MP4/MP3 الآمنة.",
                descriptionEn = "The domain (${safeUri.host}) is not supported. Ultra Downloader supports YouTube, TikTok, Instagram, and direct HTTPS MP4/MP3 links.",
                suggestedFixesAr = listOf(
                    "أدخل رابطاً من YouTube أو TikTok أو Instagram.",
                    "أو أدخل رابطاً مباشراً ينتهي بـ .mp4 أو .mp3 عبر HTTPS."
                ),
                suggestedFixesEn = listOf(
                    "Paste a link from YouTube, TikTok, or Instagram.",
                    "Or provide a direct HTTPS link ending in .mp4 or .mp3."
                )
            )
        return connector.analyze(safeUri)
    }
}
