package com.metrolist.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal val LocalAppModel = staticCompositionLocalOf<AppModel?> { null }

@Composable
internal fun TrackActions(model: AppModel, tracks: List<Track>) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, enabled = tracks.isNotEmpty(), contentPadding = PaddingValues(4.dp)) { Text("•••") }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Sonraki çal") }, onClick = { model.player.enqueue(tracks, next = true); expanded = false; model.status = "Sonraki çalınacak" })
            DropdownMenuItem(text = { Text("Sıranın sonuna ekle") }, onClick = { model.player.enqueue(tracks); expanded = false; model.status = "Sıraya eklendi" })
            model.localPlaylists.forEach { list ->
                DropdownMenuItem(text = { Text("Listeye ekle: ${list.title}") }, onClick = {
                    model.libraryTools.addTracks(list.id, tracks.map { it.toSaved() }); expanded = false
                })
            }
            DropdownMenuItem(text = { Text("Seçimden yeni liste oluştur") }, onClick = {
                model.libraryTools.createPlaylist("Yeni liste ${model.localPlaylists.size + 1}", tracks.map { it.toSaved() })
                expanded = false
            })
        }
    }
}

@Composable
internal fun DragHandle(index: Int, count: Int, onMove: (Int, Int) -> Unit) {
    val move by rememberUpdatedState(onMove)
    val currentIndex by rememberUpdatedState(index)
    val rowHeight = with(LocalDensity.current) { 64.dp.toPx() }
    var dragging by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(0f) }
    var at by remember { mutableStateOf(index) }
    Text("≡", color = if (dragging) Accent else Muted,
        modifier = Modifier.width(32.dp).height(48.dp).pointerInput(count) {
            detectDragGestures(onDragStart = { at = currentIndex; dragging = true; offset = 0f },
                onDragEnd = { dragging = false; offset = 0f }, onDragCancel = { dragging = false; offset = 0f }) { change, delta ->
                change.consume()
                offset += delta.y
                while (abs(offset) >= rowHeight) {
                    val direction = if (offset > 0) 1 else -1
                    val to = (at + direction).coerceIn(0, count - 1)
                    if (at != to) { move(at, to); at = to }
                    offset -= direction * rowHeight
                }
            }
        }.padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
}

@Composable
internal fun LocalListScreen(model: AppModel, id: String) {
    val list = model.localPlaylists.find { it.id == id }
    SavedTracks(model, list?.title ?: "Liste", list?.tracks.orEmpty(), "Liste boş. Şarkı menüsünden ekleyebilirsin.",
        thumbnail = list?.tracks?.firstOrNull()?.thumbnail, playlistId = id)
}

@Composable
internal fun SmartScreen(model: AppModel, rule: SmartRule) {
    val revision = model.libraryTools.revision
    val history = model.history
    val likes = model.liked
    val tracks = remember(rule, revision, history, likes) { smartTracks(rule, model.store.library, history) }
    SavedTracks(model, rule.title, tracks, "Dinledikçe ve beğendikçe bu liste kendiliğinden oluşur.")
}

private enum class TrackSort(val label: String) { Recent("Sıra"), Title("Başlık"), Artist("Sanatçı"), Duration("Süre") }

