package com.metrolist.desktop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class Store {
    private val json = Json { ignoreUnknownKeys = true }
    private val settingsFile = File(appDir(), "settings.json")
    private val historyFile = File(appDir(), "history.json")
    private val libraryFile = File(appDir(), "library.json")

    var settings: Settings = read(settingsFile, Settings())
        private set

    var history: List<HistoryEntry> = read(historyFile, emptyList())
        private set

    var library: LocalLibrary = read(libraryFile, LocalLibrary())
        private set

    private val historyLock = Any()
    private val libraryLock = Any()

    fun saveSettings(value: Settings) {
        settings = value
        settingsFile.writeText(json.encodeToString(value))
    }

    fun addHistory(track: Track) {
        synchronized(historyLock) {
            val entry = HistoryEntry(
                id = track.id,
                title = track.title,
                artists = track.artists,
                thumbnail = track.thumbnail,
                playedAt = System.currentTimeMillis(),
            )
            history = (listOf(entry) + history.filterNot { it.id == track.id && System.currentTimeMillis() - it.playedAt < 30_000 })
                .take(500)
            historyFile.writeText(json.encodeToString(history))
        }
    }

    fun toggleLike(track: SavedTrack): Boolean = synchronized(libraryLock) {
        val exists = library.liked.any { it.id == track.id }
        library = if (exists) {
            library.copy(liked = library.liked.filterNot { it.id == track.id })
        } else {
            library.copy(liked = listOf(track) + library.liked)
        }
        writeLibrary()
        !exists
    }

    fun createPlaylist(title: String): LocalPlaylist = synchronized(libraryLock) {
        val created = LocalPlaylist(id = "local-" + java.util.UUID.randomUUID(), title = title)
        library = library.copy(playlists = listOf(created) + library.playlists)
        writeLibrary()
        created
    }

    fun addToPlaylist(playlistId: String, track: SavedTrack) = synchronized(libraryLock) {
        library = library.copy(
            playlists = library.playlists.map { playlist ->
                if (playlist.id != playlistId) playlist
                else playlist.copy(tracks = listOf(track) + playlist.tracks.filterNot { it.id == track.id })
            },
        )
        writeLibrary()
    }

    private fun writeLibrary() {
        libraryFile.writeText(json.encodeToString(library))
    }

    private inline fun <reified T> read(file: File, fallback: T): T {
        if (!file.exists()) return fallback
        return runCatching { json.decodeFromString<T>(file.readText()) }.getOrDefault(fallback)
    }
}
