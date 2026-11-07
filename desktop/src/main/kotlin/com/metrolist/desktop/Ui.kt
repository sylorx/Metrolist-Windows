package com.metrolist.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.MusicNote
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
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.system.exitProcess
import coil3.compose.AsyncImage
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.ArtistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.models.YTItem
import com.metrolist.innertube.pages.HomePage

private val Lift = spring<Float>(dampingRatio = 0.72f, stiffness = 420f)
private val Glide = spring<Dp>(dampingRatio = 0.78f, stiffness = 380f)

private data class NowPlaying(val id: String, val title: String, val artists: String, val thumbnail: String?)

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
    LaunchedEffect(model.status) {
        val message = model.status ?: return@LaunchedEffect
        kotlinx.coroutines.delay(2_200)
        if (model.status == message) model.status = null
    }

    val playingId = model.player.current?.id
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val player = model.player
                when {
                    event.key == Key.Spacebar -> {
                        player.toggle()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.DirectionRight -> {
                        player.next()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.DirectionLeft -> {
                        player.previous()
                        true
                    }
                    event.isCtrlPressed && event.key == Key.DirectionUp -> {
                        player.changeVolume(player.volume + 0.05f)
                        true
                    }
                    event.isCtrlPressed && event.key == Key.DirectionDown -> {
                        player.changeVolume(player.volume - 0.05f)
                        true
                    }
                    event.isCtrlPressed && event.key == Key.M -> {
                        player.toggleMute()
                        true
                    }
                    else -> false
                }
            },
    ) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Rail(model)
            Column(Modifier.weight(1f).fillMaxHeight()) {
                TopBar(model)
                CompositionLocalProvider(LocalPlayingId provides playingId) {
                    Content(model, Modifier.fillMaxSize())
                }
            }
            AnimatedVisibility(
                visible = player.showLyrics,
                enter = fadeIn(tween(240)) + expandHorizontally(tween(320)),
                exit = fadeOut(tween(160)) + shrinkHorizontally(tween(240)),
            ) {
                LyricsPanel(player, Modifier.width(380.dp).fillMaxHeight())
            }
            AnimatedVisibility(
                visible = player.showQueue,
                enter = fadeIn(tween(240)) + expandHorizontally(tween(320)),
                exit = fadeOut(tween(160)) + shrinkHorizontally(tween(240)),
            ) {
                QueuePanel(model, Modifier.width(300.dp).fillMaxHeight())
            }
        }
        UpdateNotice(model)
        PlayerBar(model)
    }

    if (model.showAdd) {
        AlertDialog(
            onDismissRequest = { model.showAdd = false },
            title = { Text("Listeye ekle") },
            text = {
                LazyColumn(Modifier.height(240.dp)) {
                    items(model.localPlaylists, key = { it.id }) { playlist ->
                        Text(
                            playlist.title,
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth().clickable { model.addCurrentTo(playlist.id) }.padding(10.dp),
                        )
                    }
                    items(model.playlistsForAdd, key = { it.id }) { playlist ->
                        Text(
                            "${playlist.title} · YouTube",
                            color = Color.White,
                            modifier = Modifier.fillMaxWidth().clickable { model.addCurrentTo(playlist.id) }.padding(10.dp),
                        )
                    }
                    if (model.localPlaylists.isEmpty() && model.playlistsForAdd.isEmpty()) {
                        item { Text("Kayıtlı liste yok. Kitaplık’tan oluştur.", color = Muted) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { model.showAdd = false }) { Text("Kapat") } },
        )
    }
    if (model.showCreate) {
        AlertDialog(
            onDismissRequest = { model.showCreate = false },
            title = { Text("Yeni çalma listesi") },
            text = {
                OutlinedTextField(
                    value = model.newPlaylistTitle,
                    onValueChange = { model.newPlaylistTitle = it },
                    placeholder = { Text("Yeni liste adı") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { Button(onClick = model::createPlaylist) { Text("Oluştur") } },
            dismissButton = { TextButton(onClick = { model.showCreate = false }) { Text("Vazgeç") } },
        )
    }
}

@Composable
private fun Rail(model: AppModel) {
    Column(
        Modifier.width(228.dp).fillMaxHeight().background(Panel).padding(horizontal = 10.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArtOrIcon("icons/logo.jpg", Icons.Default.MusicNote, "Metrolist", Color.White, Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Metrolist", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text("Masaüstü Oynatıcı", color = Muted, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(14.dp))
        NavRow("Ana sayfa", Icons.Default.Home, model.current is Dest.Home, "icons/home.jpg") { model.openRail(Dest.Home) }
        NavRow("Keşfet", Icons.Default.GraphicEq, model.current is Dest.Explore, "icons/explore.jpg") { model.openRail(Dest.Explore) }
        NavRow("Kitaplık", Icons.Default.LibraryMusic, model.current is Dest.Library, "icons/library.jpg") { model.openRail(Dest.Library) }
        NavRow("Geçmiş", Icons.Default.History, model.current is Dest.History, "icons/history.jpg") { model.openRail(Dest.History) }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
        Spacer(Modifier.height(10.dp))
        Text("Kitaplığın", color = Muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        NavRow("Beğenilenler", Icons.Default.Favorite, model.current is Dest.Liked, "icons/heart.jpg") { model.openRail(Dest.Liked) }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(model.localPlaylists, key = { it.id }) { playlist ->
                val open = (model.current as? Dest.LocalList)?.id == playlist.id
                NavRow(playlist.title, Icons.Default.QueueMusic, open, "icons/playlist.jpg") { model.openLocal(playlist.id) }
            }
        }
        Surface(
            color = Color.White.copy(alpha = 0.06f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().clickable { model.showCreate = true },
        ) {
            Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Text("Yeni çalma listesi", color = Color.White, maxLines = 1)
            }
        }
        Spacer(Modifier.height(6.dp))
        NavRow("Ayarlar", Icons.Default.Settings, model.current is Dest.Settings, "icons/settings.jpg") { model.openRail(Dest.Settings) }
    }
}

@Composable
private fun TopBar(model: AppModel) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SoftIcon(onClick = model::back, enabled = model.stack.size > 1) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri", tint = Color.White)
        }
        OutlinedTextField(
            value = model.query,
            onValueChange = model::suggest,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Ne dinlemek istersin?") },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Ara", modifier = Modifier.clickable { model.submitSearch() })
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { model.submitSearch() }),
        )
    }
}

@Composable
private fun ArtOrIcon(path: String?, icon: ImageVector, label: String, tint: Color, modifier: Modifier = Modifier) {
    val image = path?.let { remember(it) { loadImage(it) } }
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = label,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(RoundedCornerShape(8.dp)).graphicsLayer {
                scaleX = 1.34f
                scaleY = 1.34f
                translationY = -size.height * 0.08f
            },
        )
    } else {
        Icon(icon, contentDescription = label, tint = tint, modifier = modifier)
    }
}

