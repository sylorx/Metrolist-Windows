package com.metrolist.desktop

import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class UpdaterTest {
    @get:Rule val temporary = TemporaryFolder()
    private val bytes = byteArrayOf(77, 90, 1, 2, 3, 4, 5, 6)
    private val url = "https://github.com/sylorx/Metrolist-Windows/releases/download/v1.2.0/Metrolist-1.2.0.exe"
    private fun asset(): UpdateAsset {
        val file = temporary.newFile().apply { writeBytes(bytes) }
        return UpdateAsset("Metrolist-1.2.0.exe", url, bytes.size.toLong(), sha256(file))
    }
    private fun payload(asset: UpdateAsset, extra: String = "") = """{
        "tag_name":"v1.2.0", "body":"Sürüm notları", "draft":false, "prerelease":false,
        "assets":[{"name":"${asset.name}","state":"uploaded","size":${asset.size},
        "browser_download_url":"${asset.url}","digest":"sha256:${asset.sha256}"}]$extra
    }"""

    @Test fun versionComparisonRejectsMalformedPrereleaseAndDowngrade() {
        assertTrue(isNewerVersion("v1.10.0", "1.9.9"))
        assertTrue(isNewerVersion("2.0.0", "1.99.999"))
        assertFalse(isNewerVersion("1.1.0", "1.1.0"))
        assertFalse(isNewerVersion("1.0.0", "1.1.0"))
        assertFalse(isNewerVersion("1.2.0-beta", "1.1.0"))
        assertFalse(isNewerVersion("garbage", "1.1.0"))
    }
    @Test fun releaseRequiresMatchingUploadedExeTrustedUrlAndChecksum() {
        val asset = asset()
        assertEquals(asset, GitHubUpdateService.parseRelease(payload(asset), AppVersion.REPO)?.asset)
        assertNull(GitHubUpdateService.parseRelease(payload(asset.copy(url = "https://attacker.test/Metrolist.exe")), AppVersion.REPO)?.asset)
        assertNull(GitHubUpdateService.parseRelease(payload(asset.copy(sha256 = "")), AppVersion.REPO)?.asset)
        assertNull(GitHubUpdateService.parseRelease(payload(asset.copy(name = "Other-1.2.0.exe")), AppVersion.REPO)?.asset)
        assertNull(GitHubUpdateService.parseRelease(payload(asset).replace("\"draft\":false", "\"draft\":true"), AppVersion.REPO))
    }
    @Test fun downloadVerifiesPackageCachesItAndDeletesPartialFilesOnFailure() {
        val asset = asset()
        val requests = AtomicInteger()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            requests.incrementAndGet()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(bytes.toResponseBody("application/octet-stream".toMediaType())).build()
        }.build()
        val service = GitHubUpdateService(http = client)
        val folder = temporary.newFolder()
        val downloaded = service.download(asset, folder) { _, _ -> }
        assertArrayEquals(bytes, downloaded.readBytes())
        assertEquals(downloaded, service.download(asset, folder) { _, _ -> })
        assertEquals(1, requests.get())
        val bad = asset.copy(sha256 = "0".repeat(64))
        assertTrue(runCatching { service.download(bad, folder) { _, _ -> } }.isFailure)
        assertTrue(folder.listFiles().orEmpty().none { it.extension == "part" })
        assertTrue(folder.listFiles().orEmpty().none { it.name.contains("0000000000000000") })
    }
    @Test fun corruptAndTruncatedPackagesAreNeverAccepted() {
        val asset = asset()
        val short = temporary.newFile().apply { writeBytes(bytes.take(4).toByteArray()) }
        assertTrue(runCatching { GitHubUpdateService.verifyPackage(short, asset) }.isFailure)
        val altered = temporary.newFile().apply { writeBytes(bytes.copyOf().also { it[3] = 99 }) }
        assertTrue(runCatching { GitHubUpdateService.verifyPackage(altered, asset) }.isFailure)
        val html = temporary.newFile().apply { writeText("<html>") }
        val htmlAsset = asset.copy(size = html.length(), sha256 = sha256(html))
        assertTrue(runCatching { GitHubUpdateService.verifyPackage(html, htmlAsset) }.isFailure)
    }
    private class FakeService(var release: UpdateRelease?, val file: File) : UpdateService {
        var downloads = 0
        var failure: Exception? = null
        override fun latest() = release
        override fun download(asset: UpdateAsset, directory: File, progress: (Long, Long) -> Unit): File {
            downloads++; failure?.let { throw it }; progress(asset.size, asset.size); return file
        }
        override fun cancel() {}
    }
    private class FakeInstaller : UpdateInstaller {
        var starts = 0
        var failure: Exception? = null
        override fun launch(packageFile: File, version: String, hash: String, directory: File) { failure?.let { throw it }; starts++ }
    }
    private suspend fun await(condition: () -> Boolean) { withTimeout(5000) { while (!condition()) delay(10) } }

    @Test fun checkDownloadsInBackgroundButOnlyExplicitInstallClosesApplication() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val asset = asset()
            val file = temporary.newFile().apply { writeBytes(bytes) }
            val source = FakeService(UpdateRelease("1.2.0", "Notes", asset), file)
            val installer = FakeInstaller()
            val store = Store(temporary.newFolder(), false)
            val updater = Updater(scope, store, source, installer, temporary.newFolder(), "1.1.0")
            var exits = 0
            updater.requestCheck()
            await { updater.ready }
            assertEquals(1, source.downloads)
            assertEquals(0, installer.starts)
            assertEquals(0, exits)
            updater.install { exits++ }
            await { exits == 1 }
            assertEquals(1, installer.starts)
        } finally { scope.cancel() }
    }
    @Test fun disabledAutoDownloadAndMissingInstallerAreReportedWithoutClosing() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val store = Store(temporary.newFolder(), false)
            store.saveSettings(store.settings.copy(downloadUpdates = false))
            val source = FakeService(UpdateRelease("1.2.0", null, null), temporary.newFile())
            val updater = Updater(scope, store, source, FakeInstaller(), temporary.newFolder(), "1.1.0")
            updater.requestCheck(); await { !updater.checking }
            assertEquals("1.2.0", updater.available)
            assertFalse(updater.canDownload)
            assertTrue(updater.message.contains("henüz hazır değil"))
            assertEquals(0, source.downloads)
            updater.install { fail("Missing package must not close the application") }
        } finally { scope.cancel() }
    }
    @Test fun installerStartupFailureLeavesApplicationOpenAndAllowsRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val asset = asset()
            val file = temporary.newFile().apply { writeBytes(bytes) }
            val installer = FakeInstaller().apply { failure = IllegalStateException("helper missing") }
            val updater = Updater(scope, Store(temporary.newFolder(), false), FakeService(UpdateRelease("1.2.0", null, asset), file), installer, temporary.newFolder(), "1.1.0")
            updater.requestCheck(); await { updater.ready }
            updater.install { fail("Failed helper must not close the application") }
            await { !updater.installing }
            assertTrue(updater.message.contains("helper missing"))
            installer.failure = null
            var closed = false
            updater.install { closed = true }
            await { closed }
        } finally { scope.cancel() }
    }
    @Test fun tamperedReadyPackageRequiresDownloadAgainAndNeverLaunchesInstaller() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val asset = asset()
            val file = temporary.newFile().apply { writeBytes(bytes) }
            val installer = FakeInstaller()
            val updater = Updater(scope, Store(temporary.newFolder(), false), FakeService(UpdateRelease("1.2.0", null, asset), file), installer, temporary.newFolder(), "1.1.0")
            updater.requestCheck(); await { updater.ready }
            file.writeText("tampered")
            updater.install { fail("Tampered file must not close the app") }
            await { !updater.installing }
            assertFalse(updater.ready)
            assertEquals(0, installer.starts)
            assertTrue(updater.canDownload)
        } finally { scope.cancel() }
    }
    @Test fun updateNotificationFiresOncePerStage() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val asset = asset()
            val file = temporary.newFile().apply { writeBytes(bytes) }
            val folder = temporary.newFolder()
            val notes = java.util.Collections.synchronizedList(mutableListOf<String>())
            val store = Store(temporary.newFolder(), false)
            store.saveSettings(store.settings.copy(downloadUpdates = false))
            val updater = Updater(scope, store, FakeService(UpdateRelease("1.2.0", null, asset), file), FakeInstaller(), folder, "1.1.0") { title, _ ->
                notes += title
            }
            updater.requestCheck(); await { notes.size == 1 }
            assertEquals(listOf("Yeni sürüm"), notes.toList())
            updater.requestCheck(); await { !updater.checking }
            assertEquals(listOf("Yeni sürüm"), notes.toList())
            updater.download(); await { notes.size == 2 }
            assertEquals(listOf("Yeni sürüm", "Güncelleme hazır"), notes.toList())
            updater.download()
            assertEquals(listOf("Yeni sürüm", "Güncelleme hazır"), notes)
        } finally { scope.cancel() }
    }
    @Test fun cancellationReturnsToRetryableState() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val started = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        try {
            val asset = asset()
            val source = object : UpdateService {
                override fun latest() = UpdateRelease("1.2.0", null, asset)
                override fun download(asset: UpdateAsset, directory: File, progress: (Long, Long) -> Unit): File {
                    started.countDown(); check(cancelled.await(5, TimeUnit.SECONDS)); throw java.io.IOException("cancelled")
                }
                override fun cancel() { cancelled.countDown() }
            }
            val updater = Updater(scope, Store(temporary.newFolder(), false), source, FakeInstaller(), temporary.newFolder(), "1.1.0")
            updater.requestCheck()
            assertTrue(started.await(5, TimeUnit.SECONDS))
            updater.cancelDownload(); await { !updater.downloading }
            assertFalse(updater.ready)
            assertTrue(updater.message.contains("iptal"))
        } finally { cancelled.countDown(); scope.cancel() }
    }
    @Test fun windowsHelperHandlesSuccessInstallerFailureAndTamperingWithoutRunningInstallers() {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val folder = temporary.newFolder()
        val helper = File(folder, "apply-update.ps1")
        helper.writeBytes(javaClass.getResourceAsStream("/updates/apply-update.ps1")!!.use { it.readBytes() })
        val test = File(folder, "Test-UpdateHelper.ps1")
        test.writeBytes(javaClass.getResourceAsStream("/updates/Test-UpdateHelper.ps1")!!.use { it.readBytes() })
        val powershell = File(System.getenv("SystemRoot"), "System32/WindowsPowerShell/v1.0/powershell.exe")
        val process = ProcessBuilder(powershell.path, "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-File", test.path,
            "-HelperPath", helper.path, "-WorkDirectory", folder.path).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertTrue("Helper test timed out", process.waitFor(20, TimeUnit.SECONDS))
        assertEquals(output, 0, process.exitValue())
        assertTrue(output, output.contains("checksum-failure passed"))
    }

}
