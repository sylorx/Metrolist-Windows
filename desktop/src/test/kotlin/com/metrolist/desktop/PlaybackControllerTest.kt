package com.metrolist.desktop

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class PlaybackControllerTest {
    private class Engine(val ended: () -> Unit, val failed: (String) -> Unit) : PlaybackEngine {
        @Volatile override var positionMs = 0L
        @Volatile override var durationMs = 0L
        @Volatile override var ready = false
        override var volume = 1f
        override var envelope = 1f
        override var audio = AudioSettings()
        @Volatile var paused = false
        @Volatile var stopped = false
        @Volatile var opened: String? = null
        override fun play(url: String, headers: Map<String, String>, startMs: Long, duration: Long, startPaused: Boolean) {
            positionMs = startMs; durationMs = duration; paused = startPaused; opened = url; ready = true
        }
        override fun pause() { paused = true }
        override fun resume() { paused = false }
        override fun stop() { stopped = true }
    }
    private class Streams : PlaybackStreams {
        val forgotten = Collections.synchronizedList(mutableListOf<String>())
        override fun forget(videoId: String, quality: String) { forgotten += videoId }
        override fun keep(videoId: String, quality: String, stream: StreamResolver.Stream) {}
        override suspend fun openers(videoId: String, quality: String) = listOf(StreamResolver.Stream("first:$videoId", emptyMap()), StreamResolver.Stream("second:$videoId", emptyMap()))
        override suspend fun innertube(videoId: String, quality: String): StreamResolver.Stream? = null
        override suspend fun resolve(videoId: String, quality: String) = StreamResolver.Stream("prepared:$videoId", emptyMap())
    }
    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engines = Collections.synchronizedList(mutableListOf<Engine>())
        val sessions = Collections.synchronizedList(mutableListOf<ListeningSession>())
        val streams = Streams()
        var now = 1_000_000L
        val controller = PlaybackController(streams, scope, { "AUTO" }, {}, { sessions += it }, { false },
            { ended, failed -> Engine(ended, failed).also { engines += it } }, { now })
        fun active() = synchronized(engines) { engines.last { it.opened != null && !it.paused && !it.stopped } }
        fun close() { controller.stop(); scope.cancel() }
    }
    private fun track(id: String, local: Boolean = true) = Track(id, id, "Artist", null, 100_000, if (local) "C:/test/$id.wav" else null)
    private suspend fun await(condition: () -> Boolean) { withTimeout(5000) { while (!condition()) delay(10) } }

    @Test fun reorderingAndRemovingPreserveActiveOccurrenceIncludingDuplicates() {
        val f = Fixture()
        try {
            val a = track("a"); val b = track("b")
            f.controller.restore(PlaybackMemory(listOf(a.toSaved(), b.toSaved(), a.toSaved()), 2, 50_000))
            f.controller.moveQueue(2, 0)
            assertEquals(0, f.controller.index)
            assertEquals(50_000L, f.controller.positionMs)
            f.controller.removeQueue(2)
            assertEquals(0, f.controller.index)
            assertEquals(2, f.controller.queue.size)
            f.controller.clearQueue()
            assertTrue(f.controller.memory().tracks.isEmpty())
        } finally { f.close() }
    }

    @Test fun manualNextAdvancesWithRepeatOneAndPausedSeekStaysPaused() = runBlocking {
        val f = Fixture()
        try {
            f.controller.playAll(listOf(track("a"), track("b")), 0)
            await { f.controller.isPlaying }
            f.controller.cycleRepeat(); f.controller.cycleRepeat()
            assertEquals(RepeatMode.One, f.controller.repeat)
            f.controller.next()
            await { f.controller.current?.id == "b" && f.controller.isPlaying }
            f.controller.toggle()
            assertFalse(f.controller.isPlaying)
            f.controller.seek(20_000)
            await { !f.controller.busy || f.engines.any { it.opened != null && it.positionMs == 20_000L } }
            assertFalse(f.controller.isPlaying)
            assertEquals(20_000L, f.controller.positionMs)
        } finally { f.close() }
    }

    @Test fun actualListeningExcludesPauseAndSeekAndTimerPreservesVolume() = runBlocking {
        val f = Fixture()
        try {
            f.controller.playAll(listOf(track("a")), 0)
            await { f.controller.isPlaying }
            f.now += 1000; f.active().positionMs = 1000; f.controller.poll()
            f.controller.toggle()
            f.now += 5000; f.controller.poll()
            f.controller.flushListening()
            assertEquals(1000L, f.sessions.sumOf { it.listenedMs })
            f.controller.changeVolume(0.7f)
            f.controller.setSleep(1)
            f.now += 55_000; f.controller.poll()
            assertEquals(0.7f, f.controller.volume, 0.001f)
            f.now += 5000; f.controller.poll()
            assertNull(f.controller.sleepDeadline)
            assertFalse(f.controller.isPlaying)
            assertEquals(0.7f, f.controller.volume, 0.001f)
        } finally { f.close() }
    }

    @Test fun preparedCrossfadeActivatesNextTrackAndStopsPreviousEngine() = runBlocking {
        val f = Fixture()
        try {
            f.controller.configureAudio(AudioSettings(crossfadeSeconds = 3))
            f.controller.playAll(listOf(track("a"), track("b")), 0)
            await { f.controller.isPlaying }
            val old = f.active()
            f.now += 1000; old.positionMs = 80_000; f.controller.poll()
            await { f.engines.any { it.opened != null && it.paused && it.ready } }
            f.now += 1000; old.positionMs = 98_000; f.controller.poll()
            assertEquals("b", f.controller.current?.id)
            assertTrue(f.controller.isPlaying)
            assertEquals(0f, f.active().envelope, 0.001f)
            f.now += 2000; f.controller.poll()
            await { old.stopped }
            assertEquals(1f, f.active().envelope, 0.001f)
        } finally { f.close() }
    }

    @Test fun interruptedStreamRecoversAtCurrentPositionAndFailureDoesNotSkipTrack() = runBlocking {
        val f = Fixture()
        try {
            f.controller.playAll(listOf(track("remote", false)), 0)
            await { f.controller.isPlaying }
            val old = f.active()
            old.positionMs = 5000; old.failed("Connection reset")
            await { f.controller.isPlaying && f.engines.any { it !== old && it.opened != null && it.positionMs == 5000L } }
            assertEquals("remote", f.controller.current?.id)
            assertTrue(f.streams.forgotten.contains("remote"))
        } finally { f.close() }
    }

    @Test fun startupTimeoutTriesNextStreamAndExplicitRetryInvalidatesCache() = runBlocking {
        val f = Fixture()
        try {
            f.controller.playAll(listOf(track("remote", false)), 0)
            await { f.controller.isPlaying }
            val first = f.active()
            first.failed("avformat_open_input() error -138: Could not open input https://example.com/signed?token=private")
            await { f.engines.any { it.opened == "second:remote" } && f.controller.isPlaying }
            assertEquals("remote", f.controller.current?.id)
            assertNull(f.controller.error)
            val before = f.streams.forgotten.size
            f.controller.retry()
            assertTrue(f.streams.forgotten.size > before)
        } finally { f.close() }
    }

    @Test fun sleepAfterTrackStopsInsteadOfRepeatingOrAdvancing() = runBlocking {
        val f = Fixture()
        try {
            f.controller.playAll(listOf(track("a"), track("b")), 0)
            await { f.controller.isPlaying }
            f.controller.setSleep(afterTrack = true)
            f.active().ended()
            await { !f.controller.isPlaying }
            assertEquals("a", f.controller.current?.id)
            assertFalse(f.controller.sleepAfterTrack)
        } finally { f.close() }
    }
}
