package com.metrolist.desktop

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal enum class HitKind { Command, List, Song, Web }

internal class PaletteHit(
    val kind: HitKind,
    val title: String,
    val subtitle: String,
    val thumbnail: String? = null,
    val run: () -> Unit,
)

/** Higher is better; null means no match. Every word has to be found in the title or the subtitle. */
internal fun paletteScore(query: String, title: String, subtitle: String): Int? {
    val text = fold(query).trim()
    if (text.isEmpty()) return 0
    val name = fold(title)
    val rest = fold(subtitle)
    val words = text.split(' ').filter { it.isNotBlank() }
    if (!words.all { it in name || it in rest }) return null
    return when {
        name == text -> 100
        name.startsWith(text) -> 80
        name.split(' ').any { it.startsWith(words.first()) } && words.all { it in name } -> 60
        words.all { it in name } -> 40
        else -> 20
    }
}

/** Everything that can be reached from the keyboard, best matches first. */
internal fun paletteHits(model: AppModel, query: String): List<PaletteHit> {
    val player = model.player
    val commands = listOf(
        PaletteHit(HitKind.Command, "Ana sayfa", "Sayfa") { model.openRail(Dest.Home) },
        PaletteHit(HitKind.Command, "Keşfet", "Sayfa") { model.openRail(Dest.Explore) },
        PaletteHit(HitKind.Command, "Sana özel", "Kitaplık araması, akıllı listeler ve kişisel DJ") { model.openRail(Dest.Personal) },
        PaletteHit(HitKind.Command, "Kitaplık", "Sayfa") { model.openRail(Dest.Library) },
        PaletteHit(HitKind.Command, "Beğenilenler", "Sayfa") { model.openRail(Dest.Liked) },
        PaletteHit(HitKind.Command, "İndirilenler", "Çevrimdışı dinleme") { model.openRail(Dest.Downloads) },
        PaletteHit(HitKind.Command, "Çalan şarkıyı indir", "Çevrimdışı dinleme") { player.current?.let { model.download(listOf(it)) } },
        PaletteHit(HitKind.Command, "Yerel müzik", "Sayfa") { model.openRail(Dest.LocalMusic) },
        PaletteHit(HitKind.Command, "İstatistikler", "Sayfa") { model.openRail(Dest.Insights) },
        PaletteHit(HitKind.Command, "Geçmiş", "Sayfa") { model.openRail(Dest.History) },
        PaletteHit(HitKind.Command, "Ayarlar", "Sayfa") { model.openRail(Dest.Settings) },
        PaletteHit(HitKind.Command, "Tanılama", "Çalma sorunları") { model.open(Dest.Diagnostics) },
        PaletteHit(HitKind.Command, "Radyoda çalınmayanlar", "Engellenen şarkı ve sanatçılar") { model.open(Dest.Blocked) },
        PaletteHit(HitKind.Command, "Klavye kısayolları", "Yardım") { model.open(Dest.Shortcuts) },
        PaletteHit(HitKind.Command, "Oynat / duraklat", "Oynatma") { player.toggle() },
        PaletteHit(HitKind.Command, "Sonraki parça", "Oynatma") { player.next() },
        PaletteHit(HitKind.Command, "Önceki parça", "Oynatma") { player.previous() },
        PaletteHit(HitKind.Command, "Karıştırmayı değiştir", "Oynatma") { player.toggleShuffle() },
        PaletteHit(HitKind.Command, "Tekrarı değiştir", "Oynatma") { player.cycleRepeat() },
        PaletteHit(HitKind.Command, "Çalan şarkıyı beğen", "Oynatma") { model.likeCurrent() },
        PaletteHit(HitKind.Command, "Çalan şarkıdan radyo başlat", "Oynatma") { player.current?.let(model::startRadio) },
        PaletteHit(HitKind.Command, "Sırayı temizle", "Oynatma") { player.clearUpcoming() },
        PaletteHit(HitKind.Command, "Sözleri aç / kapat", "Görünüm") { player.toggleLyrics() },
        PaletteHit(HitKind.Command, "Sırayı aç / kapat", "Görünüm") { player.toggleQueue() },
        PaletteHit(HitKind.Command, "Tam ekran oynatıcı", "Görünüm") { if (player.current != null) model.showNowPlaying = true },
        PaletteHit(HitKind.Command, "Yeni çalma listesi", "Kitaplık") { model.showCreate = true },
        PaletteHit(HitKind.Command, "Uyku: 30 dakika", "Zamanlayıcı") { player.setSleep(30) },
        PaletteHit(HitKind.Command, "Uyku: parça sonunda dur", "Zamanlayıcı") { player.setSleep(afterTrack = true) },
    )
    val lists = model.localPlaylists.map { list ->
        PaletteHit(HitKind.List, list.title, "Çalma listesi · ${list.tracks.size} şarkı", list.tracks.firstOrNull()?.thumbnail) { model.openLocal(list.id) }
    }
    if (query.isBlank()) return commands.take(8) + lists.take(6)
    fun ranked(hits: List<PaletteHit>, limit: Int) = hits.mapNotNull { hit -> paletteScore(query, hit.title, hit.subtitle)?.let { hit to it } }
        .sortedByDescending { it.second }.take(limit)
    // The personal library search also matches playlist names a song belongs to.
    val songHits = searchCatalog(libraryCatalog(model.store.library, model.history), query).take(12).map { item ->
        val song = item.track.toTrack()
        val score = paletteScore(query, song.title, "${song.artists} ${song.album}") ?: 10
        PaletteHit(HitKind.Song, song.title, song.artists, song.thumbnail) { player.playAll(listOf(song), 0) } to score
    }
    val merged = (ranked(commands, 6) + ranked(lists, 6) + songHits).sortedByDescending { it.second }.map { it.first }
    return merged.take(18) + PaletteHit(HitKind.Web, "YouTube Music’te ara: ${query.trim()}", "Çevrimiçi arama") {
        model.query = query.trim(); model.submitSearch()
    }
}

