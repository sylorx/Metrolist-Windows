package com.metrolist.desktop

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
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
                TextButton(onClick = player::clearUpcoming, enabled = player.index < player.queue.lastIndex) { Text("Sonrakileri sil") }
                TextButton(onClick = player::clearQueue, enabled = player.queue.isNotEmpty()) { Text("Temizle") }
            }
            if (player.queue.isEmpty()) Text("Şarkı menüsünden sıraya parça ekle.", color = Muted)
            val keys = remember(player.queue) { occurrenceKeys(player.queue) { it.id } }
            val scroll = rememberLazyListState(initialFirstVisibleItemIndex = (player.index - 2).coerceAtLeast(0))
            LazyColumn(Modifier.weight(1f), state = scroll, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(player.queue, key = { at, _ -> keys[at] }) { at, track ->
                    Row(Modifier.animateItem().fillMaxWidth().background(if (at == player.index) Accent.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(8.dp)),
                        verticalAlignment = Alignment.CenterVertically) {
                        DragHandle(at, player.queue.size, player::moveQueue)
                        Row(Modifier.weight(1f).clickable { player.jumpTo(at) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Cover(track.thumbnail, Modifier.size(36.dp))
                            Spacer(Modifier.width(8.dp))
                            if (at == player.index) {
                                EqualizerBars(player.isPlaying, Accent)
                                Spacer(Modifier.width(8.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(track.title, color = if (at == player.index) Accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artists, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(onClick = { player.removeQueue(at) }, modifier = Modifier.width(32.dp), contentPadding = PaddingValues(2.dp)) { Text("×") }
                    }
                }
            }
        }
    }
    if (save) AlertDialog(onDismissRequest = { save = false }, title = { Text("Sırayı listeye kaydet") },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.keepTyping()) },
        confirmButton = { TextButton(onClick = { model.libraryTools.saveQueue(name); save = false }, enabled = name.isNotBlank()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = { save = false }) { Text("Vazgeç") } })
}

@Composable
internal fun LyricsPanel(player: PlaybackController, modifier: Modifier) {
    Column(modifier.background(Panel).padding(20.dp)) {
        Text(player.current?.title ?: "Çalmıyor", color = Color.White, style = MaterialTheme.typography.titleLarge, maxLines = 2)
        Text(player.current?.artists.orEmpty(), color = Muted)
        LyricsLines(player, Modifier.weight(1f).padding(top = 8.dp), large = false)
    }
}

/** Lyrics that follow the music; the current line grows and lights up. */
@Composable
internal fun LyricsLines(player: PlaybackController, modifier: Modifier, large: Boolean) {
    val doc = player.lyricDocument
    val timed = doc?.lines.orEmpty()
    val current by remember(timed) { derivedStateOf { timed.indexOfLast { it.timeMs <= player.positionMs + 150 } } }
    val scroll = rememberLazyListState()
    var follow by remember { mutableStateOf(true) }
    LaunchedEffect(doc) { scroll.scrollToItem(0) }
    LaunchedEffect(current, follow, doc) {
        if (follow && current >= 0 && timed.isNotEmpty()) scroll.animateScrollToItem((current - if (large) 3 else 1).coerceAtLeast(0))
    }
    val motion = LocalMotion.current
    Column(modifier) {
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
                    val active = at == current
                    val color by animateColorAsState(
                        if (active) Accent else Color.White.copy(alpha = if (at < current) 0.35f else 0.6f), tween(280), label = "satir",
                    )
                    val scale by animateFloatAsState(if (active) 1.06f else 1f, if (motion) spring(0.7f, 300f) else snap(), label = "olcek")
                    Text(line.text.ifBlank { "♪" }, color = color,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        style = if (large) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                        modifier = Modifier.fillMaxWidth().clickable { player.seek(line.timeMs) }.padding(vertical = 4.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = TransformOrigin(0f, 0.5f) })
                }
            } else {
                item { Text(player.lyrics ?: "Sözler yükleniyor…", color = Color.White, style = MaterialTheme.typography.titleMedium) }
            }
        }
    }
}
