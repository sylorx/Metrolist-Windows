package com.metrolist.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.concurrent.TimeUnit

internal class Updater(private val scope: CoroutineScope) {
    var checking by mutableStateOf(false)
    var downloading by mutableStateOf(false)
    var progress by mutableStateOf(0f)
    var latest by mutableStateOf<String?>(null)
    var notes by mutableStateOf<String?>(null)
    var message by mutableStateOf("Henüz kontrol edilmedi")
    private var assetUrl: String? = null
    private var assetName: String? = null
    private var checksumUrl: String? = null
    private var dismissed: String? = null

    val pending: String?
        get() {
            val remote = latest ?: return null
            if (remote == dismissed) return null
            return remote.takeIf { newer(it, AppVersion.NAME) }
        }

    fun start() {
        scope.launch {
            delay(4_000)
            while (true) {
                check()
                delay(CHECK_EVERY_MS)
            }
        }
    }

    fun requestCheck() {
        if (checking || downloading) return
        scope.launch { check() }
    }

    fun dismiss() {
        dismissed = latest
    }

    fun install(onExit: () -> Unit) {
        val url = assetUrl ?: return
        if (downloading) return
        val name = assetName
        val checksum = checksumUrl
        scope.launch {
            downloading = true
            progress = 0f
            if (checksum.isNullOrBlank() || name.isNullOrBlank()) {
                deleteDownloadedInstaller()
                downloading = false
                fail(ReleaseChecksum.message(ReleaseChecksum.Status.Missing))
                return@launch
            }
            message = "Güncelleme indiriliyor"
            val file = runCatching { withContext(Dispatchers.IO) { downloadVerified(url, checksum, name) } }
            downloading = false
            file.onSuccess { installer ->
                val started = runCatching {
                    ProcessBuilder(installer.absolutePath)
                        .directory(installer.parentFile)
                        .start()
                }
                if (started.isSuccess) {
                    message = "Kurulum açıldı"
                    onExit()
                } else {
                    message = started.exceptionOrNull()?.message ?: "Kurulum başlatılamadı"
                }
            }.onFailure { error ->
                fail(error.message ?: "İndirme başarısız")
            }
        }
    }

    private suspend fun check() {
        if (checking) return
        checking = true
        val found = runCatching { withContext(Dispatchers.IO) { fetch() } }
        checking = false
        found.onSuccess { release ->
            if (release == null) {
                latest = null
                assetUrl = null
                assetName = null
                checksumUrl = null
                message = "Yayınlanmış güncelleme yok. Bu sürüm güncel."
                return@onSuccess
            }
            latest = release.version
            notes = release.notes
            assetUrl = release.assetUrl
            assetName = release.assetName
            checksumUrl = release.checksumUrl
            message = if (newer(release.version, AppVersion.NAME)) {
                "Yeni sürüm ${release.version} hazır"
            } else {
                "Güncelsin ($NAME_LABEL)"
            }
        }.onFailure { error ->
            message = error.message ?: "Güncelleme kontrolü başarısız"
        }
    }

    private fun fetch(): Release? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/${AppVersion.REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Metrolist/${AppVersion.NAME}")
            .build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) error("GitHub ${response.code}")
            val root = json.parseToJsonElement(response.body?.string().orEmpty()).jsonObject
            val version = root["tag_name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty().removePrefix("v")
            if (version.isBlank()) return null
            val body = root["body"]?.jsonPrimitive?.contentOrNull?.trim()?.take(280)
            val assetUrls = linkedMapOf<String, String>()
            for (item in root["assets"]?.jsonArray.orEmpty()) {
                val obj = item.jsonObject
                val name = obj["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                val download = obj["browser_download_url"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (name.isNotEmpty() && download.isNotEmpty()) assetUrls.putIfAbsent(name, download)
            }
            val installerName = assetUrls.keys.firstOrNull { name ->
                name.endsWith(".exe", ignoreCase = true) && name.contains("Metrolist", ignoreCase = true)
            }
            val installerUrl = installerName?.let { assetUrls[it] }
            val checksum = installerName?.let { ReleaseChecksum.checksumUrl(assetUrls, it) }
            return Release(version, body, installerUrl, installerName, checksum)
        }
    }

