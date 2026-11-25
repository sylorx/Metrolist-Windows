package com.metrolist.desktop

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.Rectangle
import java.io.File
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine

class ReliabilityTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun track(id: String, artist: String = "Sanatçı") = Track(id, "Başlık $id", artist, null, 100_000)

    private fun wav(file: File, seconds: Int): File {
        val format = AudioFormat(48000f, 16, 2, true, false)
        AudioInputStream(ByteArray(48000 * 4 * seconds).inputStream(), format, 48000L * seconds).use {
            AudioSystem.write(it, AudioFileFormat.Type.WAVE, file)
        }
        return file
    }

    @Test fun deadServerDoesNotDelayAnswersFromOthers() = runBlocking {
        val started = System.nanoTime()
        val found = raceSources<String>(
            listOf({ delay(60_000); "dead" }, { error("broken") }, { delay(30); "fast" }, { delay(80); "second" }, { null }),
            want = 2, graceMs = 400, deadlineMs = 5_000,
        )
        assertEquals(listOf("fast", "second"), found)
        assertTrue((System.nanoTime() - started) / 1_000_000 < 3_000)
        val none = raceSources<String>(listOf({ delay(60_000); "dead" }), want = 1, graceMs = 100, deadlineMs = 150)
        assertTrue(none.isEmpty())
    }

    @Test fun signedAddressKeepsEverythingButTheHostOnAlternateServers() {
        val url = "https://rr1---sn-local-5nge.googlevideo.com/videoplayback?expire=1&mn=sn-local-5nge%2Csn-far4c-5h&fvip=2&sig=a%3D%3D&lsig=x"
        val all = MediaHosts.candidates(url)
        assertEquals(listOf(url, url.replace("rr1---sn-local-5nge", "rr2---sn-far4c-5h")), all)
        val foreign = "https://proxy.example/videoplayback?mn=sn-a,sn-b"
        assertEquals(listOf(foreign), MediaHosts.candidates(foreign))
        val hostile = "https://rr1---sn-a.googlevideo.com/x?mn=sn-a,evil.example/&fvip=1"
        assertEquals(listOf(hostile), MediaHosts.candidates(hostile))
        assertEquals("Doğrudan kaynak", sourceLabel(StreamResolver.Stream(url, emptyMap())))
    }

    @Test fun stoppedEngineNeverStartsEvenWhenPlayArrivesLate() {
        val failures = mutableListOf<String>()
        val engine = FfmpegPlayer({}, { failures += it })
        engine.stop()
        engine.play("C:/does/not/exist.wav", emptyMap(), 0, 1000)
        Thread.sleep(150)
        assertFalse(engine.playing)
        assertTrue(failures.isEmpty())
        assertFalse(engine.seek(10))
    }

    @Test fun engineSeeksInsideOpenFileWhilePlayingAndWhilePaused() {
        assumeTrue(AudioSystem.isLineSupported(DataLine.Info(SourceDataLine::class.java, AudioFormat(48000f, 16, 2, true, false))))
        val file = wav(temporary.newFile("uzun.wav"), 20)
        val failures = mutableListOf<String>()
        val engine = FfmpegPlayer({}, { failures += it })
        engine.volume = 0f
        try {
            engine.play(file.path, emptyMap(), 0, 20_000)
            waitFor { engine.ready && engine.positionMs > 200 }
            assertTrue(engine.seek(12_000))
            waitFor { engine.positionMs > 12_200 }
            assertTrue(engine.positionMs < 14_000)
            engine.pause()
            Thread.sleep(200)
            assertTrue(engine.seek(3_000))
            Thread.sleep(400)
            assertTrue("paused seek moved to ${engine.positionMs}", engine.positionMs in 2_900..3_400)
            engine.resume()
            waitFor { engine.positionMs > 3_500 }
            assertTrue(failures.toString(), failures.isEmpty())
        } finally { engine.stop() }
    }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 6_000_000_000L
        while (!condition()) {
            assertTrue("Timed out", System.nanoTime() < deadline)
            Thread.sleep(20)
        }
    }

    @Test fun volumeChangesGlideInsteadOfStepping() {
        val processor = AudioProcessor(48000, 2)
        fun block() = ByteArray(48000 / 50 * 4).also { data ->
            for (at in data.indices step 2) { data[at] = 0x10; data[at + 1] = 0x27 }
        }
        fun sample(data: ByteArray, at: Int) = ((data[at].toInt() and 255) or (data[at + 1].toInt() shl 8)).toShort().toInt()
        processor.process(block(), 3840, AudioSettings(), 1f)
        val next = block()
        processor.process(next, 3840, AudioSettings(), 0f)
        assertTrue("first sample jumped", sample(next, 0) > 9_000)
        val quiet = block()
        repeat(40) { processor.process(block(), 3840, AudioSettings(), 0f) }
        processor.process(quiet, 3840, AudioSettings(), 0f)
        assertTrue(sample(quiet, 3836) < 10)
    }

    @Test fun recommendationsAvoidRepeatingAnArtistButKeepEverySong() {
        val songs = listOf(track("a1", "A"), track("a2", "A, X"), track("b1", "B"), track("c1", "C"), track("a1", "A"))
        val mixed = diversify(songs, listOf(track("seed", "A")))
        assertEquals(setOf("a1", "a2", "b1", "c1"), mixed.map { it.id }.toSet())
        assertEquals(4, mixed.size)
        assertEquals("b1", mixed.first().id)
        val only = diversify(listOf(track("1", "A"), track("2", "A")), listOf(track("seed", "A")))
        assertEquals(listOf("1", "2"), only.map { it.id })
    }

    @Test fun radioAdditionsSkipBlockedSongsAndSpreadArtists() {
        val blocks = PlaybackBlocks(songs = listOf("x"), artists = listOf("Engelli"))
        val found = listOf(track("x", "A"), track("a1", "A"), track("a2", "A"), track("b", "Engelli, Konuk"), track("c", "C"))
        val added = diversify(radioAdditions(found, setOf("seed")) { matchesBlock(it, blocks) }, listOf(track("seed", "A")))
        assertEquals(listOf("c", "a1", "a2"), added.map { it.id })
    }

    @Test fun unchangedPlaybackIsNotWrittenAgain() {
        val directory = temporary.newFolder()
        val store = Store(directory, false)
        val memory = PlaybackMemory(listOf(track("a").toSaved().copy(addedAt = 0)), 0, 5_000)
        store.savePlayback(memory)
        val file = File(directory, "playback.json")
        file.setLastModified(1_000_000)
        val stamp = file.lastModified()
        store.savePlayback(memory.copy())
        assertEquals(stamp, file.lastModified())
        store.savePlayback(memory.copy(positionMs = 6_000))
        assertNotEquals(stamp, file.lastModified())
    }

    @Test fun rescanOpensOnlyChangedFilesAndPicksUpNewOnes() = runBlocking {
        val folder = temporary.newFolder()
        val first = wav(File(folder, "Bir.wav"), 1)
        val known = LocalMusic.scan(listOf(folder.path)) { _, _ -> }.tracks
        // Would be unreadable if it were opened again.
        val stamp = first.lastModified()
        first.writeText("bozuk"); first.setLastModified(stamp)
        wav(File(folder, "İki.wav"), 1)
        val again = LocalMusic.scan(listOf(folder.path), known) { _, _ -> }
        assertEquals(listOf("Bir", "İki"), again.tracks.map { it.title }.sorted())
        assertEquals(0, again.failed)
        first.setLastModified(stamp + 10_000)
        assertEquals(1, LocalMusic.scan(listOf(folder.path), known) { _, _ -> }.failed)
    }

    @Test fun searchIgnoresCaseAccentsAndTurkishDots() {
        val song = SavedTrack("1", "IŞIKLAR Altında", "Çağrı İnce", album = "Gündüz")
        listOf("ışık", "ISIK", "isiklar cagri", "ince gunduz", "  ").forEach { assertTrue(it, song.matches(it)) }
        assertFalse(song.matches("ışık gece"))
        val names = listOf("Zeytin", "Şarkı", "Çiçek", "Su", "Ceviz").sortedWith { a, b -> turkishOrder().compare(a, b) }
        assertEquals(listOf("Ceviz", "Çiçek", "Su", "Şarkı", "Zeytin"), names)
    }

    @Test fun paletteRanksTitlesAboveArtistsAndRejectsMissingWords() {
        val exact = paletteScore("gece", "Gece", "X")!!
        val starts = paletteScore("gece", "Gece Yolcusu", "X")!!
        val word = paletteScore("yol", "Gece Yolcusu", "X")!!
        val artist = paletteScore("gece", "Şarkı", "Gece")!!
        assertTrue(exact > starts && starts > word && word > artist)
        assertNull(paletteScore("gece gündüz", "Gece", "X"))
    }

    @Test fun skippingAStuckSyncNeverTakesAnotherAccountsWork() {
        val other = SyncAction("like", value = "a", account = "other")
        val mine = SyncAction("rename", "list", value = "Yeni", account = "mine")
        assertNull(firstOwnAction(listOf(other), "mine"))
        assertEquals(mine, firstOwnAction(listOf(other, mine), "mine"))
    }

    @Test fun audioCacheCopiesInPiecesEvictsOldestAndTurnsOff() = runBlocking {
        val bytes = ByteArray(2_500_000) { (it % 251).toByte() }
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        val ranges = java.util.Collections.synchronizedList(mutableListOf<String>())
        server.createContext("/a") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")!!.removePrefix("bytes=").split('-')
            ranges += exchange.requestHeaders.getFirst("Range")
            val from = range[0].toInt(); val to = minOf(range[1].toInt(), bytes.lastIndex)
            exchange.responseHeaders.set("Content-Range", "bytes $from-$to/${bytes.size}")
            exchange.sendResponseHeaders(206, (to - from + 1).toLong())
            exchange.responseBody.use { it.write(bytes, from, to - from + 1) }
        }
        server.start()
        try {
            var limit = 6_000_000L
            val directory = temporary.newFolder()
            val cache = AudioCache(directory, { limit })
            val stream = StreamResolver.Stream("http://127.0.0.1:${server.address.port}/a", emptyMap())
            cache.download(stream, File(directory, "one.audio"))
            assertEquals(3, ranges.size)
            assertArrayEquals(bytes, File(directory, "one.audio").readBytes())
            File(directory, "one.audio").setLastModified(1_000_000)
            cache.download(stream, File(directory, "two.audio"))
            cache.download(stream, File(directory, "three.audio"))
            assertFalse("oldest copy should be evicted", File(directory, "one.audio").exists())
            assertTrue(File(directory, "three.audio").exists())
            assertNull(cache.file("x", "AUTO"))
            limit = 0
            cache.keep("x", "AUTO", stream)
            assertNull(cache.file("x", "AUTO"))
            cache.clear()
            assertEquals(0L, cache.usage())
            assertTrue(directory.listFiles()!!.none { it.name.endsWith(".part") })
        } finally { server.stop(0) }
    }

    @Test fun proxyThatAnswersRangesWithPlain200IsStillCopiedCompletely() = runBlocking {
        val bytes = ByteArray(2_300_000) { (it % 241).toByte() }
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        // Sends only the requested range, but calls it a whole response and gives no Content-Range.
        server.createContext("/a") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")!!.removePrefix("bytes=").split('-')
            val from = range[0].toInt(); val to = minOf(range[1].toInt(), bytes.lastIndex)
            exchange.sendResponseHeaders(200, (to - from + 1).toLong())
            exchange.responseBody.use { it.write(bytes, from, to - from + 1) }
        }
        // Reports every piece as the whole file, like one public proxy does.
        server.createContext("/liar") { exchange ->
            val range = exchange.requestHeaders.getFirst("Range")!!.removePrefix("bytes=").split('-')
            val from = range[0].toInt()
            if (from > bytes.lastIndex) { exchange.sendResponseHeaders(416, -1); exchange.close(); return@createContext }
            val to = minOf(range[1].toInt(), bytes.lastIndex)
            exchange.responseHeaders.set("Content-Range", "bytes $from-$to/${to + 1}")
            exchange.sendResponseHeaders(206, (to - from + 1).toLong())
            exchange.responseBody.use { it.write(bytes, from, to - from + 1) }
        }
        // Stops after the first piece.
        server.createContext("/short") { exchange ->
            exchange.sendResponseHeaders(200, 1_048_576)
            exchange.responseBody.use { it.write(bytes, 0, 1_048_576) }
        }
        server.start()
        try {
            val directory = temporary.newFolder()
            val cache = AudioCache(directory, { 1L shl 30 })
            val base = "http://127.0.0.1:${server.address.port}"
            cache.download(StreamResolver.Stream("$base/a?clen=${bytes.size}", emptyMap()), File(directory, "full.audio"))
            assertArrayEquals(bytes, File(directory, "full.audio").readBytes())
            assertTrue(runCatching { cache.download(StreamResolver.Stream("$base/liar", emptyMap()), File(directory, "liar.audio")) }.isFailure)
            assertFalse(File(directory, "liar.audio").exists())
            cache.download(StreamResolver.Stream("$base/liar?clen=${bytes.size}", emptyMap()), File(directory, "known.audio"))
            assertArrayEquals(bytes, File(directory, "known.audio").readBytes())
            assertTrue(runCatching { cache.download(StreamResolver.Stream("$base/short?clen=${bytes.size}", emptyMap()), File(directory, "cut.audio")) }.isFailure)
            assertFalse(File(directory, "cut.audio").exists())
        } finally { server.stop(0) }
    }

    @Test fun failedCacheCopyLeavesNothingBehind() = runBlocking {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/a") { exchange -> exchange.sendResponseHeaders(403, -1); exchange.close() }
        server.start()
        try {
            val directory = temporary.newFolder()
            val cache = AudioCache(directory, { 1L shl 30 })
            assertTrue(runCatching { cache.download(StreamResolver.Stream("http://127.0.0.1:${server.address.port}/a", emptyMap()), File(directory, "x.audio")) }.isFailure)
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally { server.stop(0) }
    }

    @Test fun downloadsSurviveRestartSkipLocalFilesAndRecordFailures() = runBlocking {
        val directory = temporary.newFolder()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val fetched = mutableListOf<String>()
        val downloads = Downloads(directory, scope, fetch = { track, target ->
            if (track.id == "bad") error("403")
            fetched += track.id; target.writeText("ses " + track.id)
        }, io = kotlinx.coroutines.Dispatchers.Unconfined)
        val local = track("local").toSaved().copy(localPath = "C:/m/a.mp3")
        assertEquals(2, downloads.save(listOf(track("a").toSaved(), track("a").toSaved(), local, track("bad").toSaved())))
        assertEquals(listOf("a"), fetched)
        assertEquals(1, downloads.failures)
        assertTrue(downloads.active.isEmpty())
        assertTrue(downloads.isSaved("a"))
        assertEquals("ses a", downloads.file("a")!!.readText())
        assertEquals(0, downloads.save(listOf(track("a").toSaved())))
        val reopened = Downloads(directory, scope, fetch = { _, _ -> })
        assertEquals(listOf("a"), reopened.entries.map { it.track.id })
        reopened.remove(setOf("a"))
        assertNull(reopened.file("a"))
        assertTrue(Downloads(directory, scope, fetch = { _, _ -> }).entries.isEmpty())
    }

    @Test fun savedSongIsPlayedBeforeAnyServerIsAsked() {
        val file = temporary.newFile("a.audio").apply { writeText("x") }
        val resolver = StreamResolver(temporary.newFolder(), downloaded = { id -> file.takeIf { id == "a" } })
        assertEquals(file.path, resolver.saved("a", "AUTO")!!.url)
        assertNull(resolver.saved("b", "AUTO"))
        assertEquals("İndirilen dosya", sourceLabel(StreamResolver.Stream("C:\\Users\\x\\Metrolist\\downloads\\a.audio", emptyMap())))
    }

    @Test fun repeatedSongsGetDistinctStableKeys() {
        assertEquals(listOf("a#1", "b#1", "a#2"), occurrenceKeys(listOf("a", "b", "a")) { it })
    }

    @Test fun onlyOneCopyRunsAndTheSecondAsksTheFirstToShow() {
        val directory = temporary.newFolder()
        val first = SingleInstance(directory)
        assertTrue(first.acquire())
        assertFalse(first.takeShowRequest())
        assertFalse(SingleInstance(directory).acquire())
        assertTrue(first.takeShowRequest())
        assertFalse(first.takeShowRequest())
        first.release()
        val next = SingleInstance(directory)
        assertTrue(next.acquire())
        next.release()
    }

    @Test fun taskbarPixelsBecomeAwtUnitsOnScaledScreens() {
        assertEquals(Rectangle(0, 680, 1280, 32), toLogical(Rectangle(0, 1020, 1920, 48), 1.5))
        assertEquals(Rectangle(0, 1032, 1920, 48), toLogical(Rectangle(0, 1032, 1920, 48), 1.0))
    }

    @Test fun pastedCookieHeaderIsCleanedAndErrorsStayReadable() {
        assertEquals("SID=a; SAPISID=b", cleanCookie("  Cookie: SID=a;\r\n  SAPISID=b \n"))
        assertTrue(friendlyError(java.net.UnknownHostException("music.youtube.com")).contains("İnternete"))
        assertFalse(friendlyError(IllegalStateException("Bad https://x.y/z?token=gizli")).contains("gizli"))
    }

    @Test fun lyricsHelpersCleanTitlesAndReadLegacyTurkishFiles() {
        assertEquals("Yol", lyricTitle("Yol (Official Video) [HD Audio]"))
        assertEquals("Yol (Akustik)", lyricTitle("Yol (Akustik)"))
        assertEquals("Şarkı", decodeText("Şarkı".toByteArray(java.nio.charset.Charset.forName("windows-1254"))))
        assertEquals("Şarkı", decodeText("\uFEFFŞarkı".toByteArray()))
    }

    @Test fun longDurationsShowHours() {
        assertEquals("1:02:03", formatTime(3_723_000))
        assertEquals("59:59", formatTime(3_599_000))
        assertEquals("0:05", formatTime(5_900))
    }

    @Test fun ambientColourFollowsTheCoverAndStaysVisible() {
        assertNull(liveliest(emptyList()))
        val red = liveliest(List(40) { 0xFF8A1010.toInt() } + List(60) { 0xFF101010.toInt() })!!
        assertTrue((red shr 16 and 255) > 2 * (red and 255))
        val black = liveliest(List(10) { 0xFF000000.toInt() })!!
        assertTrue((black and 255) > 80)
    }
}
