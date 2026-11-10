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
    data object LocalMusic : Dest
    data object Insights : Dest
    data class Smart(val rule: SmartRule) : Dest
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
    val localPath: String? = null,
    val album: String = "",
)

fun SongItem.toTrack() = Track(
    id = id,
    title = title,
    artists = artists.joinToString { it.name }.ifBlank { "Bilinmeyen sanatçı" },
    thumbnail = thumbnail,
    durationMs = (duration ?: 0) * 1000L,
    album = album?.name.orEmpty(),
)

fun EpisodeItem.toTrack() = asSongItem().toTrack()

fun YTItem.asTrack(): Track? = when (this) {
    is SongItem -> toTrack()
    is EpisodeItem -> toTrack()
    else -> null
}

fun appDir(): File =
    (System.getProperty("metrolist.dataDir")?.let { File(it) }
        ?: File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Metrolist")).also { it.mkdirs() }

@Serializable
data class PlaybackMemory(
    val tracks: List<SavedTrack> = emptyList(),
    val index: Int = 0,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: String = "Off",
    val volume: Float = 1f,
)

@Serializable
data class Settings(
    val cookie: String = "",
    val quality: String = "AUTO",
    val accent: String = "mint",
    val surface: String = "ink",
    val corners: String = "soft",
    val cards: String = "normal",
    val widget: Boolean = true,
    val widgetX: Int = -1,
    val widgetY: Int = -1,
    val toast: Boolean = true,
    val toastOpacity: Float = 0.86f,
    val toastScale: Float = 1f,
    val toastSeconds: Int = 1,
    val toastColor: String = "mint",
    val toastX: Int = -1,
    val toastY: Int = -1,
    val bar: Boolean = true,
    val barX: Int = -1,
    val barScale: Float = 1f,
    val caption: String = "ink",
    val captionText: String = "app",
    val captionStart: String = "#06281A",
    val captionEnd: String = "#1ED760",
    val captionCorner: String = "round",
    val audio: AudioSettings = AudioSettings(),
    val musicFolders: List<String> = emptyList(),
    val autoplay: Boolean = true,
    val checkUpdates: Boolean = true,
    val downloadUpdates: Boolean = true,
    val shortcuts: ShortcutBook = ShortcutBook(),
)

@Serializable
data class SavedTrack(
    val id: String,
    val title: String,
    val artists: String,
    val thumbnail: String? = null,
    val durationMs: Long = 0,
    val localPath: String? = null,
    val album: String = "",
    val addedAt: Long = 0,
)

@Serializable
data class LocalPlaylist(
    val id: String,
    val title: String,
    val tracks: List<SavedTrack> = emptyList(),
    val remoteId: String? = null,
    val remoteBindings: Map<String, String> = emptyMap(),
)

@Serializable
data class LocalLibrary(
    val liked: List<SavedTrack> = emptyList(),
    val playlists: List<LocalPlaylist> = emptyList(),
    val localTracks: List<SavedTrack> = emptyList(),
)

fun Track.toSaved() = SavedTrack(id, title, artists, thumbnail, durationMs, localPath, album, System.currentTimeMillis())

fun SavedTrack.toTrack() = Track(id, title, artists, thumbnail, durationMs, localPath, album)

@Serializable
data class HistoryEntry(
    val id: String,
    val title: String,
    val artists: String,
    val thumbnail: String? = null,
    val playedAt: Long,
    val localPath: String? = null,
    val durationMs: Long = 0,
    val album: String = "",
)

fun HistoryEntry.toTrack() = Track(id, title, artists, thumbnail, durationMs, localPath, album)

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
