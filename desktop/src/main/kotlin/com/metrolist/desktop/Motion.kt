package com.metrolist.desktop

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode as AnimationRepeat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** False when the user turned animations off; every effect below then shows its final state at once. */
internal val LocalMotion = staticCompositionLocalOf { true }

/** Three dancing bars for the row that is playing; they settle when playback pauses. */
@Composable
internal fun EqualizerBars(playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val moving = playing && LocalMotion.current
    val strength by animateFloatAsState(if (moving) 1f else 0f, tween(260), label = "bars")
    val phase = if (strength > 0f) {
        rememberInfiniteTransition(label = "bars").animateFloat(
            0f, 2f * PI.toFloat(), infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase",
        )
    } else null
    Canvas(modifier.size(16.dp)) {
        val bar = size.width / 5f
        val time = phase?.value ?: 0f
        repeat(3) { at ->
            val wave = 0.55f + 0.45f * sin(time * (at + 1) + at * 1.9f)
            val tall = size.height * (0.25f + (wave * 0.75f - 0.0f) * strength).coerceIn(0.2f, 1f)
            drawRoundRect(color, Offset(at * bar * 2f, size.height - tall), Size(bar, tall), CornerRadius(bar / 2))
        }
    }
}

/** Slides and fades the first rows of a page in, once; scrolling back never replays it. */
internal fun Modifier.appear(index: Int): Modifier = composed {
    if (index > 11 || !LocalMotion.current) return@composed this
    var done by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (done) return@LaunchedEffect
        delay(index * 32L)
        progress.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
        done = true
    }
    graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 22f
    }
}

/** A light band travelling over placeholder blocks while a page loads. */
internal fun Modifier.shimmer(shape: Shape): Modifier = composed {
    val base = LocalPalette.current.card
    if (!LocalMotion.current) return@composed clip(shape).background(base)
    val sweep = rememberInfiniteTransition(label = "shimmer").animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "sweep",
    )
    clip(shape).background(base).drawWithCache {
        onDrawBehind {
            val x = size.width * sweep.value
            drawRect(
                Brush.linearGradient(
                    listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                    start = Offset(x, 0f),
                    end = Offset(x + size.width * 0.6f, size.height),
                ),
            )
        }
    }
}

@Composable
internal fun SkeletonRows(count: Int = 8) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).shimmer(RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Box(Modifier.width((180 + it * 37 % 120).dp).height(13.dp).shimmer(CircleShape))
                    Box(Modifier.width((90 + it * 29 % 70).dp).height(10.dp).shimmer(CircleShape))
                }
            }
        }
    }
}

@Composable
internal fun SkeletonShelf(rows: Int = 2) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        repeat(rows) {
            Box(Modifier.width(220.dp).height(20.dp).shimmer(CircleShape))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(6) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(172.dp).shimmer(RoundedCornerShape(8.dp)))
                        Box(Modifier.width(120.dp).height(12.dp).shimmer(CircleShape))
                    }
                }
            }
        }
    }
}

@Composable
internal fun SkeletonHeader(circle: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(168.dp).shimmer(if (circle) CircleShape else RoundedCornerShape(12.dp)))
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.width(280.dp).height(28.dp).shimmer(CircleShape))
            Box(Modifier.width(160.dp).height(14.dp).shimmer(CircleShape))
            Box(Modifier.size(48.dp).shimmer(CircleShape))
        }
    }
}

/** Play and pause turn into each other instead of switching. */
@Composable
internal fun PlayPauseIcon(playing: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val motion = LocalMotion.current
    val turn by animateFloatAsState(if (playing) 90f else 0f, if (motion) spring(0.65f, 420f) else snap(), label = "turn")
    AnimatedContent(
        playing,
        transitionSpec = {
            if (motion) (fadeIn(tween(160)) + scaleIn(spring(0.55f, 500f), 0.4f)) togetherWith (fadeOut(tween(110)) + scaleOut(tween(140), 0.4f))
            else fadeIn(snap()) togetherWith fadeOut(snap())
        },
        label = "playPause",
    ) { on ->
        Icon(
            if (on) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (on) "Duraklat" else "Oynat",
            tint = tint,
            modifier = modifier.graphicsLayer { rotationZ = if (on) 90f - turn else -turn * 0.5f },
        )
    }
}

