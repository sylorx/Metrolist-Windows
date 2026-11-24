package com.metrolist.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
private fun rememberCatalog(model: AppModel): List<CatalogTrack> {
    val revision = model.libraryTools.revision
    val history = model.history
    val liked = model.liked
    val playlists = model.localPlaylists
    return remember(revision, history, liked, playlists) { libraryCatalog(model.store.library, history) }
}

@Composable
internal fun PersonalLibraryScreen(model: AppModel) {
    val catalog = rememberCatalog(model)
    var tab by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Sana özel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("${catalog.size} parça · Kendi müzik alanın", color = Muted)
            }
            TextButton(onClick = { model.quickOpen = true }) { Text("Hızlı erişim  Ctrl+K") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Kitaplığında ara", "Akıllı listeler", "Kişisel DJ").forEachIndexed { at, label ->
                FilterChip(tab == at, { tab = at }, label = { Text(label) })
            }
        }
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> CatalogSearch(model, catalog)
                1 -> SmartLibrary(model, catalog)
                else -> PersonalDj(model, catalog)
            }
        }
    }
}

@Composable
private fun CatalogSearch(model: AppModel, catalog: List<CatalogTrack>) {
    var query by remember { mutableStateOf(model.query) }
    var scope by remember { mutableStateOf(CatalogScope.All) }
    val results = remember(catalog, query, scope) { searchCatalog(catalog, query, scope) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, { query = it }, label = { Text("Şarkı, sanatçı, albüm veya liste") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CatalogScope.entries.forEach { choice -> FilterChip(scope == choice, { scope = choice }, label = { Text(choice.label) }) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${results.size} sonuç", color = Muted, modifier = Modifier.weight(1f))
            TextButton(onClick = { model.query = query; model.submitSearch() }, enabled = query.isNotBlank()) { Text("YouTube Music'te ara") }
            TextButton(onClick = { model.player.playAll(results.map { it.track.toTrack() }, 0) }, enabled = results.isNotEmpty()) { Text("Sonuçları çal") }
        }
        val lists = remember(model.localPlaylists, query) {
            if (query.isBlank()) emptyList() else model.localPlaylists.filter { searchText(query) in searchText(it.title) }.take(4)
        }
        if (lists.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            lists.forEach { list -> SuggestionChip(onClick = { model.openLocal(list.id) }, label = { Text("Liste: ${list.title}") }) }
        }
        if (results.isEmpty()) EmptyLibraryHint(catalog.isEmpty())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            itemsIndexed(results, key = { _, item -> item.track.id }) { at, item ->
                CatalogRow(model, item, sourceDescription(item)) { model.player.playAll(results.map { it.track.toTrack() }, at) }
            }
        }
    }
}

private fun sourceDescription(item: CatalogTrack): String = buildList {
    if (item.liked) add("Beğenilen")
    if (item.track.localPath != null) add("Yerel")
    if (item.playlists.isNotEmpty()) add("${item.playlists.size} liste")
    if (item.playCount > 0) add("${item.playCount} dinleme")
}.joinToString(" · ")

