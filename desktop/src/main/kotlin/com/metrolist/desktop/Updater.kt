package com.metrolist.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import java.io.File

internal class Updater(
    private val scope: CoroutineScope,
    private val store: Store,
    private val service: UpdateService = GitHubUpdateService(),
    private val installer: UpdateInstaller = WindowsUpdateInstaller(),
    private val directory: File = File(appDir(), "updates"),
    private val currentVersion: String = AppVersion.NAME,
    private val notify: (String, String) -> Unit = { _, _ -> },
) {
    var checking by mutableStateOf(false)
        private set
    var downloading by mutableStateOf(false)
        private set
    var installing by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var receivedBytes by mutableStateOf(0L)
        private set
    var totalBytes by mutableStateOf(0L)
        private set
    var latest by mutableStateOf<String?>(null)
        private set
    var notes by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf("Henüz kontrol edilmedi")
        private set
    var ready by mutableStateOf(false)
        private set
    var automatic by mutableStateOf(store.settings.checkUpdates)
        private set
    var backgroundDownload by mutableStateOf(store.settings.downloadUpdates)
        private set
    var resultMessage by mutableStateOf(readResult())
        private set
    private var release: UpdateRelease? = null
    private var packageFile: File? = null
    private var dismissed: String? = null
    private var started = false
    private var downloadJob: Job? = null
    private var cancelRequested = false
    val available: String? get() = latest?.takeIf { isNewerVersion(it, currentVersion) }
    val pending: String? get() = available?.takeUnless { it == dismissed }
    val canDownload: Boolean get() = release?.asset != null && available != null

    fun start() {
        if (started) return
        started = true
        scope.launch {
            delay(4000)
            while (isActive) {
                if (automatic) check()
                delay(3 * 60 * 60 * 1000L)
            }
        }
    }
    fun changeAutomatic(enabled: Boolean) {
        store.saveSettings(store.settings.copy(checkUpdates = enabled))
        automatic = enabled
        if (enabled) requestCheck()
    }
    fun changeBackgroundDownload(enabled: Boolean) {
        store.saveSettings(store.settings.copy(downloadUpdates = enabled))
        backgroundDownload = enabled
        if (enabled && canDownload && !ready) download()
    }
    fun reloadPreferences() {
        automatic = store.settings.checkUpdates; backgroundDownload = store.settings.downloadUpdates
    }
    fun requestCheck() {
        if (checking || downloading || installing) return
        dismissed = null
        // Reserve the state before dispatching so repeated clicks cannot overlap.
        checking = true
        scope.launch { check(reserved = true) }
    }
    fun dismiss() { if (!installing) dismissed = latest }
    private suspend fun check(reserved: Boolean = false) {
        if ((!reserved && checking) || downloading || installing) return
        checking = true
        message = "Yeni sürüm kontrol ediliyor…"
        var cancelled = false
        try {
            val found = withContext(Dispatchers.IO) { service.latest() }
            if (found?.version != release?.version || found?.asset != release?.asset) {
                ready = false; packageFile = null; progress = 0f
            }
            release = found; latest = found?.version; notes = found?.notes
            message = when {
                found == null -> "Henüz yayımlanmış kararlı sürüm yok"
                available == null -> "Güncelsin (sürüm $currentVersion)"
                found.asset == null -> "${found.version} yayımlanmış; doğrulanabilir Windows kurulum paketi henüz hazır değil"
                ready -> "${found.version} indirildi; yeniden başlatarak güncelleyebilirsin"
                else -> "Yeni sürüm ${found.version} hazır"
            }
        } catch (cancel: CancellationException) { cancelled = true; throw cancel }
        catch (failure: Exception) { message = "Kontrol başarısız: ${failure.message ?: "Bağlantıyı kontrol et"}" }
        finally {
            checking = false
            if (!cancelled) {
                if (automatic && backgroundDownload && canDownload && !ready) download()
                announce()
            }
        }
    }
    fun download() {
        val asset = release?.asset ?: return
        if (available == null || checking || downloading || installing || ready) return
        downloading = true; cancelRequested = false; receivedBytes = 0; totalBytes = asset.size; progress = 0f
        message = "Güncelleme arka planda indiriliyor…"
        downloadJob = scope.launch {
            try {
                var lastReportedAt = 0L
                val file = withContext(Dispatchers.IO) {
                    service.download(asset, directory) { received, total ->
                        if (cancelRequested) throw java.io.IOException("İndirme iptal edildi")
                        val now = System.nanoTime()
                        if (received == total || now - lastReportedAt > 100_000_000) {
                            lastReportedAt = now
                            scope.launch {
                                if (downloading && !cancelRequested) {
                                    receivedBytes = received; totalBytes = total
                                    progress = (received.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
                                }
                            }
                        }
                    }
                }
                if (cancelRequested) { message = "İndirme iptal edildi"; return@launch }
                packageFile = file; ready = true; progress = 1f; receivedBytes = asset.size
                message = "Güncelleme indirildi ve doğrulandı. Yeniden başlatarak yükle."
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) {
                message = if (cancelRequested) "İndirme iptal edildi" else "İndirme başarısız: ${failure.message}"
            } finally {
                downloading = false
                downloadJob = null
                if (!cancelRequested) announce()
            }
        }
    }
    fun cancelDownload() {
        if (!downloading) return
        cancelRequested = true
        service.cancel()
        message = "İndirme iptal ediliyor…"
    }
    fun install(onExit: () -> Unit) {
        if (checking || downloading || installing || !canDownload) return
        if (!ready) { download(); return }
        val asset = release?.asset ?: return
        val version = available ?: return
        val file = packageFile ?: return
        installing = true
        message = "Güncelleme hazırlanıyor…"
        scope.launch {
            var packageVerified = false
            try {
                withContext(Dispatchers.IO) {
                    GitHubUpdateService.verifyPackage(file, asset)
                    packageVerified = true
                    installer.launch(file, version, asset.sha256, directory)
                }
                message = "Yeniden başlatılıyor…"
                onExit()
            } catch (cancel: CancellationException) { installing = false; throw cancel }
            catch (failure: Exception) {
                installing = false
                if (!packageVerified) { ready = false; packageFile = null; progress = 0f }
                message = "Kurulum başlatılamadı: ${failure.message}"
            }
        }
    }
    private fun announce() {
        val version = available ?: return
        if (installing || (downloading && ready)) return
        val stage = if (ready) "ready" else "found"
        val key = "$version:$stage"
        val marker = File(directory, "notified.txt")
        if (runCatching { marker.readText().trim() }.getOrNull() == key) return
        runCatching { directory.mkdirs(); marker.writeText(key) }
        if (ready) notify("Güncelleme hazır", "Metrolist $version indirildi. Yeniden başlatarak güncelleyebilirsin.")
        else notify("Yeni sürüm", "Metrolist $version yayımlandı.")
    }
    private fun readResult(): String? {
        val file = File(directory, "update-result.json")
        if (!file.isFile) return null
        return runCatching {
            val result = kotlinx.serialization.json.Json.decodeFromString<UpdateResult>(file.readText().removePrefix("\uFEFF"))
            if (result.success) "Güncelleme tamamlandı: ${result.version}" else "Önceki güncelleme tamamlanamadı: ${result.message}"
        }.getOrNull().also { file.delete() }
    }
}
