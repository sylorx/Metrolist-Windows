package com.metrolist.desktop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class Store {
    private val json = Json { ignoreUnknownKeys = true }
    private val settingsFile = File(appDir(), "settings.json")
    private val historyFile = File(appDir(), "history.json")

    var settings: Settings = read(settingsFile, Settings())
        private set

    var history: List<HistoryEntry> = read(historyFile, emptyList())
        private set

    private val historyLock = Any()

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

    private inline fun <reified T> read(file: File, fallback: T): T {
        if (!file.exists()) return fallback
        return runCatching { json.decodeFromString<T>(file.readText()) }.getOrDefault(fallback)
    }
}
