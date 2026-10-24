package com.ultradownloader.app.data.download

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ultradownloader.app.data.local.DownloadTaskDao
import com.ultradownloader.app.data.local.DownloadTaskEntity
import com.ultradownloader.app.data.local.UserPreferencesRepository
import com.ultradownloader.app.data.storage.MediaStorageManager
import com.ultradownloader.app.domain.model.DownloadStatus
import com.ultradownloader.app.domain.model.DownloadTask
import com.ultradownloader.app.domain.model.MediaAnalysisResult
import com.ultradownloader.app.domain.model.MediaFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManagerRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadTaskDao: DownloadTaskDao,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val mediaStorageManager: MediaStorageManager
) {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    val allTasksFlow: Flow<List<DownloadTask>> =
        downloadTaskDao.observeAllTasks().map { list -> list.map { it.toDomain() } }

    val activeTasksFlow: Flow<List<DownloadTask>> =
        downloadTaskDao.observeActiveTasks().map { list -> list.map { it.toDomain() } }

    val completedTasksFlow: Flow<List<DownloadTask>> =
        downloadTaskDao.observeCompletedTasks().map { list -> list.map { it.toDomain() } }

    suspend fun enqueueDownload(
        analysis: MediaAnalysisResult,
        selectedFormat: MediaFormat
    ): DownloadTask {
        val taskId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val newTask = DownloadTask(
            taskId = taskId,
            sourceUrl = analysis.sourceUrl,
            downloadUrl = selectedFormat.downloadUrl,
            secondaryAudioUrl = selectedFormat.secondaryAudioUrl,
            title = analysis.title,
            authorName = analysis.authorName,
            thumbnailUrl = analysis.thumbnailUrl,
            platform = analysis.platform,
            qualityLabel = selectedFormat.qualityLabel,
            extension = selectedFormat.extension,
            mimeType = selectedFormat.mimeType,
            streamType = selectedFormat.streamType,
            status = DownloadStatus.QUEUED,
            downloadedBytes = 0L,
            totalBytes = selectedFormat.expectedSizeBytes ?: 0L,
            speedBytesPerSec = 0L,
            etaSeconds = null,
            supportsPauseResume = selectedFormat.supportsRangeResume,
            outputUriString = null,
            localFilePath = null,
            sha256Checksum = null,
            errorMessageAr = null,
            errorMessageEn = null,
            retryCount = 0,
            createdAtEpochMs = now,
            completedAtEpochMs = null
        )

        downloadTaskDao.upsertTask(DownloadTaskEntity.fromDomain(newTask))
        scheduleWorkerForTask(taskId)
        return newTask
    }

    suspend fun pauseDownload(taskId: String) {
        val current = downloadTaskDao.getTaskById(taskId) ?: return
        downloadTaskDao.updateStatusAndError(
            taskId = taskId,
            status = DownloadStatus.PAUSED.name,
            errorAr = null,
            errorEn = null,
            retryCount = current.retryCount
        )
        workManager.cancelUniqueWork(uniqueWorkName(taskId))
    }

    suspend fun resumeOrRetryDownload(taskId: String) {
        val current = downloadTaskDao.getTaskById(taskId) ?: return
        downloadTaskDao.updateStatusAndError(
            taskId = taskId,
            status = DownloadStatus.QUEUED.name,
            errorAr = null,
            errorEn = null,
            retryCount = current.retryCount
        )
        scheduleWorkerForTask(taskId)
    }

    suspend fun cancelAndDeleteTask(taskId: String, deleteMediaFromDisk: Boolean = true) {
        workManager.cancelUniqueWork(uniqueWorkName(taskId))
        val current = downloadTaskDao.getTaskById(taskId)
        if (current != null && deleteMediaFromDisk) {
            mediaStorageManager.deleteDownloadedMedia(current.outputUriString, current.localFilePath)
            // Also clean up any staging partial files
            val stagingDir = mediaStorageManager.getStagingDirectory()
            stagingDir.listFiles()?.filter { it.name.startsWith(taskId) }?.forEach {
                runCatching { it.delete() }
            }
        }
        downloadTaskDao.deleteById(taskId)
    }

    suspend fun getTaskById(taskId: String): DownloadTask? {
        return downloadTaskDao.getTaskById(taskId)?.toDomain()
    }

    private suspend fun scheduleWorkerForTask(taskId: String) {
        val prefs = userPreferencesRepository.settingsFlow.first()
        val networkType = if (prefs.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val request = OneTimeWorkRequestBuilder<MediaDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(MediaDownloadWorker.KEY_TASK_ID to taskId))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .addTag("ultra_download_worker")
            .build()

        workManager.enqueueUniqueWork(
            uniqueWorkName(taskId),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun uniqueWorkName(taskId: String): String = "ultra_download_$taskId"
}
