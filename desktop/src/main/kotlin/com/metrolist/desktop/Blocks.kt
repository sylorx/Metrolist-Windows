package com.metrolist.desktop

import kotlinx.serialization.Serializable

@Serializable
data class PlaybackBlocks(
    val songs: List<String> = emptyList(),
    val artists: List<String> = emptyList(),
)

internal fun artistNames(artists: String): List<String> =
    artists.split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.equals("Bilinmeyen sanatçı", ignoreCase = true) }

internal fun artistKey(name: String): String = name.trim().lowercase(java.util.Locale.ROOT)

internal fun matchesBlock(track: Track, blocks: PlaybackBlocks): Boolean {
    if (track.id in blocks.songs) return true
    val blocked = blocks.artists.map(::artistKey).toSet()
    return artistNames(track.artists).any { artistKey(it) in blocked }
}

internal fun withSongBlock(blocks: PlaybackBlocks, id: String, blocked: Boolean): PlaybackBlocks {
    val songs = if (blocked) (listOf(id) + blocks.songs.filterNot { it == id }).take(500)
    else blocks.songs.filterNot { it == id }
    return blocks.copy(songs = songs)
}

internal fun withArtistBlocks(blocks: PlaybackBlocks, names: List<String>, blocked: Boolean): PlaybackBlocks {
    val keys = names.map(::artistKey).filter { it.isNotEmpty() }.toSet()
    if (keys.isEmpty()) return blocks
    val kept = blocks.artists.filterNot { artistKey(it) in keys }
    if (!blocked) return blocks.copy(artists = kept)
    val added = names.map { it.trim() }.filter { it.isNotEmpty() && artistKey(it) !in kept.map(::artistKey).toSet() }
    return blocks.copy(artists = (added + kept).distinctBy(::artistKey).take(500))
}

internal fun radioAdditions(extra: List<Track>, known: Set<String>, blocked: (Track) -> Boolean): List<Track> =
    extra.filter { it.id !in known && !blocked(it) }.distinctBy { it.id }

internal data class QueueEdit(
    val tracks: List<Track>,
    val index: Int,
    val restart: Boolean,
)

/** Drops auto-queued blocked tracks. The track that is playing is skipped too, because the listener just rejected it. */
internal fun queueAfterBlock(
    tracks: List<Track>,
    index: Int,
    radioIds: Set<String>,
    blocked: (Track) -> Boolean,
): QueueEdit {
    if (tracks.isEmpty() || index !in tracks.indices) return QueueEdit(tracks, index, restart = false)
    val kept = ArrayList<Track>(tracks.size)
    var removedBefore = 0
    tracks.forEachIndexed { position, track ->
        val drop = position != index && track.id in radioIds && blocked(track)
        if (drop) {
            if (position < index) removedBefore++
        } else {
            kept.add(track)
        }
    }
    val at = index - removedBefore
    if (at !in kept.indices || !blocked(kept[at])) return QueueEdit(kept, at, restart = false)
    val rest = kept.filterIndexed { position, _ -> position != at }
    if (rest.isEmpty()) return QueueEdit(emptyList(), -1, restart = false)
    return QueueEdit(rest, at.coerceAtMost(rest.lastIndex), restart = true)
}
