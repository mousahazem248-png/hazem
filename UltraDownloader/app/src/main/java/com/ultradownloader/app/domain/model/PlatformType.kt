package com.ultradownloader.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlatformType(
    val displayNameAr: String,
    val displayNameEn: String,
    val badgeColorHex: Long
) {
    YOUTUBE("يوتيوب (YouTube)", "YouTube", 0xFFFF0033),
    TIKTOK("تيك توك (TikTok)", "TikTok", 0xFF00F2FE),
    INSTAGRAM("إنستغرام (Instagram)", "Instagram", 0xFFE1306C),
    DIRECT_MEDIA("رابط وسائط مباشر (Direct Media)", "Direct Media", 0xFF7C3AED),
    UNKNOWN("غير مدعوم (Unsupported)", "Unsupported", 0xFF64748B)
}
