package com.metrolist.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Short confirmations float above the player instead of pushing the page down. */
@Composable
internal fun StatusToast(model: AppModel, modifier: Modifier) {
    val status = model.status
    // The text stays while the card slides out.
    var shown by remember { mutableStateOf("") }
    LaunchedEffect(status) { if (status != null) shown = status }
    AnimatedVisibility(
        status != null, modifier,
        enter = fadeIn(tween(180)) + slideInVertically(spring(0.7f, 380f)) { it },
        exit = fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 2 },
    ) {
        Text(
            status ?: shown,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 560.dp).shadow(18.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp))
                .background(Card).border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(14.dp))
                .padding(horizontal = 18.dp, vertical = 11.dp),
        )
    }
}

/** Shown in place of a page that could not be loaded. */
@Composable
internal fun LoadFailure(model: AppModel) {
    Column(Modifier.padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(model.error ?: "Sayfa yüklenemedi", color = Color.White)
        Button(onClick = { model.refresh(force = true) }) { Text("Yeniden dene") }
    }
}

@Composable
internal fun ImportDialog(model: AppModel, progress: ImportProgress) {
    val tools = model.libraryTools
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        title = { Text(if (progress.finished) "İçe aktarma bitti" else "“${progress.title}” içe aktarılıyor", maxLines = 2) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.widthIn(max = 520.dp)) {
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progress.done.toFloat() / progress.total.coerceAtLeast(1) },
                    modifier = Modifier.fillMaxWidth(), color = Accent,
                )
                Text("${progress.done}/${progress.total} satır · ${progress.matched} şarkı bulundu", color = Color.White)
                if (progress.finished) {
                    Text(
                        if (progress.playlistId != null) "“${progress.title}” listesi kitaplığına eklendi." else "Hiçbir satır için eşleşen şarkı bulunamadı.",
                        color = Muted,
                    )
                    if (progress.missing.isNotEmpty()) {
                        Text("Bulunamayanlar (${progress.missing.size}):", color = Color.White)
                        LazyColumn(Modifier.heightIn(max = 200.dp)) {
                            items(progress.missing) { Text(it, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                } else Text("Her satır YouTube Music’te aranıyor; başlık, sanatçı ve süre birlikte tutan sonuç seçilir.", color = Muted)
            }
        },
        confirmButton = {
            if (progress.finished) TextButton(onClick = tools::closeImport) { Text(if (progress.playlistId != null) "Listeyi aç" else "Kapat") }
        },
        dismissButton = { if (!progress.finished) TextButton(onClick = tools::cancelImport) { Text("Vazgeç") } },
    )
}

@Composable
internal fun DiagnosticsScreen(model: AppModel) {
    val clock = remember { DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault()) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Tanılama", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text("Son çalma denemeleri. Rapor; bağlantı adresi, çerez veya klasör yolu içermez ve hiçbir yere gönderilmez.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = model::copyDiagnostics) { Text("Raporu kopyala") }
            TextButton(onClick = model::clearDiagnostics, enabled = model.events.isNotEmpty()) { Text("Temizle") }
        }
        if (model.events.isEmpty()) Text("Henüz kayıt yok. Bir şarkı çaldığında burada görünür.", color = Muted)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(model.events) { event ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.size(9.dp).clip(CircleShape).background(if (event.failed) Color(0xFFFF6B6B) else Accent))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${event.stage} · ${event.track}", color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(event.detail, color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    if (event.tookMs > 0) Text("${event.tookMs} ms", color = Muted, modifier = Modifier.padding(end = 12.dp))
                    Text(clock.format(Instant.ofEpochMilli(event.at)), color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
internal fun BlockedScreen(model: AppModel) {
    // Song blocks keep only the ID; names come from what this computer knows about the song.
    val known = remember(model.history, model.liked, model.libraryTools.revision) {
        libraryCatalog(model.store.library, model.history).associate { it.track.id to it.track }
    }
    val songs = model.blocks.songs
    val artists = model.blocks.artists
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("Radyoda çalınmayanlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Radyo ve otomatik devam bu şarkı ve sanatçıları sıraya eklemez. Kendin açarsan yine çalar.", color = Muted)
            Spacer(Modifier.height(12.dp))
            if (artists.isEmpty() && songs.isEmpty()) {
                Text("Bir şarkının ••• menüsünden veya oynatıcıdaki menüden eklenir.", color = Muted)
            }
        }
        itemsIndexed(artists) { at, name ->
            BlockedRow(name, "Sanatçı", at) { model.unblockArtist(name) }
        }
        itemsIndexed(songs, key = { _, id -> id }) { at, id ->
            val song = known[id]
            BlockedRow(song?.title ?: "Bilinmeyen şarkı", song?.artists ?: id, at) { model.unblock(id) }
        }
    }
}

@Composable
private fun BlockedRow(title: String, subtitle: String, at: Int, onRemove: () -> Unit) {
    Row(
        Modifier.appear(at).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Card).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onRemove) { Text("Kaldır") }
    }
}
