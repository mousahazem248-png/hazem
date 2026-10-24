package com.ultradownloader.app.domain.model

enum class ErrorCategory {
    INVALID_URL,
    UNSUPPORTED_PLATFORM,
    SSRF_SECURITY_BLOCK,
    INSECURE_HTTP_PROTOCOL,
    PLATFORM_POLICY_RESTRICTED,
    PRIVATE_CONTENT_RESTRICTED,
    NETWORK_DISCONNECTED,
    INSUFFICIENT_STORAGE,
    FILE_INTEGRITY_FAILED,
    SERVER_OR_PARSING_ERROR
}

data class AppError(
    val category: ErrorCategory,
    val titleAr: String,
    val titleEn: String,
    val descriptionAr: String,
    val descriptionEn: String,
    val suggestedFixesAr: List<String>,
    val suggestedFixesEn: List<String>,
    val officialFallbackUrl: String? = null
) : Exception(descriptionEn)