@Composable
private fun NavRow(label: String, icon: ImageVector, selected: Boolean, art: String? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> Color.White.copy(alpha = 0.12f)
            hovered -> Color.White.copy(alpha = 0.06f)
            else -> Color.Transparent
        },
        tween(220),
        label = "nav",
    )
    val labelColor by animateColorAsState(if (selected) Color.White else Muted, tween(220), label = "navText")
    val iconScale by animateFloatAsState(if (selected) 1.08f else if (hovered) 1.04f else 1f, Lift, label = "navIcon")
    val rail by animateDpAsState(if (selected) 16.dp else 0.dp, Glide, label = "navRail")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .clickable(interaction, ripple(color = Accent), onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(end = 8.dp)
                .width(3.dp)
                .height(rail)
                .clip(CircleShape)
                .background(Accent),
        )
        ArtOrIcon(art, icon, label, if (selected) Color.White else Muted, Modifier.size(26.dp).graphicsLayer {
            scaleX = iconScale
            scaleY = iconScale
        })
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun Content(model: AppModel, modifier: Modifier) {
    Box(modifier.padding(horizontal = 22.dp, vertical = 6.dp)) {
        AnimatedContent(
            targetState = model.current,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                (fadeIn(tween(280)) + slideInVertically(tween(340)) { it / 16 }) togetherWith
                    (fadeOut(tween(180)) + slideOutVertically(tween(220)) { it / 22 })
            },
            label = "sayfa",
        ) { dest ->
            when (dest) {
                Dest.Home -> HomeScreen(model)
                Dest.Search -> SearchScreen(model)
                Dest.Library -> LibraryScreen(model)
                Dest.History -> HistoryScreen(model)
                Dest.Explore -> ExploreScreen(model)
                Dest.Settings -> SettingsScreen(model)
                Dest.Liked -> SavedTracks(model, "Beğenilen şarkılar", model.liked, "Beğendiğin şarkılar bu bilgisayarda durur.", heart = true)
                Dest.Shortcuts -> ShortcutsScreen(model)
                is Dest.LocalList -> LocalListScreen(model, dest.id)
                is Dest.Album -> AlbumScreen(model)
                is Dest.Artist -> ArtistScreen(model)
                is Dest.Playlist -> PlaylistScreen(model)
                is Dest.Collection -> CollectionScreen(model, dest.title)
            }
        }
        AnimatedVisibility(
            visible = model.loading,
            modifier = Modifier.align(Alignment.TopEnd),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
        ) {
            CircularProgressIndicator(color = Accent)
        }
    }
}

@Composable
private fun PageHeader(title: String, model: AppModel, extra: String? = null) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
        extra?.let { Text(it, color = Muted) }
    }
    model.error?.let { Text(it, color = Accent, modifier = Modifier.padding(top = 8.dp)) }
    model.status?.let { Text(it, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
    Spacer(Modifier.height(16.dp))
}

@Composable
private fun HomeScreen(model: AppModel) {
    val page = model.home
    val hour = java.time.LocalTime.now().hour
    val hello = when (hour) {
        in 5..11 -> "Günaydın"
        in 12..17 -> "İyi günler"
        in 18..22 -> "İyi akşamlar"
        else -> "İyi geceler"
    }
    val recent = model.history.distinctBy { it.id }.take(5)
    val listState = rememberLazyListState()
    val loadAtEnd = remember(listState, model) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                val towardBottom = consumed.y < 0f || available.y < 0f
                if (towardBottom && !listState.canScrollForward) model.loadMoreHome()
                return Offset.Zero
            }
        }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().nestedScroll(loadAtEnd),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Text(
                model.accountName?.let { "$hello, $it" } ?: hello,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            model.error?.let { Text(it, color = Accent, modifier = Modifier.padding(top = 6.dp)) }
            model.status?.let { Text(it, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickTile("Beğenilen şarkılar", "${model.liked.size} şarkı", null, accentTile = true) {
                    model.open(Dest.Liked)
                }
                recent.forEach { entry ->
                    QuickTile(entry.title, entry.artists, entry.thumbnail, accentTile = false) {
                        model.player.playAll(listOf(Track(entry.id, entry.title, entry.artists, entry.thumbnail, 0)), 0)
                    }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { Text("Yakın zamanda çalınanlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(recent, key = { it.id }) { entry ->
                        PlayedCard(entry, 0) {
                            model.player.playAll(listOf(Track(entry.id, entry.title, entry.artists, entry.thumbnail, 0)), 0)
                        }
                    }
                }
            }
        }
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
                                }
                            },
                            label = { Text(chip.title) },
                        )
                    }
                }
            }
        }
        items(page?.sections.orEmpty(), key = { it.title + (it.items.firstOrNull()?.id ?: it.items.size) }) { section ->
            SectionRow(section, model)
        }
        if (model.homePaging) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = Accent) }
        }
    }
}