@Composable
private fun CatalogRow(model: AppModel, item: CatalogTrack, detail: String, onPlay: () -> Unit) {
    val active = model.player.current?.id == item.track.id
    Row(Modifier.fillMaxWidth().background(if (active) Accent.copy(alpha = 0.12f) else Panel, RoundedCornerShape(10.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).clickable(onClick = onPlay), verticalAlignment = Alignment.CenterVertically) {
            Cover(item.track.thumbnail, Modifier.size(46.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(item.track.title, color = if (active) Accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.track.artists, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (detail.isNotBlank()) Text(detail, color = Accent, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(if (item.track.durationMs > 0) formatTime(item.track.durationMs) else "—:—", color = Muted)
        }
        TrackActions(model, listOf(item.track.toTrack()))
    }
}

@Composable
private fun EmptyLibraryHint(empty: Boolean) {
    Text(if (empty) "Bir müzik klasörü ekle, şarkı beğen veya dinlemeye başla. Kitaplığın burada birleşecek." else "Eşleşen parça yok. Arama veya kuralları değiştirebilirsin.", color = Muted)
}

@Composable
private fun SmartLibrary(model: AppModel, catalog: List<CatalogTrack>) {
    val revision = model.libraryTools.revision
    val lists = remember(revision) { model.store.library.smartPlaylists }
    var selected by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SmartPlaylist?>(null) }
    var deleting by remember { mutableStateOf<SmartPlaylist?>(null) }
    val list = lists.find { it.id == selected }
    val matches = remember(list, catalog) { list?.let { evaluateSmart(it, catalog) }.orEmpty() }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Kuralların değiştikçe listelerin de değişir.", color = Muted, modifier = Modifier.weight(1f))
            Button(onClick = { editing = SmartPlaylist() }) { Text("Yeni akıllı liste") }
        }
        if (lists.isEmpty()) Text("Örneğin: beğenilmiş + en az 30 gündür dinlenmemiş. Önizlemeyi görüp kaydet.", color = Muted)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(lists, key = { it.id }) { saved ->
                Row(Modifier.fillMaxWidth().background(if (list?.id == saved.id) Accent.copy(alpha = 0.12f) else Panel, RoundedCornerShape(10.dp)).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { selected = saved.id }.padding(6.dp)) {
                        Text(saved.title, fontWeight = FontWeight.SemiBold)
                        Text("${saved.conditions.size} kural · ${if (saved.matchAll) "Tümü" else "Herhangi biri"} · En fazla ${saved.limit} parça", color = Muted)
                    }
                    TextButton(onClick = { selected = saved.id }) { Text("Aç") }
                    TextButton(onClick = { editing = saved }) { Text("Düzenle") }
                    TextButton(onClick = { deleting = saved }) { Text("Sil") }
                }
            }
            if (list != null) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(list.title, style = MaterialTheme.typography.titleLarge)
                            Text("${matches.size} parça · ${formatTime(matches.sumOf { it.track.durationMs })}", color = Muted)
                        }
                        Button(onClick = { model.player.playAll(matches.map { it.track.toTrack() }, 0) }, enabled = matches.isNotEmpty()) { Text("Çal") }
                        TextButton(onClick = {
                            val id = model.libraryTools.createPlaylist(list.title, matches.map { it.track })
                            model.openLocal(id)
                        }, enabled = matches.isNotEmpty()) { Text("Normal listeye kaydet") }
                    }
                }
                if (matches.isEmpty()) item { EmptyLibraryHint(catalog.isEmpty()) }
                itemsIndexed(matches, key = { _, item -> "match-${item.track.id}" }) { at, item ->
                    CatalogRow(model, item, sourceDescription(item)) { model.player.playAll(matches.map { it.track.toTrack() }, at) }
                }
            }
        }
    }
    editing?.let { initial -> SmartEditor(initial, catalog, onDismiss = { editing = null }) { saved ->
        model.store.saveSmartPlaylist(saved); model.libraryTools.changed(); selected = saved.id; editing = null
    } }
    deleting?.let { target -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Akıllı liste silinsin mi?") },
        text = { Text("${target.title} silinecek. Şarkı dosyaları korunur.") },
        confirmButton = { TextButton(onClick = { model.store.deleteSmartPlaylist(target.id); model.libraryTools.changed(); deleting = null }) { Text("Sil") } },
        dismissButton = { TextButton(onClick = { deleting = null }) { Text("Vazgeç") } }) }
}

@Composable
private fun <T> ChoiceMenu(value: T, options: List<T>, label: (T) -> String, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Text(label(value)) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(label(option)) }, onClick = { onChange(option); expanded = false }) }
        }
    }
}

