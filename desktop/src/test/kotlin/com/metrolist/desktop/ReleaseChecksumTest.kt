package com.metrolist.desktop

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseChecksumTest {
    @Test
    fun matchingHash() {
        val hash = ABC_HASH
        val record = "$hash  $ASSET"
        staged(ABC, record) { installer, status ->
            assertEquals(ReleaseChecksum.Status.Match, status)
            val decision = ReleaseChecksum.settle(installer, status)
            assertTrue(decision.launch)
            assertFalse(decision.deleteInstaller)
            assertTrue(installer.isFile)
            assertNull(decision.message)
        }
        assertEquals(ReleaseChecksum.Status.Match, verify(ABC, hash.uppercase()))
        assertEquals(
            ReleaseChecksum.Status.Match,
            verify(ABC, "Ba7816bF8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"),
        )
        assertEquals(ReleaseChecksum.Status.Match, verify(ABC, "$hash *$ASSET"))
        assertEquals(ReleaseChecksum.Status.Match, verify(ABC, "\uFEFF$record\r\n"))
        assertEquals(
            "https://example.test/Metrolist-1.2.0.exe.sha256",
            ReleaseChecksum.checksumUrl(
                mapOf(
                    ASSET to "https://example.test/Metrolist-1.2.0.exe",
                    ReleaseChecksum.checksumAssetName(ASSET) to "https://example.test/Metrolist-1.2.0.exe.sha256",
                ),
                ASSET,
            ),
        )
    }

    @Test
    fun wrongHash() {
        val wrong = ABC_HASH.dropLast(1) + "e"
        staged(ABC, wrong) { installer, status ->
            assertEquals(ReleaseChecksum.Status.Mismatch, status)
            val decision = ReleaseChecksum.settle(installer, status)
            assertFalse(decision.launch)
            assertTrue(decision.deleteInstaller)
            assertFalse(installer.exists())
            assertEquals(
                "Güncelleme doğrulanamadı: SHA-256 uyuşmuyor. İndirilen dosya silindi, kurulum başlatılmadı.",
                decision.message,
            )
        }
        assertEquals(ReleaseChecksum.Status.Mismatch, verify("abcd".encodeToByteArray(), "$ABC_HASH  $ASSET"))
    }

    @Test
    fun missingChecksum() {
        staged(ABC, checksumText = null) { installer, status ->
            assertEquals(ReleaseChecksum.Status.Missing, status)
            val ghost = File(installer.parentFile, "no-such-file.sha256")
            assertEquals(ReleaseChecksum.Status.Missing, ReleaseChecksum.verifyInstaller(installer, ghost, ASSET))
            val decision = ReleaseChecksum.settle(installer, status)
            assertFalse(decision.launch)
            assertTrue(decision.deleteInstaller)
            assertFalse(installer.exists())
            assertEquals(
                "Güncelleme doğrulanamadı: yayında SHA-256 özeti yok. Kurulum başlatılmadı.",
                decision.message,
            )
        }
        val assets = mapOf(
            ASSET to "https://example.test/Metrolist-1.2.0.exe",
            "Metrolist-9.9.9.exe.sha256" to "https://example.test/other.sha256",
        )
        assertEquals("Metrolist-1.2.0.exe.sha256", ReleaseChecksum.checksumAssetName(ASSET))
        assertNull(ReleaseChecksum.checksumUrl(assets, ASSET))
        assertNull(
            ReleaseChecksum.checksumUrl(
                mapOf(ReleaseChecksum.checksumAssetName(ASSET) to "  "),
                ASSET,
            ),
        )
    }

    @Test
    fun malformedChecksumFile() {
        val samples = listOf(
            "",
            "\n\n",
            "not a hash",
            ABC_HASH.take(63),
            ABC_HASH + "aa",
            "g".repeat(64),
            "$ABC_HASH  other.exe",
            "$ABC_HASH\n$ABC_HASH  $ASSET",
            "$ABC_HASH trailing-text",
            "SHA256 hash of $ASSET:\n$ABC_HASH",
        )
        for (sample in samples) {
            staged(ABC, sample) { installer, status ->
                assertEquals(ReleaseChecksum.Status.Malformed, status, sample)
                val decision = ReleaseChecksum.settle(installer, status)
                assertFalse(decision.launch, sample)
                assertFalse(installer.exists(), sample)
                assertEquals(
                    "Güncelleme doğrulanamadı: SHA-256 dosyası bozuk. İndirilen dosya silindi, kurulum başlatılmadı.",
                    decision.message,
                    sample,
                )
            }
        }
        staged(ABC, ABC_HASH) { installer, _ ->
            val oversize = File(installer.parentFile, "oversize.sha256")
            oversize.writeBytes(ByteArray(ReleaseChecksum.MAX_CHECKSUM_BYTES + 1) { 'a'.code.toByte() })
            assertEquals(
                ReleaseChecksum.Status.Malformed,
                ReleaseChecksum.verifyInstaller(installer, oversize, ASSET),
            )
        }
    }

    private fun verify(bytes: ByteArray, checksumText: String?, assetName: String = ASSET): ReleaseChecksum.Status =
        staged(bytes, checksumText, assetName) { _, status -> status }

    private fun <T> staged(
        bytes: ByteArray,
        checksumText: String?,
        assetName: String = ASSET,
        block: (installer: File, status: ReleaseChecksum.Status) -> T,
    ): T {
        val dir = Files.createTempDirectory("metrolist-checksum")
        try {
            val installer = dir.resolve("Metrolist-setup.exe").toFile().also { it.writeBytes(bytes) }
            val checksumFile = checksumText?.let { text ->
                dir.resolve("checksum.sha256").toFile().also { it.writeText(text, Charsets.UTF_8) }
            }
            val status = ReleaseChecksum.verifyInstaller(installer, checksumFile, assetName)
            return block(installer, status)
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    private companion object {
        const val ASSET = "Metrolist-1.2.0.exe"

        /** SHA-256 of the bytes `abc` (NIST test vector). */
        const val ABC_HASH = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
        val ABC = "abc".encodeToByteArray()
    }
}