@Composable
private fun SectionRow(section: HomePage.Section, model: AppModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(section.title, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            section.endpoint?.let { endpoint ->
                TextButton(onClick = { model.open(Dest.Collection(endpoint.browseId, endpoint.params, section.title)) }) {
                    Text("Tümünü göster")
                }
            }
        }
        val artists = section.items.filterIsInstance<ArtistItem>()
        if (artists.isNotEmpty() && artists.size == section.items.size) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(artists, key = { it.id }) { artist ->
                    Column(
                        Modifier.width(112.dp).clickable { model.openItem(artist) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Cover(artist.thumbnail, Modifier.size(96.dp), artist.title, circle = true)
                        Spacer(Modifier.height(8.dp))
                        Text(artist.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text("Sanatçı", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(section.items, key = { it.id + it.title }) { item -> MediaCard(item, model) }
            }
        }
    }
}

@Composable
private fun Cover(
    url: String?,
    modifier: Modifier,
    description: String? = null,
    circle: Boolean = false,
) {
    val palette = LocalPalette.current
    val source = remember(url) { coverUrl(url) }
    val shape = if (circle) CircleShape else RoundedCornerShape(palette.corner)
    Box(modifier.clip(shape).background(palette.card)) {
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
    val playing = LocalPlayingId.current == item.id
    val scale by animateFloatAsState(if (hovered) 1.045f else 1f, Lift, label = "card")
    val rise by animateFloatAsState(if (hovered) -8f else 0f, Lift, label = "rise")
    val veil by animateFloatAsState(if (hovered) 1f else 0f, tween(200), label = "veil")
    val frame by animateColorAsState(if (playing) palette.accent else Color.Transparent, tween(240), label = "frame")
    Column(
        Modifier
            .width(172.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = rise
            }
            .clickable(interaction, ripple(color = palette.accent)) { model.openItem(item) },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(172.dp)
                .clip(RoundedCornerShape(6.dp))
                .border(2.dp, frame, RoundedCornerShape(6.dp)),
        ) {
            Cover(item.thumbnail, Modifier.fillMaxSize(), item.title)
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = veil * 0.42f }.background(Color.Black))
            Surface(
                shape = CircleShape,
                color = palette.accent,
                shadowElevation = 10.dp,
                modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(44.dp).graphicsLayer {
                    alpha = veil
                    val badge = 0.82f + 0.18f * veil
                    scaleX = badge
                    scaleY = badge
                },
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Çal", tint = Color.Black)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(item.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(itemSubtitle(item), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun SearchScreen(model: AppModel) {
    Column(Modifier.fillMaxSize()) {
        PageHeader("Ara", model, "Üstteki alandan da arayabilirsin")
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
    val libraryTracks = model.liked.map { it.toTrack() }.ifEmpty {
        model.localPlaylists.flatMap { it.tracks }.map { it.toTrack() }.distinctBy { it.id }
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kitaplık", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Bu bilgisayar", color = Muted)
                }
                RandomButton(libraryTracks.isNotEmpty()) {
                    model.player.playShuffled(libraryTracks)
                    model.status = "Rastgele çalınıyor"
                }
            }
            model.error?.let { Text(it, color = Accent, modifier = Modifier.padding(top = 6.dp)) }
            model.status?.let { Text(it, color = Muted, modifier = Modifier.padding(top = 4.dp)) }
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Card)
                    .clickable { model.open(Dest.Liked) }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Black, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Beğenilen şarkılar", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text(if (model.liked.isEmpty()) "Kalbe bastığın şarkılar burada durur" else "${model.liked.size} şarkı", color = Muted)
                }
                if (model.liked.isNotEmpty()) {
                    PlayButton { model.player.playAll(model.liked.map { it.toTrack() }, 0) }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Koleksiyonlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
                Text("${model.localPlaylists.size} liste", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                model.localPlaylists.forEach { playlist ->
                    ShelfTile(playlist.title, "${playlist.tracks.size} şarkı", playlist.tracks.firstOrNull()?.thumbnail) {
                        model.openLocal(playlist.id)
                    }
                }
                ShelfTile("Yeni çalma listesi", "Koleksiyonuna ekle", create = true) {
                    model.showCreate = true
                }
            }
        }
        if (model.liked.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Beğenilenlerden", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
                    TextButton(onClick = { model.open(Dest.Liked) }) { Text("Tümü") }
                }
            }
            items(model.liked.take(8), key = { "lib-" + it.id }) { track ->
                val tracks = model.liked.map { it.toTrack() }
                SongRow(track.title, track.artists, track.thumbnail, id = track.id) {
                    model.player.playAll(tracks, tracks.indexOfFirst { it.id == track.id }.coerceAtLeast(0))
                }
            }
        }
        if (model.store.settings.cookie.isNotBlank()) {
            item { Text("YouTube Music", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LibraryTab.entries.forEach { tab ->
                        FilterChip(selected = model.libraryTab == tab, onClick = { model.selectLibrary(tab) }, label = { Text(tab.label) })
                    }
                }
            }
            items(model.libraryItems, key = { "yt-" + it.id + it.title }) { item ->
                SongRow(item.title, itemSubtitle(item), item.thumbnail) { model.openItem(item) }
            }
            if (model.libraryContinuation != null) {
                item { Button(onClick = model::loadMoreLibrary) { Text("Daha fazla") } }
            }
        }
    }
}

