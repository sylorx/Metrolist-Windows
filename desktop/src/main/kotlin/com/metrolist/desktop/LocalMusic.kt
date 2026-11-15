package com.metrolist.desktop

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Java2DFrameConverter
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import javax.imageio.ImageIO
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class ScanResult(val tracks: List<SavedTrack>, val failed: Int)
internal object LocalMusic {
    private val extensions = setOf("mp3", "flac", "wav", "ogg", "opus", "m4a", "aac", "wma", "aiff")
    suspend fun scan(folders: List<String>, onProgress: (Int, String) -> Unit): ScanResult {
        val songs = mutableListOf<SavedTrack>()
        val seen = mutableSetOf<String>()
        var failed = 0
        for (folder in folders) {
            val root = File(folder)
            if (!root.isDirectory) { failed++; continue }
            Files.walk(root.toPath()).use { paths ->
                val iterator = paths.iterator()
                while (iterator.hasNext()) {
                    currentCoroutineContext().ensureActive()
                    val path = iterator.next()
                    val file = path.toFile()
                    if (!Files.isRegularFile(path) || file.extension.lowercase() !in extensions) continue
                    val canonical = file.canonicalPath
                    if (!seen.add(canonical.lowercase())) continue
                    onProgress(songs.size, file.name)
                    try {
                        FFmpegFrameGrabber(file).use { reader ->
                            reader.setOption("probesize", "2000000")
                            reader.start()
                            val tags = reader.metadata.entries.associate { it.key.lowercase() to it.value }
                            val title = tags["title"]?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                            val artist = tags["artist"]?.takeIf { it.isNotBlank() } ?: tags["album_artist"] ?: "Bilinmeyen sanatçı"
                            val id = "file:" + MessageDigest.getInstance("SHA-256").digest(canonical.lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
                            val cover = findCover(file) ?: embeddedCover(reader, id.substringAfter(':'))
                            songs += SavedTrack(id, title, artist, cover, (reader.lengthInTime / 1000).coerceAtLeast(0), canonical, tags["album"].orEmpty(), file.lastModified())
                        }
                    } catch (_: Exception) { failed++ }
                }
            }
        }
        return ScanResult(songs.sortedWith(compareBy({ it.artists.lowercase() }, { it.album.lowercase() }, { it.title.lowercase() })), failed)
    }
    private fun findCover(file: File): String? = listOf(file.nameWithoutExtension + ".jpg", file.nameWithoutExtension + ".png", "cover.jpg", "folder.jpg", "cover.png")
        .map { File(file.parentFile, it) }.firstOrNull { it.isFile }?.toURI()?.toString()

    private fun embeddedCover(reader: FFmpegFrameGrabber, hash: String): String? = runCatching {
        val context = reader.formatContext
        val hasPicture = (0 until context.nb_streams()).any { (context.streams(it).disposition() and 0x400) != 0 }
        if (!hasPicture) return@runCatching null
        val image = reader.grabImage() ?: return@runCatching null
        Java2DFrameConverter().use { converter ->
            val buffered = converter.convert(image) ?: return@use null
            val output = File(appDir(), "local-covers/$hash.png")
            output.parentFile.mkdirs()
            ImageIO.write(buffered, "png", output)
            output.toURI().toString()
        }
    }.getOrNull()
}
