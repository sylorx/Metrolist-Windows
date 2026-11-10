package com.metrolist.desktop

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*
import kotlinx.coroutines.runBlocking

class LibraryIntegrationTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun track(id: String) = SavedTrack(id, "Başlık $id", "Sanatçı", addedAt = 1_000)

    @Test fun librarySurvivesRestartAndRecoversPreviousValidDocument() {
        val directory = temporary.newFolder()
        val store = Store(directory, protectCookie = false)
        val list = store.createPlaylist("İlk liste")
        store.addToPlaylist(list.id, listOf(track("a"), track("b"), track("a")))
        store.updatePlaylist(list.id) { it.copy(title = "Yeni ad") }
        File(directory, "library.json").writeText("{interrupted")
        val recovered = Store(directory, protectCookie = false)
        assertEquals("İlk liste", recovered.library.playlists.single().title)
        assertEquals(listOf("a", "b"), recovered.library.playlists.single().tracks.map { it.id })
        assertNotNull(recovered.warning)
        recovered.removeTracks(list.id, setOf("a"))
        assertEquals(listOf("b"), Store(directory, false).library.playlists.single().tracks.map { it.id })
    }

    @Test fun backupMergesWithoutExportingOrReplacingAccountCredentials() {
        val source = Store(temporary.newFolder(), false)
        source.saveSettings(source.settings.copy(cookie = "source-secret", accent = "rose"))
        val list = source.createPlaylist("Taşınan liste", listOf(track("a")))
        source.updatePlaylist(list.id) { it.copy(remoteId = "remote-source") }
        source.toggleLike(track("a"))
        source.addSession(ListeningSession(track("a"), 1_000, 25_000))
        val backup = temporary.newFile("backup.json")
        source.exportBackup(backup)
        assertFalse(backup.readText().contains("source-secret"))
        val target = Store(temporary.newFolder(), false)
        target.saveSettings(target.settings.copy(cookie = "target-secret", musicFolders = listOf("D:/Music")))
        target.createPlaylist("Mevcut liste")
        target.importBackup(backup)
        target.importBackup(backup)
        assertEquals(2, target.library.playlists.size)
        assertEquals(1, target.sessions.size)
        assertNull(target.library.playlists.first { it.id == list.id }.remoteId)
        assertEquals("target-secret", target.settings.cookie)
        assertEquals(listOf("D:/Music"), target.settings.musicFolders)
        assertEquals("rose", target.settings.accent)
    }

    @Test fun windowsSecretIsMigratedAndPlaintextIsRemovedFromSettingsAndBackup() {
        val directory = temporary.newFolder()
        val secret = "test-account-cookie-123"
        File(directory, "settings.json").writeText("""{"cookie":"$secret"}""")
        File(directory, "settings.json.bak").writeText("""{"cookie":"$secret"}""")
        val store = Store(directory)
        assertEquals(secret, store.settings.cookie)
        assertFalse(File(directory, "settings.json").readText().contains(secret))
        assertFalse(File(directory, "settings.json.bak").readText().contains(secret))
        assertEquals(secret, Store(directory).settings.cookie)
        store.saveSettings(store.settings.copy(cookie = ""))
        assertFalse(File(directory, "account.dpapi").exists())
        assertEquals("", Store(directory).settings.cookie)
    }

    @Test fun malformedBackupDoesNotChangeExistingLibrary() {
        val store = Store(temporary.newFolder(), false)
        store.toggleLike(track("keep"))
        val invalid = temporary.newFile("bad.json").apply { writeText("""{"version":99}""") }
        assertTrue(runCatching { store.importBackup(invalid) }.isFailure)
        assertEquals("keep", store.library.liked.single().id)
    }

    @Test fun syncActionsRemainDurableAndAreRemovedIndividually() {
        val directory = temporary.newFolder()
        val store = Store(directory, false)
        val first = SyncAction("like", value = "a", enabled = true)
        val second = SyncAction("like", value = "a", enabled = false)
        store.enqueueSync(first); store.enqueueSync(second)
        val reopened = Store(directory, false)
        assertEquals(listOf(second), reopened.pendingSync)
        reopened.completeSync(first.id)
        assertEquals(listOf(second), Store(directory, false).pendingSync)
        reopened.completeSync(second.id)
        assertTrue(Store(directory, false).pendingSync.isEmpty())
    }

    @Test fun lrcSupportsFractionsOffsetsMultipleTimestampsAndChronologicalOrder() {
        val lines = parseLrc("[offset:-100]\n[00:02.5][00:04.050]Tekrar\n[00:01.05]Başlangıç\n[00:70]Geçersiz\n[ar:Sanatçı]")
        assertEquals(listOf(950L, 2400L, 3950L), lines.map { it.timeMs })
        assertEquals(listOf("Başlangıç", "Tekrar", "Tekrar"), lines.map { it.text })
    }

    @Test fun smartListsUseActualLikeDatesAndLatestHistory() {
        val now = 100L * 24 * 3600 * 1000
        val day = 24L * 3600 * 1000
        val recent = track("recent").copy(addedAt = now - day)
        val old = track("old").copy(addedAt = now - 60 * day)
        val library = LocalLibrary(liked = listOf(recent, old))
        val history = listOf(HistoryEntry("recent", recent.title, recent.artists, playedAt = now - day))
        assertEquals(listOf("recent"), smartTracks(SmartRule.RecentLikes, library, history, now).map { it.id })
        assertEquals(listOf("old"), smartTracks(SmartRule.Forgotten, library, history, now).map { it.id })
        assertEquals(listOf("recent"), smartTracks(SmartRule.MostPlayed, library, history, now).map { it.id })
    }

    @Test fun listeningSummarySplitsMidnightAndExcludesOutsidePeriod() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2026, 11, 3)
        val midnight = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = listOf(ListeningSession(track("a"), midnight - 10_000, 20_000), ListeningSession(track("b"), midnight + 30_000, 40_000))
        val todayOnly = listeningSummary(sessions, 1, today, zone)
        assertEquals(50_000L, todayOnly.totalMs)
        assertEquals(2, todayOnly.tracks)
        val week = listeningSummary(sessions, 7, today, zone)
        assertEquals(60_000L, week.totalMs)
        assertEquals(10_000L, week.daily[5].second)
    }

    @Test fun listeningStorageCompactsTicksByTrackAndDayAndSplitsMidnight() {
        val zone = ZoneId.of("UTC")
        val midnight = LocalDate.of(2026, 11, 3).atStartOfDay(zone).toInstant().toEpochMilli()
        val compacted = compactListening(listOf(
            ListeningSession(track("a"), midnight - 5000, 10000),
            ListeningSession(track("a"), midnight + 10000, 5000),
            ListeningSession(track("a"), midnight + 20000, 5000),
        ), zone)
        assertEquals(2, compacted.size)
        assertEquals(20_000L, compacted.sumOf { it.listenedMs })
        assertEquals(5000L, compacted.first().listenedMs)
        assertEquals(15000L, compacted.last().listenedMs)
    }

    private fun rmsAt(hz: Int, settings: AudioSettings): Double {
        val rate = 48000
        val buffer = ByteArray(rate * 4)
        repeat(rate) { at ->
            val value = (sin(2 * PI * hz * at / rate) * 8000).toInt()
            for (channel in 0..1) { val offset = at * 4 + channel * 2; buffer[offset] = value.toByte(); buffer[offset + 1] = (value shr 8).toByte() }
        }
        AudioProcessor(rate, 2).process(buffer, buffer.size, settings, 1f)
        return sqrt((rate / 2 until rate).sumOf { at ->
            val value = ((buffer[at * 4].toInt() and 255) or (buffer[at * 4 + 1].toInt() shl 8)).toShort().toDouble()
            value * value
        } / (rate / 2))
    }
    @Test fun equalizerBoostsSelectedBandRelativeToUnrelatedFrequencies() {
        val settings = AudioSettings(bands = List(10) { if (it == 5) 6f else 0f })
        val flat = rmsAt(1000, AudioSettings())
        assertTrue(flat > 5000)
        val center = rmsAt(1000, settings)
        val outside = rmsAt(100, settings)
        assertTrue("Expected a clear frequency-selective boost", center > outside * 1.7)
        assertTrue(center <= flat * 1.05)
    }

    @Test fun localScanReadsWavDurationAndSidecarLyricsWithoutNetwork() = runBlocking {
        val folder = temporary.newFolder()
        val wav = File(folder, "Deneme.wav")
        val format = javax.sound.sampled.AudioFormat(48000f, 16, 2, true, false)
        val data = ByteArray(48000 * 4)
        javax.sound.sampled.AudioInputStream(data.inputStream(), format, 48000L).use {
            javax.sound.sampled.AudioSystem.write(it, javax.sound.sampled.AudioFileFormat.Type.WAVE, wav)
        }
        File(folder, "Deneme.lrc").writeText("[00:00.00]İlk satır\n[00:00.50]İkinci satır")
        val result = LocalMusic.scan(listOf(folder.path, folder.path)) { _, _ -> }
        assertEquals(1, result.tracks.size)
        assertEquals(0, result.failed)
        val found = result.tracks.single()
        assertEquals("Deneme", found.title)
        assertEquals(1000L, found.durationMs)
        assertEquals(wav.canonicalPath, found.localPath)
        val lyrics = LyricsRepository().load(found.toTrack())
        assertEquals("Yerel LRC", lyrics.source)
        assertEquals(listOf(0L, 500L), lyrics.lines.map { it.timeMs })
    }
}
