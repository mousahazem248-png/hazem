package com.ultradownloader.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class StreamType {
    VIDEO_MUXED,      // Single MP4 containing both video and audio
    VIDEO_ADAPTIVE,   // Separate high-res video + separate audio stream requiring legal MediaMuxer merge
    AUDIO_ONLY        // Authorized MP3 / M4A audio stream
}

@Serializable
data class MediaFormat(
    val formatId: String,
    val qualityLabel: String,       // e.g. "360p", "480p", "720p HD", "1080p Full HD", "MP3 192kbps"
    val resolutionHeight: Int?,     // e.g. 360, 480, 720, 1080 (never fabricated if source doesn't offer it)
    val width: Int?,
    val extension: String,          // "mp4", "mp3", "m4a"
    val mimeType: String,           // "video/mp4", "audio/mpeg", "audio/mp4"
    val streamType: StreamType,
    val downloadUrl: String,
    val secondaryAudioUrl: String? = null, // Populated when streamType == VIDEO_ADAPTIVE
    val expectedSizeBytes: Long? = null,   // Populated from Content-Length / manifest when available
    val bitrateKbps: Int? = null,
    val supportsRangeResume: Boolean = true
) {
    val formattedExpectedSize: String?
        get() = expectedSizeBytes?.takeIf { it > 0 }?.let { bytes ->
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            if (mb >= 1024.0) {
                String.format(java.util.Locale.US, "%.2f GB", mb / 1024.0)
            } else {
                String.format(java.util.Locale.US, "%.1f MB", mb)
            }
        }
}
