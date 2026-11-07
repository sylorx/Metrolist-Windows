package com.metrolist.desktop

internal const val RadioLookahead = 8

internal enum class PlaybackEffect { None, Keep, Start, Stop }

/**
 * Whether editing the queue should restart playback.
 * The same entry key keeps playing, including when its index changes.
 * A different current entry starts. An empty queue stops.
 */
internal fun playbackRestart(before: QueueState, after: QueueState): PlaybackEffect = when {
    after == before -> PlaybackEffect.None
    after.entries.isEmpty() -> PlaybackEffect.Stop
    after.currentKey != before.currentKey -> PlaybackEffect.Start
    else -> PlaybackEffect.Keep
}

internal data class QueueEntry(
    val key: Long,
    val track: Track,
)

internal data class RadioToken(
    val epoch: Long,
    val anchorKey: Long?,
    val presentIds: Set<String>,
)

internal sealed interface RadioApply {
    data class Accepted(val state: QueueState) : RadioApply
    data object Rejected : RadioApply
}

/**
 * Queue entries carry a stable [QueueEntry.key] so one song can appear twice.
 * [epoch] changes when the queue is replaced, cleared, or the active entry is removed.
 * Auto-radio captured under an older token is rejected instead of being written over later edits.
 */
internal data class QueueState(
    val entries: List<QueueEntry> = emptyList(),
    val index: Int = -1,
    val nextKey: Long = 1L,
    val epoch: Long = 0L,
) {
    val currentKey: Long? get() = entries.getOrNull(index)?.key
    val current: Track? get() = entries.getOrNull(index)?.track

    fun radioToken(): RadioToken = RadioToken(
        epoch = epoch,
        anchorKey = currentKey,
        presentIds = entries.map { it.track.id }.toSet(),
    )

    fun replace(tracks: List<Track>, start: Int): QueueState {
        if (tracks.isEmpty()) return clear()
        val (added, next) = mint(tracks)
        return copy(
            entries = added,
            index = start.coerceIn(added.indices),
            nextKey = next,
            epoch = epoch + 1,
        )
    }

    fun playNext(tracks: List<Track>): QueueState {
        if (tracks.isEmpty()) return this
        val at = if (entries.isEmpty() || index < 0) 0 else index + 1
        return insert(tracks, at)
    }

    fun append(tracks: List<Track>): QueueState = insert(tracks, entries.size)

    fun remove(key: Long): QueueState {
        val position = entries.indexOfFirst { it.key == key }
        if (position < 0) return this
        val updated = entries.filterIndexed { entryIndex, _ -> entryIndex != position }
        val removingCurrent = position == index
        val newIndex = when {
            updated.isEmpty() -> -1
            position < index -> index - 1
            position == index -> index.coerceAtMost(updated.lastIndex)
            else -> index
        }
        return copy(
            entries = updated,
            index = newIndex,
            epoch = if (removingCurrent) epoch + 1 else epoch,
        )
    }

    fun move(key: Long, toIndex: Int): QueueState {
        val from = entries.indexOfFirst { it.key == key }
        if (from < 0 || entries.isEmpty()) return this
        val to = toIndex.coerceIn(0, entries.lastIndex)
        if (from == to) return this
        val updated = entries.toMutableList()
        val item = updated.removeAt(from)
        updated.add(to, item)
        val newIndex = when {
            index < 0 -> -1
            from == index -> to
            else -> {
                var shifted = index
                if (from < shifted) shifted -= 1
                if (to <= shifted) shifted += 1
                shifted
            }
        }
        return copy(entries = updated, index = newIndex)
    }

    fun moveUp(key: Long): QueueState {
        val from = entries.indexOfFirst { it.key == key }
        if (from <= 0) return this
        return move(key, from - 1)
    }

    fun moveDown(key: Long): QueueState {
        val from = entries.indexOfFirst { it.key == key }
        if (from < 0 || from >= entries.lastIndex) return this
        return move(key, from + 1)
    }

    fun moveToPlayNext(key: Long): QueueState {
        val from = entries.indexOfFirst { it.key == key }
        if (from < 0 || index < 0 || from == index) return this
        val destination = if (from < index) index else index + 1
        return move(key, destination)
    }

    fun moveToEnd(key: Long): QueueState {
        val from = entries.indexOfFirst { it.key == key }
        if (from < 0 || from == entries.lastIndex) return this
        return move(key, entries.lastIndex)
    }

    fun jump(toIndex: Int): QueueState {
        if (toIndex !in entries.indices || toIndex == index) return this
        return copy(index = toIndex)
    }

    fun clear(): QueueState {
        if (entries.isEmpty() && index < 0) return this
        return copy(entries = emptyList(), index = -1, epoch = epoch + 1)
    }

    /**
     * Accepts [incoming] only when [token] still matches this queue.
     * Existing entries stay in place. Songs already in the queue, or present when the
     * radio request started, are not put back.
     */
    fun applyRadio(token: RadioToken, incoming: List<Track>): RadioApply {
        val anchor = currentKey
        if (token.epoch != epoch || token.anchorKey == null || token.anchorKey != anchor) {
            return RadioApply.Rejected
        }
        val blocked = token.presentIds + entries.map { it.track.id }
        val fresh = ArrayList<Track>()
        val seen = HashSet<String>()
        for (track in incoming) {
            if (track.id.isBlank() || track.id in blocked || !seen.add(track.id)) continue
            fresh.add(track)
        }
        if (fresh.isEmpty()) return RadioApply.Accepted(this)
        return RadioApply.Accepted(append(fresh))
    }

    private fun insert(tracks: List<Track>, at: Int): QueueState {
        if (tracks.isEmpty()) return this
        val (added, next) = mint(tracks)
        val position = at.coerceIn(0, entries.size)
        val updated = ArrayList<QueueEntry>(entries.size + added.size)
        updated.addAll(entries)
        updated.addAll(position, added)
        val newIndex = when {
            entries.isEmpty() -> 0
            position <= index -> index + added.size
            else -> index
        }
        return copy(entries = updated, index = newIndex, nextKey = next)
    }

    private fun mint(tracks: List<Track>): Pair<List<QueueEntry>, Long> {
        var key = nextKey
        val minted = tracks.map { track -> QueueEntry(key = key++, track = track) }
        return minted to key
    }
}

/**
 * Applies radio onto the live queue. A rejected token leaves [current] untouched,
 * so a result captured before an edit cannot restore the earlier list.
 */
internal fun mergeRadio(
    current: QueueState,
    token: RadioToken,
    incoming: List<Track>,
    minimumAhead: Int = RadioLookahead,
): QueueState {
    if (current.index >= 0 && current.entries.size - current.index - 1 >= minimumAhead) return current
    return when (val result = current.applyRadio(token, incoming)) {
        is RadioApply.Accepted -> result.state
        RadioApply.Rejected -> current
    }
}