@Composable
private fun SmartEditor(initial: SmartPlaylist, catalog: List<CatalogTrack>, onDismiss: () -> Unit, onSave: (SmartPlaylist) -> Unit) {
    var draft by remember(initial.id) { mutableStateOf(initial) }
    var limit by remember { mutableStateOf(initial.limit.toString()) }
    var minutes by remember { mutableStateOf(initial.maxMinutes.toString()) }
    var failure by remember { mutableStateOf<String?>(null) }
    val candidate = draft.copy(limit = limit.toIntOrNull() ?: 0, maxMinutes = minutes.toIntOrNull() ?: -1)
    val matches = remember(candidate, catalog) { evaluateSmart(candidate, catalog) }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.widthIn(max = 800.dp),
        title = { Text("Akıllı liste oluştur") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(draft.title, { draft = draft.copy(title = it.take(100)) }, label = { Text("Liste adı") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(draft.matchAll, { draft = draft.copy(matchAll = true) }, label = { Text("Tüm kurallar") })
                    FilterChip(!draft.matchAll, { draft = draft.copy(matchAll = false) }, label = { Text("Herhangi biri") })
                }
                draft.conditions.forEachIndexed { at, condition ->
                    fun change(value: SmartCondition) { draft = draft.copy(conditions = draft.conditions.toMutableList().apply { this[at] = value }) }
                    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(8.dp)).padding(8.dp)) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ChoiceMenu(condition.field, RuleField.entries, { it.label }) { field -> change(SmartCondition(field, field.operators().first())) }
                            ChoiceMenu(condition.operator, condition.field.operators(), { it.label }) { change(condition.copy(operator = it)) }
                            TextButton(onClick = { draft = draft.copy(conditions = draft.conditions.filterIndexed { index, _ -> index != at }) }, enabled = draft.conditions.size > 1) { Text("Kaldır") }
                        }
                        if (condition.operator !in listOf(RuleOperator.Yes, RuleOperator.No, RuleOperator.Unknown)) {
                            OutlinedTextField(condition.value, { change(condition.copy(value = it.take(200))) }, label = { Text("Değer") }, singleLine = true,
                                isError = !condition.valid(), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
                TextButton(onClick = { draft = draft.copy(conditions = draft.conditions + SmartCondition()) }, enabled = draft.conditions.size < 12) { Text("+ Kural ekle") }
                ChoiceMenu(draft.order, SmartOrder.entries, { it.label }) { draft = draft.copy(order = it) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(limit, { limit = it.take(3) }, label = { Text("Parça sınırı (1–500)") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(minutes, { minutes = it.take(4) }, label = { Text("Dakika sınırı (0: sınırsız)") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                if (!candidate.valid()) Text("Bir ad gir; kuralları ve sınırları kontrol et.", color = Muted)
                Text("Önizleme: ${matches.size} parça · ${formatTime(matches.sumOf { it.track.durationMs })}", color = Accent)
                matches.take(5).forEach { Text("${it.track.title} · ${it.track.artists}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text("Süre bilinmiyorsa dakika sınırı olan listeye alınmaz. Eski geçmiş kayıtlarında süre bilgisi olmayabilir.", color = Muted, style = MaterialTheme.typography.bodySmall)
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { runCatching { onSave(candidate) }.onFailure { failure = "Liste kaydedilemedi. Disk erişimini kontrol et." } }, enabled = candidate.valid()) { Text("Kaydet") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } })
}

@Composable
private fun PersonalDj(model: AppModel, catalog: List<CatalogTrack>) {
    var mode by remember { mutableStateOf(DjMode.Balanced) }
    var seed by remember { mutableStateOf(0) }
    var limit by remember { mutableStateOf(25) }
    var excluded by remember { mutableStateOf(emptySet<String>()) }
    val blocks = model.blocks
    val picks = remember(catalog, blocks, mode, seed, limit, excluded) { personalMix(catalog.filterNot { matchesBlock(it.track.toTrack(), blocks) }, mode, limit, seed, exclude = excluded) }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Kitaplığından senin için bir karışım", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Beğenilerin ve bu bilgisayardaki geçmişin kullanılır. Parçaların neden seçildiğini aşağıda görebilirsin.", color = Muted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DjMode.entries.forEach { choice -> FilterChip(mode == choice, { mode = choice }, label = { Text(choice.label) }) }
            ChoiceMenu(limit, listOf(10, 25, 50), { "$it parça" }) { limit = it }
        }
        Text(mode.description, color = Accent)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { model.player.playAll(picks.map { it.item.track.toTrack() }, 0) }, enabled = picks.isNotEmpty()) { Text("Karışımı çal") }
            TextButton(onClick = { seed++ }) { Text("Yenile") }
            TextButton(onClick = { model.player.enqueue(picks.map { it.item.track.toTrack() }); model.status = "Karışım sıraya eklendi" }, enabled = picks.isNotEmpty()) { Text("Sıraya ekle") }
            TextButton(onClick = {
                val id = model.libraryTools.createPlaylist("DJ · ${mode.label}", picks.map { it.item.track })
                model.openLocal(id)
            }, enabled = picks.isNotEmpty()) { Text("Liste olarak kaydet") }
            if (excluded.isNotEmpty()) TextButton(onClick = { excluded = emptySet() }) { Text("Gizlenenleri geri getir (${excluded.size})") }
        }
        Text("${picks.size} parça · ${formatTime(picks.sumOf { it.item.track.durationMs })} · Aynı sanatçıya ara verilerek sıralanır", color = Muted)
        if (picks.isEmpty()) EmptyLibraryHint(catalog.isEmpty())
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            itemsIndexed(picks, key = { _, pick -> pick.item.track.id }) { at, pick ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { CatalogRow(model, pick.item, pick.reason) { model.player.playAll(picks.map { it.item.track.toTrack() }, at) } }
                    TextButton(onClick = { excluded = excluded + pick.item.track.id }) { Text("Bu karışımda gizle") }
                }
            }
        }
    }
}
