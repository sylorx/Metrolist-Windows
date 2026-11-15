package com.metrolist.desktop

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PersonalLibraryTest {
    @get:Rule val temporary = TemporaryFolder()
    private val now = 100L * 86_400_000
    private fun song(id: String, title: String = id, artist: String = "Artist $id", duration: Long = 180_000) =
        SavedTrack(id, title, artist, durationMs = duration)
    private fun item(id: String, artist: String = id, liked: Boolean = false, plays: Int = 0, last: Long? = null, duration: Long = 180_000) =
        CatalogTrack(song(id, artist = artist, duration = duration), liked, emptySet(), plays, last)
    private fun rule(vararg conditions: SmartCondition) = SmartPlaylist(title = "Test", conditions = conditions.toList())

    @Test fun catalogUnifiesSourcesAndKeepsIndexedMetadataAndLikeDate() {
        val local = song("a", "Fresh").copy(localPath = "C:/Music/a.wav")
        val library = LocalLibrary(liked = listOf(song("a", "Old").copy(addedAt = 42)), localTracks = listOf(local),
            playlists = listOf(LocalPlaylist("list", "Road trip", listOf(local, song("b")))))
        val history = listOf(HistoryEntry("a", "Very old", "Old artist", playedAt = 10), HistoryEntry("a", "Very old", "Old", playedAt = 20))
        val catalog = libraryCatalog(library, history)
        assertEquals(2, catalog.size)
        val a = catalog.first { it.track.id == "a" }
        assertEquals("Fresh", a.track.title)
        assertEquals(42L, a.track.addedAt)
        assertTrue(a.liked)
        assertEquals(setOf("Road trip"), a.playlists)
        assertEquals(2, a.playCount)
        assertEquals(20L, a.lastPlayedAt)
    }

    @Test fun searchMatchesTurkishTextAcrossFieldsAndScopeWithoutDuplicatingTracks() {
        val a = CatalogTrack(song("a", "İçimdeki Işık", "Şebnem Ferah").copy(album = "Özgür"), true, setOf("Yolculuk"), 1, now)
        val b = item("b")
        val catalog = listOf(a, b)
        assertEquals(listOf(a), searchCatalog(catalog, "ISIK sebnem"))
        assertEquals(listOf(a), searchCatalog(catalog, "ozgur yolculuk", CatalogScope.Liked))
        assertTrue(searchCatalog(catalog, "isik", CatalogScope.Local).isEmpty())
        assertEquals(1, searchCatalog(catalog, "", CatalogScope.History).size)
    }

    @Test fun searchRanksExactTitleBeforePrefixAndOtherFieldMatches() {
        val catalog = listOf(item("x", artist = "Hello"), CatalogTrack(song("b", "Hello again"), false, emptySet(), 0, null), CatalogTrack(song("a", "Hello"), false, emptySet(), 0, null))
        assertEquals(listOf("a", "b", "x"), searchCatalog(catalog, "hello").map { it.track.id })
    }

    @Test fun rulesCombineWithAllOrAnyAndRespectKnownPlayDates() {
        val catalog = listOf(item("a", liked = true, last = now - 40L * 86_400_000), item("b", last = now - 50L * 86_400_000), item("c", liked = true))
        val list = rule(SmartCondition(RuleField.Liked, RuleOperator.Yes), SmartCondition(RuleField.DaysSincePlayed, RuleOperator.AtLeast, "30"))
        assertEquals(listOf("a"), evaluateSmart(list, catalog, now).map { it.track.id })
        assertEquals(3, evaluateSmart(list.copy(matchAll = false), catalog, now).size)
        assertEquals(listOf("c"), evaluateSmart(rule(SmartCondition(RuleField.DaysSincePlayed, RuleOperator.Unknown)), catalog, now).map { it.track.id })
    }

    @Test fun durationBudgetSkipsUnknownAndOversizedTracksAndAppliesCountLimit() {
        val catalog = listOf(item("a", duration = 600_000), item("b", duration = 0), item("c", duration = 120_000), item("d", duration = 120_000))
        val list = rule(SmartCondition(RuleField.PlayCount, RuleOperator.AtLeast, "0")).copy(maxMinutes = 5)
        assertEquals(listOf("c", "d"), evaluateSmart(list, catalog).map { it.track.id })
        assertEquals(listOf("c"), evaluateSmart(list.copy(limit = 1), catalog).map { it.track.id })
    }

    @Test fun malformedRulesFailClosedIncludingIncompatibleOperatorsAndNumericOverflow() {
        val catalog = listOf(item("a"))
        assertTrue(evaluateSmart(rule(SmartCondition(RuleField.PlayCount, RuleOperator.AtLeast, "-1")), catalog).isEmpty())
        assertTrue(evaluateSmart(rule(SmartCondition(RuleField.PlayCount, RuleOperator.AtLeast, "9999999999999999999999")), catalog).isEmpty())
        assertTrue(evaluateSmart(rule(SmartCondition(RuleField.Title, RuleOperator.Yes)), catalog).isEmpty())
        assertFalse(rule(SmartCondition()).valid())
        assertFalse(rule(SmartCondition(RuleField.Liked, RuleOperator.Yes)).copy(limit = 0).valid())
    }

    @Test fun playlistEqualityMatchesAnIndividualMembershipAndTextRulesNormalizeAccents() {
        val a = item("a").copy(playlists = setOf("Gündüz", "Yol"))
        assertEquals(1, evaluateSmart(rule(SmartCondition(RuleField.Playlist, RuleOperator.Equals, "Gunduz")), listOf(a)).size)
        assertEquals(0, evaluateSmart(rule(SmartCondition(RuleField.Playlist, RuleOperator.NotContains, "yol")), listOf(a)).size)
    }

    @Test fun blockPreferencesSurviveRestartAlongsideSmartLists() {
        val dir = temporary.newFolder()
        val store = Store(dir, false)
        store.setSongBlocked("blocked", true)
        store.setArtistsBlocked(listOf("Şebnem Ferah"), true)
        store.saveSmartPlaylist(rule(SmartCondition(RuleField.Liked, RuleOperator.Yes)))
        val restored = Store(dir, false)
        assertTrue(matchesBlock(song("blocked").toTrack(), restored.blocks))
        assertTrue(matchesBlock(song("other", artist = "Şebnem Ferah").toTrack(), restored.blocks))
        assertEquals(1, restored.library.smartPlaylists.size)
    }

    @Test fun oldLibraryJsonStillLoadsWithNoCustomLists() {
        val library = Json.decodeFromString<LocalLibrary>("""{"liked":[],"playlists":[],"localTracks":[]}""")
        assertTrue(library.smartPlaylists.isEmpty())
    }

    @Test fun smartListsPersistSurviveBackupAndNeverReplaceExistingDefinitionOnImport() {
        val source = Store(temporary.newFolder(), false)
        val saved = rule(SmartCondition(RuleField.Liked, RuleOperator.Yes))
        source.saveSmartPlaylist(saved)
        assertEquals(saved, Store(temporary.newFolder().also { dir ->
            // Round-trip through the actual store document, separately from backup merging.
            val mirror = Store(dir, false); mirror.saveSmartPlaylist(saved)
        }, false).library.smartPlaylists.single())
        val backup = temporary.newFile()
        source.exportBackup(backup)
        val target = Store(temporary.newFolder(), false)
        target.importBackup(backup)
        target.saveSmartPlaylist(saved.copy(title = "Local edit"))
        target.importBackup(backup)
        assertEquals("Local edit", target.library.smartPlaylists.single().title)
        target.deleteSmartPlaylist(saved.id)
        assertTrue(target.library.smartPlaylists.isEmpty())
    }

    @Test fun mixesAreDeterministicUniqueBoundedAndHonorExplicitExclusions() {
        val catalog = (1..40).map { item("$it", artist = "Artist ${it % 6}", liked = it % 2 == 0, plays = it % 4) }
        val first = personalMix(catalog + catalog.first(), DjMode.Balanced, 25, 123, now, setOf("1"))
        assertEquals(first, personalMix(catalog, DjMode.Balanced, 25, 123, now, setOf("1")))
        assertEquals(25, first.size)
        assertEquals(25, first.map { it.item.track.id }.toSet().size)
        assertFalse(first.any { it.item.track.id == "1" })
        assertTrue(first.all { it.reason.isNotBlank() })
        assertNotEquals(first.map { it.item.track.id }, personalMix(catalog, DjMode.Balanced, 25, 456, now, setOf("1")).map { it.item.track.id })
    }

    @Test fun rediscoveryPrioritizesNeverPlayedAndArtistSpacingUsesAvailableAlternatives() {
        val catalog = listOf(item("a", artist = "One", plays = 8, last = now), item("b", artist = "One"), item("c", artist = "Two"), item("d", artist = "Three"))
        val picks = personalMix(catalog, DjMode.Rediscover, 4, now = now)
        assertNotEquals("a", picks.first().item.track.id)
        assertEquals(3, picks.take(3).map { it.item.track.artists }.toSet().size)
        assertEquals(4, picks.size)
        assertTrue(personalMix(emptyList(), DjMode.Favorites).isEmpty())
    }
}
