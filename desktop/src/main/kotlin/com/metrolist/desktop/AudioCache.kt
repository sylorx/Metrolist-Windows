package com.metrolist.desktop

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Keeps the audio of songs that were actually listened to, so playing or rewinding them again needs no server.
 * The oldest unused files go first once the size limit is reached; a limit of zero turns the cache off.
 */
internal class AudioCache(
    private val directory: File,
    private val limitBytes: () -> Long,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = ConcurrentHashMap.newKeySet<String>()

    /** A complete cached copy, marked as just used. */
    fun file(videoId: String, quality: String): File? {
        if (limitBytes() <= 0) return null
        val found = target(videoId, quality).takeIf { it.isFile && it.length() >= MIN_BYTES } ?: return null
        found.setLastModified(System.currentTimeMillis())
        return found
    }

    /** Copies a working remote stream in the background; does nothing when a copy exists or is being made. */
    fun keep(videoId: String, quality: String, stream: StreamResolver.Stream) {
        if (limitBytes() <= 0 || !stream.url.startsWith("http", ignoreCase = true)) return
        val key = name(videoId, quality)
        if (target(videoId, quality).isFile || !running.add(key)) return
        scope.launch {
            try { download(stream, target(videoId, quality)) } catch (_: Exception) {
                // A copy that could not be completed is simply not used.
            } finally { running.remove(key) }
        }
    }

    fun remove(videoId: String, quality: String) { target(videoId, quality).delete() }

    fun usage(): Long = files().sumOf { it.length() }

    fun clear() { files().forEach { it.delete() } }

    /** Deletes least recently used copies until the cache fits its limit. */
    fun trim() {
        val limit = limitBytes()
        var total = usage()
        for (file in files().sortedBy { it.lastModified() }) {
            if (total <= limit) break
            total -= file.length()
            file.delete()
        }
    }

    internal suspend fun download(stream: StreamResolver.Stream, destination: File) {
        directory.mkdirs()
        val partial = File(directory, destination.name + ".part")
        try {
            partial.outputStream().use { output ->
                var position = 0L
                var total = -1L
                // Ranged pieces keep a slow or throttled server from dropping one long transfer.
                while (total < 0 || position < total) {
                    currentCoroutineContext().ensureActive()
                    val request = Request.Builder().url(stream.url).apply {
                        stream.headers.forEach { (name, value) -> header(name, value) }
                    }.header("Range", "bytes=$position-${position + CHUNK - 1}").build()
                    http.newCall(request).execute().use { response ->
                        check(response.code == 206 || response.code == 200) { "HTTP ${response.code}" }
                        val body = response.body ?: error("Boş yanıt")
                        if (response.code == 200) {
                            // The server ignored the range and sends the whole file at once.
                            if (position > 0) error("Sunucu parçalı indirmeyi bıraktı")
                            body.byteStream().copyTo(output)
                            total = partial.length().coerceAtLeast(output.channel.position())
                            position = total
                        } else {
                            total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: error("Boyut bilinmiyor")
                            check(total <= MAX_BYTES) { "Dosya çok büyük" }
                            val read = body.byteStream().copyTo(output)
                            check(read > 0) { "Boş parça" }
                            position += read
                        }
                    }
                }
                output.fd.sync()
            }
            check(partial.length() >= MIN_BYTES) { "Kayıt eksik" }
            if (!partial.renameTo(destination)) {
                destination.delete()
                check(partial.renameTo(destination)) { "Kayıt taşınamadı" }
            }
            trim()
        } finally { partial.delete() }
    }

    private fun files() = directory.listFiles { file -> file.isFile && file.name.endsWith(SUFFIX) }.orEmpty().toList()

    private fun target(videoId: String, quality: String) = File(directory, name(videoId, quality) + SUFFIX)

    private fun name(videoId: String, quality: String) = MessageDigest.getInstance("SHA-256")
        .digest("$videoId|$quality".toByteArray()).joinToString("") { "%02x".format(it) }.take(32)

    companion object {
        private const val SUFFIX = ".audio"
        private const val CHUNK = 1L shl 20
        private const val MIN_BYTES = 32L * 1024
        private const val MAX_BYTES = 200L * 1024 * 1024
        val sizes = listOf(0 to "Kapalı", 512 to "512 MB", 1024 to "1 GB", 2048 to "2 GB", 5120 to "5 GB")
    }
}
