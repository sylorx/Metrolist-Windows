package com.metrolist.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlocksTest {
    private fun track(id: String, artists: String) = Track(id, id, artists, null, 1_000)

    @Test
    fun radioSkipsBlockedSongAndArtist() {
        val blocks = withArtistBlocks(withSongBlock(PlaybackBlocks(), "a", blocked = true), listOf("Duman"), blocked = true)
        val known = setOf("now")
        val extra = listOf(
            track("a", "Başka"),
            track("b", "Duman"),
            track("c", "Sezen Aksu"),
            track("c", "Sezen Aksu"),
            track("now", "Sezen Aksu"),
        )
        assertEquals(listOf("c"), radioAdditions(extra, known) { matchesBlock(it, blocks) }.map { it.id })
    }

    @Test
    fun artistMatchIgnoresCaseAndBlankName() {
        val blocks = withArtistBlocks(PlaybackBlocks(), listOf("  Duman "), blocked = true)
        assertTrue(matchesBlock(track("1", "duman, Birisi"), blocks))
        assertFalse(matchesBlock(track("2", "Bilinmeyen sanatçı"), blocks))
        assertEquals(listOf("Birisi"), artistNames("Bilinmeyen sanatçı, Birisi"))
    }

    @Test
    fun blockingDropsRadioTailAndSkipsCurrent() {
        val tracks = listOf(
            track("manual-before", "Duman"),
            track("current", "Duman"),
            track("radio-next", "Duman"),
            track("manual-after", "Duman"),
        )
        val edit = queueAfterBlock(tracks, index = 1, radioIds = setOf("radio-next")) { it.artists == "Duman" }
        assertEquals(listOf("manual-before", "manual-after"), edit.tracks.map { it.id })
        assertEquals(1, edit.index)
        assertTrue(edit.restart)
    }

    @Test
    fun manualQueueStaysWhenCurrentIsNotBlocked() {
        val tracks = listOf(track("current", "Sezen"), track("radio", "Duman"), track("album", "Duman"))
        val edit = queueAfterBlock(tracks, index = 0, radioIds = setOf("radio")) { it.id == "radio" }
        assertEquals(listOf("current", "album"), edit.tracks.map { it.id })
        assertEquals(0, edit.index)
        assertFalse(edit.restart)
    }
}
