package com.metrolist.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class DownloadEntry(val track: SavedTrack, val file: String, val bytes: Long, val savedAt: Long)

/**
 * Songs the listener saved for offline listening. Unlike the audio cache nothing here is ever removed
 * automatically. The index is replaced atomically, so an interrupted save never loses the list.
 */
internal class Downloads(
    private val directory: File,
    private val scope: CoroutineScope,
    private val fetch: suspend (SavedTrack, File) -> Unit,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val index = File(directory, "index.json")
    private val permits = Semaphore(2)
    private val jobs = HashMap<String, Job>()

    var entries by mutableStateOf(load())
        private set
    /** Songs waiting or being saved right now, by track ID. */
    var active by mutableStateOf<Map<String, SavedTrack>>(emptyMap())
        private set
    var failures by mutableStateOf(0)
        private set

    fun file(id: String): File? = entries.firstOrNull { it.track.id == id }?.let { File(directory, it.file) }?.takeIf { it.isFile }

    fun isSaved(id: String) = entries.any { it.track.id == id }

    fun usage(): Long = entries.sumOf { it.bytes }

    /** Queues remote songs that are not saved yet; local files are already on this computer. */
    fun save(tracks: List<SavedTrack>): Int {
        val fresh = tracks.filter { it.localPath == null && !isSaved(it.id) && it.id !in active }.distinctBy { it.id }
        if (fresh.isEmpty()) return 0
        failures = 0
        active = active + fresh.associateBy { it.id }
        fresh.forEach { track -> jobs[track.id] = scope.launch { run(track) } }
        return fresh.size
    }

    private suspend fun run(track: SavedTrack) {
        try {
            permits.withPermit {
                val name = track.id.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(40) + ".audio"
                val target = File(directory, name)
                withContext(io) {
                    directory.mkdirs()
                    fetch(track, target)
                    check(target.isFile && target.length() > 0) { "İndirme tamamlanmadı" }
                }
                entries = entries.filterNot { it.track.id == track.id } +
                    DownloadEntry(track.copy(addedAt = 0), name, target.length(), System.currentTimeMillis())
                write()
            }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            failures++
        } finally {
            active = active - track.id
            jobs.remove(track.id)
        }
    }

    fun cancelAll() {
        jobs.values.toList().forEach { it.cancel() }
    }

    fun remove(ids: Set<String>) {
        val (gone, kept) = entries.partition { it.track.id in ids }
        if (gone.isEmpty()) return
        gone.forEach { File(directory, it.file).delete() }
        entries = kept
        write()
    }

    fun removeAll() = remove(entries.map { it.track.id }.toSet())

    private fun load(): List<DownloadEntry> = runCatching {
        json.decodeFromString<List<DownloadEntry>>(index.readText()).filter { File(directory, it.file).isFile }
    }.getOrDefault(emptyList())

    private fun write() = atomicWrite(index, json.encodeToString(entries))
}