@Composable
internal fun SavedTracks(
    model: AppModel, title: String, tracks: List<SavedTrack>, empty: String,
    thumbnail: String? = null, heart: Boolean = false, playlistId: String? = null,
) {
    var sort by remember(title) { mutableStateOf(TrackSort.Recent) }
    var query by remember(title) { mutableStateOf("") }
    var selected by remember(title) { mutableStateOf(setOf<String>()) }
    var rename by remember { mutableStateOf(false) }
    var draft by remember(title) { mutableStateOf(title) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(tracks) { selected = selected.intersect(tracks.map { it.id }.toSet()) }
    val filtered = tracks.filter { query.isBlank() || it.title.contains(query, true) || it.artists.contains(query, true) || it.album.contains(query, true) }
    val shown = when (sort) {
        TrackSort.Recent -> filtered
        TrackSort.Title -> filtered.sortedBy { it.title.lowercase() }
        TrackSort.Artist -> filtered.sortedBy { it.artists.lowercase() }
        TrackSort.Duration -> filtered.sortedByDescending { it.durationMs }
    }
    val chosen = shown.filter { it.id in selected }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Cover(thumbnail, Modifier.size(100.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2)
                Text("${tracks.size} şarkı · ${formatTime(tracks.sumOf { it.durationMs })}", color = Muted)
                if (heart) Text("Beğenilerin bu bilgisayarda saklanır.", color = Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { model.player.playAll(shown.map { it.toTrack() }, 0) }, enabled = shown.isNotEmpty()) { Text("Çal") }
                    TextButton(onClick = { model.player.playShuffled(shown.map { it.toTrack() }) }, enabled = shown.isNotEmpty()) { Text("Karıştır") }
                    if (playlistId != null) {
                        TextButton(onClick = { rename = true }) { Text("Adını değiştir") }
                        TextButton(onClick = { model.libraryTools.exportPlaylist(playlistId) }) { Text("Dışa aktar") }
                        TextButton(onClick = { deleting = true }) { Text("Sil") }
                    }
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedTextField(query, { query = it }, placeholder = { Text("Listede ara") }, singleLine = true, modifier = Modifier.width(230.dp))
            TrackSort.entries.forEach { choice -> FilterChip(sort == choice, { sort = choice }, label = { Text(choice.label) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { selected = if (chosen.size == shown.size) emptySet() else shown.map { it.id }.toSet() }) { Text("${chosen.size} seçili · Tümünü seç/kaldır") }
            if (chosen.isNotEmpty()) {
                TextButton(onClick = { model.player.enqueue(chosen.map { it.toTrack() }) }) { Text("Sıraya ekle") }
                TrackActions(model, chosen.map { it.toTrack() })
                if (playlistId != null) TextButton(onClick = { model.libraryTools.removeTracks(playlistId, selected); selected = emptySet() }) { Text("Listeden çıkar") }
                if (heart) TextButton(onClick = {
                    chosen.forEach { track ->
                        model.store.toggleLike(track)
                        if (track.localPath == null) model.libraryTools.queueSync(SyncAction("like", value = track.id, enabled = false))
                    }
                    model.libraryTools.changed(); selected = emptySet()
                }) { Text("Beğeniyi kaldır") }
            }
        }
        if (shown.isEmpty()) Text(if (tracks.isEmpty()) empty else "Aramana uyan parça yok.", color = Muted)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            itemsIndexed(shown) { at, track ->
                val active = model.player.current?.id == track.id
                val reorder = playlistId != null && sort == TrackSort.Recent && query.isBlank()
                Row(Modifier.fillMaxWidth().background(if (active) Accent.copy(alpha = 0.12f) else Panel, RoundedCornerShape(8.dp)).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    if (reorder) DragHandle(at, shown.size) { from, to -> model.libraryTools.reorder(playlistId!!, from, to) }
                    Checkbox(track.id in selected, { selected = if (it) selected + track.id else selected - track.id })
                    Row(Modifier.weight(1f).clickable { model.player.playAll(shown.map { it.toTrack() }, at) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(track.thumbnail, Modifier.size(44.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, color = if (active) Accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.artists + if (track.album.isBlank()) "" else " · ${track.album}", color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(if (track.durationMs > 0) formatTime(track.durationMs) else "—:—", color = Muted)
                    }
                    TrackActions(model, listOf(track.toTrack()))
                    if (reorder) {
                        TextButton(onClick = { model.libraryTools.reorder(playlistId!!, at, at - 1) }, enabled = at > 0, contentPadding = PaddingValues(2.dp), modifier = Modifier.width(32.dp)) { Text("↑") }
                        TextButton(onClick = { model.libraryTools.reorder(playlistId!!, at, at + 1) }, enabled = at < shown.lastIndex, contentPadding = PaddingValues(2.dp), modifier = Modifier.width(32.dp)) { Text("↓") }
                    }
                }
            }
        }
    }
    if (rename) AlertDialog(onDismissRequest = { rename = false }, title = { Text("Liste adı") },
        text = { OutlinedTextField(draft, { draft = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { model.libraryTools.rename(playlistId!!, draft); rename = false }, enabled = draft.isNotBlank()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = { rename = false }) { Text("Vazgeç") } })
    if (deleting) AlertDialog(onDismissRequest = { deleting = false }, title = { Text("Liste silinsin mi?") }, text = { Text(title) },
        confirmButton = { TextButton(onClick = { model.libraryTools.delete(playlistId!!); deleting = false }) { Text("Sil") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Vazgeç") } })
}

@Composable
internal fun LocalMusicScreen(model: AppModel) {
    val tools = model.libraryTools
    Column(Modifier.fillMaxSize()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = tools::chooseFolder, enabled = !tools.scanning) { Text("Müzik klasörü ekle") }
            TextButton(onClick = tools::scan, enabled = !tools.scanning && tools.folders.isNotEmpty()) { Text("Yeniden tara") }
            if (tools.scanning) TextButton(onClick = tools::cancelScan) { Text("Taramayı iptal et") }
        }
        tools.folders.forEach { folder ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(folder, modifier = Modifier.weight(1f), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { tools.removeFolder(folder) }, enabled = !tools.scanning) { Text("Kaldır") }
            }
        }
        if (tools.scanProgress.isNotBlank()) Text(tools.scanProgress, color = Muted)
        if (tools.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        Box(Modifier.weight(1f)) { SavedTracks(model, "Yerel müzik", tools.localTracks, "MP3, FLAC, WAV, OGG ve diğer ses dosyaların için bir klasör ekle.") }
    }
}
