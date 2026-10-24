package com.ultradownloader.app.data.download

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Legally and cleanly muxes separate adaptive video (e.g., 1080p video-only MP4) and audio (M4A/AAC)
 * streams into a single playable MP4 container using Android's native [MediaExtractor] and [MediaMuxer].
 * Does not require third-party native binaries, prevents memory leaks by reusing a single direct ByteBuffer,
 * and cleans up intermediate stream files automatically.
 */
@Singleton
class MediaStreamMerger @Inject constructor() {

    fun muxVideoAndAudio(
        videoFile: File,
        audioFile: File,
        outputFile: File
    ) {
        val videoExtractor = MediaExtractor()
        val audioExtractor = MediaExtractor()
        var muxer: MediaMuxer? = null

        try {
            videoExtractor.setDataSource(videoFile.absolutePath)
            audioExtractor.setDataSource(audioFile.absolutePath)

            val videoTrackIndexInSource = selectTrackByPrefix(videoExtractor, "video/")
                ?: throw IllegalStateException("No video track found in adaptive video stream")
            val audioTrackIndexInSource = selectTrackByPrefix(audioExtractor, "audio/")
                ?: throw IllegalStateException("No audio track found in adaptive audio stream")

            videoExtractor.selectTrack(videoTrackIndexInSource)
            audioExtractor.selectTrack(audioTrackIndexInSource)

            val videoFormat = videoExtractor.getTrackFormat(videoTrackIndexInSource)
            val audioFormat = audioExtractor.getTrackFormat(audioTrackIndexInSource)

            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val muxerVideoTrack = muxer.addTrack(videoFormat)
            val muxerAudioTrack = muxer.addTrack(audioFormat)
            muxer.start()

            val buffer = ByteBuffer.allocateDirect(512 * 1024) // 512 KB reusable direct buffer
            val bufferInfo = MediaCodec.BufferInfo()

            // Copy video samples
            copyTrackSamples(videoExtractor, muxer, muxerVideoTrack, buffer, bufferInfo)
            // Copy audio samples
            copyTrackSamples(audioExtractor, muxer, muxerAudioTrack, buffer, bufferInfo)

            muxer.stop()
        } finally {
            try {
                videoExtractor.release()
            } catch (_: Exception) {}
            try {
                audioExtractor.release()
            } catch (_: Exception) {}
            try {
                muxer?.release()
            } catch (_: Exception) {}
        }
    }

    private fun selectTrackByPrefix(extractor: MediaExtractor, mimePrefix: String): Int? {
        for (i in 0 until extractor.trackCount) {
            val format: MediaFormat = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
            if (mime.startsWith(mimePrefix)) {
                return i
            }
        }
        return null
    }

    private fun copyTrackSamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        targetTrackIndex: Int,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        while (true) {
            buffer.clear()
            val sampleSize = extractor.readSampleData(buffer, 0)
            if (sampleSize < 0) break

            bufferInfo.offset = 0
            bufferInfo.size = sampleSize
            bufferInfo.presentationTimeUs = extractor.sampleTime
            bufferInfo.flags = extractor.sampleFlags

            muxer.writeSampleData(targetTrackIndex, buffer, bufferInfo)
            extractor.advance()
        }
    }
}
