package com.metrolist.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.system.exitProcess
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage

private val Ink: Color
    @Composable get() = LocalPalette.current.ink
private val Panel: Color
    @Composable get() = LocalPalette.current.panel
private val Card: Color
    @Composable get() = LocalPalette.current.card
private val Accent: Color
    @Composable get() = LocalPalette.current.accent
private val Muted: Color
    @Composable get() = LocalPalette.current.muted
private val LocalPlayingId = staticCompositionLocalOf<String?> { null }

@Composable
internal fun MetrolistTheme(look: Look, content: @Composable () -> Unit) {
    val palette = look.palette
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = darkColorScheme(
                primary = palette.accent,
                background = palette.ink,
                surface = palette.panel,
                onBackground = Color(0xFFF4F4F5),
                onSurface = Color(0xFFF4F4F5),
            ),
            content = content,
        )
    }
}

@Composable
fun MetrolistApp(model: AppModel) {
    val player = model.player
    LaunchedEffect(Unit) {
        model.openRail(Dest.Home)
        while (true) {
            kotlinx.coroutines.delay(300)
            model.player.poll()
        }
    }

    val playingId = model.player.current?.id
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Rail(model)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                CompositionLocalProvider(LocalPlayingId provides playingId) {
                    Content(model, Modifier.fillMaxSize())
                }
            }
            if (player.showQueue) QueuePanel(model, Modifier.width(320.dp).fillMaxHeight())
        }
        if (player.showLyrics) LyricsStrip(player)
        UpdateNotice(model)
        PlayerBar(model)
    }

    if (model.showAdd) {
        AlertDialog(
            onDismissRequest = { model.showAdd = false },
            title = { Text("Listeye ekle") },
            text = {
                LazyColumn(Modifier.height(240.dp)) {
                    items(model.playlistsForAdd, key = { it.id }) { playlist ->
                        Text(
                            playlist.title,
                            modifier = Modifier.fillMaxWidth().clickable { model.addCurrentTo(playlist.id) }.padding(10.dp),
                        )
                    }
                    if (model.playlistsForAdd.isEmpty()) {
                        item { Text("Kayıtlı liste bulunamadı", color = Muted) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { model.showAdd = false }) { Text("Kapat") } },
        )
    }
}

@Composable
private fun Rail(model: AppModel) {
    Column(
        Modifier.width(92.dp).fillMaxHeight().background(Panel).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("M", color = Accent, fontWeight = FontWeight.Black, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        RailButton("Ana sayfa", Icons.Default.Home, model.current is Dest.Home) { model.openRail(Dest.Home) }
        RailButton("Ara", Icons.Default.Search, model.current is Dest.Search) { model.openRail(Dest.Search) }
        RailButton("Kitaplık", Icons.Default.LibraryMusic, model.current is Dest.Library) { model.openRail(Dest.Library) }
        RailButton("Geçmiş", Icons.Default.History, model.current is Dest.History) { model.openRail(Dest.History) }
        RailButton("Keşfet", Icons.Default.GraphicEq, model.current is Dest.Explore) { model.openRail(Dest.Explore) }
        Spacer(Modifier.weight(1f))
        RailButton("Ayarlar", Icons.Default.Settings, model.current is Dest.Settings) { model.openRail(Dest.Settings) }
    }
}

@Composable
private fun RailButton(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.06f else 1f, tween(160), label = "rail")
    val background by animateColorAsState(
        when {
            selected -> Card
            hovered -> Card.copy(alpha = 0.65f)
            else -> Color.Transparent
        },
        tween(160),
        label = "rail-bg",
    )
    Column(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(LocalPalette.current.corner))
            .background(background)
            .clickable(interaction, ripple(color = Accent), onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (selected) Accent else Muted)
        Text(label, color = if (selected) Color.White else Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun Content(model: AppModel, modifier: Modifier) {
    Box(modifier.padding(24.dp)) {
        AnimatedContent(
            targetState = model.current,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(140)) },
            label = "sayfa",
        ) { dest ->
            when (dest) {
                Dest.Home -> HomeScreen(model)
                Dest.Search -> SearchScreen(model)
                Dest.Library -> LibraryScreen(model)
                Dest.History -> HistoryScreen(model)
                Dest.Explore -> ExploreScreen(model)
                Dest.Settings -> SettingsScreen(model)
                is Dest.Album -> AlbumScreen(model)
                is Dest.Artist -> ArtistScreen(model)
                is Dest.Playlist -> PlaylistScreen(model)
                is Dest.Collection -> CollectionScreen(model, dest.title)
            }
        }
        if (model.loading) {
            CircularProgressIndicator(Modifier.align(Alignment.TopEnd), color = Accent)
        }
    }
}

@Composable
private fun PageHeader(title: String, model: AppModel, extra: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (model.stack.size > 1) {
            TextButton(onClick = model::back) { Text("Geri") }
        }
        Column {
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            extra?.let { Text(it, color = Muted) }
        }
    }
    model.error?.let { Text(it, color = Accent, modifier = Modifier.padding(top = 8.dp)) }
    model.status?.let { Text(it, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun HomeScreen(model: AppModel) {
    val page = model.home
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { PageHeader("Ana sayfa", model) }
        page?.chips.orEmpty().takeIf { it.isNotEmpty() }?.let { chips ->
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(chips) { chip ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val endpoint = chip.endpoint
                                if (endpoint?.browseId != null) {
                                    model.open(Dest.Collection(endpoint.browseId, endpoint.params, chip.title))
                                } else {
                                    model.openRail(Dest.Home)
                                }
                            },
                            label = { Text(chip.title) },
                        )
                    }
                }
            }
        }
        items(page?.sections.orEmpty(), key = { it.title + it.items.size }) { section ->
            SectionRow(section, model)
        }
        if (model.homeContinuation != null) {
            item { Button(onClick = model::loadMoreHome) { Text("Daha fazla") } }
        }
    }
}

