package com.metrolist.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class QueueStateTest {
    @Test
    fun moveBeforeAndAfterTheActiveItem() {
        val state = queue(a, b, c, d, e, start = 2)
        val active = state.currentKey!!

        val movedAfter = state.move(state.entries[0].key, 4)
        assertEquals(listOf("b", "c", "d", "e", "a"), movedAfter.ids())
        assertEquals(active, movedAfter.currentKey)
        assertEquals(1, movedAfter.index)
        assertEquals(c, movedAfter.current)
        assertEquals(PlaybackEffect.Keep, playbackRestart(state, movedAfter))

        val movedBefore = state.move(state.entries[4].key, 0)
        assertEquals(listOf("e", "a", "b", "c", "d"), movedBefore.ids())
        assertEquals(active, movedBefore.currentKey)
        assertEquals(3, movedBefore.index)
        assertEquals(PlaybackEffect.Keep, playbackRestart(state, movedBefore))

        val movedActive = state.move(active, 0)
        assertEquals(listOf("c", "a", "b", "d", "e"), movedActive.ids())
        assertEquals(active, movedActive.currentKey)
        assertEquals(0, movedActive.index)
        assertEquals(PlaybackEffect.Keep, playbackRestart(state, movedActive))

        val down = state.moveDown(state.entries[0].key)
        assertEquals(listOf("b", "a", "c", "d", "e"), down.ids())
        assertEquals(active, down.currentKey)
        assertEquals(2, down.index)
        assertEquals(PlaybackEffect.Keep, playbackRestart(state, down))

        val up = state.moveUp(state.entries[3].key)
        assertEquals(listOf("a", "b", "d", "c", "e"), up.ids())
        assertEquals(active, up.currentKey)
        assertEquals(3, up.index)
        assertEquals(PlaybackEffect.Keep, playbackRestart(state, up))

        val activeDown = state.moveDown(active)
        assertEquals(listOf("a", "b", "d", "c", "e"), activeDown.ids())
        assertEquals(active, activeDown.currentKey)
        assertEquals(3, activeDown.index)
        assertEquals(PlaybackEffect.Keep, playbackRestart(state, activeDown))

        assertSame(state, state.moveUp(state.entries.first().key))
        assertSame(state, state.moveDown(state.entries.last().key))
    }

    @Test
    fun duplicateSongsKeepSeparateKeys() {
        val song = track("same", "Aynı şarkı")
        val base = queue(song, other, start = 0)
        val firstKey = base.currentKey!!

        val playedNext = base.playNext(listOf(song, song))
        assertEquals(listOf("same", "same", "same", "other"), playedNext.ids())
        assertEquals(firstKey, playedNext.currentKey)
        assertEquals(0, playedNext.index)
        assertEquals(4, playedNext.entries.map { it.key }.toSet().size)
        assertEquals(PlaybackEffect.Keep, playbackRestart(base, playedNext))

        val appended = playedNext.append(listOf(song))
        assertEquals(listOf("same", "same", "same", "other", "same"), appended.ids())
        assertEquals(firstKey, appended.currentKey)
        assertEquals(PlaybackEffect.Keep, playbackRestart(playedNext, appended))
        assertEquals(5, appended.entries.map { it.key }.toSet().size)

        val secondCopy = appended.entries[1].key
        val removedCopy = appended.remove(secondCopy)
        assertEquals(listOf("same", "same", "other", "same"), removedCopy.ids())
        assertEquals(firstKey, removedCopy.currentKey)
        assertEquals(0, removedCopy.index)
        assertTrue(removedCopy.entries.none { it.key == secondCopy })
        assertEquals(PlaybackEffect.Keep, playbackRestart(appended, removedCopy))

        val jumped = appended.jump(4)
        assertEquals(appended.entries.last().key, jumped.currentKey)
        assertNotEquals(firstKey, jumped.currentKey)
        assertEquals(song.id, jumped.current?.id)
        assertEquals(PlaybackEffect.Start, playbackRestart(appended, jumped))

        val token = appended.radioToken()
        val otherCopy = appended.jump(1)
        assertIs<RadioApply.Rejected>(otherCopy.applyRadio(token, listOf(track("fresh"))))
        assertEquals(otherCopy, mergeRadio(otherCopy, token, listOf(track("fresh"))))
    }

    @Test
    fun removeActiveItem() {
        val state = queue(a, b, c, d, start = 1)
        val removed = state.remove(state.currentKey!!)
        assertEquals(listOf("a", "c", "d"), removed.ids())
        assertEquals(1, removed.index)
        assertEquals(c, removed.current)
        assertTrue(removed.epoch > state.epoch)
        assertEquals(PlaybackEffect.Start, playbackRestart(state, removed))

        val tail = queue(a, b, start = 1)
        val previous = tail.remove(tail.currentKey!!)
        assertEquals(listOf("a"), previous.ids())
        assertEquals(0, previous.index)
        assertEquals(a, previous.current)
        assertEquals(PlaybackEffect.Start, playbackRestart(tail, previous))

        val only = queue(a, start = 0)
        val empty = only.remove(only.currentKey!!)
        assertTrue(empty.entries.isEmpty())
        assertEquals(-1, empty.index)
        assertNull(empty.current)
        assertEquals(PlaybackEffect.Stop, playbackRestart(only, empty))

        val around = queue(a, b, c, start = 1)
        val active = around.currentKey
        val before = around.remove(around.entries[0].key)
        assertEquals(listOf("b", "c"), before.ids())
        assertEquals(active, before.currentKey)
        assertEquals(0, before.index)
        assertEquals(around.epoch, before.epoch)
        assertEquals(PlaybackEffect.Keep, playbackRestart(around, before))

        val after = around.remove(around.entries[2].key)
        assertEquals(listOf("a", "b"), after.ids())
        assertEquals(active, after.currentKey)
        assertEquals(1, after.index)
        assertEquals(PlaybackEffect.Keep, playbackRestart(around, after))
        assertSame(around, around.remove(999L))
    }

    @Test
    fun emptyQueue() {
        val empty = QueueState()
        assertEquals(-1, empty.index)
        assertNull(empty.current)
        assertNull(empty.currentKey)
        assertSame(empty, empty.remove(1))
        assertSame(empty, empty.move(1, 0))
        assertSame(empty, empty.moveUp(1))
        assertSame(empty, empty.moveDown(1))
        assertSame(empty, empty.moveToPlayNext(1))
        assertSame(empty, empty.moveToEnd(1))
        assertSame(empty, empty.clear())
        assertSame(empty, empty.append(emptyList()))
        assertSame(empty, empty.playNext(emptyList()))
        assertEquals(PlaybackEffect.None, playbackRestart(empty, empty.clear()))
        assertIs<RadioApply.Rejected>(empty.applyRadio(empty.radioToken(), listOf(a)))
        assertEquals(empty, mergeRadio(empty, empty.radioToken(), listOf(a)))

        val started = empty.playNext(listOf(a, b))
        assertEquals(listOf("a", "b"), started.ids())
        assertEquals(0, started.index)
        assertEquals(a, started.current)
        assertEquals(PlaybackEffect.Start, playbackRestart(empty, started))

        val appended = empty.append(listOf(c))
        assertEquals(listOf("c"), appended.ids())
        assertEquals(0, appended.index)
        assertEquals(PlaybackEffect.Start, playbackRestart(empty, appended))

        val cleared = queue(a, b, c, start = 1).clear()
        assertTrue(cleared.entries.isEmpty())
        assertEquals(-1, cleared.index)
        assertEquals(PlaybackEffect.Stop, playbackRestart(queue(a, b, c, start = 1), cleared))
        assertSame(cleared, cleared.clear())
    }

    @Test
    fun radioAppendRaceKeepsUserEdits() {
        val state = queue(a, b, c, d, e, start = 2)
        val token = state.radioToken()
        val active = state.currentKey!!

        var edited = state.move(state.entries[0].key, 4)
        edited = edited.remove(edited.entries.first { it.track == d }.key)
        edited = edited.playNext(listOf(b))
        edited = edited.append(listOf(a))
        assertEquals(listOf("b", "c", "b", "e", "a", "a"), edited.ids())
        assertEquals(active, edited.currentKey)
        assertEquals(1, edited.index)
        assertEquals(PlaybackEffect.Keep, playbackRestart(state, edited))

        val incoming = listOf(a, c, d, track("fresh"), track("fresh"), track(""))
        val merged = mergeRadio(edited, token, incoming)
        assertEquals(listOf("b", "c", "b", "e", "a", "a", "fresh"), merged.ids())
        assertEquals(active, merged.currentKey)
        assertEquals(1, merged.index)
        assertEquals(edited.epoch, merged.epoch)
        assertEquals(PlaybackEffect.Keep, playbackRestart(edited, merged))
        assertTrue(merged.entries.take(edited.entries.size).map { it.key } == edited.entries.map { it.key })

        val snapshotAppend = state.append(incoming.filter { it.id.isNotBlank() }.distinctBy { it.id })
        assertNotEquals(snapshotAppend.ids(), merged.ids())

        val replaced = state.replace(listOf(track("z")), 0)
        assertEquals(replaced, mergeRadio(replaced, token, listOf(track("fresh"))))
        assertIs<RadioApply.Rejected>(replaced.applyRadio(token, listOf(track("fresh"))))

        val cleared = state.clear()
        assertEquals(cleared, mergeRadio(cleared, token, listOf(track("fresh"))))
        assertEquals(PlaybackEffect.Stop, playbackRestart(state, cleared))

        val removedActive = state.remove(active)
        assertEquals(removedActive, mergeRadio(removedActive, token, listOf(track("fresh"))))
        assertTrue(removedActive.ids().none { it == "fresh" })

        val filled = queue(*(0..8).map { track("s$it") }.toTypedArray(), start = 0)
        val filledToken = filled.radioToken()
        val reordered = filled.moveDown(filled.entries[1].key)
        assertEquals(reordered, mergeRadio(reordered, filledToken, listOf(track("fresh"))))
        assertEquals(PlaybackEffect.Keep, playbackRestart(filled, reordered))
    }

    @Test
    fun playNextAndMoveToEndDoNotRestartTheActiveEntry() {
        val state = queue(a, b, c, start = 1)
        val active = state.currentKey!!

        val next = state.playNext(listOf(e, d))
        assertEquals(listOf("a", "b", "e", "d", "c"), next.ids())
        assertEquals(active, next.currentKey)
        assertEquals(1, next.index)
        assertEquals(PlaybackEffect.Keep, playbackRestart(state, next))

        val end = state.append(listOf(e))
        assertEquals(listOf("a", "b", "c", "e"), end.ids())
        assertEquals(active, end.currentKey)
        assertEquals(PlaybackEffect.Keep, playbackRestart(state, end))

        val toNext = state.moveToPlayNext(state.entries[0].key)
        assertEquals(listOf("b", "a", "c"), toNext.ids())
        assertEquals(active, toNext.currentKey)
        assertEquals(0, toNext.index)
        assertEquals(PlaybackEffect.Keep, playbackRestart(state, toNext))

        val alreadyNext = state.moveToPlayNext(state.entries[2].key)
        assertSame(state, alreadyNext)

        val toEnd = state.moveToEnd(state.entries[0].key)
        assertEquals(listOf("b", "c", "a"), toEnd.ids())
        assertEquals(active, toEnd.currentKey)
        assertEquals(0, toEnd.index)

        val currentToEnd = state.moveToEnd(active)
        assertEquals(listOf("a", "c", "b"), currentToEnd.ids())
        assertEquals(active, currentToEnd.currentKey)
        assertEquals(2, currentToEnd.index)
        assertEquals(PlaybackEffect.Keep, playbackRestart(state, currentToEnd))
        assertSame(state, state.moveToPlayNext(active))
        assertSame(state, state.moveToEnd(state.entries.last().key))
    }
}

private val a = track("a")
private val b = track("b")
private val c = track("c")
private val d = track("d")
private val e = track("e")
private val other = track("other")

private fun track(id: String, title: String = id) = Track(id, title, "Sanatçı", null, 1_000)

private fun queue(vararg tracks: Track, start: Int) = QueueState().replace(tracks.toList(), start)

private fun QueueState.ids() = entries.map { it.track.id }