    private fun downloadVerified(url: String, checksumUrl: String, assetName: String): File {
        val dest = download(url)
        val checksumFile = File(dest.parentFile, dest.name + ".sha256")
        var verified = false
        try {
            writeChecksum(checksumUrl, checksumFile)
            val status = ReleaseChecksum.verifyInstaller(dest, checksumFile, assetName)
            val decision = ReleaseChecksum.settle(dest, status)
            if (!decision.launch) error(decision.message ?: ReleaseChecksum.message(status))
            verified = true
            return dest
        } finally {
            checksumFile.delete()
            if (!verified) dest.delete()
        }
    }

    private fun writeChecksum(url: String, dest: File) {
        val request = Request.Builder().url(url).header("User-Agent", "Metrolist/${AppVersion.NAME}").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Sağlama dosyası indirilemedi (${response.code}). Kurulum başlatılmadı.")
            val body = response.body ?: error("Sağlama dosyası boş. Kurulum başlatılmadı.")
            body.byteStream().use { input ->
                val bytes = input.readNBytes(ReleaseChecksum.MAX_CHECKSUM_BYTES + 1)
                if (bytes.size > ReleaseChecksum.MAX_CHECKSUM_BYTES) {
                    error(ReleaseChecksum.message(ReleaseChecksum.Status.Malformed))
                }
                dest.writeBytes(bytes)
            }
        }
    }

    private fun download(url: String): File {
        val request = Request.Builder().url(url).header("User-Agent", "Metrolist/${AppVersion.NAME}").build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("İndirme ${response.code}")
            val body = response.body ?: error("Boş yanıt")
            val dest = installerFile()
            val total = body.contentLength()
            var started = false
            try {
                body.byteStream().use { input ->
                    started = true
                    dest.outputStream().use { output ->
                        val buffer = ByteArray(16 * 1024)
                        var readTotal = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            readTotal += count
                            if (total > 0) progress = (readTotal.toFloat() / total).coerceIn(0f, 1f)
                        }
                    }
                }
            } catch (error: Throwable) {
                if (started) dest.delete()
                throw error
            }
            if (dest.length() < 1_000_000) {
                dest.delete()
                error("İndirilen dosya kurulum paketi değil")
            }
            return dest
        }
    }

    private fun deleteDownloadedInstaller() {
        val file = installerFile()
        file.delete()
        File(file.parentFile, file.name + ".sha256").delete()
    }

    private fun installerFile(): File {
        val dir = File(appDir(), "updates").apply { mkdirs() }
        return File(dir, "Metrolist-setup.exe")
    }

    private fun fail(text: String) {
        Timber.e(text)
        message = text
    }

    private data class Release(
        val version: String,
        val notes: String?,
        val assetUrl: String?,
        val assetName: String?,
        val checksumUrl: String?,
    )

    private companion object {
        const val CHECK_EVERY_MS = 3 * 60 * 60 * 1000L
        const val NAME_LABEL = "sürüm ${AppVersion.NAME}"
        val json = Json { ignoreUnknownKeys = true }
        val http = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()

        fun newer(remote: String, local: String): Boolean {
            fun parts(value: String) = value.trim().removePrefix("v").substringBefore('-')
                .split('.')
                .map { it.toIntOrNull() ?: 0 }
            val left = parts(remote)
            val right = parts(local)
            val size = maxOf(left.size, right.size)
            for (index in 0 until size) {
                val delta = left.getOrElse(index) { 0 } - right.getOrElse(index) { 0 }
                if (delta != 0) return delta > 0
            }
            return false
        }
    }
}
