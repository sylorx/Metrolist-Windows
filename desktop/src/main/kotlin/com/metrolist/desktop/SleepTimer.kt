package com.metrolist.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Wall-clock sleep timer for the desktop player.
 *
 * [clock] and [scope] are injected so a test can expire the deadline on a
 * virtual clock. There is no fade. [onTrackEnded] is the desktop playback
 * event that replaces the Android player's end callback: call it before
 * repeat and radio. When it returns true, playback was paused and must not
 * start again.
 */
class SleepTimer(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val onPause: () -> Unit,
) {
    var triggerTimeMs by mutableStateOf(INACTIVE)
        private set

    var pauseAtEndOfSong by mutableStateOf(false)
        private set

    var remainingMs by mutableStateOf<Long?>(null)
        private set

    val isActive: Boolean
        get() = triggerTimeMs != INACTIVE || pauseAtEndOfSong

    private var job: Job? = null
    private var generation = 0

    fun start(minutes: Int) {
        if (minutes <= 0) return
        val durationMs = minutes.toLong() * MILLIS_PER_MINUTE
        if (durationMs <= 0L) return
        val gen = ++generation
        job?.cancel()
        job = null
        pauseAtEndOfSong = false
        val deadline = clock() + durationMs
        triggerTimeMs = deadline
        remainingMs = durationMs
        job = scope.launch {
            var left = deadline - clock()
            while (gen == generation && left > 0L) {
                remainingMs = left
                delay(minOf(TICK_MS, left))
                if (gen != generation) return@launch
                left = deadline - clock()
            }
            if (gen == generation) complete(gen)
        }
    }

    fun pauseAtEndOfCurrentSong() {
        generation += 1
        job?.cancel()
        job = null
        triggerTimeMs = INACTIVE
        remainingMs = null
        pauseAtEndOfSong = true
    }

    fun cancel() {
        generation += 1
        job?.cancel()
        job = null
        triggerTimeMs = INACTIVE
        pauseAtEndOfSong = false
        remainingMs = null
    }

    /**
     * Natural end of the current track.
     * True only for "pause when this song ends": the timer pauses and clears,
     * and the caller must not apply repeat or radio.
     */
    fun onTrackEnded(): Boolean {
        if (!pauseAtEndOfSong) return false
        complete(generation)
        return true
    }

    private fun complete(gen: Int) {
        if (gen != generation) return
        generation += 1
        triggerTimeMs = INACTIVE
        pauseAtEndOfSong = false
        remainingMs = null
        val running = job
        job = null
        onPause()
        running?.cancel()
    }

    private companion object {
        const val INACTIVE = -1L
        const val TICK_MS = 1_000L
        const val MILLIS_PER_MINUTE = 60_000L
    }
}

/**
 * Repeat and radio may start another track only when the sleep timer did not
 * just pause. End-of-song pause reports [sleepPaused] and blocks both.
 */
internal fun playbackMayRestart(
    sleepPaused: Boolean,
    repeatOne: Boolean,
    hasUpcoming: Boolean,
    radioLoading: Boolean,
): Boolean = !sleepPaused && (repeatOne || hasUpcoming || radioLoading)
