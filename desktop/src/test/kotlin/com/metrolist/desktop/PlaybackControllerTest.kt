package com.metrolist.desktop

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections

class PlaybackControllerTest {
    private class Engine(val openReady: () -> Boolean, val ended: () -> Unit, val failed: (String) -> Unit) : PlaybackEngine {
        @Volatile override var positionMs = 0L
        @Volatile override var durationMs = 0L
        @Volatile override var ready = false
        override var volume = 1f
        override var envelope = 1f
        override var audio = AudioSettings()
        @Volatile var paused = false
        @Volatile var stopped = false
        @Volatile var opened: String? = null
        @Volatile var seekable = false
        override fun seek(ms: Long): Boolean { if (seekable) positionMs = ms; return seekable }
        override fun play(url: String, headers: Map<String, String>, startMs: Long, duration: Long, startPaused: Boolean) {
            positionMs = startMs; durationMs = duration; paused = startPaused; opened = url; ready = openReady()
        }
        override fun pause() { paused = true }
        override fun resume() { paused = false }
        override fun stop() { stopped = true }
    }
    private class Streams : PlaybackStreams {
        var remembered: StreamResolver.Stream? = null
        var gate: CompletableDeferred<Unit>? = null
        @Volatile var searches = 0
        override fun saved(videoId: String, quality: String) = remembered
        val forgotten = Collections.synchronizedList(mutableListOf<String>())
        override fun forget(videoId: String, quality: String) { forgotten += videoId }
        override fun keep(videoId: String, quality: String, stream: StreamResolver.Stream) {}
        override suspend fun openers(videoId: String, quality: String): List<StreamResolver.Stream> {
            searches++
            gate?.await()
            return listOf(StreamResolver.Stream("first:$videoId", emptyMap()), StreamResolver.Stream("second:$videoId", emptyMap()))
        }
        override suspend fun innertube(videoId: String, quality: String): StreamResolver.Stream? = null
        override suspend fun resolve(videoId: String, quality: String) = StreamResolver.Stream("prepared:$videoId", emptyMap())
    }
    private class Fixture {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val engines = Collections.synchronizedList(mutableListOf<Engine>())
        val sessions = Collections.synchronizedList(mutableListOf<ListeningSession>())
        val streams = Streams()
        val blockedIds = mutableSetOf<String>()
        var now = 1_000_000L
        @Volatile var openReady = true
        val controller = PlaybackController(streams, scope, { "AUTO" }, {}, { sessions += it }, { false },
            { ended, failed -> Engine({ openReady }, ended, failed).also { engines += it } }, { now }, autoSkipped = { it.id in blockedIds })
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

    @Test fun blockingCurrentTrackCancelsPreparationAndAdvancesWithTheNewEngine() = runBlocking {
        val f = Fixture()
        try {
            f.controller.playAll(listOf(track("a"), track("b")), 0)
            await { f.controller.isPlaying }
            f.blockedIds += "a"
            f.controller.applyBlocks()
            await { f.controller.current?.id == "b" && f.controller.isPlaying }
            assertEquals(listOf("b"), f.controller.queue.map { it.id })
        } finally { f.close() }
    }

    @Test fun seekMovesInsideOpenSourceWithoutReopeningIt() = runBlocking {
        val f = Fixture()
        try {
            f.controller.playAll(listOf(track("remote", false)), 0)
            await { f.controller.isPlaying }
            val engine = f.active().also { it.seekable = true }
            f.controller.seek(40_000)
            assertEquals(40_000L, f.controller.positionMs)
            assertTrue(f.controller.isPlaying)
            assertSame(engine, f.active())
            assertEquals(1, f.streams.searches)
            f.controller.seekBy(-50_000)
            assertEquals(0L, f.controller.positionMs)
        } finally { f.close() }
    }

