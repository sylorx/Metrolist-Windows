package com.metrolist.desktop

import kotlinx.serialization.Serializable

@Serializable
data class AudioSettings(
    val bands: List<Float> = List(10) { 0f },
    val normalize: Boolean = false,
    val device: String = "",
    val crossfadeSeconds: Int = 0,
    val gapless: Boolean = true,
)

enum class SmartRule(val title: String) {
    RecentLikes("Son 30 günde beğenilenler"),
    Forgotten("Uzun zamandır dinlenmeyenler"),
    MostPlayed("En çok dinlenenler"),
}

@Serializable
data class ListeningSession(
    val track: SavedTrack,
    val startedAt: Long,
    val listenedMs: Long = 0,
)

@Serializable
data class LibraryBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val library: LocalLibrary,
    val settings: Settings,
    val history: List<HistoryEntry>,
    val sessions: List<ListeningSession> = emptyList(),
)

fun smartTracks(rule: SmartRule, library: LocalLibrary, history: List<HistoryEntry>, now: Long = System.currentTimeMillis()): List<SavedTrack> {
    val month = 30L * 24 * 60 * 60 * 1000
    val all = (library.liked + library.playlists.flatMap { it.tracks } + library.localTracks).distinctBy { it.id }
    return when (rule) {
        SmartRule.RecentLikes -> library.liked.filter { it.addedAt >= now - month }
        SmartRule.Forgotten -> {
            val last = history.groupBy { it.id }.mapValues { (_, entries) -> entries.maxOf { it.playedAt } }
            all.filter { (last[it.id] ?: 0) < now - month }.sortedBy { last[it.id] ?: 0 }
        }
        SmartRule.MostPlayed -> {
            val counts = history.groupingBy { it.id }.eachCount()
            val known = all.associateBy { it.id }
            history.distinctBy { it.id }.sortedByDescending { counts[it.id] }.take(100).map {
                known[it.id] ?: it.toTrack().toSaved()
            }
        }
    }
}

/** Reorders occurrences, including duplicate tracks, while preserving the active occurrence. */
fun movedIndex(active: Int, from: Int, to: Int): Int = when {
    active == from -> to
    from < active && to >= active -> active - 1
    from > active && to <= active -> active + 1
    else -> active
}

/** Compact by local day and track so years of statistics do not grow once every polling tick. */
internal fun compactListening(input: List<ListeningSession>, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<ListeningSession> {
    val totals = linkedMapOf<Pair<java.time.LocalDate, String>, ListeningSession>()
    input.filter { it.listenedMs > 0 }.forEach { session ->
        var cursor = session.startedAt
        var remaining = session.listenedMs.coerceAtMost(7L * 24 * 60 * 60 * 1000)
        while (remaining > 0) {
            val day = java.time.Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val duration = minOf(remaining, end - cursor)
            val key = day to session.track.id
            val previous = totals[key]
            totals[key] = if (previous == null) session.copy(startedAt = cursor, listenedMs = duration)
                else previous.copy(startedAt = minOf(previous.startedAt, cursor), listenedMs = previous.listenedMs + duration)
            remaining -= duration; cursor += duration
        }
    }
    val newest = totals.keys.maxOfOrNull { it.first } ?: return emptyList()
    return totals.filterKeys { it.first >= newest.minusDays(399) }.values.sortedBy { it.startedAt }
}