@Composable
private fun SectionRow(section: HomePage.Section, model: AppModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            section.endpoint?.let { endpoint ->
                TextButton(onClick = { model.open(Dest.Collection(endpoint.browseId, endpoint.params, section.title)) }) {
                    Text("Tümü")
                }
            }
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(section.items, key = { it.id + it.title }) { item -> MediaCard(item, model) }
        }
    }
}

@Composable
private fun Cover(
    url: String?,
    modifier: Modifier,
    description: String? = null,
) {
    val palette = LocalPalette.current
    val source = remember(url) { coverUrl(url) }
    Box(modifier.clip(RoundedCornerShape(palette.corner)).background(palette.card)) {
        if (source != null) {
            AsyncImage(
                model = source,
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun MediaCard(item: YTItem, model: AppModel) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else if (hovered) 1.035f else 1f, tween(170), label = "card")
    Column(
        Modifier
            .width(palette.cardWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(palette.corner))
            .background(palette.card)
            .border(
                1.dp,
                if (LocalPlayingId.current == item.id) palette.accent else Color.White.copy(alpha = if (hovered) 0.14f else 0.05f),
                RoundedCornerShape(palette.corner),
            )
            .clickable(interaction, ripple(color = palette.accent)) { model.openItem(item) }
            .padding(10.dp),
    ) {
        Box {
            Cover(item.thumbnail, Modifier.fillMaxWidth().height(palette.cardWidth * 0.86f), item.title)
            if (hovered) {
                Surface(shape = CircleShape, color = palette.accent, modifier = Modifier.align(Alignment.Center)) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Çal", tint = Color.White, modifier = Modifier.padding(8.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        Text(itemSubtitle(item), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SearchScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Ara", model)
        OutlinedTextField(
            value = model.query,
            onValueChange = model::suggest,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Şarkı, albüm veya sanatçı") },
            singleLine = true,
            shape = RoundedCornerShape(LocalPalette.current.corner),
            trailingIcon = { SoftIcon(onClick = { model.search() }) { Icon(Icons.Default.Search, contentDescription = "Ara") } },
        )
        if (model.suggestions.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.suggestions.forEach { suggestion ->
                    SuggestionChip(onClick = { model.search(suggestion) }, label = { Text(suggestion) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                "Şarkılar" to YouTube.SearchFilter.FILTER_SONG,
                "Albümler" to YouTube.SearchFilter.FILTER_ALBUM,
                "Sanatçılar" to YouTube.SearchFilter.FILTER_ARTIST,
                "Listeler" to YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST,
            ).forEach { (label, filter) ->
                FilterChip(
                    selected = model.searchFilter == filter,
                    onClick = {
                        model.searchFilter = filter
                        if (model.query.isNotBlank()) model.search()
                    },
                    label = { Text(label) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (model.query.isBlank() && model.searchItems.isEmpty()) {
            Text("Bir şarkı yaz, öneriler chip olarak gelsin.", color = Muted)
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(model.searchItems, key = { index, item -> item.id + index }) { _, item ->
                val duration = (item as? SongItem)?.duration?.takeIf { it > 0 }?.let { formatTime(it * 1000L) }
                SongRow(item.title, itemSubtitle(item), item.thumbnail, duration, item.id) { model.openItem(item) }
            }
            if (model.searchContinuation != null) {
                item { Button(onClick = model::loadMoreSearch) { Text("Daha fazla") } }
            }
        }
    }
}

@Composable
private fun LibraryScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Kitaplık", model, if (model.store.settings.cookie.isBlank()) "Giriş yapılmadı" else null)
        if (model.store.settings.cookie.isBlank()) {
            Text("YouTube Music kitaplığın için Ayarlar’dan çerez yapıştır.", color = Muted)
            return
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LibraryTab.entries.forEach { tab ->
                FilterChip(selected = model.libraryTab == tab, onClick = { model.selectLibrary(tab) }, label = { Text(tab.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = model.newPlaylistTitle,
                onValueChange = { model.newPlaylistTitle = it },
                placeholder = { Text("Yeni liste adı") },
                singleLine = true,
                modifier = Modifier.width(280.dp),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = model::createPlaylist) { Text("Oluştur") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(model.libraryItems, key = { it.id + it.title }) { item ->
                SongRow(item.title, itemSubtitle(item), item.thumbnail) { model.openItem(item) }
            }
            if (model.libraryContinuation != null) {
                item { Button(onClick = model::loadMoreLibrary) { Text("Daha fazla") } }
            }
        }
    }
}

@Composable
private fun HistoryScreen(model: AppModel) {
    val top = model.history.groupBy { it.id }.map { (_, plays) -> plays.first() to plays.size }
        .sortedByDescending { it.second }
        .take(8)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { PageHeader("Geçmiş", model) }
        if (top.isNotEmpty()) {
            item { Text("En çok çalınanlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(top, key = { it.first.id }) { (entry, count) ->
                        PlayedCard(entry, count) {
                            model.player.playAll(listOf(Track(entry.id, entry.title, entry.artists, entry.thumbnail, 0)), 0)
                        }
                    }
                }
            }
        }
        item { Text("Bu bilgisayar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (model.history.isEmpty()) {
            item { Text("Henüz bir şey çalınmadı", color = Muted) }
        }
        items(model.history.take(40), key = { it.id + it.playedAt }) { entry ->
            SongRow(entry.title, entry.artists, entry.thumbnail, id = entry.id) {
                model.player.playAll(listOf(Track(entry.id, entry.title, entry.artists, entry.thumbnail, 0)), 0)
            }
        }
        if (model.remoteHistory.isNotEmpty()) {
            item { Text("YouTube Music geçmişi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp)) }
        }
        model.remoteHistory.forEach { section ->
            item { Text(section.title, color = Muted) }
            items(section.songs, key = { section.title + it.id }) { song ->
                SongRow(song.title, song.artists.joinToString { it.name }, song.thumbnail) {
                    model.playSongs(section.songs, section.songs.indexOf(song))
                }
            }
        }
    }
}

@Composable
private fun PlayedCard(entry: HistoryEntry, count: Int, onClick: () -> Unit) {
    val palette = LocalPalette.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.04f else 1f, tween(160), label = "played")
    Column(
        Modifier
            .width(148.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(palette.corner))
            .background(palette.card)
            .border(1.dp, Color.White.copy(alpha = if (hovered) 0.14f else 0.05f), RoundedCornerShape(palette.corner))
            .clickable(interaction, ripple(color = palette.accent), onClick = onClick)
            .padding(10.dp),
    ) {
        Cover(entry.thumbnail, Modifier.fillMaxWidth().height(112.dp), entry.title)
        Spacer(Modifier.height(8.dp))
        Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
        Text("$count kez", color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExploreScreen(model: AppModel) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader("Keşfet", model) }
        if (model.releases.isNotEmpty()) {
            item { Text("Yeni çıkanlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(model.releases, key = { it.browseId }) { album -> MediaCard(album, model) }
                }
            }
        }
        model.charts?.sections.orEmpty().forEach { section ->
            item { Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(section.items, key = { section.title + it.id }) { item -> MediaCard(item, model) }
                }
            }
        }
        if (model.moods.isNotEmpty()) {
            item { Text("Ruh hali ve türler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        }
        model.moods.forEach { group ->
            item { Text(group.title, color = Muted) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(group.items, key = { it.title + it.endpoint.browseId }) { mood ->
                        MoodChip(mood.title, Color(mood.stripeColor)) {
                            model.open(Dest.Collection(mood.endpoint.browseId, mood.endpoint.params, mood.title))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoodChip(title: String, color: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.06f else 1f, tween(150), label = "mood")
    Surface(
        color = color.copy(alpha = if (hovered) 1f else 0.82f),
        shape = RoundedCornerShape(LocalPalette.current.corner),
        modifier = Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(interaction, ripple(color = Color.White), onClick = onClick),
    ) {
        Text(title, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AlbumScreen(model: AppModel) {
    val page = model.album
    LazyColumn {
        item { PageHeader(page?.album?.title ?: "Albüm", model, page?.album?.artists?.joinToString { it.name }) }
        item {
            Row {
                Cover(page?.album?.thumbnail, Modifier.size(160.dp))
                Spacer(Modifier.width(16.dp))
                if (page != null) {
                    Button(onClick = { model.playSongs(page.songs, 0) }) { Text("Çal") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        itemsIndexed(page?.songs.orEmpty()) { index, song ->
            SongRow(song.title, song.artists.joinToString { it.name }, song.thumbnail, formatTime((song.duration ?: 0) * 1000L)) {
                page?.let { model.playSongs(it.songs, index) }
            }
        }
    }
}

@Composable
private fun ArtistScreen(model: AppModel) {
    val page = model.artist
    LazyColumn(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            PageHeader(page?.artist?.title ?: "Sanatçı", model, page?.subscriberCountText ?: page?.monthlyListenerCount)
            page?.description?.let { Text(it, color = Muted, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        }
        page?.sections.orEmpty().forEach { section ->
            item { Text(section.title, fontWeight = FontWeight.SemiBold) }
            item {
                val songs = section.items.filterIsInstance<SongItem>()
                if (songs.isNotEmpty()) {
                    Column {
                        songs.forEachIndexed { index, song ->
                            SongRow(song.title, song.artists.joinToString { it.name }, song.thumbnail) {
                                model.playSongs(songs, index)
                            }
                        }
                    }
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(section.items, key = { section.title + it.id }) { item -> MediaCard(item, model) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistScreen(model: AppModel) {
    val page = model.playlist
    LazyColumn {
        item {
            PageHeader(page?.playlist?.title ?: "Çalma listesi", model, page?.playlist?.author?.name)
            if (page != null) Button(onClick = { model.playSongs(page.songs, 0) }) { Text("Çal") }
            Spacer(Modifier.height(8.dp))
        }
        itemsIndexed(page?.songs.orEmpty()) { index, song ->
            SongRow(song.title, song.artists.joinToString { it.name }, song.thumbnail, formatTime((song.duration ?: 0) * 1000L)) {
                page?.let { model.playSongs(it.songs, index) }
            }
        }
        if (model.playlistContinuation != null) {
            item { Button(onClick = model::loadMorePlaylist) { Text("Daha fazla") } }
        }
    }
}

@Composable
private fun CollectionScreen(model: AppModel, title: String) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { PageHeader(model.collection?.title ?: title, model) }
        model.collection?.items.orEmpty().forEach { group ->
            group.title?.let { item { Text(it, fontWeight = FontWeight.SemiBold) } }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(group.items, key = { (group.title ?: "") + it.id }) { media -> MediaCard(media, model) }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(model: AppModel) {
    val look = model.look
    val palette = look.palette
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PageHeader("Ayarlar", model, model.accountName?.let { "Hesap: $it" } ?: "Oturum yok, çalma açık")
        Text("Görünüm", fontWeight = FontWeight.SemiBold)
        Text("Vurgu rengi", color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            accentChoices.forEach { choice ->
                val selected = look.accentId == choice.id
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(choice.color)
                        .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape)
                        .clickable { model.saveLook(accent = choice.id) },
                )
            }
        }
        Text("Panel", color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            surfaceChoices.forEach { choice ->
                val selected = look.surfaceId == choice.id
                Column(
                    Modifier.clickable { model.saveLook(surface = choice.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(palette.corner))
                            .background(choice.ink)
                            .border(
                                2.dp,
                                if (selected) palette.accent else Color.Transparent,
                                RoundedCornerShape(palette.corner),
                            )
                            .padding(7.dp),
                    ) {
                        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(palette.corner / 2)).background(choice.card))
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(choice.label, color = if (selected) Color.White else Muted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("Köşeler", color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cornerChoices.forEach { (id, label) ->
                FilterChip(
                    selected = look.cornerId == id,
                    onClick = { model.saveLook(corners = id) },
                    label = { Text(label) },
                )
            }
        }
        Text("Kart boyutu", color = Muted, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cardChoices.forEach { (id, label) ->
                FilterChip(
                    selected = look.cardId == id,
                    onClick = { model.saveLook(cards = id) },
                    label = { Text(label) },
                )
            }
        }
        Surface(color = palette.panel, shape = RoundedCornerShape(palette.corner), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(palette.corner)).background(palette.accent))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Önizleme", fontWeight = FontWeight.Medium)
                    Text("Vurgu, panel ve köşe birlikte uygulanır", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Şarkılar oturum olmadan ücretsiz bir akış servisiyle çalar. Kitaplık, beğeni ve liste için music.youtube.com çerezini yapıştır.", color = Muted)
        OutlinedTextField(
            value = model.cookieDraft,
            onValueChange = { model.cookieDraft = it },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            placeholder = { Text("Cookie") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = model::saveCookie) { Text("Kaydet") }
            TextButton(onClick = {
                model.cookieDraft = ""
                model.saveCookie()
            }) { Text("Çıkış") }
        }
        Text("Ses kalitesi", fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("AUTO" to "Otomatik", "HIGH" to "Yüksek", "LOW" to "Düşük").forEach { (id, label) ->
                FilterChip(
                    selected = model.quality == id,
                    onClick = { model.saveQuality(id) },
                    label = { Text(label) },
                )
            }
        }
        Text("Güncelleme", fontWeight = FontWeight.SemiBold)
        Text("Yüklü sürüm ${AppVersion.NAME}. ${model.updates.message}", color = Muted)
        model.updates.notes?.takeIf { model.updates.pending != null }?.let {
            Text(it, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = model.updates::requestCheck, enabled = !model.updates.checking && !model.updates.downloading) {
                Text(if (model.updates.checking) "Kontrol ediliyor" else "Güncellemeleri kontrol et")
            }
            if (model.updates.pending != null) {
                Button(onClick = { model.updates.install { model.player.stop(); exitProcess(0) } }, enabled = !model.updates.downloading) {
                    Text(if (model.updates.downloading) "İndiriliyor %${(model.updates.progress * 100).toInt()}" else "Güncelle")
                }
            }
        }
        Text(
            "Metrolist Windows. Telefon widget’ları, Android Auto, Cast, ekolayzır ve Listen Together bu sürümde yok.",
            color = Muted,
        )
    }
}

@Composable
private fun SongRow(
    title: String,
    subtitle: String,
    thumbnail: String?,
    trailing: String? = null,
    id: String? = null,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val playing = id != null && id == LocalPlayingId.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        when {
            playing -> palette.accent.copy(alpha = 0.16f)
            pressed -> palette.card
            hovered -> palette.card.copy(alpha = 0.85f)
            else -> Color.White.copy(alpha = 0.035f)
        },
        tween(140),
        label = "row",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(palette.corner))
            .background(background)
            .clickable(interaction, ripple(color = palette.accent), onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(thumbnail, Modifier.size(52.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if (playing) palette.accent else Color.Unspecified)
            Text(subtitle, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
        if (playing) {
            Icon(Icons.Default.VolumeUp, contentDescription = "Çalıyor", tint = palette.accent, modifier = Modifier.padding(end = 8.dp).size(16.dp))
        }
        AnimatedVisibility(hovered && !playing) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = palette.accent)
        }
        trailing?.let { Text(it, color = Muted, modifier = Modifier.padding(start = 8.dp)) }
    }
}

@Composable
private fun SoftIcon(
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered && enabled) 1.12f else 1f, tween(140), label = "icon")
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interaction,
        modifier = Modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
    ) { content() }
}

@Composable
private fun QueuePanel(model: AppModel, modifier: Modifier) {
    Surface(modifier, color = Panel) {
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            item { Text("Sıra", fontWeight = FontWeight.SemiBold) }
            itemsIndexed(model.player.queue) { index, track ->
                val playing = index == model.player.index
                Text(
                    track.title,
                    color = if (playing) Accent else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { model.player.jumpTo(index) }.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricsStrip(player: PlaybackController) {
    Text(
        player.lyrics ?: "Söz yok",
        modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 24.dp, vertical = 10.dp).height(72.dp),
        color = Muted,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun UpdateNotice(model: AppModel) {
    val updates = model.updates
    val pending = updates.pending ?: return
    Row(
        Modifier.fillMaxWidth().background(Accent).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Yeni sürüm $pending hazır", color = Color.White, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
        if (updates.downloading) {
            Text("%${(updates.progress * 100).toInt()}", color = Color.White)
        } else {
            Button(
                onClick = { updates.install { model.player.stop(); exitProcess(0) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
            ) { Text("Güncelle") }
            TextButton(onClick = updates::dismiss) { Text("Sonra", color = Color.White) }
        }
    }
}

@Composable
private fun SeekBar(positionMs: Long, durationMs: Long, onSeek: (Long) -> Unit) {
    var drag by remember { mutableStateOf<Float?>(null) }
    val fraction = drag ?: if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width * durationMs).toLong().coerceIn(0, durationMs))
                }
            }
            .pointerInput(durationMs) {
                if (durationMs <= 0) return@pointerInput
                detectDragGestures(
                    onDragEnd = {
                        drag?.let { onSeek((it * durationMs).toLong().coerceIn(0, durationMs)) }
                        drag = null
                    },
                    onDragCancel = { drag = null },
                ) { change, _ ->
                    drag = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            },
    ) {
        val width = maxWidth * fraction
        Box(Modifier.align(Alignment.CenterStart).fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)))
        Box(Modifier.align(Alignment.CenterStart).width(width).height(4.dp).clip(CircleShape).background(Accent))
        if (durationMs > 0) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = (width - 6.dp).coerceAtLeast(0.dp))
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun PlayerBar(model: AppModel) {
    val player = model.player
    val track = player.current
    val known = when {
        player.durationMs > 1_000 -> player.durationMs
        (track?.durationMs ?: 0L) > 1_000 -> track?.durationMs ?: 0L
        else -> 0L
    }
    Surface(color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (player.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
            player.error?.let { Text(it, color = Accent, maxLines = 1) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Cover(track?.thumbnail, Modifier.size(64.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(track?.title ?: "Çalmıyor", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(track?.artists.orEmpty().ifBlank { " " }, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Column(Modifier.weight(1.3f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SoftIcon(onClick = player::toggleShuffle) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Karıştır", tint = if (player.shuffle) Accent else Muted)
                        }
                        SoftIcon(onClick = player::previous, enabled = track != null) { Icon(Icons.Default.SkipPrevious, contentDescription = "Önceki") }
                        Surface(shape = CircleShape, color = Color.White, modifier = Modifier.size(42.dp).padding(2.dp)) {
                            SoftIcon(onClick = player::toggle, enabled = track != null) {
                                Icon(
                                    if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Oynat",
                                    tint = Color.Black,
                                )
                            }
                        }
                        SoftIcon(onClick = player::next, enabled = track != null) { Icon(Icons.Default.SkipNext, contentDescription = "Sonraki") }
                        SoftIcon(onClick = player::cycleRepeat) {
                            Icon(
                                if (player.repeat == RepeatMode.One) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = "Tekrar",
                                tint = if (player.repeat == RepeatMode.Off) Muted else Accent,
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(formatTime(player.positionMs), color = Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
                        SeekBar(player.positionMs, known) { player.seek(it) }
                        Text(
                            if (known > 0) formatTime(known) else "—:—",
                            color = Muted,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(40.dp).padding(start = 6.dp),
                        )
                    }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    SoftIcon(onClick = model::likeCurrent, enabled = track != null) { Icon(Icons.Default.Favorite, contentDescription = "Beğen") }
                    SoftIcon(onClick = model::prepareAdd, enabled = track != null) { Icon(Icons.Default.PlaylistAdd, contentDescription = "Ekle") }
                    SoftIcon(onClick = player::toggleLyrics, enabled = track != null) { Icon(Icons.Default.GraphicEq, contentDescription = "Sözler") }
                    SoftIcon(onClick = player::toggleQueue) { Icon(Icons.Default.QueueMusic, contentDescription = "Sıra") }
                    Icon(Icons.Default.VolumeUp, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                    Slider(
                        value = player.volume,
                        onValueChange = player::changeVolume,
                        modifier = Modifier.width(100.dp),
                    )
                }
            }
        }
    }
}
