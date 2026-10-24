package com.ultradownloader.app.data.download

import com.ultradownloader.app.security.UrlSecurityValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class TransferProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
    val supportsResume: Boolean
)

/**
 * High-performance HTTP Range stream downloader.
 * - Supports Pause & Resume using `Range: bytes=<offset>-` and `HTTP 206 Partial Content`.
 * - Falls back gracefully to full stream overwrite if server returns `HTTP 200 OK`.
 * - Computes smoothed transfer speed (bytes/sec) and estimated time remaining (ETA).
 * - Handles transient network dropouts with bounded exponential backoff (1s, 2s, 4s).
 * - Validates target URL against SSRF before opening any socket.
 */
@Singleton
class HttpRangeDownloader @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val urlSecurityValidator: UrlSecurityValidator
) {
    companion object {
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB chunk buffer (zero UI freeze, low GC pressure)
        private const val PROGRESS_EMIT_INTERVAL_MS = 350L
        private const val MAX_AUTO_RETRIES = 3
    }

    suspend fun downloadWithResume(
        url: String,
        targetFile: File,
        resolveDns: Boolean = true,
        isPausedCheck: () -> Boolean = { false },
        onProgress: suspend (TransferProgress) -> Unit
    ): Long = withContext(Dispatchers.IO) {
        // Re-validate URL before connecting to prevent redirect/TOCTOU SSRF
        urlSecurityValidator.validateAndNormalize(url, resolveDns = resolveDns)

        var attempt = 0
        var lastException: Exception? = null

        while (attempt <= MAX_AUTO_RETRIES && coroutineContext.isActive) {
            try {
                return@withContext executeSingleTransferAttempt(
                    url = url,
                    targetFile = targetFile,
                    isPausedCheck = isPausedCheck,
                    onProgress = onProgress
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (paused: DownloadPausedException) {
                throw paused
            } catch (e: IOException) {
                lastException = e
                attempt++
                if (attempt > MAX_AUTO_RETRIES) break
                val backoffMs = (1000L * (1 shl (attempt - 1))).coerceAtMost(8000L)
                delay(backoffMs)
            }
        }
        throw lastException ?: IOException("Download failed after $MAX_AUTO_RETRIES retries")
    }

    private suspend fun executeSingleTransferAttempt(
        url: String,
        targetFile: File,
        isPausedCheck: () -> Boolean,
        onProgress: suspend (TransferProgress) -> Unit
    ): Long {
        targetFile.parentFile?.mkdirs()
        val existingBytes = if (targetFile.exists()) targetFile.length() else 0L

        val requestBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", "UltraDownloader/1.0 (Android; ResumableRangeEngine)")

        if (existingBytes > 0L) {
            requestBuilder.header("Range", "bytes=$existingBytes-")
        }

        okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
            if (response.code == 416) {
                // Range Not Satisfiable: file is already fully downloaded
                return existingBytes
            }

            if (!response.isSuccessful) {
                throw IOException("Server returned HTTP ${response.code}")
            }

            val isPartial = response.code == 206
            val supportsRange = isPartial || response.header("Accept-Ranges")?.contains("bytes", ignoreCase = true) == true
            val contentLength = response.body?.contentLength()?.takeIf { it > 0 } ?: -1L

            val startOffset = if (isPartial) existingBytes else 0L
            val totalExpectedBytes = if (contentLength > 0) startOffset + contentLength else -1L

            val bodyStream = response.body?.byteStream()
                ?: throw IOException("Empty response body stream")

            RandomAccessFile(targetFile, "rw").use { raf ->
                if (isPartial) {
                    raf.seek(startOffset)
                } else {
                    raf.setLength(0L)
                    raf.seek(0L)
                }

                val buffer = ByteArray(BUFFER_SIZE)
                var currentBytes = startOffset
                var windowBytes = 0L
                var windowStartTimeMs = System.currentTimeMillis()
                var smoothedSpeedBps = 0L

                bodyStream.use { input ->
                    while (coroutineContext.isActive) {
                        if (isPausedCheck()) {
                            throw DownloadPausedException(currentBytes)
                        }

                        val read = input.read(buffer)
                        if (read == -1) break

                        raf.write(buffer, 0, read)
                        currentBytes += read
                        windowBytes += read

                        val now = System.currentTimeMillis()
                        val elapsedMs = now - windowStartTimeMs
                        if (elapsedMs >= PROGRESS_EMIT_INTERVAL_MS) {
                            val instantSpeed = (windowBytes * 1000L) / elapsedMs.coerceAtLeast(1L)
                            smoothedSpeedBps = if (smoothedSpeedBps == 0L) {
                                instantSpeed
                            } else {
                                ((smoothedSpeedBps * 6L) + (instantSpeed * 4L)) / 10L
                            }

                            val etaSec = if (totalExpectedBytes > currentBytes && smoothedSpeedBps > 0L) {
                                (totalExpectedBytes - currentBytes) / smoothedSpeedBps
                            } else {
                                null
                            }

                            onProgress(
                                TransferProgress(
                                    downloadedBytes = currentBytes,
                                    totalBytes = totalExpectedBytes.coerceAtLeast(currentBytes),
                                    speedBytesPerSec = smoothedSpeedBps,
                                    etaSeconds = etaSec,
                                    supportsResume = supportsRange
                                )
                            )
                            windowBytes = 0L
                            windowStartTimeMs = now
                        }
                    }
                }

                onProgress(
                    TransferProgress(
                        downloadedBytes = currentBytes,
                        totalBytes = if (totalExpectedBytes > 0) totalExpectedBytes else currentBytes,
                        speedBytesPerSec = 0L,
                        etaSeconds = 0L,
                        supportsResume = supportsRange
                    )
                )
                return currentBytes
            }
        }
    }
}

class DownloadPausedException(val bytesSaved: Long) : Exception("Download paused at $bytesSaved bytes")
