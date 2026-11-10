package com.metrolist.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import coil3.compose.AsyncImage
import java.awt.Cursor
import java.awt.Window

private fun Modifier.moveWindow(window: Window, onMoved: (Int, Int) -> Unit, dragging: (Boolean) -> Unit = {}): Modifier =
    pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR))).pointerInput(Unit) {
        var originX = 0
        var originY = 0
        var originWinX = 0
        var originWinY = 0
        detectDragGestures(
            onDragStart = {
                dragging(true)
                val pointer = java.awt.MouseInfo.getPointerInfo().location
                originX = pointer.x
                originY = pointer.y
                originWinX = window.x
                originWinY = window.y
            },
            onDragEnd = {
                onMoved(window.x, window.y)
                dragging(false)
            },
            onDragCancel = { dragging(false) },
        ) { change, _ ->
            change.consume()
            val pointer = java.awt.MouseInfo.getPointerInfo().location
            val area = TaskbarMedia.desktopBounds()
            val maxX = (area.x + area.width - window.width).coerceAtLeast(area.x)
            val maxY = (area.y + area.height - window.height).coerceAtLeast(area.y)
            window.setLocation(
                (originWinX + pointer.x - originX).coerceIn(area.x, maxX),
                (originWinY + pointer.y - originY).coerceIn(area.y, maxY),
            )
        }
    }

@Composable
fun FrameWindowScope.DesktopWidget(model: AppModel) {
    val player = model.player
    val track = player.current
    val playing = player.isPlaying
    Box(Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Color.Black.copy(alpha = 0.62f))
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.moveWindow(window, model::placeWidget),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)),
                ) {
                    val art = coverUrl(track?.thumbnail)
                    if (art != null) {
                        AsyncImage(model = art, contentDescription = track?.title, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.width(168.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        track?.title ?: "Çalmıyor",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track?.artists?.ifBlank { "Metrolist" } ?: "Bir parça seç",
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            WidgetButton(enabled = track != null, onClick = player::previous) {
                Icon(Icons.Default.SkipPrevious, contentDescription = "Önceki", tint = Color.White)
            }
            WidgetButton(enabled = track != null, onClick = player::toggle, accent = true) {
                Icon(
                    if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Oynat",
                    tint = Color.Black,
                )
            }
            WidgetButton(enabled = track != null, onClick = player::next) {
                Icon(Icons.Default.SkipNext, contentDescription = "Sonraki", tint = Color.White)
            }
        }
    }
}

@Composable
fun FrameWindowScope.TrackToast(model: AppModel) {
    val player = model.player
    val track = player.current
    val accent = accentChoices.firstOrNull { it.id == model.toastColor }?.color ?: Color(0xFF1ED760)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(hovered) {
        model.toastHold = hovered
        if (hovered) {
            armed = true
            model.toastShown = true
        } else if (armed) {
            model.toastShown = false
        }
    }
    val drag = Modifier.moveWindow(window, model::placeToast) { TaskbarMedia.toastDrag = it }
    Row(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(22.dp))
            .background(Color.Black.copy(alpha = model.toastOpacity))
            .hoverable(interaction)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.08f)).then(drag),
        ) {
            val art = coverUrl(track?.thumbnail)
            if (art != null) AsyncImage(model = art, contentDescription = track?.title, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                track?.title ?: "Çalmıyor",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = drag,
            )
            Text(
                track?.artists?.ifBlank { "Metrolist" } ?: "Bir parça seç",
                color = Color.White.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = drag,
            )
            Slider(value = player.volume, onValueChange = player::changeVolume, modifier = Modifier.height(28.dp))
        }
        WidgetButton(enabled = track != null, onClick = player::previous) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Önceki", tint = Color.White)
        }
        WidgetButton(enabled = track != null, onClick = player::toggle, accent = true, tint = accent) {
            Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Oynat", tint = Color.Black)
        }
        WidgetButton(enabled = track != null, onClick = player::next) {
            Icon(Icons.Default.SkipNext, contentDescription = "Sonraki", tint = Color.White)
        }
    }
}

@Composable
fun FrameWindowScope.TaskbarDock(model: AppModel) {
    val player = model.player
    val track = player.current
    val drag = Modifier
        .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)))
        .pointerInput(Unit) {
                var origin = 0
                var originAlong = 0
                detectDragGestures(
                    onDragStart = {
                        TaskbarMedia.barDrag = true
                        val pointer = java.awt.MouseInfo.getPointerInfo().location
                        val bar = TaskbarMedia.taskbarBounds()
                        val horizontal = bar.width >= bar.height
                        origin = if (horizontal) pointer.x else pointer.y
                        originAlong = if (horizontal) window.x else window.y
                    },
                    onDragEnd = {
                        val bar = TaskbarMedia.taskbarBounds()
                        val along = if (bar.width >= bar.height) window.x else window.y
                        model.placeBar(along)
                        TaskbarMedia.barDrag = false
                    },
                    onDragCancel = { TaskbarMedia.barDrag = false },
                ) { change, _ ->
                    change.consume()
                    val pointer = java.awt.MouseInfo.getPointerInfo().location
                    val bar = TaskbarMedia.taskbarBounds()
                    if (bar.width >= bar.height) {
                        val x = (originAlong + pointer.x - origin).coerceIn(bar.x, (bar.x + bar.width - window.width).coerceAtLeast(bar.x))
                        window.setLocation(x, bar.y)
                    } else {
                        val y = (originAlong + pointer.y - origin).coerceIn(bar.y, (bar.y + bar.height - window.height).coerceAtLeast(bar.y))
                        window.setLocation(bar.x, y)
                    }
                }
            }
    Row(
        Modifier.fillMaxSize().background(Color(0xF0101014)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val art = coverUrl(track?.thumbnail)
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = track?.title,
                modifier = Modifier.padding(start = 6.dp).size(28.dp).clip(RoundedCornerShape(6.dp)).then(drag),
            )
        }
        Text(
            track?.title ?: "Metrolist",
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp).weight(1f).then(drag),
        )
        WidgetButton(enabled = track != null, onClick = player::previous, size = 28.dp) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Önceki", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        WidgetButton(enabled = track != null, onClick = player::toggle, accent = true, size = 28.dp) {
            Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = "Oynat", tint = Color.Black, modifier = Modifier.size(16.dp))
        }
        WidgetButton(enabled = track != null, onClick = player::next, size = 28.dp) {
            Icon(Icons.Default.SkipNext, contentDescription = "Sonraki", tint = Color.White, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun WidgetButton(
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Boolean = false,
    tint: Color = Color(0xFF1ED760),
    size: Dp = 36.dp,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .padding(horizontal = 2.dp)
            .size(size)
            .clip(CircleShape)
            .background(if (accent) tint else Color.White.copy(alpha = 0.10f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
