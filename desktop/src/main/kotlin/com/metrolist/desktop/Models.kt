package com.metrolist.desktop

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.EpisodeItem
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.PodcastItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import kotlinx.serialization.Serializable
import java.io.File

enum class RepeatMode { Off, All, One }

enum class LibraryTab(val label: String, val browseId: String) {
    Playlists("Listeler", "FEmusic_liked_playlists"),
    Songs("Şarkılar", "FEmusic_liked_videos"),
    Albums("Albümler", "FEmusic_liked_albums"),
    Artists("Sanatçılar", "FEmusic_library_corpus_artists"),
}

sealed interface Dest {
    data object Home : Dest
    data object Search : Dest
    data object Library : Dest
    data object History : Dest
    data object Explore : Dest
    data object Settings : Dest
    data object Liked : Dest
    data object Shortcuts : Dest
    data class LocalList(val id: String) : Dest
    data class Album(val browseId: String) : Dest
    data class Artist(val browseId: String) : Dest
    data class Playlist(val playlistId: String) : Dest
    data class Collection(val browseId: String, val params: String?, val title: String) : Dest
}

data class Track(
    val id: String,
    val title: String,
    val artists: String,
    val thumbnail: String?,
    val durationMs: Long,
)

fun SongItem.toTrack() = Track(
    id = id,
    title = title,
    artists = artists.joinToString { it.name }.ifBlank { "Bilinmeyen sanatçı" },
    thumbnail = thumbnail,
    durationMs = (duration ?: 0) * 1000L,
)

fun EpisodeItem.toTrack() = asSongItem().toTrack()

fun YTItem.asTrack(): Track? = when (this) {
    is SongItem -> toTrack()
    is EpisodeItem -> toTrack()
    else -> null
}

fun appDir(): File =
    File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Metrolist").also { it.mkdirs() }

@Serializable
data class Settings(
    val cookie: String = "",
    val quality: String = "AUTO",
    val accent: String = "mint",
    val surface: String = "ink",
    val corners: String = "soft",
    val cards: String = "normal",
)

@Serializable
data class SavedTrack(
    val id: String,
    val title: String,
    val artists: String,
    val thumbnail: String? = null,
    val durationMs: Long = 0,
)

@Serializable
data class LocalPlaylist(
    val id: String,
    val title: String,
    val tracks: List<SavedTrack> = emptyList(),
)

@Serializable
data class LocalLibrary(
    val liked: List<SavedTrack> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
)

fun Track.toSaved() = SavedTrack(id, title, artists, thumbnail, durationMs)

fun SavedTrack.toTrack() = Track(id, title, artists, thumbnail, durationMs)

@Serializable
data class HistoryEntry(
    val id: String,
    val title: String,
    val artists: String,
    val thumbnail: String? = null,
    val playedAt: Long,
)

fun coverUrl(raw: String?): String? {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isEmpty()) return null
    var url = if (trimmed.startsWith("//")) "https:$trimmed" else trimmed
    url = url.replace("/vi_webp/", "/vi/").replace(".webp", ".jpg")
    url = url.replace("maxresdefault", "hqdefault").replace("sddefault", "hqdefault")
    val host = url.substringAfter("://").substringBefore('/')
    if (host.endsWith("googleusercontent.com") || host.endsWith("ggpht.com")) {
        url = url.substringBefore('=') + "=w320-h320-l90-rj"
    }
    return url
}

fun formatTime(ms: Long): String {
    val total = (ms.coerceAtLeast(0) / 1000).toInt()
    return "%d:%02d".format(total / 60, total % 60)
}

fun itemSubtitle(item: YTItem): String = when (item) {
    is SongItem -> item.artists.joinToString { it.name }
    is EpisodeItem -> item.author?.name.orEmpty()
    is AlbumItem -> item.artists.orEmpty().joinToString { it.name }
    is PlaylistItem -> item.author?.name ?: item.songCountText.orEmpty()
    is ArtistItem -> "Sanatçı"
    is PodcastItem -> item.author?.name ?: item.episodeCountText.orEmpty()
}
