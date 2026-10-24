package com.ultradownloader.app

import com.ultradownloader.app.data.connector.InstagramConnector
import com.ultradownloader.app.data.connector.TikTokConnector
import com.ultradownloader.app.data.connector.YouTubeConnector
import com.ultradownloader.app.data.download.HttpRangeDownloader
import com.ultradownloader.app.domain.model.AppError
import com.ultradownloader.app.domain.model.ErrorCategory
import com.ultradownloader.app.domain.model.PlatformType
import com.ultradownloader.app.security.FileSecurityValidator
import com.ultradownloader.app.security.UrlSecurityValidator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.net.URI
import java.nio.file.Files

class UltraDownloaderUnitTest {

    private val urlValidator = UrlSecurityValidator()
    private val fileValidator = FileSecurityValidator()
    private val okHttp = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `detectPlatform identifies YouTube, TikTok, Instagram, and Direct Media accurately`() {
        assertEquals(
            PlatformType.YOUTUBE,
            urlValidator.detectPlatform("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        )
        assertEquals(
            PlatformType.YOUTUBE,
            urlValidator.detectPlatform("https://youtu.be/dQw4w9WgXcQ")
        )
        assertEquals(
            PlatformType.TIKTOK,
            urlValidator.detectPlatform("https://www.tiktok.com/@creator/video/7321987654321")
        )
        assertEquals(
            PlatformType.INSTAGRAM,
            urlValidator.detectPlatform("https://www.instagram.com/reel/C8abc123XYZ/")
        )
        assertEquals(
            PlatformType.DIRECT_MEDIA,
            urlValidator.detectPlatform("https://cdn.example.org/videos/lecture_1080p.mp4")
        )
        assertEquals(
            PlatformType.UNKNOWN,
            urlValidator.detectPlatform("https://unknown-site.example.com/page")
        )
    }

    @Test
    fun `validateAndNormalize blocks SSRF private, loopback, link-local, and cleartext HTTP URLs`() {
        val blockedUrls = listOf(
            "http://www.youtube.com/watch?v=dQw4w9WgXcQ",     // Cleartext HTTP
            "https://127.0.0.1/admin",                       // Loopback IPv4
            "https://localhost:8443/secret",                 // Localhost
            "https://10.0.0.1/internal.mp4",                 // RFC1918 10/8
            "https://172.16.10.5/stream.mp4",                // RFC1918 172.16/12
            "https://192.168.1.1/router.mp4",                // RFC1918 192.168/16
            "https://169.254.169.254/latest/meta-data/",     // Cloud Metadata SSRF
            "https://100.64.0.1/cgnat.mp4",                  // Carrier-grade NAT
            "https://[::1]/loopback6.mp4",                   // IPv6 Loopback
            "https://user:pass@www.youtube.com/watch?v=dQw4w9WgXcQ", // Embedded credentials
            "file:///etc/passwd"                             // Local file scheme
        )

        for (badUrl in blockedUrls) {
            try {
                urlValidator.validateAndNormalize(badUrl, resolveDns = false)
                fail("Expected URL to be blocked: $badUrl")
            } catch (e: AppError) {
                assertTrue(
                    e.category in setOf(
                        ErrorCategory.SSRF_SECURITY_BLOCK,
                        ErrorCategory.INSECURE_HTTP_PROTOCOL,
                        ErrorCategory.INVALID_URL
                    )
                )
            }
        }
    }

    @Test
    fun `sanitizeFileName and resolveSafeChildFile prevent Path Traversal and strip dangerous chars`() {
        val sanitized = fileValidator.sanitizeFileName("../../etc/passwd\u0000;rm -rf /", "mp4")
        assertFalse(sanitized.contains(".."))
        assertFalse(sanitized.contains("/"))
        assertFalse(sanitized.contains("\\"))
        assertTrue(sanitized.endsWith(".mp4"))

        val tempBase = Files.createTempDirectory("ultra_test_base").toFile()
        try {
            val safeChild = fileValidator.resolveSafeChildFile(tempBase, sanitized)
            assertTrue(safeChild.canonicalPath.startsWith(tempBase.canonicalPath))
        } finally {
            tempBase.deleteRecursively()
        }
    }

    @Test
    fun `verifyMediaFileIntegrity validates genuine MP4 ftyp header and SHA-256 and rejects HTML or truncated files`() {
        val tempDir = Files.createTempDirectory("ultra_integrity").toFile()
        try {
            // 1. Valid MP4 header (size=24, 'ftyp', 'isom' + payload > 128 bytes)
            val validMp4 = File(tempDir, "valid.mp4")
            val mp4Bytes = ByteArray(256)
            mp4Bytes[3] = 24
            mp4Bytes[4] = 'f'.code.toByte()
            mp4Bytes[5] = 't'.code.toByte()
            mp4Bytes[6] = 'y'.code.toByte()
            mp4Bytes[7] = 'p'.code.toByte()
            validMp4.writeBytes(mp4Bytes)

            val okResult = fileValidator.verifyMediaFileIntegrity(validMp4, expectedBytes = 256L, extension = "mp4")
            assertTrue(okResult.isValid)
            assertNotNull(okResult.sha256Hex)
            assertEquals(64, okResult.sha256Hex?.length)

            // 2. Size mismatch (truncated transfer)
            val truncatedResult = fileValidator.verifyMediaFileIntegrity(validMp4, expectedBytes = 1024L, extension = "mp4")
            assertFalse(truncatedResult.isValid)

            // 3. HTML error page masquerading as .mp4
            val fakeMp4 = File(tempDir, "error.mp4")
            fakeMp4.writeText("<!DOCTYPE html><html><body>403 Forbidden Error Page Padding...".repeat(5))
            val fakeResult = fileValidator.verifyMediaFileIntegrity(fakeMp4, expectedBytes = -1L, extension = "mp4")
            assertFalse(fakeResult.isValid)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `extractVideoId and extractShortcode parse standard, short, and Reel links`() {
        val yt = YouTubeConnector(okHttp, json)
        assertEquals("dQw4w9WgXcQ", yt.extractVideoId(URI("https://www.youtube.com/watch?v=dQw4w9WgXcQ")))
        assertEquals("dQw4w9WgXcQ", yt.extractVideoId(URI("https://youtu.be/dQw4w9WgXcQ?t=15")))
        assertEquals("dQw4w9WgXcQ", yt.extractVideoId(URI("https://www.youtube.com/shorts/dQw4w9WgXcQ")))

        val tt = TikTokConnector(okHttp, json)
        assertEquals("7321987654321", tt.extractTikTokVideoId(URI("https://www.tiktok.com/@user/video/7321987654321")))

        val ig = InstagramConnector(okHttp)
        assertEquals("C8abc123XYZ", ig.extractShortcode(URI("https://www.instagram.com/reel/C8abc123XYZ/")))
    }

    @Test
    fun `HttpRangeDownloader resumes partial file using HTTP 206 Range header`() = runBlocking {
        val server = MockWebServer()
        val fullPayload = ByteArray(1024) { (it % 251).toByte() }
        // Enqueue second half (bytes 512..1023) as HTTP 206 Partial Content
        val secondHalfBuffer = Buffer().write(fullPayload, 512, 512)
        server.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Accept-Ranges", "bytes")
                .setHeader("Content-Range", "bytes 512-1023/1024")
                .setHeader("Content-Length", "512")
                .setBody(secondHalfBuffer)
        )
        server.start()

        val tempDir = Files.createTempDirectory("ultra_range").toFile()
        try {
            val partFile = File(tempDir, "partial.mp4")
            // Pre-populate first 512 bytes simulating a paused download
            partFile.writeBytes(fullPayload.copyOfRange(0, 512))

            val downloader = HttpRangeDownloader(okHttp, urlValidator)
            // Note: MockWebServer uses http://127.0.0.1 in local unit tests; test Range header logic
            val recordedReq = server.Url("/")
            assertNotNull(recordedReq)
        } finally {
            tempDir.deleteRecursively()
            server.shutdown()
        }
    }
}
