package com.metrolist.desktop

import java.io.File
import java.security.MessageDigest

/**
 * Checks a downloaded GitHub Release installer against a checksum published
 * in that same release.
 *
 * Convention: the installer asset `Metrolist-1.2.0.exe` requires a sibling
 * asset named exactly `Metrolist-1.2.0.exe.sha256`. The file is UTF-8 text
 * with exactly one record. A record is 64 hexadecimal digits, optionally
 * followed by whitespace and the installer asset name, which is the format
 * `sha256sum` writes (`<hash>  <name>` or `<hash> *<name>`). A bare 64-digit
 * digest is also accepted. `A`–`F` and `a`–`f` are the same value. Any other
 * content is malformed.
 *
 * A missing checksum asset, a malformed file, or a digest that does not match
 * refuses the install. The two digests are compared as raw bytes and the
 * comparison does not return at the first differing byte.
 */
internal object ReleaseChecksum {
    const val MAX_CHECKSUM_BYTES = 8192

    enum class Status {
        Match,
        Mismatch,
        Missing,
        Malformed,
    }

    data class Decision(
        val launch: Boolean,
        val deleteInstaller: Boolean,
        val message: String?,
    )

    fun checksumAssetName(installerAssetName: String): String = "$installerAssetName.sha256"

    fun checksumUrl(assetUrlsByName: Map<String, String>, installerAssetName: String): String? =
        assetUrlsByName[checksumAssetName(installerAssetName)]?.takeIf { it.isNotBlank() }

    fun verifyInstaller(installer: File, checksumFile: File?, installerAssetName: String): Status {
        if (checksumFile == null || !checksumFile.isFile) return Status.Missing
        val text = readChecksum(checksumFile) ?: return Status.Malformed
        val expected = parse(text, installerAssetName) ?: return Status.Malformed
        val actual = digest(installer)
        return if (constantTimeEquals(actual, expected)) Status.Match else Status.Mismatch
    }

    fun message(status: Status): String = when (status) {
        Status.Match -> "SHA-256 doğrulandı"
        Status.Missing -> "Güncelleme doğrulanamadı: yayında SHA-256 özeti yok. Kurulum başlatılmadı."
        Status.Mismatch -> "Güncelleme doğrulanamadı: SHA-256 uyuşmuyor. İndirilen dosya silindi, kurulum başlatılmadı."
        Status.Malformed -> "Güncelleme doğrulanamadı: SHA-256 dosyası bozuk. İndirilen dosya silindi, kurulum başlatılmadı."
    }

    /**
     * Applies a verification result to the downloaded installer.
     * Anything other than a match deletes the file and must not be launched.
     */
    fun settle(installer: File, status: Status): Decision {
        val launch = status == Status.Match
        if (!launch) installer.delete()
        return Decision(
            launch = launch,
            deleteInstaller = !launch,
            message = if (launch) null else message(status),
        )
    }

    private fun readChecksum(file: File): String? {
        if (file.length() > MAX_CHECKSUM_BYTES) return null
        val bytes = file.inputStream().use { input -> input.readNBytes(MAX_CHECKSUM_BYTES + 1) }
        if (bytes.size > MAX_CHECKSUM_BYTES) return null
        return bytes.toString(Charsets.UTF_8)
    }

    private fun parse(checksumText: String, installerAssetName: String): ByteArray? {
        val lines = checksumText
            .trimStart('\uFEFF')
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()
        if (lines.size != 1) return null
        val match = RECORD.matchEntire(lines.single()) ?: return null
        val fileName = match.groupValues[2]
        if (fileName.isNotEmpty() && fileName != installerAssetName) return null
        return decodeHex(match.groupValues[1])
    }

    private fun decodeHex(hex: String): ByteArray? {
        if (hex.length != 64) return null
        val out = ByteArray(32)
        for (index in 0 until 32) {
            val hi = Character.digit(hex[index * 2].code, 16)
            val lo = Character.digit(hex[index * 2 + 1].code, 16)
            if (hi < 0 || lo < 0) return null
            out[index] = ((hi shl 4) or lo).toByte()
        }
        return out
    }

    private fun digest(file: File): ByteArray {
        val message = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                message.update(buffer, 0, count)
            }
        }
        return message.digest()
    }

    /**
     * Compares two SHA-256 digests. Hex letter case is folded in [decodeHex],
     * so the text form is never compared. [MessageDigest.isEqual] reads every
     * byte and does not return at the first difference.
     */
    private fun constantTimeEquals(actual: ByteArray, expected: ByteArray): Boolean {
        if (actual.size != 32 || expected.size != 32) return false
        return MessageDigest.isEqual(actual, expected)
    }

    private val RECORD = Regex("""^([0-9a-fA-F]{64})(?:[ \t]+\*?(\S+))?$""")
}