    @Test fun reopeningSeekReusesTheWorkingAddressInsteadOfSearchingAgain() = runBlocking {
        val f = Fixture()
        try {
            f.controller.playAll(listOf(track("remote", false)), 0)
            await { f.controller.isPlaying }
            f.controller.seek(40_000)
            await { f.controller.isPlaying && f.active().positionMs == 40_000L }
            assertEquals("first:remote", f.active().opened)
            assertEquals(1, f.streams.searches)
        } finally { f.close() }
    }

    @Test fun failureWhilePausedNeverStartsPlaying() = runBlocking {
        val f = Fixture()
        try {
            f.controller.playAll(listOf(track("remote", false)), 0)
            await { f.controller.isPlaying }
            val first = f.active()
            f.controller.toggle()
            assertFalse(f.controller.isPlaying)
            first.failed("Connection reset")
            await { f.engines.any { it.opened == "second:remote" } }
            delay(60)
            assertFalse(f.controller.isPlaying)
            assertTrue(f.engines.last { it.opened == "second:remote" }.paused)
            // Same for a drop in the middle of the song, which reopens at the reached position.
            val second = f.engines.last { it.opened == "second:remote" }
            second.positionMs = 30_000; second.failed("Connection reset")
            await { f.engines.any { it !== second && it.positionMs == 30_000L && it.opened != null } }
            delay(60)
            assertFalse(f.controller.isPlaying)
        } finally { f.close() }
    }

    @Test fun pauseWhileLoadingIsHonouredWithoutStartingOver() = runBlocking {
        val f = Fixture()
        try {
            f.streams.gate = CompletableDeferred()
            f.controller.playAll(listOf(track("remote", false)), 0)
            await { f.streams.searches == 1 }
            f.controller.toggle()
            assertTrue(f.controller.pendingPause)
            f.streams.gate!!.complete(Unit)
            await { f.engines.any { it.opened != null } }
            delay(60)
            assertEquals(1, f.streams.searches)
            assertFalse(f.controller.isPlaying)
            f.controller.toggle()
            await { f.controller.isPlaying }
        } finally { f.close() }
    }

    @Test fun expiredRememberedAddressFallsBackToAFreshSearch() = runBlocking {
        val f = Fixture()
        try {
            f.streams.remembered = StreamResolver.Stream("old:remote", emptyMap())
            f.controller.playAll(listOf(track("remote", false)), 0)
            await { f.controller.isPlaying }
            assertEquals("old:remote", f.active().opened)
            assertEquals(0, f.streams.searches)
            f.active().failed("HTTP error 403")
            await { f.controller.isPlaying && f.engines.any { it.opened == "first:remote" } }
            assertEquals(1, f.streams.searches)
        } finally { f.close() }
    }

    @Test fun clearingUpcomingKeepsTheCurrentSong() = runBlocking {
        val f = Fixture()
        try {
            f.controller.playAll(listOf(track("a"), track("b"), track("c")), 1)
            await { f.controller.isPlaying }
            f.controller.clearUpcoming()
            assertEquals(listOf("a", "b"), f.controller.queue.map { it.id })
            assertEquals("b", f.controller.current?.id)
        } finally { f.close() }
    }

    @Test fun sourceThatNeverOpensIsReplacedAndFinalFailureStopsTheEngine() = runBlocking {
        val f = Fixture()
        try {
            f.openReady = false
            f.controller.playAll(listOf(track("remote", false)), 0)
            await { f.controller.isPlaying }
            f.now += 8_000; f.controller.poll()
            assertEquals("first:remote", f.engines.last { it.opened != null }.opened)
            f.now += 2_000; f.controller.poll()
            await { f.engines.any { it.opened == "second:remote" } && f.controller.isPlaying }
            val engine = f.engines.last { it.opened == "second:remote" }
            f.now += 10_000; f.controller.poll()
            await { f.controller.error != null }
            await { engine.stopped }
            assertFalse(f.controller.isPlaying)
            assertTrue(f.controller.error!!.contains("zaman aşımı"))
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