@Composable
private fun ShelfTile(
    title: String,
    subtitle: String,
    thumbnail: String? = null,
    heart: Boolean = false,
    create: Boolean = false,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .width(196.dp)
            .clip(RoundedCornerShape(palette.corner))
            .background(if (create) Color.Transparent else palette.card)
            .border(
                1.dp,
                if (create) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                RoundedCornerShape(palette.corner),
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        when {
            heart -> Box(
                Modifier.size(112.dp).clip(RoundedCornerShape(10.dp)).background(palette.accent),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Black) }
            create -> Box(
                Modifier.size(112.dp).clip(RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.Add, contentDescription = null, tint = Muted, modifier = Modifier.size(32.dp)) }
            else -> Cover(thumbnail, Modifier.fillMaxWidth().height(112.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun QuickTile(
    title: String,
    subtitle: String,
    thumbnail: String?,
    accentTile: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .width(300.dp)
            .height(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (accentTile) Accent else Card)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (accentTile) {
            Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Black)
            }
        } else {
            Cover(thumbnail, Modifier.size(64.dp))
        }
        Column(Modifier.padding(horizontal = 12.dp)) {
            Text(title, color = if (accentTile) Color.Black else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = if (accentTile) Color.Black.copy(alpha = 0.7f) else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RandomButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black, disabledContainerColor = Card, disabledContentColor = Muted),
    ) {
        Icon(Icons.Default.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Rastgele çal")
    }
}

@Composable
private fun PlayButton(onClick: () -> Unit) {
    Surface(shape = CircleShape, color = Accent, modifier = Modifier.size(48.dp).clickable(onClick = onClick)) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.PlayArrow, contentDescription = "Çal", tint = Color.Black)
        }
    }
}

@Composable
private fun LocalListScreen(model: AppModel, id: String) {
    val playlist = model.localPlaylists.find { it.id == id }
    SavedTracks(
        model = model,
        title = playlist?.title ?: "Liste",
        tracks = playlist?.tracks.orEmpty(),
        empty = "Bu liste boş. Çalan şarkıyı ekle düğmesiyle doldur.",
        thumbnail = playlist?.tracks?.firstOrNull()?.thumbnail,
    )
}

private enum class TrackSort(val label: String) {
    Recent("Tarihe göre"),
    Title("Ada göre"),
    Artist("Sanatçı"),
    Duration("Süre"),
}

private fun List<SavedTrack>.arrange(sort: TrackSort, query: String): List<SavedTrack> {
    val filtered = if (query.isBlank()) {
        this
    } else {
        filter { it.title.contains(query, ignoreCase = true) || it.artists.contains(query, ignoreCase = true) }
    }
    return when (sort) {
        TrackSort.Recent -> filtered
        TrackSort.Title -> filtered.sortedBy { it.title.lowercase() }
        TrackSort.Artist -> filtered.sortedBy { it.artists.lowercase() }
        TrackSort.Duration -> filtered.sortedByDescending { it.durationMs }
    }
}

@Composable
private fun SavedTracks(
    model: AppModel,
    title: String,
    tracks: List<SavedTrack>,
    empty: String,
    thumbnail: String? = null,
    heart: Boolean = false,
) {
    var sort by remember { mutableStateOf(TrackSort.Recent) }
    var filter by remember { mutableStateOf("") }
    val shown = tracks.arrange(sort, filter)
    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (heart) {
                Box(
                    Modifier.size(148.dp).clip(RoundedCornerShape(12.dp)).background(Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Favorite, contentDescription = null, tint = Color.Black, modifier = Modifier.size(56.dp))
                }
            } else {
                Cover(thumbnail, Modifier.size(148.dp))
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${tracks.size} şarkı", color = Muted)
                model.error?.let { Text(it, color = Accent) }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (shown.isNotEmpty()) PlayButton { model.player.playAll(shown.map { it.toTrack() }, 0) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                modifier = Modifier.width(240.dp),
                placeholder = { Text("Listede ara") },
                singleLine = true,
            )
            Spacer(Modifier.weight(1f))
            TrackSort.entries.forEach { choice ->
                FilterChip(
                    selected = sort == choice,
                    onClick = { sort = choice },
                    label = { Text(choice.label) },
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (tracks.isEmpty()) Text(empty, color = Muted)
        else if (shown.isEmpty()) Text("Bu aramaya uyan şarkı yok.", color = Muted)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Text("#", color = Muted, modifier = Modifier.width(36.dp))
                    Text("Başlık", color = Muted, modifier = Modifier.weight(1f))
                    Text("Süre", color = Muted)
                }
            }
            itemsIndexed(shown, key = { index, track -> track.id + index }) { index, track ->
                SongRow(
                    track.title,
                    track.artists,
                    track.thumbnail,
                    trailing = track.durationMs.takeIf { it > 0 }?.let { formatTime(it) },
                    id = track.id,
                    index = "${index + 1}",
                ) {
                    model.player.playAll(shown.map { it.toTrack() }, index)
                }
            }
        }
    }
}

@Composable
private fun ShortcutsScreen(model: AppModel) {
    val rows = listOf(
        "Boşluk" to "Oynat veya duraklat",
        "Ctrl + Sağ" to "Sonraki parça",
        "Ctrl + Sol" to "Önceki parça",
        "Ctrl + Yukarı" to "Sesi aç",
        "Ctrl + Aşağı" to "Sesi kıs",
        "Ctrl + M" to "Sessiz",
    )
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PageHeader("Klavye kısayolları", model, "Pencere odaktayken çalışır")
        rows.forEach { (keys, action) ->
            Surface(
                color = Card,
                shape = RoundedCornerShape(LocalPalette.current.corner),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(keys, color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(160.dp))
                    Text(action, color = Muted)
                }
            }
        }
    }
}

