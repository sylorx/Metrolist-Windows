package com.metrolist.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeat
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min

/** Full-window player: large cover on living colour, with lyrics or the queue beside it. */
@Composable
internal fun NowPlayingScreen(model: AppModel, modifier: Modifier) {
    val player = model.player
    val track = player.current
    val ambient = rememberAmbient(coverUrl(track?.thumbnail))
    var side by remember { mutableStateOf("Sözler") }
    LaunchedEffect(track?.id, side) { if (side == "Sözler") player.requestLyrics() }
    Box(
        modifier
            .background(Ink)
            // Clicks must not reach the page underneath.
            .clickable(remember { MutableInteractionSource() }, indication = null) {},
    ) {
        AmbientLights(ambient, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Ink.copy(alpha = 0.72f)))))
        Row(Modifier.fillMaxSize().padding(horizontal = 36.dp, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = { model.showNowPlaying = false }, modifier = Modifier.align(Alignment.Start)) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Küçült", tint = Color.White)
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    val edge = min(min(maxWidth, maxHeight), 460.dp)
                    BigCover(track, player.isPlaying, ambient, Modifier.size(edge))
                }
                Text(
                    track?.title ?: "Çalmıyor", color = Color.White, style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold, maxLines = 1,
                    modifier = if (LocalMotion.current) Modifier.basicMarquee() else Modifier,
                )
                Text(
                    track?.let { it.artists + if (it.album.isBlank()) "" else " · ${it.album}" }.orEmpty(),
                    color = Color.White.copy(alpha = 0.72f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    HeartButton(track != null && model.isLiked(track.id), track != null, 28.dp, model::likeCurrent)
                    IconButton(onClick = model::prepareAdd, enabled = track != null) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Listeye ekle", tint = Color.White)
                    }
                    IconButton(onClick = { track?.let(model::startRadio) }, enabled = track != null && track.localPath == null && !model.radioLoading) {
                        Icon(Icons.Default.Radio, contentDescription = "Şarkı radyosu", tint = Color.White)
                    }
                    IconButton(onClick = { track?.let(model::copyLink) }, enabled = track != null && track.localPath == null) {
                        Icon(Icons.Default.Link, contentDescription = "Bağlantıyı kopyala", tint = Color.White)
                    }
                }
            }
            Spacer(Modifier.width(36.dp))
            Column(Modifier.weight(1f).fillMaxHeight().padding(top = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Sözler", "Sıradaki").forEach { label ->
                        FilterChip(side == label, { side = label }, label = { Text(label) })
                    }
                }
                Spacer(Modifier.height(12.dp))
                AnimatedContent(
                    side, modifier = Modifier.weight(1f),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) }, label = "yan",
                ) { shown ->
                    if (shown == "Sözler") LyricsLines(player, Modifier.fillMaxSize(), large = true) else UpNext(player)
                }
            }
        }
    }
}

@Composable
private fun BigCover(track: Track?, playing: Boolean, glow: Color, modifier: Modifier) {
    val motion = LocalMotion.current
    // Rests a little smaller while paused, then breathes very slightly with the music.
    val rest by animateFloatAsState(if (playing) 1f else 0.92f, spring(0.6f, 160f), label = "rest")
    val breath = if (playing && motion) {
        rememberInfiniteTransition(label = "breath").animateFloat(
            1f, 1.018f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), AnimationRepeat.Reverse), label = "scale",
        )
    } else null
    AnimatedContent(
        track?.id to track?.thumbnail,
        modifier = modifier.graphicsLayer {
            val scale = rest * (breath?.value ?: 1f)
            scaleX = scale
            scaleY = scale
        },
        transitionSpec = {
            (fadeIn(tween(420)) + scaleIn(tween(460, easing = FastOutSlowInEasing), 0.82f)) togetherWith
                (fadeOut(tween(260)) + scaleOut(tween(320), 1.1f))
        },
        contentAlignment = Alignment.Center,
        label = "kapak",
    ) { (_, art) ->
        Box(Modifier.fillMaxSize().shadow(36.dp, RoundedCornerShape(LocalPalette.current.corner), ambientColor = glow, spotColor = glow)) {
            Cover(art, Modifier.fillMaxSize(), track?.title, size = 720)
        }
    }
}

@Composable
private fun UpNext(player: PlaybackController) {
    val upcoming = player.queue.drop(player.index + 1)
    val keys = remember(upcoming) { occurrenceKeys(upcoming) { it.id } }
    if (upcoming.isEmpty()) {
        Text("Sırada başka şarkı yok.", color = Color.White.copy(alpha = 0.7f))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(upcoming, key = { at, _ -> keys[at] }) { at, track ->
            Row(
                Modifier.animateItem().fillMaxWidth()
                    .clickable { player.jumpTo(player.index + 1 + at) }.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Cover(track.thumbnail, Modifier.size(44.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(track.title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(track.artists, color = Color.White.copy(alpha = 0.65f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                }
                if (track.durationMs > 0) Text(formatTime(track.durationMs), color = Color.White.copy(alpha = 0.6f))
            }
        }
    }
}
