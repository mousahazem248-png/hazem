package com.ultradownloader.app.data.storage

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.ultradownloader.app.domain.model.AppError
import com.ultradownloader.app.domain.model.ErrorCategory
import com.ultradownloader.app.security.FileSecurityValidator
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scoped Storage & MediaStore Manager.
 * - Requests minimal permissions (zero storage permission needed on Android 10+ / API 29+).
 * - Checks available free disk space (with a 50 MB safety buffer) before starting or resuming downloads.
 * - Exports verified media files into `Movies/UltraDownloader` or `Music/UltraDownloader` via [MediaStore]
 *   using `IS_PENDING = 1` until the copy finishes atomically.
 * - Provides safe Open, Share, and Delete operations.
 */
@Singleton
class MediaStorageManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val fileSecurityValidator: FileSecurityValidator
) {
    companion object {
        private const val SAFETY_FREE_SPACE_BUFFER_BYTES = 50L * 1024L * 1024L // 50 MB buffer
    }

    fun getStagingDirectory(): File {
        val dir = File(context.filesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Verifies that the device storage partition has enough free bytes for [requiredBytes].
     */
    fun ensureSufficientStorage(requiredBytes: Long) {
        val stagingDir = getStagingDirectory()
        val stat = StatFs(stagingDir.absolutePath)
        val availableBytes = stat.availableBytes
        val needed = if (requiredBytes > 0) requiredBytes + SAFETY_FREE_SPACE_BUFFER_BYTES else SAFETY_FREE_SPACE_BUFFER_BYTES

        if (availableBytes < needed) {
            val availMb = availableBytes / (1024 * 1024)
            val reqMb = needed / (1024 * 1024)
            throw AppError(
                category = ErrorCategory.INSUFFICIENT_STORAGE,
                titleAr = "مساحة التخزين غير كافية",
                titleEn = "Insufficient Storage Space",
                descriptionAr = "المساحة المتاحة حالياً ($availMb MB) أقل من المساحة المطلوبة لتنزيل الملف بأمان ($reqMb MB).",
                descriptionEn = "Available storage ($availMb MB) is less than the required space ($reqMb MB) including safety buffer.",
                suggestedFixesAr = listOf(
                    "احذف بعض الفيديوهات أو الملفات القديمة لتوفير مساحة على الهاتف.",
                    "اختر دقة فيديو أقل حجماً (مثل 480p أو 360p) أو صيغة الصوت MP3."
                ),
                suggestedFixesEn = listOf(
                    "Free up storage space on your device by deleting unneeded files.",
                    "Select a lower video resolution (e.g., 480p or 360p) or MP3 audio."
                )
            )
        }
    }

    /**
     * Exports a verified local file from the app staging directory into public Android [MediaStore]
     * (`Movies/UltraDownloader` for video, `Music/UltraDownloader` for audio) so the user can access it
     * in their Gallery / Files app without requiring broad storage permissions.
     */
    suspend fun publishVerifiedFileToMediaStore(
        verifiedFile: File,
        rawTitle: String,
        extension: String,
        mimeType: String
    ): Uri = withContext(Dispatchers.IO) {
        val safeFileName = fileSecurityValidator.sanitizeFileName(rawTitle, extension)
        val isAudio = mimeType.startsWith("audio/") || extension.equals("mp3", ignoreCase = true) || extension.equals("m4a", ignoreCase = true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = if (isAudio) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relativeFolder = if (isAudio) {
                "${Environment.DIRECTORY_MUSIC}/UltraDownloader"
            } else {
                "${Environment.DIRECTORY_MOVIES}/UltraDownloader"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeFileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativeFolder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }

            val itemUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Failed to create MediaStore entry for $safeFileName")

            try {
                resolver.openOutputStream(itemUri)?.use { outStream ->
                    FileInputStream(verifiedFile).use { inStream ->
                        inStream.copyTo(outStream, bufferSize = 64 * 1024)
                    }
                } ?: throw IllegalStateException("Unable to open MediaStore OutputStream")

                val publishValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(itemUri, publishValues, null, null)
                return@withContext itemUri
            } catch (e: Exception) {
                resolver.delete(itemUri, null, null)
                throw e
            }
        } else {
            // Android 8.0 - 9.0 (API 26-28)
            val basePublicDir = Environment.getExternalStoragePublicDirectory(
                if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
            )
            val ultraDir = File(basePublicDir, "UltraDownloader").apply { mkdirs() }
            val destFile = fileSecurityValidator.resolveSafeChildFile(ultraDir, safeFileName)
            verifiedFile.copyTo(destFile, overwrite = true)
            return@withContext FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destFile
            )
        }
    }

    fun createOpenFileIntent(uriString: String, mimeType: String): Intent {
        val uri = Uri.parse(uriString)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun createShareFileIntent(uriString: String, mimeType: String, title: String): Intent {
        val uri = Uri.parse(uriString)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TITLE, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(shareIntent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun deleteDownloadedMedia(uriString: String?, localFilePath: String?): Boolean = withContext(Dispatchers.IO) {
        var deleted = false
        if (!uriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(uriString)
                val rows = context.contentResolver.delete(uri, null, null)
                if (rows > 0) deleted = true
            } catch (_: Exception) {}
        }
        if (!localFilePath.isNullOrBlank()) {
            try {
                val file = File(localFilePath)
                if (file.exists() && file.delete()) deleted = true
            } catch (_: Exception) {}
        }
        deleted
    }
}
