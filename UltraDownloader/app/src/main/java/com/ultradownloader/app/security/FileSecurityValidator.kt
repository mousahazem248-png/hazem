package com.ultradownloader.app.security

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File & Storage Security Validator.
 * - Prevents Path Traversal attacks (`../`, `..\\`, null bytes, control chars, reserved OS names).
 * - Verifies downloaded file magic bytes (MP4 `ftyp` box, MP3 `ID3` or MPEG sync frame) to ensure
 *   no executable script or HTML error page is masquerading as a media file.
 * - Computes SHA-256 checksums and validates exact file size before marking any download complete.
 */
@Singleton
class FileSecurityValidator @Inject constructor() {

    companion object {
        private val ALLOWED_EXTENSIONS = setOf("mp4", "m4a", "mp3", "webm", "mov")
        private val RESERVED_WINDOWS_NAMES = setOf(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9"
        )
        private const val MAX_BASENAME_LENGTH = 80
    }

    /**
     * Produces a safe, deterministic filename free from Path Traversal sequences or shell metacharacters.
     */
    fun sanitizeFileName(rawTitle: String, desiredExtension: String): String {
        val ext = desiredExtension.lowercase(Locale.ROOT).trim().removePrefix(".")
        val safeExt = if (ext in ALLOWED_EXTENSIONS) ext else "mp4"

        // Strip null bytes, path separators, traversal sequences, and control characters
        var cleaned = rawTitle
            .replace("\u0000", "")
            .replace("..", "_")
            .replace("/", "_")
            .replace("\\\\", "_")
            .replace(Regex("[<>:\"|?*\r\n\t`$;&|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trim('.')

        if (cleaned.isEmpty() || cleaned.lowercase(Locale.ROOT) in RESERVED_WINDOWS_NAMES) {
            cleaned = "ultra_media_${System.currentTimeMillis()}"
        }

        if (cleaned.length > MAX_BASENAME_LENGTH) {
            cleaned = cleaned.substring(0, MAX_BASENAME_LENGTH).trim().trim('.')
        }

        return "${cleaned}.${safeExt}"
    }

    /**
     * Resolves a target file inside [baseDir] and verifies via canonical paths that the file
     * cannot escape [baseDir] via symlinks or `..` segments.
     */
    fun resolveSafeChildFile(baseDir: File, fileName: String): File {
        val canonicalBase = baseDir.canonicalFile
        val candidate = File(canonicalBase, fileName).canonicalFile
        val basePathWithSlash = canonicalBase.path + File.separator
        if (!candidate.path.startsWith(basePathWithSlash)) {
            throw SecurityException("Path traversal attempt blocked: ${candidate.path} is outside ${canonicalBase.path}")
        }
        return candidate
    }

    /**
     * Verifies that a downloaded file is non-empty, matches [expectedBytes] (when positive),
     * and starts with a valid media container signature (MP4/M4A `ftyp` or MP3 `ID3`/sync word)
     * rather than an HTML error page or executable binary.
     */
    fun verifyMediaFileIntegrity(
        file: File,
        expectedBytes: Long,
        extension: String
    ): IntegrityVerificationResult {
        if (!file.exists() || !file.isFile) {
            return IntegrityVerificationResult(
                isValid = false,
                actualBytes = 0L,
                sha256Hex = null,
                failureReasonAr = "الملف غير موجود على وحدة التخزين.",
                failureReasonEn = "File does not exist on storage."
            )
        }

        val actualLength = file.length()
        if (actualLength <= 128L) {
            return IntegrityVerificationResult(
                isValid = false,
                actualBytes = actualLength,
                sha256Hex = null,
                failureReasonAr = "الملف فارغ أو غير مكتمل ($actualLength بايت).",
                failureReasonEn = "File is empty or truncated ($actualLength bytes)."
            )
        }

        if (expectedBytes > 0L && actualLength != expectedBytes) {
            return IntegrityVerificationResult(
                isValid = false,
                actualBytes = actualLength,
                sha256Hex = null,
                failureReasonAr = "حجم الملف المحمل ($actualLength بايت) لا يطابق الحجم المتوقع ($expectedBytes بايت).",
                failureReasonEn = "Downloaded byte count ($actualLength) does not match expected size ($expectedBytes)."
            )
        }

        // Read first 16 bytes to inspect container signature and reject HTML/ELF/PE executables
        val header = ByteArray(16)
        val bytesRead = FileInputStream(file).use { it.read(header) }
        if (bytesRead < 8) {
            return IntegrityVerificationResult(
                isValid = false,
                actualBytes = actualLength,
                sha256Hex = null,
                failureReasonAr = "تعذر قراءة ترويسة ملف الوسائط.",
                failureReasonEn = "Unable to read media container header."
            )
        }

        val headerAscii = String(header, Charsets.ISO_8859_1)
        // Reject HTML error documents or scripts disguised as media
        if (headerAscii.trimStart().startsWith("<!DOCTYPE", ignoreCase = true) ||
            headerAscii.trimStart().startsWith("<html", ignoreCase = true) ||
            headerAscii.startsWith("#!") ||
            (header[0] == 0x7F.toByte() && header[1] == 'E'.code.toByte() && header[2] == 'L'.code.toByte() && header[3] == 'F'.code.toByte()) ||
            (header[0] == 'M'.code.toByte() && header[1] == 'Z'.code.toByte())
        ) {
            return IntegrityVerificationResult(
                isValid = false,
                actualBytes = actualLength,
                sha256Hex = null,
                failureReasonAr = "المحتوى المستلم ليس ملف فيديو أو صوت صالحاً (تم رفض محتوى غير آمن).",
                failureReasonEn = "Received payload is not a valid media stream (unsafe or HTML content rejected)."
            )
        }

        val extLower = extension.lowercase(Locale.ROOT)
        val validSignature = when (extLower) {
            "mp4", "m4a", "mov" -> {
                // ISO Base Media File Format: bytes 4..7 are typically 'ftyp', 'moov', 'wide', or 'mdat'
                val boxType = String(header, 4, 4, Charsets.US_ASCII)
                boxType in setOf("ftyp", "moov", "mdat", "wide", "free", "styp")
            }
            "mp3" -> {
                // ID3v2 tag ("ID3") or MPEG audio frame sync (0xFF followed by 0xE0..0xFF)
                val isId3 = header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()
                val isFrameSync = (header[0].toInt() and 0xFF) == 0xFF && ((header[1].toInt() and 0xE0) == 0xE0)
                isId3 || isFrameSync
            }
            "webm" -> {
                // EBML magic number 0x1A 0x45 0xDF 0xA3
                (header[0].toInt() and 0xFF) == 0x1A &&
                    (header[1].toInt() and 0xFF) == 0x45 &&
                    (header[2].toInt() and 0xFF) == 0xDF &&
                    (header[3].toInt() and 0xFF) == 0xA3
            }
            else -> true
        }

        if (!validSignature) {
            return IntegrityVerificationResult(
                isValid = false,
                actualBytes = actualLength,
                sha256Hex = null,
                failureReasonAr = "توقيع حاوية الوسائط ($extLower) غير صالح أو تالف.",
                failureReasonEn = "Invalid or corrupted ($extLower) media container header."
            )
        }

        val sha256 = computeSha256(file)
        return IntegrityVerificationResult(
            isValid = true,
            actualBytes = actualLength,
            sha256Hex = sha256,
            failureReasonAr = null,
            failureReasonEn = null
        )
    }

    fun computeSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        FileInputStream(file).use { fis ->
            var read = fis.read(buffer)
            while (read != -1) {
                digest.update(buffer, 0, read)
                read = fis.read(buffer)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

data class IntegrityVerificationResult(
    val isValid: Boolean,
    val actualBytes: Long,
    val sha256Hex: String?,
    val failureReasonAr: String?,
    val failureReasonEn: String?
)
