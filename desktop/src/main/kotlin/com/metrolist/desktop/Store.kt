package com.metrolist.desktop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Each document is replaced atomically; a previous valid copy survives interrupted writes. */
internal fun atomicWrite(file: File, text: String, backup: Boolean = true) {
    file.parentFile.mkdirs()
    val temporary = File.createTempFile(file.name, ".tmp", file.parentFile)
    try {
        temporary.outputStream().use { stream ->
            stream.write(text.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        if (backup && file.exists()) Files.copy(file.toPath(), File(file.path + ".bak").toPath(), StandardCopyOption.REPLACE_EXISTING)
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally { temporary.delete() }
}

class Store(private val directory: File = appDir(), private val protectCookie: Boolean = true) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private fun file(name: String) = File(directory, "$name.json")
    var warning: String? = null
        private set
    var settings: Settings = read(file("settings"), Settings())
        private set
    var history: List<HistoryEntry> = read(file("history"), emptyList())
        private set
    var library: LocalLibrary = read(file("library"), LocalLibrary())
        private set
    var playback: PlaybackMemory = read(file("playback"), PlaybackMemory())
        private set
    var sessions: List<ListeningSession> = read(file("listening"), emptyList())
        private set
    var pendingSync: List<SyncAction> = read(file("sync"), emptyList())
        private set
    private val secret = File(directory, "account.dpapi")

    init {
        if (protectCookie) {
            try {
                if (settings.cookie.isNotBlank()) {
                    ProtectedAccount.write(secret, settings.cookie)
                    atomicWrite(file("settings"), json.encodeToString(settings.copy(cookie = "")), backup = false)
                    // Older backups may contain the migrated plaintext cookie too.
                    atomicWrite(File(file("settings").path + ".bak"), json.encodeToString(settings.copy(cookie = "")), backup = false)
                } else if (secret.exists()) settings = settings.copy(cookie = ProtectedAccount.read(secret))
            } catch (_: Exception) { warning = "Hesap bilgisi Windows kasasından okunamadı veya taşınamadı. Hesap ayarlarını kontrol et." }
        }
    }

    @Synchronized fun savePlayback(value: PlaybackMemory) {
        atomicWrite(file("playback"), json.encodeToString(value))
        playback = value
    }

    @Synchronized fun saveSettings(value: Settings) {
        if (protectCookie) {
            if (value.cookie.isBlank()) Files.deleteIfExists(secret.toPath())
            else if (value.cookie != settings.cookie || !secret.exists()) ProtectedAccount.write(secret, value.cookie)
        }
        atomicWrite(file("settings"), json.encodeToString(value.copy(cookie = "")))
        settings = value
    }

    @Synchronized fun addHistory(track: Track) {
        val now = System.currentTimeMillis()
        val entry = HistoryEntry(track.id, track.title, track.artists, track.thumbnail, now, track.localPath, track.durationMs, track.album)
        val updated = (listOf(entry) + history.filterNot { it.id == track.id && now - it.playedAt < 30_000 }).take(20_000)
        atomicWrite(file("history"), json.encodeToString(updated))
        history = updated
    }

    @Synchronized fun addSession(value: ListeningSession) {
        if (value.listenedMs <= 0) return
        val updated = compactListening(sessions + value)
        atomicWrite(file("listening"), json.encodeToString(updated))
        sessions = updated
    }

    @Synchronized fun toggleLike(track: SavedTrack): Boolean {
        val exists = library.liked.any { it.id == track.id }
        saveLibrary(library.copy(liked = if (exists) library.liked.filterNot { it.id == track.id } else listOf(track) + library.liked))
        return !exists
    }

    @Synchronized fun createPlaylist(title: String, tracks: List<SavedTrack> = emptyList()): LocalPlaylist {
        require(title.isNotBlank()) { "Liste adı boş olamaz" }
        val created = LocalPlaylist("local-" + java.util.UUID.randomUUID(), title.trim(), tracks.distinctBy { it.id })
        saveLibrary(library.copy(playlists = listOf(created) + library.playlists))
        return created
    }

    @Synchronized fun updatePlaylist(id: String, transform: (LocalPlaylist) -> LocalPlaylist) {
        saveLibrary(library.copy(playlists = library.playlists.map { if (it.id == id) transform(it) else it }))
    }
    @Synchronized fun deletePlaylist(id: String) { saveLibrary(library.copy(playlists = library.playlists.filterNot { it.id == id })) }
    @Synchronized fun addToPlaylist(playlistId: String, track: SavedTrack) { addToPlaylist(playlistId, listOf(track)) }
    @Synchronized fun addToPlaylist(playlistId: String, tracks: List<SavedTrack>) {
        updatePlaylist(playlistId) { it.copy(tracks = (it.tracks + tracks).distinctBy { song -> song.id }) }
    }
    @Synchronized fun removeTracks(id: String, ids: Set<String>) {
        updatePlaylist(id) { it.copy(tracks = it.tracks.filterNot { track -> track.id in ids }) }
    }
    @Synchronized fun updateLocalTracks(tracks: List<SavedTrack>) { saveLibrary(library.copy(localTracks = tracks)) }
    @Synchronized fun saveLibrary(value: LocalLibrary) {
        atomicWrite(file("library"), json.encodeToString(value))
        library = value
    }
    @Synchronized fun enqueueSync(action: SyncAction) {
        val updated = pendingSync.filterNot { old ->
            action.kind in listOf("order", "rename", "like") && old.kind == action.kind && old.account == action.account &&
                old.playlistId == action.playlistId && (action.kind != "like" || old.value == action.value)
        } + action
        atomicWrite(file("sync"), json.encodeToString(updated))
        pendingSync = updated
    }
    @Synchronized fun completeSync(id: String) {
        val updated = pendingSync.filterNot { it.id == id }
        atomicWrite(file("sync"), json.encodeToString(updated))
        pendingSync = updated
    }
    @Synchronized fun exportBackup(destination: File) {
        val backup = LibraryBackup(library = library, settings = settings.copy(cookie = ""), history = history, sessions = sessions)
        atomicWrite(destination, json.encodeToString(backup))
    }
    @Synchronized fun importBackup(source: File) {
        require(source.length() <= 64L * 1024 * 1024) { "Yedek 64 MB sınırını aşıyor" }
        val backup = json.decodeFromString<LibraryBackup>(source.readText())
        require(backup.version == 1) { "Bu yedek sürümü desteklenmiyor" }
        // Preserve this computer's account, output device and folder configuration.
        val importedSettings = backup.settings.copy(cookie = settings.cookie, musicFolders = settings.musicFolders,
            audio = backup.settings.audio.copy(device = settings.audio.device)).validated()
        val playlists = backup.library.playlists.map { it.copy(remoteId = null, remoteBindings = emptyMap()) }
        val merged = library.copy(liked = (library.liked + backup.library.liked).distinctBy { it.id },
            playlists = (library.playlists + playlists.filter { incoming -> library.playlists.none { it.id == incoming.id } }))
        val mergedHistory = (history + backup.history).distinctBy { it.id to it.playedAt }.sortedByDescending { it.playedAt }.take(20_000)
        val mergedSessions = compactListening((sessions + backup.sessions.filter { it.listenedMs > 0 }).distinctBy { it.track.id to it.startedAt })
        // Keep a complete recovery snapshot before merging any document.
        exportBackup(File(directory, "before-import.json"))
        saveLibrary(merged)
        atomicWrite(file("history"), json.encodeToString(mergedHistory)); history = mergedHistory
        atomicWrite(file("listening"), json.encodeToString(mergedSessions)); sessions = mergedSessions
        saveSettings(importedSettings)
    }

    private inline fun <reified T> read(file: File, fallback: T): T {
        if (!file.exists() && !File(file.path + ".bak").exists()) return fallback
        for (candidate in listOf(file, File(file.path + ".bak"))) {
            val result = runCatching { json.decodeFromString<T>(candidate.readText()) }
            if (result.isSuccess) {
                if (candidate != file) {
                    warning = "${file.name} önceki yedeğinden kurtarıldı."
                    atomicWrite(file, candidate.readText(), backup = false)
                }
                return result.getOrThrow()
            }
        }
        // Never silently overwrite the only corrupt copy.
        if (file.exists()) Files.copy(file.toPath(), File(file.path + ".corrupt-${System.currentTimeMillis()}").toPath())
        warning = "${file.name} okunamadı; kurtarma için eski dosya saklandı."
        return fallback
    }
}

fun Settings.validated() = copy(audio = audio.copy(
    bands = List(10) { audio.bands.getOrElse(it) { 0f }.let { gain -> if (gain.isFinite()) gain.coerceIn(-12f, 12f) else 0f } },
    crossfadeSeconds = audio.crossfadeSeconds.coerceIn(0, 12),
))
