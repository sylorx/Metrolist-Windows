package com.metrolist.desktop

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.security.MessageDigest

@Serializable
data class SyncAction(
    val kind: String,
    val playlistId: String = "",
    val value: String = "",
    val enabled: Boolean = true,
    val trackIds: List<String> = emptyList(),
    val remoteId: String? = null,
    val account: String = "",
    val id: String = java.util.UUID.randomUUID().toString(),
)

internal fun accountFingerprint(cookie: String): String = MessageDigest.getInstance("SHA-256")
    .digest(cookie.toByteArray()).joinToString("") { "%02x".format(it) }

/** Actions survive restart and stop at the first failure to preserve edit order. */
internal class LibrarySync(private val store: Store) {
    private val mutex = Mutex()
    suspend fun drain(): String? = mutex.withLock {
        if (store.settings.cookie.isBlank()) return@withLock null
        val account = accountFingerprint(store.settings.cookie)
        for (action in store.pendingSync.filter { it.account == account }) {
            val outcome = runCatching { perform(action) }
            if (outcome.isFailure) return@withLock "Eşitleme bekliyor: ${outcome.exceptionOrNull()?.message ?: "Bağlantıyı kontrol et"}"
            store.completeSync(action.id)
        }
        if (store.pendingSync.any { it.account != account }) "Bazı işlemler başka bir oturuma ait; o hesapla giriş yapıldığında gönderilecek." else null
    }

    private suspend fun songs(id: String): List<SongItem> {
        val page = YouTube.playlist(id).getOrThrow()
        val result = page.songs.toMutableList()
        var token = page.songsContinuation
        val seen = mutableSetOf<String>()
        while (token != null && seen.add(token)) {
            val next = YouTube.playlistContinuation(token).getOrThrow()
            result.addAll(next.songs)
            token = next.continuation
        }
        return result
    }

    private suspend fun perform(action: SyncAction) {
        if (action.kind == "like") {
            YouTube.likeVideo(action.value, action.enabled).getOrThrow()
            return
        }
        if (action.kind == "delete") {
            action.remoteId?.let { YouTube.deletePlaylist(it).getOrThrow() }
            return
        }
        val local = store.library.playlists.find { it.id == action.playlistId } ?: return
        if (action.kind == "create") {
            val remote = local.remoteBindings[action.account] ?: YouTube.createPlaylist(local.title).also { remoteId ->
                store.updatePlaylist(local.id) { it.copy(remoteId = remoteId, remoteBindings = it.remoteBindings + (action.account to remoteId)) }
            }
            val latest = store.library.playlists.find { it.id == local.id }
            if (latest == null) { YouTube.deletePlaylist(remote).getOrThrow(); return }
            val existing = songs(remote).map { it.id }.toMutableSet()
            latest.tracks.filter { it.localPath == null }.forEach {
                if (existing.add(it.id)) YouTube.addToPlaylist(remote, it.id).getOrThrow()
            }
            return
        }
        val remote = local.remoteBindings[action.account] ?: error("Bu oturum için liste bağlantısı oluşturulamadı")
        when (action.kind) {
            "rename" -> YouTube.renamePlaylist(remote, action.value).getOrThrow()
            "add" -> {
                val existing = songs(remote).map { it.id }.toMutableSet()
                action.trackIds.filterNot { it.startsWith("file:") }.forEach {
                    if (existing.add(it)) YouTube.addToPlaylist(remote, it).getOrThrow()
                }
            }
            "remove" -> songs(remote).filter { it.id in action.trackIds }.forEach { song ->
                val setId = song.setVideoId ?: error("Şarkının liste kimliği alınamadı")
                YouTube.removeFromPlaylist(remote, song.id, setId).getOrThrow()
            }
            "order" -> {
                val existing = songs(remote).associateBy { it.id }
                var successor: String? = null
                for (id in action.trackIds.asReversed()) {
                    val setId = existing[id]?.setVideoId ?: continue
                    YouTube.moveSongPlaylist(remote, setId, successor).getOrThrow()
                    successor = setId
                }
            }
        }
    }
}