@Composable
private fun HistoryScreen(model: AppModel) {
    val counts = model.history.groupingBy { it.id }.eachCount()
    val unique = model.history.distinctBy { it.id }
    val tracks = unique.map { Track(it.id, it.title, it.artists, it.thumbnail, 0) }
    val top = unique.map { it to (counts[it.id] ?: 1) }.sortedByDescending { it.second }.take(8)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Geçmiş", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(if (unique.isEmpty()) "Henüz bir şey çalınmadı" else "${unique.size} şarkı", color = Muted)
                }
                RandomButton(tracks.isNotEmpty()) {
                    model.player.playShuffled(tracks)
                    model.status = "Geçmiş karışık çalıyor"
                }
            }
            model.error?.let { Text(it, color = Accent, modifier = Modifier.padding(top = 6.dp)) }
        }
        if (top.isNotEmpty()) {
            item { Text("En çok çalınanlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(top, key = { it.first.id }) { (entry, count) ->
                        PlayedCard(entry, count) {
                            val start = tracks.indexOfFirst { it.id == entry.id }.coerceAtLeast(0)
                            model.player.playAll(tracks, start)
                        }
                    }
                }
            }
        }
        item { Text("Son çalınanlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White) }
        if (unique.isEmpty()) {
            item { Text("Bir şarkı açınca burada görünür.", color = Muted) }
        }
        itemsIndexed(unique.take(40), key = { _, entry -> entry.id }) { index, entry ->
            SongRow(entry.title, entry.artists, entry.thumbnail, id = entry.id, index = "${index + 1}") {
                model.player.playAll(tracks, index)
            }
        }
        if (model.remoteHistory.isNotEmpty()) {
            item { Text("YouTube Music geçmişi", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.padding(top = 12.dp)) }
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
    val scale by animateFloatAsState(if (hovered) 1.045f else 1f, Lift, label = "played")
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
        Text(entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = Color.White)
        Text(if (count > 0) "$count kez" else entry.artists, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ExploreScreen(model: AppModel) {
    var filter by remember { mutableStateOf("Tümü") }
    val filters = listOf("Tümü", "Kategoriler", "Yeni çıkanlar", "Listeler", "Ruh hali")
    val releases = model.releases
    val charts = model.charts?.sections.orEmpty()
    val moods = model.moods
    val rising = charts.firstOrNull { section -> section.items.any { it is SongItem } }
    val risingSongs = rising?.items?.filterIsInstance<SongItem>().orEmpty().take(8)
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item {
            Text("Keşfet", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text("Yeni çıkanlar, listeler ve ruh halleri", color = Muted)
            model.error?.let { Text(it, color = Accent, modifier = Modifier.padding(top = 6.dp)) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                filters.forEach { label ->
                    FilterChip(selected = filter == label, onClick = { filter = label }, label = { Text(label) })
                }
            }
        }
        if (filter == "Tümü" || filter == "Kategoriler") {
            item { Text("Kategoriler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(exploreGenres, key = { it.id }) { genre ->
                        GenreCard(genre, selected = model.categoryId == genre.id) {
                            model.openGenre(genre.id, genre.title, genre.query)
                        }
                    }
                }
            }
            if (model.categoryId != null) {
                item { GenreDetail(model) }
            }
        }
        if ((filter == "Tümü" || filter == "Yeni çıkanlar") && releases.isNotEmpty()) {
            item { Text("Öne çıkanlar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White) }
            item {
                Row(Modifier.fillMaxWidth().height(248.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ExploreHero(releases.first(), model, Modifier.weight(1.7f).fillMaxHeight())
                    if (releases.size > 1) {
                        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            releases.drop(1).take(2).forEach { album ->
                                ExploreHero(album, model, Modifier.weight(1f).fillMaxWidth())
                            }
                        }
                    }
                }
            }
            if (releases.size > 3) {
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(releases.drop(3), key = { it.browseId }) { album -> MediaCard(album, model) }
                    }
                }
            }
        }
        if ((filter == "Tümü" || filter == "Ruh hali") && moods.isNotEmpty()) {
            moods.forEach { group ->
                item { Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(group.items, key = { group.title + it.title }) { mood ->
                            MoodCard(mood.title, Color(mood.stripeColor)) {
                                model.open(Dest.Collection(mood.endpoint.browseId, mood.endpoint.params, mood.title))
                            }
                        }
                    }
                }
            }
        }
        if (filter == "Tümü" && risingSongs.isNotEmpty()) {
            item { Text(rising?.title ?: "Yükselenler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White) }
            items(risingSongs, key = { it.id }) { song ->
                val index = risingSongs.indexOf(song)
                SongRow(song.title, song.artists.joinToString { it.name }, song.thumbnail, formatTime((song.duration ?: 0) * 1000L), id = song.id, index = "${index + 1}") {
                    model.playSongs(risingSongs, index)
                }
            }
        }
        if (filter == "Tümü" || filter == "Listeler") {
            charts.filter { it != rising || filter == "Listeler" }.forEach { section ->
                item { Text(section.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(section.items, key = { section.title + it.id }) { item -> MediaCard(item, model) }
                    }
                }
            }
        }
        if (releases.isEmpty() && charts.isEmpty() && moods.isEmpty()) {
            item { Text("Keşfet içeriği yükleniyor.", color = Muted) }
        }
    }
}

@Composable
private fun ExploreHero(item: YTItem, model: AppModel, modifier: Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable { model.openItem(item) },
    ) {
        Cover(item.thumbnail, Modifier.fillMaxSize(), item.title)
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))),
            ),
        )
        Column(Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(item.title, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(itemSubtitle(item), color = Color.White.copy(alpha = 0.78f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun GenreCard(genre: Genre, selected: Boolean, onClick: () -> Unit) {
    val image = remember(genre.art) { loadImage(genre.art) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered || selected) 1.04f else 1f, Lift, label = "genre")
    val edge by animateColorAsState(
        when {
            selected -> Accent
            hovered -> Color.White.copy(alpha = 0.45f)
            else -> Color.Transparent
        },
        tween(200),
        label = "genreEdge",
    )
    Box(
        Modifier
            .width(168.dp)
            .height(104.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, edge, RoundedCornerShape(12.dp))
            .clickable(interaction, ripple(color = Accent), onClick = onClick),
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = genre.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleX = 1.12f
                    scaleY = 1.12f
                    translationY = -size.height * 0.04f
                },
            )
        } else {
            Box(Modifier.fillMaxSize().background(Card))
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)))))
        Text(
            genre.title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

@Composable
private fun GenreDetail(model: AppModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(model.categoryTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Şarkılar, albümler ve listeler", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (model.categorySongs.isNotEmpty()) PlayButton { model.playSongs(model.categorySongs, 0) }
        }
        if (model.categoryLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
        model.categorySongs.forEachIndexed { index, song ->
            SongRow(
                song.title,
                song.artists.joinToString { it.name },
                song.thumbnail,
                formatTime((song.duration ?: 0) * 1000L),
                id = song.id,
                index = "${index + 1}",
            ) { model.playSongs(model.categorySongs, index) }
        }
        if (model.categoryAlbums.isNotEmpty()) {
            Text("Albümler", fontWeight = FontWeight.SemiBold, color = Color.White)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(model.categoryAlbums, key = { it.id }) { album -> MediaCard(album, model) }
            }
        }
        if (model.categoryLists.isNotEmpty()) {
            Text("Listeler", fontWeight = FontWeight.SemiBold, color = Color.White)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(model.categoryLists, key = { it.id }) { playlist -> MediaCard(playlist, model) }
            }
        }
        if (!model.categoryLoading && model.categorySongs.isEmpty() && model.categoryAlbums.isEmpty() && model.categoryLists.isEmpty()) {
            Text("Bu kategoride sonuç çıkmadı.", color = Muted)
        }
    }
}

