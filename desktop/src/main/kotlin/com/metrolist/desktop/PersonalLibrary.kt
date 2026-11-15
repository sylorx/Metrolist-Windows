package com.metrolist.desktop

import kotlinx.serialization.Serializable
import java.text.Normalizer
import java.util.Locale
import kotlin.random.Random

/** Shared search and recommendation input. Identity stays the existing source track ID. */
data class CatalogTrack(
    val track: SavedTrack,
    val liked: Boolean,
    val playlists: Set<String>,
    val playCount: Int,
    val lastPlayedAt: Long?,
)

internal fun searchText(value: String): String = Normalizer.normalize(
    value.lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFD,
).replace(Regex("\\p{M}+"), "").replace(Regex("\\s+"), " ").trim()

fun libraryCatalog(library: LocalLibrary, history: List<HistoryEntry>): List<CatalogTrack> {
    val likes = library.liked.associateBy { it.id }
    val memberships = mutableMapOf<String, MutableSet<String>>()
    library.playlists.forEach { list -> list.tracks.forEach { memberships.getOrPut(it.id) { linkedSetOf() }.add(list.title) } }
    val plays = history.groupBy { it.id }
    // Current indexed metadata takes precedence over old history snapshots.
    val tracks = (library.localTracks + library.liked + library.playlists.flatMap { it.tracks } + history.map { it.toTrack().toSaved().copy(addedAt = 0) })
        .distinctBy { it.id }
    return tracks.map { song -> CatalogTrack(song.copy(addedAt = likes[song.id]?.addedAt ?: song.addedAt), song.id in likes,
        memberships[song.id].orEmpty(), plays[song.id]?.size ?: 0, plays[song.id]?.maxOfOrNull { it.playedAt }) }
}

enum class CatalogScope(val label: String) { All("Tümü"), Liked("Beğenilenler"), Local("Yerel"), Playlists("Listeler"), History("Geçmiş") }

fun searchCatalog(catalog: List<CatalogTrack>, query: String, scope: CatalogScope = CatalogScope.All): List<CatalogTrack> {
    val tokens = searchText(query).split(' ').filter { it.isNotBlank() }
    return catalog.filter { item ->
        val inScope = when (scope) {
            CatalogScope.All -> true
            CatalogScope.Liked -> item.liked
            CatalogScope.Local -> item.track.localPath != null
            CatalogScope.Playlists -> item.playlists.isNotEmpty()
            CatalogScope.History -> item.playCount > 0
        }
        inScope && (tokens.isEmpty() || searchText(listOf(item.track.title, item.track.artists, item.track.album,
            item.playlists.joinToString(" ")).joinToString(" ")).let { text -> tokens.all { it in text } })
    }.sortedWith(compareBy<CatalogTrack> {
        val title = searchText(it.track.title)
        when { tokens.isEmpty() -> 2; title == searchText(query) -> 0; title.startsWith(searchText(query)) -> 1; else -> 2 }
    }.thenBy { searchText(it.track.title) }.thenBy { it.track.id })
}

@Serializable enum class RuleField(val label: String) {
    Title("Başlık"), Artist("Sanatçı"), Album("Albüm"), Playlist("Liste adı"),
    Liked("Beğenilmiş"), Local("Yerel dosya"), PlayCount("Dinleme sayısı"),
    DaysSincePlayed("Son dinlemeden geçen gün"), DurationSeconds("Süre (saniye)"),
}
@Serializable enum class RuleOperator(val label: String) {
    Contains("içerir"), NotContains("içermez"), Equals("eşittir"), AtLeast("en az"), AtMost("en fazla"),
    Yes("evet"), No("hayır"), Unknown("bilinmiyor"),
}
fun RuleField.operators(): List<RuleOperator> = when (this) {
    RuleField.Title, RuleField.Artist, RuleField.Album, RuleField.Playlist -> listOf(RuleOperator.Contains, RuleOperator.NotContains, RuleOperator.Equals)
    RuleField.Liked, RuleField.Local -> listOf(RuleOperator.Yes, RuleOperator.No)
    RuleField.PlayCount -> listOf(RuleOperator.AtLeast, RuleOperator.AtMost, RuleOperator.Equals)
    RuleField.DaysSincePlayed, RuleField.DurationSeconds -> listOf(RuleOperator.AtLeast, RuleOperator.AtMost, RuleOperator.Unknown)
}
@Serializable data class SmartCondition(val field: RuleField = RuleField.Artist, val operator: RuleOperator = RuleOperator.Contains, val value: String = "") {
    fun valid(): Boolean = operator in field.operators() && when (operator) {
        RuleOperator.Yes, RuleOperator.No, RuleOperator.Unknown -> true
        RuleOperator.AtLeast, RuleOperator.AtMost -> value.toLongOrNull()?.let { it in 0..1_000_000_000L } == true
        RuleOperator.Equals -> if (field == RuleField.PlayCount) value.toLongOrNull()?.let { it in 0..1_000_000_000L } == true else value.isNotBlank()
        else -> value.isNotBlank()
    }
}
@Serializable enum class SmartOrder(val label: String) { Title("Başlık"), Artist("Sanatçı"), MostPlayed("En çok dinlenen"), LeastPlayed("En az dinlenen"), Recent("Son dinlenen"), Oldest("Uzun süredir dinlenmeyen") }
@Serializable data class SmartPlaylist(
    val id: String = "smart-" + java.util.UUID.randomUUID(),
    val title: String = "",
    val conditions: List<SmartCondition> = listOf(SmartCondition()),
    val matchAll: Boolean = true,
    val order: SmartOrder = SmartOrder.Title,
    val limit: Int = 50,
    val maxMinutes: Int = 0,
) {
    fun valid(): Boolean = title.isNotBlank() && title.length <= 100 && conditions.size in 1..12 &&
        conditions.all { it.valid() } && limit in 1..500 && maxMinutes in 0..1440
}

