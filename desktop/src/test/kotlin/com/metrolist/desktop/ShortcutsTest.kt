package com.metrolist.desktop

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShortcutsTest {
    private val names = mapOf("space" to 32L, "left" to 37L, "right" to 39L, "up" to 38L, "down" to 40L, "m" to 77L)

    @Test fun defaultsPlayPauseAndChangeTracks() {
        val book = ShortcutBook()
        assertEquals(ShortcutSlot.PlayPause, book.actionFor(PressedKey(32), names))
        assertEquals(ShortcutSlot.Previous, book.actionFor(PressedKey(37), names))
        assertEquals(ShortcutSlot.Next, book.actionFor(PressedKey(39), names))
        assertEquals(ShortcutSlot.VolumeUp, book.actionFor(PressedKey(38), names))
        assertEquals(ShortcutSlot.VolumeDown, book.actionFor(PressedKey(40), names))
        assertEquals(ShortcutSlot.Mute, book.actionFor(PressedKey(77, ctrl = true), names))
        assertNull(book.actionFor(PressedKey(77), names))
    }

    @Test fun disabledBindingDoesNothingAndReassignSwapsPlaces() {
        val off = ShortcutBook(playPauseOn = false)
        assertNull(off.actionFor(PressedKey(32), names))
        val swapped = ShortcutBook().assign(ShortcutSlot.Next, "space")
        assertEquals("space", swapped.next)
        assertEquals("right", swapped.playPause)
        assertEquals(ShortcutSlot.Next, swapped.actionFor(PressedKey(32), names))
        assertEquals(ShortcutSlot.PlayPause, swapped.actionFor(PressedKey(39), names))
    }

    @Test fun capturedCodeMatchesWithoutAName() {
        val book = ShortcutBook().assign(ShortcutSlot.Previous, "ctrl+81")
        assertEquals(ShortcutSlot.Previous, book.actionFor(PressedKey(81, ctrl = true), names))
        assertNull(book.actionFor(PressedKey(81), names))
        assertEquals("ctrl+81", encodePressed(PressedKey(81, ctrl = true)))
    }
}
