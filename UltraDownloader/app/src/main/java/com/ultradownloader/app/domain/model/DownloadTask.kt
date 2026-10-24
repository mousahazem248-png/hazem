package com.ultradownloader.app.domain.model

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    MERGING_STREAMS,
    VERIFYING_INTEGRITY,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class DownloadTask(
    val taskId: String,
    val sourceUrl: String,
    val downloadUrl: String,
    val secondaryAudioUrl: String?,
    val title: String,
    val authorName: String,
    val thumbnailUrl: String?,
    val platform: PlatformType,
    val qualityLabel: String,
    val extension: String,
    val mimeType: String,
    val streamType: StreamType,
    val status: DownloadStatus,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
    val supportsPauseResume: Boolean,
    val outputUriString: String?,
    val localFilePath: String?,
    val sha256Checksum: String?,
    val errorMessageAr: String?,
    val errorMessageEn: String?,
    val retryCount: Int,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long?
) {
    val progressPercent: Int
        get() = if (totalBytes > 0) {
            ((downloadedBytes * 100L) / totalBytes).coerceIn(0L, 100L).toInt()
        } else if (status == DownloadStatus.COMPLETED) {
            100
        } else {
            0
        }

    val progressFraction: Float
        get() = if (totalBytes > 0) {
            (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
        } else if (status == DownloadStatus.COMPLETED) {
            1f
        } else {
            0f
        }
}
