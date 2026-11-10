package com.metrolist.desktop

import kotlinx.serialization.json.*
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal data class UpdateRelease(val version: String, val notes: String?, val asset: UpdateAsset?)
internal data class UpdateAsset(val name: String, val url: String, val size: Long, val sha256: String)

internal interface UpdateService {
    fun latest(): UpdateRelease?
    fun download(asset: UpdateAsset, directory: File, progress: (Long, Long) -> Unit): File
    fun cancel()
}

internal fun isNewerVersion(remote: String, local: String): Boolean {
    fun parse(value: String): List<Long>? {
        val normalized = value.trim().removePrefix("v")
        if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:\\.[0-9]+)?").matches(normalized)) return null
        return normalized.split('.').map { it.toLongOrNull() ?: return null }
    }
    val left = parse(remote) ?: return false
    val right = parse(local) ?: return false
    for (index in 0 until maxOf(left.size, right.size)) {
        val comparison = left.getOrElse(index) { 0 }.compareTo(right.getOrElse(index) { 0 })
        if (comparison != 0) return comparison > 0
    }
    return false
}

internal fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal class GitHubUpdateService(
    private val repository: String = AppVersion.REPO,
    private val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS).callTimeout(30, TimeUnit.MINUTES).build(),
) : UpdateService {
    @Volatile private var activeCall: Call? = null
    @Volatile private var cancelled = false
    private val json = Json { ignoreUnknownKeys = true }
    override fun cancel() { cancelled = true; activeCall?.cancel() }

    override fun latest(): UpdateRelease? {
        val request = Request.Builder().url("https://api.github.com/repos/$repository/releases/latest")
            .header("Accept", "application/vnd.github+json").header("User-Agent", "Metrolist/${AppVersion.NAME}").build()
        http.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (response.code == 403 || response.code == 429) error("GitHub kontrol sınırına ulaşıldı; daha sonra yeniden dene")
            check(response.isSuccessful) { "Sürüm bilgisi alınamadı (HTTP ${response.code})" }
            return parseRelease(response.body?.string().orEmpty(), repository)
        }
    }

    override fun download(asset: UpdateAsset, directory: File, progress: (Long, Long) -> Unit): File {
        requireTrustedAssetUrl(asset.url, repository)
        require(Regex("[a-fA-F0-9]{64}").matches(asset.sha256)) { "Güncellemenin doğrulama bilgisi yok" }
        require(asset.size in 1..MAX_PACKAGE_BYTES) { "Güncelleme boyutu geçersiz" }
        directory.mkdirs()
        val destination = File(directory, "Metrolist-${asset.sha256.take(16)}.exe")
        if (destination.isFile && destination.length() == asset.size && sha256(destination).equals(asset.sha256, true)) {
            progress(asset.size, asset.size); return destination
        }
        val partial = File(destination.path + ".part")
        cancelled = false
        val call = http.newCall(Request.Builder().url(asset.url).header("User-Agent", "Metrolist/${AppVersion.NAME}").build())
        activeCall = call
        try {
            call.execute().use { response ->
                check(response.isSuccessful) { "Güncelleme indirilemedi (HTTP ${response.code})" }
                val body = response.body ?: error("Güncelleme yanıtı boş")
                val announced = body.contentLength()
                check(announced < 0 || announced == asset.size) { "Paket boyutu sürüm bilgisiyle uyuşmuyor" }
                var received = 0L
                body.byteStream().use { input -> partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (cancelled) throw IOException("İndirme iptal edildi")
                        val count = input.read(buffer); if (count < 0) break
                        received += count
                        check(received <= asset.size) { "Güncelleme beklenenden büyük" }
                        output.write(buffer, 0, count); progress(received, asset.size)
                    }
                    output.fd.sync()
                } }
                verifyPackage(partial, asset)
                java.nio.file.Files.move(partial.toPath(), destination.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                return destination
            }
        } finally { activeCall = null; partial.delete() }
    }

    companion object {
        private const val MAX_PACKAGE_BYTES = 2L * 1024 * 1024 * 1024
        internal fun requireTrustedAssetUrl(url: String, repository: String) {
            val uri = java.net.URI(url)
            require(uri.scheme == "https" && uri.host.equals("github.com", true) && uri.rawUserInfo == null &&
                (uri.port == -1 || uri.port == 443) && uri.path.startsWith("/$repository/releases/download/", ignoreCase = true) &&
                uri.fragment == null && uri.query == null) { "Güncelleme adresi bu projenin GitHub yayınına ait değil" }
        }
        internal fun parseRelease(payload: String, repository: String): UpdateRelease? {
            val root = Json.parseToJsonElement(payload).jsonObject
            if (root["draft"]?.jsonPrimitive?.booleanOrNull == true || root["prerelease"]?.jsonPrimitive?.booleanOrNull == true) return null
            val version = root["tag_name"]?.jsonPrimitive?.contentOrNull?.removePrefix("v") ?: return null
            if (!Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:\\.[0-9]+)?").matches(version)) return null
            val asset = root["assets"]?.jsonArray.orEmpty().map { it.jsonObject }.firstOrNull {
                val name = it["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                name.equals("Metrolist-$version.exe", true) && it["state"]?.jsonPrimitive?.contentOrNull == "uploaded"
            }
            val candidate = asset?.let {
                val name = it["name"]!!.jsonPrimitive.content
                val url = it["browser_download_url"]?.jsonPrimitive?.contentOrNull.orEmpty()
                val digest = it["digest"]?.jsonPrimitive?.contentOrNull.orEmpty().removePrefix("sha256:")
                val size = it["size"]?.jsonPrimitive?.longOrNull ?: 0
                if (runCatching { requireTrustedAssetUrl(url, repository) }.isSuccess &&
                    Regex("[a-fA-F0-9]{64}").matches(digest) && size in 1..MAX_PACKAGE_BYTES) UpdateAsset(name, url, size, digest) else null
            }
            return UpdateRelease(version, root["body"]?.jsonPrimitive?.contentOrNull?.take(3000), candidate)
        }
        internal fun verifyPackage(file: File, asset: UpdateAsset) {
            check(file.length() == asset.size) { "Güncelleme eksik indirildi" }
            check(sha256(file).equals(asset.sha256, true)) { "Güncelleme doğrulanamadı (SHA-256 uyuşmuyor)" }
            file.inputStream().use { check(it.read() == 'M'.code && it.read() == 'Z'.code) { "İndirilen dosya Windows kurucusu değil" } }
        }
    }
}