fun evaluateSmart(list: SmartPlaylist, catalog: List<CatalogTrack>, now: Long = System.currentTimeMillis()): List<CatalogTrack> {
    if (!list.valid()) return emptyList()
    val matched = catalog.filter { item ->
        val tests = list.conditions.map { condition ->
            val text = when (condition.field) {
                RuleField.Title -> item.track.title
                RuleField.Artist -> item.track.artists
                RuleField.Album -> item.track.album
                RuleField.Playlist -> item.playlists.joinToString("\n")
                else -> ""
            }
            val number: Long? = when (condition.field) {
                RuleField.PlayCount -> item.playCount.toLong()
                RuleField.DaysSincePlayed -> item.lastPlayedAt?.let { ((now - it).coerceAtLeast(0)) / 86_400_000 }
                RuleField.DurationSeconds -> item.track.durationMs.takeIf { it > 0 }?.div(1000)
                else -> null
            }
            val flag = if (condition.field == RuleField.Liked) item.liked else item.track.localPath != null
            when (condition.operator) {
                RuleOperator.Contains -> searchText(condition.value) in searchText(text)
                RuleOperator.NotContains -> searchText(condition.value) !in searchText(text)
                RuleOperator.Equals -> if (condition.field == RuleField.PlayCount) number == condition.value.toLongOrNull()
                    else if (condition.field == RuleField.Playlist) item.playlists.any { searchText(it) == searchText(condition.value) }
                    else searchText(text) == searchText(condition.value)
                RuleOperator.AtLeast -> number != null && number >= condition.value.toLong()
                RuleOperator.AtMost -> number != null && number <= condition.value.toLong()
                RuleOperator.Yes -> flag
                RuleOperator.No -> !flag
                RuleOperator.Unknown -> number == null
            }
        }
        if (list.matchAll) tests.all { it } else tests.any { it }
    }
    val sorted = when (list.order) {
        SmartOrder.Title -> matched.sortedBy { searchText(it.track.title) }
        SmartOrder.Artist -> matched.sortedBy { searchText(it.track.artists) }
        SmartOrder.MostPlayed -> matched.sortedByDescending { it.playCount }
        SmartOrder.LeastPlayed -> matched.sortedBy { it.playCount }
        SmartOrder.Recent -> matched.sortedByDescending { it.lastPlayedAt ?: Long.MIN_VALUE }
        SmartOrder.Oldest -> matched.sortedBy { it.lastPlayedAt ?: Long.MIN_VALUE }
    }
    var remaining = if (list.maxMinutes == 0) Long.MAX_VALUE else list.maxMinutes * 60_000L
    return sorted.filter { item ->
        // An unknown duration cannot safely fit within a duration budget.
        val duration = item.track.durationMs
        if (list.maxMinutes > 0 && (duration <= 0 || duration > remaining)) false
        else { remaining -= duration.coerceAtLeast(0); true }
    }.take(list.limit)
}

enum class DjMode(val label: String, val description: String) {
    Favorites("Favoriler", "Beğendiklerin ve sık döndüğün parçalar"),
    Balanced("Dengeli", "Tanıdık parçalarla az dinlediklerini birleştir"),
    Rediscover("Yeniden keşfet", "Kitaplığında bekleyen parçalara dön"),
}
data class DjPick(val item: CatalogTrack, val reason: String)

fun personalMix(catalog: List<CatalogTrack>, mode: DjMode, limit: Int = 25, seed: Int = 0,
    now: Long = System.currentTimeMillis(), exclude: Set<String> = emptySet()): List<DjPick> {
    val random = Random(seed)
    val ranked = catalog.distinctBy { it.track.id }.filterNot { it.track.id in exclude }.map { item ->
        val days = item.lastPlayedAt?.let { (now - it).coerceAtLeast(0) / 86_400_000 } ?: 365L
        val score = when (mode) {
            DjMode.Favorites -> (if (item.liked) 12.0 else 0.0) + minOf(item.playCount, 20) + random.nextDouble(6.0)
            DjMode.Balanced -> (if (item.liked) 4.0 else 0.0) + minOf(days, 90) / 15.0 + random.nextDouble(12.0)
            DjMode.Rediscover -> minOf(days, 365).toDouble() + 20.0 / (item.playCount + 1) + random.nextDouble(15.0)
        }
        val reason = when {
            mode == DjMode.Favorites && item.liked -> "Beğendiklerin arasında"
            item.playCount == 0 -> "Henüz dinleme geçmişinde yok"
            days >= 30 -> "$days gündür dinlemedin"
            item.playCount >= 3 -> "${item.playCount} kez açtın"
            else -> "Karışıma çeşitlilik katmak için"
        }
        Triple(item, score, reason)
    }.sortedByDescending { it.second }.toMutableList()
    val result = mutableListOf<DjPick>()
    while (ranked.isNotEmpty() && result.size < limit.coerceIn(1, 100)) {
        val recentArtists = result.takeLast(2).map { searchText(it.item.track.artists) }.toSet()
        val at = ranked.indexOfFirst { searchText(it.first.track.artists) !in recentArtists }.takeIf { it >= 0 } ?: 0
        val next = ranked.removeAt(at)
        result += DjPick(next.first, next.third)
    }
    return result
}