@Composable
private fun MoodCard(title: String, color: Color, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.04f else 1f, Lift, label = "mood")
    val fill by animateColorAsState(if (hovered) Color.White.copy(alpha = 0.08f) else Card, tween(180), label = "moodFill")
    Row(
        Modifier
            .width(196.dp)
            .height(64.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .background(fill)
            .clickable(interaction, ripple(color = color), onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.padding(start = 12.dp).size(10.dp).clip(CircleShape).background(color))
        Text(
            title,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
    }
}

@Composable
private fun AlbumScreen(model: AppModel) {
    val page = model.album
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(page?.album?.thumbnail, Modifier.size(168.dp))
                Spacer(Modifier.width(20.dp))
                Column {
                    Text(page?.album?.title ?: "Albüm", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(page?.album?.artists?.joinToString { it.name }.orEmpty(), color = Muted)
                    Spacer(Modifier.height(12.dp))
                    if (page != null && page.songs.isNotEmpty()) PlayButton { model.playSongs(page.songs, 0) }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        itemsIndexed(page?.songs.orEmpty()) { index, song ->
            SongRow(
                song.title,
                song.artists.joinToString { it.name },
                song.thumbnail,
                formatTime((song.duration ?: 0) * 1000L),
                index = "${index + 1}",
            ) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(page?.artist?.thumbnail, Modifier.size(168.dp), circle = true)
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("Sanatçı", color = Muted, style = MaterialTheme.typography.labelMedium)
                    Text(page?.artist?.title ?: "Sanatçı", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(page?.subscriberCountText ?: page?.monthlyListenerCount ?: "", color = Muted)
                    page?.description?.let { Text(it, color = Muted, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        page?.sections.orEmpty().forEach { section ->
            item { Text(section.title, fontWeight = FontWeight.SemiBold, color = Color.White) }
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
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Cover(page?.playlist?.thumbnail, Modifier.size(168.dp))
                Spacer(Modifier.width(20.dp))
                Column {
                    Text("Çalma listesi", color = Muted, style = MaterialTheme.typography.labelMedium)
                    Text(page?.playlist?.title ?: "Çalma listesi", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(page?.playlist?.author?.name.orEmpty(), color = Muted)
                    Spacer(Modifier.height(12.dp))
                    if (page != null && page.songs.isNotEmpty()) PlayButton { model.playSongs(page.songs, 0) }
                }
            }
        }
        itemsIndexed(page?.songs.orEmpty()) { index, song ->
            SongRow(
                song.title,
                song.artists.joinToString { it.name },
                song.thumbnail,
                formatTime((song.duration ?: 0) * 1000L),
                index = "${index + 1}",
            ) {
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
            group.title?.let { item { Text(it, fontWeight = FontWeight.SemiBold, color = Color.White) } }
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
    var tab by remember { mutableStateOf("Görünüm") }
    Column(
        Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Ayarlar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
        Text(model.accountName?.let { "Hesap: $it" } ?: "Oturum yok, çalma açık", color = Muted)
        model.error?.let { Text(it, color = Accent) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Hesap", "Görünüm", "Ses", "Sistem").forEach { label ->
                FilterChip(selected = tab == label, onClick = { tab = label }, label = { Text(label) })
            }
        }
        when (tab) {
            "Hesap" -> AccountSettings(model)
            "Görünüm" -> LookSettings(model)
            "Ses" -> AudioSettings(model)
            else -> SystemSettings(model)
        }
    }
}

@Composable
private fun AccountSettings(model: AppModel) {
    StudioCard("Profil") {
        Text(model.accountName ?: "Oturum açık değil", color = Color.White, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleLarge)
        Text("Çalma hesap istemez. YouTube kitaplığın için music.youtube.com çerezini yapıştır.", color = Muted)
    }
    StudioCard("YouTube çerezi") {
        OutlinedTextField(
            value = model.cookieDraft,
            onValueChange = { model.cookieDraft = it },
            modifier = Modifier.fillMaxWidth().height(120.dp),
            placeholder = { Text("Cookie") },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = model::saveCookie) { Text("Kaydet") }
            TextButton(onClick = {
                model.cookieDraft = ""
                model.saveCookie()
            }) { Text("Çıkış") }
        }
    }
}

@Composable
private fun LookSettings(model: AppModel) {
    val look = model.look
    val palette = look.palette
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1.4f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            StudioCard("01  Taban teması") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    surfaceChoices.forEach { choice ->
                        val selected = look.surfaceId == choice.id
                        Column(
                            Modifier.width(108.dp).clickable { model.saveLook(surface = choice.id) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(choice.ink)
                                    .border(2.dp, if (selected) palette.accent else Color.Transparent, RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                            ) {
                                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(choice.panel)) {
                                    Box(Modifier.align(Alignment.BottomStart).size(width = 36.dp, height = 8.dp).clip(RoundedCornerShape(4.dp)).background(palette.accent))
                                }
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(choice.label, color = if (selected) Color.White else Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            StudioCard("02  Vurgu rengi") {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    accentChoices.forEach { choice ->
                        val selected = look.accentId == choice.id
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { model.saveLook(accent = choice.id) }) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(choice.color)
                                    .border(2.dp, if (selected) Color.White else Color.Transparent, CircleShape),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(choice.label, color = if (selected) Color.White else Muted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
            StudioCard("03  Köşe yuvarlaklığı") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cornerChoices.forEach { (id, label) ->
                        FilterChip(selected = look.cornerId == id, onClick = { model.saveLook(corners = id) }, label = { Text(label) })
                    }
                }
            }
            StudioCard("04  Kart boyutu") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    cardChoices.forEach { (id, label) ->
                        FilterChip(selected = look.cardId == id, onClick = { model.saveLook(cards = id) }, label = { Text(label) })
                    }
                }
                Text("Seçimler anında kaydolur.", color = Muted, style = MaterialTheme.typography.bodySmall)
            }
        }
        LookPreview(model, Modifier.width(280.dp))
    }
}

@Composable
private fun LookPreview(model: AppModel, modifier: Modifier) {
    val palette = LocalPalette.current
    val track = model.player.current
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(palette.panel).padding(16.dp),
    ) {
        Text("Canlı önizleme", color = Muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(12.dp))
        Cover(track?.thumbnail, Modifier.fillMaxWidth().height(120.dp))
        Spacer(Modifier.height(12.dp))
        Text(track?.title ?: "Gamsız Hayat", color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track?.artists ?: "Candan Erçetin", color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f))) {
            Box(Modifier.fillMaxWidth(0.38f).height(4.dp).background(palette.accent))
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).clip(CircleShape).background(palette.accent), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
            }
            Spacer(Modifier.width(10.dp))
            Text("Oynat", color = Color.White, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun AudioSettings(model: AppModel) {
    StudioCard("Ses kalitesi") {
        Text("Akış servisinin sunduğu kalite. Parça yine de kaynaktaki dosyaya bağlı.", color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("AUTO" to "Otomatik", "HIGH" to "Yüksek", "LOW" to "Düşük").forEach { (id, label) ->
                FilterChip(selected = model.quality == id, onClick = { model.saveQuality(id) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun SystemSettings(model: AppModel) {
    StudioCard("Güncelleme") {
        Text("Yüklü sürüm ${AppVersion.NAME}", color = Color.White, fontWeight = FontWeight.Medium)
        Text(model.updates.message, color = Muted)
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
    }
    StudioCard("Klavye") {
        Text("Boşluk oynatır. Ctrl ile parça ve ses değişir.", color = Muted)
        TextButton(onClick = { model.open(Dest.Shortcuts) }) { Text("Kısayolları aç") }
    }
}

@Composable
private fun StudioCard(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = {
            Text(title, color = Color.White, fontWeight = FontWeight.SemiBold)
            content()
        },
    )
}

@Composable
private fun SongRow(
    title: String,
    subtitle: String,
    thumbnail: String?,
    trailing: String? = null,
    id: String? = null,
    index: String? = null,
    onClick: () -> Unit,
) {
    val palette = LocalPalette.current
    val playing = id != null && id == LocalPlayingId.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val background by animateColorAsState(
        when {
            playing -> Color.White.copy(alpha = 0.07f)
            pressed -> palette.card
            hovered -> palette.card.copy(alpha = 0.85f)
            else -> Color.White.copy(alpha = 0.035f)
        },
        tween(200),
        label = "row",
    )
    val shift by animateDpAsState(if (hovered) 6.dp else 0.dp, Glide, label = "shift")
    val rail by animateDpAsState(if (playing) 28.dp else 0.dp, Glide, label = "rail")
    Row(
        Modifier
            .fillMaxWidth()
            .offset(x = shift)
            .clip(RoundedCornerShape(palette.corner))
            .background(background)
            .clickable(interaction, ripple(color = palette.accent), onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playing) {
            Box(Modifier.padding(end = 8.dp).width(3.dp).height(rail).clip(RoundedCornerShape(2.dp)).background(palette.accent))
        } else if (index != null) {
            Text(index, color = Muted, modifier = Modifier.width(28.dp), textAlign = TextAlign.Center)
        }
        Cover(thumbnail, Modifier.size(44.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, color = if (playing) palette.accent else Color.White)
            Text(subtitle, color = Color(0xFFC8C8D0), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
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
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        when {
            !enabled -> 1f
            pressed -> 0.9f
            hovered -> 1.14f
            else -> 1f
        },
        Lift,
        label = "icon",
    )
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
            item { Text("Sıradaki", fontWeight = FontWeight.SemiBold, color = Color.White) }
            itemsIndexed(model.player.queue, key = { index, track -> track.id + index }) { index, track ->
                val playing = index == model.player.index
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { model.player.jumpTo(index) }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(track.thumbnail, Modifier.size(36.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, color = if (playing) Accent else Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(track.artists, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsPanel(player: PlaybackController, modifier: Modifier) {
    val lines = player.lyrics?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.toList().orEmpty()
    Column(
        modifier.background(Panel).verticalScroll(rememberScrollState()).padding(24.dp),
    ) {
        Text("Şarkı sözleri", color = Muted, style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))
        Text(player.current?.title ?: "Çalmıyor", color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
        Text(player.current?.artists.orEmpty(), color = Muted)
        Spacer(Modifier.height(20.dp))
        if (lines.isEmpty()) {
            Text(if (player.lyrics == null) "Söz yükleniyor" else "Söz yok", color = Muted)
        } else {
            lines.forEach { line ->
                Text(line, color = Color.White.copy(alpha = 0.88f), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
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
    var sleepOpen by remember { mutableStateOf(false) }
    val known = when {
        player.durationMs > 1_000 -> player.durationMs
        (track?.durationMs ?: 0L) > 1_000 -> track?.durationMs ?: 0L
        else -> 0L
    }
    if (sleepOpen) SleepTimerDialog(player) { sleepOpen = false }
    Surface(color = Panel, shadowElevation = 18.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (player.busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent)
            player.error?.let { Text(it, color = Accent, maxLines = 1) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(
                    targetState = track?.let { NowPlaying(it.id, it.title, it.artists, it.thumbnail) },
                    modifier = Modifier.weight(1f),
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(180)) },
                    label = "parca",
                ) { shown ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Cover(shown?.thumbnail, Modifier.size(64.dp))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(shown?.title ?: "Çalmıyor", maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(shown?.artists.orEmpty().ifBlank { " " }, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                Column(Modifier.width(480.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    sleepStatus(player, known)?.let { status ->
                        Text(status, color = Accent, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SoftIcon(onClick = player::toggleShuffle) {
                            Icon(Icons.Default.Shuffle, contentDescription = "Karıştır", tint = if (player.shuffle) Accent else Muted)
                        }
                        SoftIcon(onClick = player::previous, enabled = track != null) { Icon(Icons.Default.SkipPrevious, contentDescription = "Önceki") }
                        Surface(shape = CircleShape, color = Accent, shadowElevation = 8.dp, modifier = Modifier.size(46.dp)) {
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
                        Clock(formatTime(player.positionMs), TextAlign.End)
                        Spacer(Modifier.width(8.dp))
                        Box(Modifier.weight(1f)) {
                            SeekBar(player.positionMs, known) { player.seek(it) }
                        }
                        Spacer(Modifier.width(8.dp))
                        Clock(if (known > 0) formatTime(known) else "—:——", TextAlign.Start)
                    }
                }
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    val sleepOn = player.sleepRemainingMs != null || player.sleepPauseAtEndOfSong
                    SoftIcon(onClick = { sleepOpen = true }) {
                        Icon(
                            Icons.Default.Bedtime,
                            contentDescription = "Uyku zamanlayıcısı",
                            tint = if (sleepOn) Accent else Muted,
                        )
                    }
                    SoftIcon(onClick = model::likeCurrent, enabled = track != null) {
                        Icon(
                            Icons.Default.Favorite,
                            contentDescription = "Beğen",
                            tint = if (track != null && model.liked.any { it.id == track.id }) Accent else Muted,
                        )
                    }
                    SoftIcon(onClick = model::prepareAdd, enabled = track != null) { Icon(Icons.Default.PlaylistAdd, contentDescription = "Ekle") }
                    SoftIcon(onClick = player::toggleLyrics, enabled = track != null) { Icon(Icons.Default.GraphicEq, contentDescription = "Sözler") }
                    SoftIcon(onClick = player::toggleQueue) { Icon(Icons.Default.QueueMusic, contentDescription = "Sıra") }
                    SoftIcon(onClick = player::toggleMute) {
                        Icon(
                            if (player.volume <= 0.001f) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                            contentDescription = "Ses",
                            tint = Muted,
                        )
                    }
                    Slider(
                        value = player.volume,
                        onValueChange = player::changeVolume,
                        modifier = Modifier.width(96.dp),
                    )
                }
            }
        }
    }
}

private const val MAX_SLEEP_MINUTES = 24 * 60

private fun sleepMinutes(text: String): Int? =
    text.trim().toIntOrNull()?.takeIf { it in 1..MAX_SLEEP_MINUTES }

private fun sleepStatus(player: PlaybackController, knownDurationMs: Long): String? {
    player.sleepRemainingMs?.let { return "Uyku ${formatTime(it)}" }
    if (!player.sleepPauseAtEndOfSong) return null
    if (knownDurationMs <= 0L) return "Şarkı bitince duraklat"
    val songLeft = (knownDurationMs - player.positionMs).coerceAtLeast(0L)
    return "Şarkı bitince · ${formatTime(songLeft)}"
}

@Composable
private fun SleepTimerDialog(player: PlaybackController, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    val minutes = sleepMinutes(custom)
    val active = player.sleepRemainingMs != null || player.sleepPauseAtEndOfSong
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uyku zamanlayıcısı") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                val remaining = player.sleepRemainingMs
                when {
                    remaining != null -> Text("Kalan süre: ${formatTime(remaining)}", color = Accent)
                    player.sleepPauseAtEndOfSong -> Text("Bu şarkı bitince duraklatılacak", color = Accent)
                }
                listOf(15, 30, 60).forEach { preset ->
                    TextButton(
                        onClick = {
                            player.startSleep(preset)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("$preset dakika") }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.filter(Char::isDigit).take(4) },
                    placeholder = { Text("Dakika") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        val chosen = sleepMinutes(custom)
                        if (chosen != null) {
                            player.startSleep(chosen)
                            onDismiss()
                        }
                    }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("1–1440 dakika", color = Muted, style = MaterialTheme.typography.labelSmall)
                Button(
                    onClick = {
                        if (minutes != null) {
                            player.startSleep(minutes)
                            onDismiss()
                        }
                    },
                    enabled = minutes != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Özel süreyi başlat") }
                TextButton(
                    onClick = {
                        player.pauseWhenSongEnds()
                        onDismiss()
                    },
                    enabled = player.current != null,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Bu şarkı bitince duraklat") }
            }
        },
        confirmButton = {
            if (active) {
                TextButton(onClick = {
                    player.cancelSleep()
                    onDismiss()
                }) { Text("İptal") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

@Composable
private fun Clock(text: String, align: TextAlign) {
    Text(
        text,
        color = Muted,
        style = MaterialTheme.typography.labelMedium,
        textAlign = align,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        modifier = Modifier.requiredWidth(56.dp),
    )
}