@Composable
internal fun CommandPalette(model: AppModel) {
    AnimatedVisibility(model.showPalette, enter = fadeIn(tween(140)), exit = fadeOut(tween(140))) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f))
                .clickable(remember { MutableInteractionSource() }, indication = null) { model.showPalette = false },
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                Modifier.padding(top = 72.dp).animateEnterExit(
                    enter = scaleIn(spring(0.72f, 520f), 0.94f) + fadeIn(tween(160)),
                    exit = scaleOut(tween(120), 0.97f) + fadeOut(tween(120)),
                ),
            ) { PaletteCard(model) }
        }
    }
}

@Composable
private fun PaletteCard(model: AppModel) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(0) }
    val focus = remember { FocusRequester() }
    val scroll = rememberLazyListState()
    val hits = remember(query, model.liked, model.localPlaylists, model.history, model.libraryTools.localTracks) { paletteHits(model, query) }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(query) { selected = 0 }
    LaunchedEffect(selected) { if (hits.isNotEmpty()) scroll.animateScrollToItem((selected - 3).coerceAtLeast(0)) }
    fun run(at: Int) {
        val hit = hits.getOrNull(at) ?: return
        model.showPalette = false
        hit.run()
    }
    Column(
        Modifier.width(620.dp).clip(RoundedCornerShape(18.dp)).background(Panel)
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(18.dp))
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(12.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Şarkı, liste, sayfa veya komut ara") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focus).keepTyping().onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> { if (hits.isNotEmpty()) selected = (selected + 1) % hits.size; true }
                    Key.DirectionUp -> { if (hits.isNotEmpty()) selected = (selected - 1 + hits.size) % hits.size; true }
                    Key.Enter, Key.NumPadEnter -> { run(selected); true }
                    Key.Escape -> { model.showPalette = false; true }
                    else -> false
                }
            },
        )
        LazyColumn(Modifier.heightIn(max = 420.dp).padding(top = 8.dp), state = scroll) {
            itemsIndexed(hits) { at, hit ->
                val active = at == selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                        .background(if (active) Accent.copy(alpha = 0.16f) else Color.Transparent)
                        .clickable { run(at) }.padding(horizontal = 10.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hit.thumbnail != null) Cover(hit.thumbnail, Modifier.size(36.dp))
                    else Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(Card), contentAlignment = Alignment.Center) {
                        Icon(
                            when (hit.kind) {
                                HitKind.Command -> Icons.Default.Bolt
                                HitKind.Web -> Icons.Default.Search
                                else -> Icons.AutoMirrored.Filled.QueueMusic
                            },
                            contentDescription = null, tint = if (active) Accent else Muted, modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(hit.title, color = if (active) Accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(hit.subtitle, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                    if (active) Text("Enter", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

/** Put on text fields: a typed space stays in the field instead of reaching the play/pause shortcut. */
internal fun Modifier.keepTyping(): Modifier = onKeyEvent { event ->
    !event.isCtrlPressed && !event.isAltPressed &&
        (event.key == Key.Spacebar || event.key == Key.DirectionLeft || event.key == Key.DirectionRight)
}

internal val shortcutHelp = listOf(
    "Boşluk" to "Oynat veya duraklat",
    "Ctrl + K" to "Hızlı arama ve komutlar",
    "Ctrl + F" to "Arama kutusuna git",
    "Sağ / Sol" to "5 saniye ileri / geri",
    "Ctrl + Sağ / Sol" to "Sonraki / önceki parça",
    "Ctrl + Yukarı / Aşağı" to "Sesi aç / kıs",
    "Ctrl + M" to "Sessiz",
    "Ctrl + L" to "Çalan şarkıyı beğen",
    "Ctrl + S" to "Karıştır",
    "Ctrl + R" to "Tekrar",
    "Ctrl + Q" to "Sırayı aç / kapat",
    "Ctrl + Shift + L" to "Sözleri aç / kapat",
    "F11" to "Tam ekran oynatıcı",
    "Alt + Sol" to "Geri",
    "Esc" to "Açık paneli kapat",
)

/** Window-level shortcuts: they run only for keys that no focused control used. */
internal fun shortcut(model: AppModel, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val player = model.player
    val ctrl = event.isCtrlPressed
    when {
        event.key == Key.Escape -> when {
            model.showPalette -> model.showPalette = false
            model.showNowPlaying -> model.showNowPlaying = false
            player.showLyrics -> player.toggleLyrics()
            player.showQueue -> player.toggleQueue()
            else -> return false
        }
        ctrl && event.key == Key.K -> model.showPalette = !model.showPalette
        model.showPalette || model.showAdd || model.showCreate -> return false
        ctrl && event.key == Key.F -> model.focusSearch++
        event.isAltPressed && event.key == Key.DirectionLeft -> model.back()
        event.key == Key.F11 -> if (player.current != null) model.showNowPlaying = !model.showNowPlaying
        ctrl && event.key == Key.DirectionRight -> player.next()
        ctrl && event.key == Key.DirectionLeft -> player.previous()
        ctrl && event.key == Key.DirectionUp -> player.changeVolume(player.volume + 0.05f)
        ctrl && event.key == Key.DirectionDown -> player.changeVolume(player.volume - 0.05f)
        ctrl && event.key == Key.M -> player.toggleMute()
        ctrl && event.isShiftPressed && event.key == Key.L -> player.toggleLyrics()
        ctrl && event.key == Key.L -> model.likeCurrent()
        ctrl && event.key == Key.S -> player.toggleShuffle()
        ctrl && event.key == Key.R -> player.cycleRepeat()
        ctrl && event.key == Key.Q -> player.toggleQueue()
        ctrl || event.isAltPressed -> return false
        event.key == Key.Spacebar -> player.toggle()
        event.key == Key.DirectionRight -> player.seekBy(5_000)
        event.key == Key.DirectionLeft -> player.seekBy(-5_000)
        else -> return false
    }
    return true
}
