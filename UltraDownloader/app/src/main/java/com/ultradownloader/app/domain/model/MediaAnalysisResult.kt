package com.ultradownloader.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadAuthorizationStatus {
    AUTHORIZED_DIRECT,          // Platform/source explicitly provides a direct downloadable stream
    RESTRICTED_BY_PLATFORM_TOS, // Platform metadata fetched via oEmbed, but direct file extraction is disallowed by ToS
    PRIVATE_OR_LOGIN_REQUIRED   // Content belongs to a private account or requires authentication (never bypassed)
}

@Serializable
data class MediaAnalysisResult(
    val sourceUrl: String,
    val canonicalUrl: String,
    val platform: PlatformType,
    val mediaId: String,
    val title: String,
    val authorName: String,
    val authorUrl: String? = null,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null,
    val authorizationStatus: DownloadAuthorizationStatus,
    val availableFormats: List<MediaFormat>,
    val policyMessageAr: String? = null,
    val policyMessageEn: String? = null,
    val officialAlternativeUrl: String? = null,
    val officialAlternativeLabelAr: String? = null,
    val officialAlternativeLabelEn: String? = null
) {
    val isDownloadPermitted: Boolean
        get() = authorizationStatus == DownloadAuthorizationStatus.AUTHORIZED_DIRECT && availableFormats.isNotEmpty()
}
