@file:OptIn(ExperimentalCoroutinesApi::class)

package com.metrolist.desktop

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SleepTimerTest {
    @Test
    fun expiryPausesAtTheDeadline() = runTest {
        var pauses = 0
        val timer = timer { pauses += 1 }
        timer.start(15)
        assertTrue(timer.isActive)
        assertEquals(15 * MINUTE, timer.remainingMs)
        advanceTimeBy(MINUTE)
        runCurrent()
        assertEquals(14 * MINUTE, timer.remainingMs)
        assertEquals(0, pauses)
        advanceTimeBy(14 * MINUTE - 1)
        assertEquals(0, pauses)
        assertTrue(timer.isActive)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, pauses)
        assertFalse(timer.isActive)
        assertNull(timer.remainingMs)
        advanceTimeBy(15 * MINUTE)
        assertEquals(1, pauses)
    }

    @Test
    fun rearmReplacesThePreviousDeadline() = runTest {
        var pauses = 0
        val timer = timer { pauses += 1 }
        timer.pauseAtEndOfCurrentSong()
        timer.start(60)
        assertFalse(timer.pauseAtEndOfSong)
        assertEquals(60 * MINUTE, timer.remainingMs)
        advanceTimeBy(10 * MINUTE)
        assertEquals(0, pauses)
        timer.start(15)
        assertEquals(15 * MINUTE, timer.remainingMs)
        advanceTimeBy(15 * MINUTE - 1)
        assertEquals(0, pauses)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, pauses)
        assertFalse(timer.isActive)
        advanceTimeBy(50 * MINUTE)
        assertEquals(1, pauses)
    }

    @Test
    fun cancelPreventsPause() = runTest {
        var pauses = 0
        val timer = timer { pauses += 1 }
        timer.start(30)
        advanceTimeBy(5 * MINUTE)
        timer.cancel()
        assertFalse(timer.isActive)
        assertNull(timer.remainingMs)
        assertFalse(timer.pauseAtEndOfSong)
        advanceTimeBy(60 * MINUTE)
        assertEquals(0, pauses)
        timer.pauseAtEndOfCurrentSong()
        timer.cancel()
        assertFalse(timer.onTrackEnded())
        assertEquals(0, pauses)
        timer.start(0)
        timer.start(-5)
        assertFalse(timer.isActive)
    }

    @Test
    fun endOfSongWaitsForTheTrackInsteadOfTheClock() = runTest {
        var pauses = 0
        val timer = timer { pauses += 1 }
        timer.pauseAtEndOfCurrentSong()
        assertTrue(timer.isActive)
        assertTrue(timer.pauseAtEndOfSong)
        assertNull(timer.remainingMs)
        advanceTimeBy(90 * MINUTE)
        assertEquals(0, pauses)
        assertTrue(timer.isActive)
        assertTrue(timer.onTrackEnded())
        assertEquals(1, pauses)
        assertFalse(timer.isActive)
        assertFalse(timer.pauseAtEndOfSong)
        assertFalse(timer.onTrackEnded())
        assertEquals(1, pauses)
    }

    @Test
    fun endOfSongDoesNotRestartWhenRepeatOrRadioWould() = runTest {
        var pauses = 0
        val timer = timer { pauses += 1 }
        timer.start(30)
        assertFalse(timer.onTrackEnded())
        assertEquals(0, pauses)
        assertTrue(
            playbackMayRestart(
                sleepPaused = false,
                repeatOne = true,
                hasUpcoming = true,
                radioLoading = true,
            ),
        )
        timer.pauseAtEndOfCurrentSong()
        advanceTimeBy(30 * MINUTE)
        assertEquals(0, pauses)
        val sleepPaused = timer.onTrackEnded()
        assertTrue(sleepPaused)
        assertEquals(1, pauses)
        assertFalse(
            playbackMayRestart(
                sleepPaused = sleepPaused,
                repeatOne = true,
                hasUpcoming = false,
                radioLoading = false,
            ),
        )
        assertFalse(
            playbackMayRestart(
                sleepPaused = true,
                repeatOne = false,
                hasUpcoming = true,
                radioLoading = false,
            ),
        )
        assertFalse(
            playbackMayRestart(
                sleepPaused = true,
                repeatOne = false,
                hasUpcoming = false,
                radioLoading = true,
            ),
        )
        assertFalse(playbackMayRestart(sleepPaused = true, repeatOne = true, hasUpcoming = true, radioLoading = true))
        assertTrue(playbackMayRestart(sleepPaused = false, repeatOne = true, hasUpcoming = false, radioLoading = false))
        assertTrue(playbackMayRestart(sleepPaused = false, repeatOne = false, hasUpcoming = true, radioLoading = false))
        assertTrue(playbackMayRestart(sleepPaused = false, repeatOne = false, hasUpcoming = false, radioLoading = true))
        assertFalse(playbackMayRestart(sleepPaused = false, repeatOne = false, hasUpcoming = false, radioLoading = false))
    }

    private fun TestScope.timer(onPause: () -> Unit): SleepTimer {
        val scheduler = testScheduler
        return SleepTimer(scope = backgroundScope, clock = { scheduler.currentTime }, onPause = onPause)
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
