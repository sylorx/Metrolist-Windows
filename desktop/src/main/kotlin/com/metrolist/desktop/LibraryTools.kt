package com.metrolist.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.*
import java.io.File
import javax.swing.JFileChooser
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.coroutines.resume

/** Owns library editing, file operations and account synchronization independently of navigation. */
class LibraryTools(private val model: AppModel) {
    private val store get() = model.store
    private val sync = LibrarySync(model.store)
    var localTracks by mutableStateOf(store.library.localTracks)
        private set
    var revision by mutableStateOf(0)
        private set
    var scanning by mutableStateOf(false)
        private set
    var scanProgress by mutableStateOf("")
        private set
    var pendingCount by mutableStateOf(store.pendingSync.size)
        private set
    var syncMessage by mutableStateOf<String?>(null)
        private set
    var syncing by mutableStateOf(false)
        private set
    var fileBusy by mutableStateOf(false)
        private set
    var folders by mutableStateOf(store.settings.musicFolders)
        private set
    var autoplay by mutableStateOf(store.settings.autoplay)
        private set
    private var scanJob: Job? = null

    init {
        model.scope.launch {
            while (true) { retrySync(); delay(60_000) }
        }
    }
    fun changed() {
        model.localPlaylists = store.library.playlists
        model.liked = store.library.liked
        localTracks = store.library.localTracks
        revision++
    }
    fun queueSync(action: SyncAction) {
        if (store.settings.cookie.isBlank()) return
        val account = accountFingerprint(store.settings.cookie)
        val local = store.library.playlists.find { it.id == action.playlistId }
        if (action.kind !in listOf("create", "delete", "like") && local != null && local.remoteBindings[account] == null &&
            store.pendingSync.none { it.kind == "create" && it.playlistId == local.id && it.account == account }) {
            store.enqueueSync(SyncAction("create", local.id, account = account))
        }
        store.enqueueSync(action.copy(account = account))
        pendingCount = store.pendingSync.size
        retrySync()
    }
    fun retrySync() {
        if (syncing) return
        model.scope.launch {
            syncing = true
            try {
                syncMessage = withContext(Dispatchers.IO) { sync.drain() }
                pendingCount = store.pendingSync.size
                changed()
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { syncMessage = "Eşitleme başarısız: ${failure.message}" }
            finally { syncing = false }
        }
    }
    fun createPlaylist(title: String, tracks: List<SavedTrack> = emptyList()): String {
        val created = store.createPlaylist(title, tracks)
        changed()
        queueSync(SyncAction("create", created.id))
        val remoteTracks = tracks.filter { it.localPath == null }.map { it.id }
        if (remoteTracks.isNotEmpty()) queueSync(SyncAction("add", created.id, trackIds = remoteTracks))
        return created.id
    }
    fun rename(id: String, title: String) {
        if (title.isBlank()) return
        store.updatePlaylist(id) { it.copy(title = title.trim()) }
        changed(); queueSync(SyncAction("rename", id, value = title.trim()))
    }
    fun delete(id: String) {
        val list = store.library.playlists.find { it.id == id } ?: return
        store.deletePlaylist(id); changed()
        queueSync(SyncAction("delete", id, remoteId = list.remoteBindings[accountFingerprint(store.settings.cookie)]))
        model.openRail(Dest.Library)
    }
    fun addTracks(id: String, tracks: List<SavedTrack>) {
        store.addToPlaylist(id, tracks); changed()
        val remote = tracks.filter { it.localPath == null }.map { it.id }
        if (remote.isNotEmpty()) queueSync(SyncAction("add", id, trackIds = remote))
        model.status = "${tracks.size} şarkı listeye eklendi"
    }
    fun removeTracks(id: String, ids: Set<String>) {
        store.removeTracks(id, ids); changed()
        queueSync(SyncAction("remove", id, trackIds = ids.filterNot { it.startsWith("file:") }))
    }
    fun reorder(id: String, from: Int, to: Int) {
        val list = store.library.playlists.find { it.id == id } ?: return
        if (from !in list.tracks.indices || to !in list.tracks.indices) return
        store.updatePlaylist(id) { it.copy(tracks = it.tracks.toMutableList().apply { add(to, removeAt(from)) }) }
        changed()
        queueSync(SyncAction("order", id, trackIds = store.library.playlists.first { it.id == id }.tracks.map { it.id }))
    }
    fun saveQueue(title: String) {
        if (model.player.queue.isEmpty()) return
        val id = createPlaylist(title, model.player.queue.map { it.toSaved() })
        model.openLocal(id)
        model.status = "Sıra liste olarak kaydedildi"
    }
    fun configureAudio(audio: AudioSettings) {
        val settings = store.settings.copy(audio = audio).validated()
        store.saveSettings(settings)
        model.player.configureAudio(settings.audio)
    }
    fun changeAutoplay(enabled: Boolean) {
        store.saveSettings(store.settings.copy(autoplay = enabled)); autoplay = enabled
    }
    fun chooseFolder() {
        model.scope.launch {
            val selected = chooseFile(directory = true) ?: return@launch
            val path = selected.canonicalPath
            folders = (folders + path).distinctBy { it.lowercase() }
            store.saveSettings(store.settings.copy(musicFolders = folders))
            scan()
        }
    }
    fun removeFolder(folder: String) {
        scanJob?.cancel()
        folders = folders.filterNot { it == folder }
        store.saveSettings(store.settings.copy(musicFolders = folders))
        val roots = folders.map { File(it).canonicalFile.toPath() }
        store.saveLibrary(store.library.copy(localTracks = store.library.localTracks.filter { track ->
            track.localPath?.let { path -> roots.any { File(path).canonicalFile.toPath().startsWith(it) } } == true
        }))
        changed()
    }
    fun cancelScan() { scanJob?.cancel() }
    fun scan() {
        if (scanning) return
        scanJob = model.scope.launch {
            scanning = true; scanProgress = "Müzik dosyaları taranıyor…"
            try {
                val roots = folders.toList()
                val result = withContext(Dispatchers.IO) {
                    LocalMusic.scan(roots) { count, name -> model.scope.launch { scanProgress = "$count parça · $name" } }
                }
                // Do not discard a disconnected disk's indexed tracks.
                val unavailable = store.library.localTracks.filter { song -> roots.any { root ->
                    !File(root).isDirectory && song.localPath?.let { File(it).toPath().startsWith(File(root).toPath()) } == true
                } }
                withContext(Dispatchers.IO) { store.updateLocalTracks((result.tracks + unavailable).distinctBy { it.id }) }
                changed()
                scanProgress = "${result.tracks.size} parça bulundu · ${result.failed} dosya/klasör okunamadı"
            } catch (cancel: CancellationException) { scanProgress = "Tarama iptal edildi"; throw cancel }
            catch (failure: Exception) { scanProgress = "Tarama başarısız: ${failure.message}" }
            finally { scanning = false }
        }
    }
    fun exportBackup() = fileOperation {
        val destination = chooseFile(save = true, defaultName = "Metrolist-yedek.json") ?: return@fileOperation
        withContext(Dispatchers.IO) { store.exportBackup(withJsonExtension(destination)) }
        model.status = "Yedek kaydedildi. Hesap çerezi yedeğe eklenmedi."
    }
    fun importBackup() = fileOperation {
        val source = chooseFile() ?: return@fileOperation
        model.player.flushListening()
        withContext(Dispatchers.IO) { store.importBackup(source) }
        changed(); model.history = store.history
        model.reloadPreferences()
        autoplay = store.settings.autoplay
        model.status = "Yedek kitaplığa birleştirildi. Önceki veriler before-import.json içinde saklandı."
    }
    fun exportPlaylist(id: String) = fileOperation {
        val list = store.library.playlists.find { it.id == id } ?: return@fileOperation
        val target = chooseFile(save = true, defaultName = "${list.title.replace(Regex("[^\\p{L}\\p{N} _-]"), "_")}.m3u8", playlist = true) ?: return@fileOperation
        withContext(Dispatchers.IO) {
            val text = buildString {
                appendLine("#EXTM3U")
                list.tracks.forEach {
                    appendLine("#EXTINF:${it.durationMs / 1000},${it.artists.replace('\n', ' ')} - ${it.title.replace('\n', ' ')}")
                    appendLine(it.localPath ?: "https://music.youtube.com/watch?v=${it.id}")
                }
            }
            atomicWrite(target, text)
        }
        model.status = "Liste M3U8 olarak kaydedildi"
    }
    private fun fileOperation(block: suspend () -> Unit) {
        if (fileBusy) return
        model.scope.launch {
            fileBusy = true
            try { block() }
            catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { model.status = "Dosya işlemi başarısız: ${failure.message}" }
            finally { fileBusy = false }
        }
    }
}

private fun withJsonExtension(file: File) = if (file.extension.equals("json", true)) file else File(file.parentFile, file.name + ".json")
internal suspend fun chooseFile(directory: Boolean = false, save: Boolean = false, defaultName: String? = null, playlist: Boolean = false): File? =
    suspendCancellableCoroutine { continuation ->
        SwingUtilities.invokeLater {
            if (!continuation.isActive) return@invokeLater
            val chooser = JFileChooser().apply {
                fileSelectionMode = if (directory) JFileChooser.DIRECTORIES_ONLY else JFileChooser.FILES_ONLY
                if (!directory) fileFilter = FileNameExtensionFilter(if (playlist) "Çalma listesi" else "Metrolist yedeği", if (playlist) "m3u8" else "json")
                defaultName?.let { selectedFile = File(it) }
            }
            val result = if (save) chooser.showSaveDialog(null) else chooser.showOpenDialog(null)
            val raw = if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
            val selected = if (save && raw != null && !directory) {
                val extension = if (playlist) "m3u8" else "json"
                if (raw.extension.equals(extension, true)) raw else File(raw.parentFile, raw.name + "." + extension)
            } else raw
            val approved = selected != null && (!save || !selected.exists() || javax.swing.JOptionPane.showConfirmDialog(null,
                "${selected.name} değiştirilsin mi?", "Dosya zaten var", javax.swing.JOptionPane.YES_NO_OPTION) == javax.swing.JOptionPane.YES_OPTION)
            if (continuation.isActive) continuation.resume(if (approved) selected else null)
        }
    }
