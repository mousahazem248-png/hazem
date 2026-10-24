package com.ultradownloader.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadTaskDao {

    @Query("SELECT * FROM download_tasks ORDER BY createdAtEpochMs DESC")
    fun observeAllTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED', 'DOWNLOADING', 'MERGING_STREAMS', 'VERIFYING_INTEGRITY', 'PAUSED', 'FAILED') ORDER BY createdAtEpochMs DESC")
    fun observeActiveTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status = 'COMPLETED' ORDER BY completedAtEpochMs DESC")
    fun observeCompletedTasks(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE taskId = :taskId LIMIT 1")
    suspend fun getTaskById(taskId: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(entity: DownloadTaskEntity)

    @Query(
        """
        UPDATE download_tasks
        SET status = :status,
            downloadedBytes = :downloadedBytes,
            totalBytes = :totalBytes,
            speedBytesPerSec = :speedBytesPerSec,
            etaSeconds = :etaSeconds,
            supportsPauseResume = :supportsPauseResume
        WHERE taskId = :taskId
        """
    )
    suspend fun updateProgress(
        taskId: String,
        status: String,
        downloadedBytes: Long,
        totalBytes: Long,
        speedBytesPerSec: Long,
        etaSeconds: Long?,
        supportsPauseResume: Boolean
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = 'COMPLETED',
            downloadedBytes = :finalBytes,
            totalBytes = :finalBytes,
            speedBytesPerSec = 0,
            etaSeconds = 0,
            outputUriString = :outputUriString,
            localFilePath = :localFilePath,
            sha256Checksum = :sha256Checksum,
            completedAtEpochMs = :completedAtEpochMs,
            errorMessageAr = NULL,
            errorMessageEn = NULL
        WHERE taskId = :taskId
        """
    )
    suspend fun markCompleted(
        taskId: String,
        finalBytes: Long,
        outputUriString: String,
        localFilePath: String,
        sha256Checksum: String,
        completedAtEpochMs: Long
    )

    @Query(
        """
        UPDATE download_tasks
        SET status = :status,
            speedBytesPerSec = 0,
            etaSeconds = NULL,
            errorMessageAr = :errorAr,
            errorMessageEn = :errorEn,
            retryCount = :retryCount
        WHERE taskId = :taskId
        """
    )
    suspend fun updateStatusAndError(
        taskId: String,
        status: String,
        errorAr: String?,
        errorEn: String?,
        retryCount: Int
    )

    @Query("DELETE FROM download_tasks WHERE taskId = :taskId")
    suspend fun deleteById(taskId: String)
}