/** A heart that jumps and throws sparks when a song is liked. */
@Composable
internal fun HeartButton(liked: Boolean, enabled: Boolean = true, size: Dp = 24.dp, onClick: () -> Unit) {
    val motion = LocalMotion.current
    val accent = LocalPalette.current.accent
    val scale = remember { Animatable(1f) }
    val burst = remember { Animatable(1f) }
    var clicked by remember { mutableStateOf(false) }
    val tint by animateColorAsState(if (liked) accent else LocalPalette.current.muted, tween(200), label = "heart")
    LaunchedEffect(liked) {
        if (!clicked || !motion) return@LaunchedEffect
        clicked = false
        if (liked) launch { burst.snapTo(0f); burst.animateTo(1f, tween(620, easing = FastOutSlowInEasing)) }
        scale.snapTo(if (liked) 0.6f else 1.2f)
        scale.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMediumLow))
    }
    IconButton(onClick = { clicked = true; onClick() }, enabled = enabled) {
        Box(contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(size)) {
                val progress = burst.value
                if (progress >= 1f) return@Canvas
                repeat(8) { at ->
                    val angle = at * (2 * PI / 8).toFloat() - PI.toFloat() / 2
                    val reach = this.size.minDimension * (0.45f + 0.65f * progress)
                    drawCircle(
                        accent.copy(alpha = 1f - progress),
                        radius = 2.4.dp.toPx() * (1f - progress * 0.6f),
                        center = center + Offset(cos(angle) * reach, sin(angle) * reach),
                    )
                }
            }
            Icon(
                if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (liked) "Beğeniyi kaldır" else "Beğen",
                tint = if (enabled) tint else tint.copy(alpha = 0.38f),
                modifier = Modifier.size(size).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
            )
        }
    }
}

/** Two soft lights that drift slowly behind the now-playing view. */
@Composable
internal fun AmbientLights(color: Color, modifier: Modifier) {
    val moving = LocalMotion.current
    val drift = if (moving) {
        rememberInfiniteTransition(label = "lights").animateFloat(
            0f, 1f, infiniteRepeatable(tween(14_000, easing = FastOutSlowInEasing), AnimationRepeat.Reverse), label = "drift",
        )
    } else null
    val shade by animateColorAsState(color, tween(900), label = "ambient")
    Canvas(modifier) {
        val at = drift?.value ?: 0.4f
        val radius = max(size.width, size.height) * 0.62f
        drawCircle(
            Brush.radialGradient(listOf(shade.copy(alpha = 0.55f), Color.Transparent), Offset(size.width * (0.15f + 0.2f * at), size.height * (0.2f + 0.15f * at)), radius),
            radius, Offset(size.width * (0.15f + 0.2f * at), size.height * (0.2f + 0.15f * at)),
        )
        drawCircle(
            Brush.radialGradient(listOf(shade.copy(alpha = 0.32f), Color.Transparent), Offset(size.width * (0.95f - 0.25f * at), size.height * (0.9f - 0.2f * at)), radius),
            radius, Offset(size.width * (0.95f - 0.25f * at), size.height * (0.9f - 0.2f * at)),
        )
    }
}

/** The liveliest colour of a cover, readable on the dark shell; falls back to the accent. */
@Composable
internal fun rememberAmbient(url: String?): Color {
    val fallback = LocalPalette.current.accent
    var color by remember { mutableStateOf(fallback) }
    LaunchedEffect(url, fallback) {
        if (url == null) { color = fallback; return@LaunchedEffect }
        val found = runCatching {
            withContext(Dispatchers.IO) {
                val context = PlatformContext.INSTANCE
                val result = SingletonImageLoader.get(context).execute(ImageRequest.Builder(context).data(url).size(48).build())
                val bitmap = (result as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
                val step = max(1, min(bitmap.width, bitmap.height) / 24)
                val pixels = buildList {
                    for (y in 0 until bitmap.height step step) for (x in 0 until bitmap.width step step) add(bitmap.getColor(x, y))
                }
                liveliest(pixels)
            }
        }.getOrNull()
        color = found?.let { Color(it) } ?: fallback
    }
    return color
}

/** Averages pixels weighted by saturation, then keeps the result in a range that glows on black. */
internal fun liveliest(pixels: List<Int>): Int? {
    if (pixels.isEmpty()) return null
    var red = 0.0; var green = 0.0; var blue = 0.0; var total = 0.0
    val hsb = FloatArray(3)
    pixels.forEach { pixel ->
        val r = pixel shr 16 and 255; val g = pixel shr 8 and 255; val b = pixel and 255
        java.awt.Color.RGBtoHSB(r, g, b, hsb)
        val weight = 0.02 + hsb[1].toDouble() * hsb[1] * hsb[2]
        red += r * weight; green += g * weight; blue += b * weight; total += weight
    }
    java.awt.Color.RGBtoHSB((red / total).toInt(), (green / total).toInt(), (blue / total).toInt(), hsb)
    return java.awt.Color.HSBtoRGB(hsb[0], (hsb[1] * 1.15f).coerceAtMost(0.85f), hsb[2].coerceIn(0.45f, 0.85f))
}

/** Stable unique keys for lists that may contain the same song more than once. */
internal fun <T> occurrenceKeys(items: List<T>, id: (T) -> String): List<String> {
    val seen = HashMap<String, Int>()
    return items.map { item ->
        val name = id(item)
        val count = seen.merge(name, 1, Int::plus) ?: 1
        "$name#$count"
    }
}
