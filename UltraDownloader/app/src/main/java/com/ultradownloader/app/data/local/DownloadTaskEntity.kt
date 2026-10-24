package com.ultradownloader.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ultradownloader.app.domain.model.DownloadStatus
import com.ultradownloader.app.domain.model.DownloadTask
import com.ultradownloader.app.domain.model.PlatformType
import com.ultradownloader.app.domain.model.StreamType

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val taskId: String,
    val sourceUrl: String,
    val downloadUrl: String,
    val secondaryAudioUrl: String?,
    val title: String,
    val authorName: String,
    val thumbnailUrl: String?,
    val platform: String,
    val qualityLabel: String,
    val extension: String,
    val mimeType: String,
    val streamType: String,
    val status: String,
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
    fun toDomain(): DownloadTask = DownloadTask(
        taskId = taskId,
        sourceUrl = sourceUrl,
        downloadUrl = downloadUrl,
        secondaryAudioUrl = secondaryAudioUrl,
        title = title,
        authorName = authorName,
        thumbnailUrl = thumbnailUrl,
        platform = runCatching { PlatformType.valueOf(platform) }.getOrDefault(PlatformType.DIRECT_MEDIA),
        qualityLabel = qualityLabel,
        extension = extension,
        mimeType = mimeType,
        streamType = runCatching { StreamType.valueOf(streamType) }.getOrDefault(StreamType.VIDEO_MUXED),
        status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.QUEUED),
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        speedBytesPerSec = speedBytesPerSec,
        etaSeconds = etaSeconds,
        supportsPauseResume = supportsPauseResume,
        outputUriString = outputUriString,
        localFilePath = localFilePath,
        sha256Checksum = sha256Checksum,
        errorMessageAr = errorMessageAr,
        errorMessageEn = errorMessageEn,
        retryCount = retryCount,
        createdAtEpochMs = createdAtEpochMs,
        completedAtEpochMs = completedAtEpochMs
    )

    companion object {
        fun fromDomain(task: DownloadTask): DownloadTaskEntity = DownloadTaskEntity(
            taskId = task.taskId,
            sourceUrl = task.sourceUrl,
            downloadUrl = task.downloadUrl,
            secondaryAudioUrl = task.secondaryAudioUrl,
            title = task.title,
            authorName = task.authorName,
            thumbnailUrl = task.thumbnailUrl,
            platform = task.platform.name,
            qualityLabel = task.qualityLabel,
            extension = task.extension,
            mimeType = task.mimeType,
            streamType = task.streamType.name,
            status = task.status.name,
            downloadedBytes = task.downloadedBytes,
            totalBytes = task.totalBytes,
            speedBytesPerSec = task.speedBytesPerSec,
            etaSeconds = task.etaSeconds,
            supportsPauseResume = task.supportsPauseResume,
            outputUriString = task.outputUriString,
            localFilePath = task.localFilePath,
            sha256Checksum = task.sha256Checksum,
            errorMessageAr = task.errorMessageAr,
            errorMessageEn = task.errorMessageEn,
            retryCount = task.retryCount,
            createdAtEpochMs = task.createdAtEpochMs,
            completedAtEpochMs = task.completedAtEpochMs
        )
    }
}
