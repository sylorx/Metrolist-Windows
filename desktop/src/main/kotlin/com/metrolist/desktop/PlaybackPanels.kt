package com.metrolist.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun QueuePanel(model: AppModel, modifier: Modifier) {
    val player = model.player
    var save by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("Çalma sıram") }
    Surface(modifier, color = Panel) {
        Column(Modifier.padding(10.dp)) {
            Text("Sıradaki · ${player.queue.size} parça", fontWeight = FontWeight.SemiBold, color = Color.White)
            Row {
                TextButton(onClick = { save = true }, enabled = player.queue.isNotEmpty()) { Text("Listeye kaydet") }
                TextButton(onClick = player::clearQueue, enabled = player.queue.isNotEmpty()) { Text("Temizle") }
            }
            if (player.queue.isEmpty()) Text("Şarkı menüsünden sıraya parça ekle.", color = Muted)
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(player.queue) { at, track ->
                    Row(Modifier.fillMaxWidth().background(if (at == player.index) Accent.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically) {
                        DragHandle(at, player.queue.size, player::moveQueue)
                        Row(Modifier.weight(1f).clickable { player.jumpTo(at) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cover(track.thumbnail, Modifier.size(36.dp))
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = if (at == player.index) Accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artists, color = Muted, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(onClick = { player.removeQueue(at) }, modifier = Modifier.width(32.dp), contentPadding = PaddingValues(2.dp)) { Text("×") }
                    }
                }
            }
        }
    }
    if (save) AlertDialog(onDismissRequest = { save = false }, title = { Text("Sırayı listeye kaydet") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { model.libraryTools.saveQueue(name); save = false }, enabled = name.isNotBlank()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = { save = false }) { Text("Vazgeç") } })
}

@Composable
internal fun LyricsPanel(player: PlaybackController, modifier: Modifier) {
    val doc = player.lyricDocument
    val timed = doc?.lines.orEmpty()
    val current = timed.indexOfLast { it.timeMs <= player.positionMs }
    val scroll = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(current, follow, player.current?.id) {
        if (follow && current >= 0 && timed.isNotEmpty()) scroll.animateScrollToItem((current - 1).coerceAtLeast(0))
    }
    Column(modifier.background(Panel).padding(20.dp)) {
        Text(player.current?.title ?: "Çalmıyor", color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2)
        Text(player.current?.artists.orEmpty(), color = Muted)
        if (timed.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Otomatik takip", color = Muted, modifier = Modifier.weight(1f))
                Switch(follow, { follow = it })
            }
        }
        Text(if (doc?.source.isNullOrBlank()) "Şarkı sözleri" else "Kaynak: ${doc?.source}", color = Muted, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), state = scroll, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (timed.isNotEmpty()) {
                itemsIndexed(timed) { at, line ->
                    Text(line.text.ifBlank { "♪" }, color = if (at == current) Accent else Color.White.copy(alpha = 0.55f),
                        fontWeight = if (at == current) FontWeight.Bold else FontWeight.Normal,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().clickable { player.seek(line.timeMs) }.padding(vertical = 4.dp))
                }
            } else {
                item { Text(player.lyrics ?: "Sözler yükleniyor…", color = Color.White, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
