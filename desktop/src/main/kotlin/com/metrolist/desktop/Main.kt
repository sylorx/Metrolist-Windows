package com.metrolist.desktop

import kotlinx.coroutines.flow.collectLatest

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.YouTubeLocale
import timber.log.Timber

fun main() {
    Timber.plant(object : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            if (priority >= 5) println("${tag ?: "Metrolist"}: $message")
        }
    })
    val instance = SingleInstance(appDir())
    if (!instance.acquire()) return
    YouTube.locale = YouTubeLocale(gl = "TR", hl = "tr")
    application {
        val model = androidx.compose.runtime.remember { AppModel() }
        val images = androidx.compose.runtime.remember {
            OkHttpClient.Builder().addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                        .header("Accept", "image/jpeg,image/png,image/*;q=0.8")
                        .header("Referer", "https://music.youtube.com/")
                        .build(),
                )
            }.build()
        }
        setSingletonImageLoaderFactory { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory(callFactory = { images })) }
                .diskCache {
                    DiskCache.Builder()
                        .directory(appDir().resolve("image-cache").toOkioPath())
                        .maxSizeBytes(64L * 1024 * 1024)
                        .build()
                }
                .build()
        }
        var showSplash by remember { mutableStateOf(true) }
        var toastLive by remember { mutableStateOf(false) }
        LaunchedEffect(model.toastShown) {
            if (model.toastShown) toastLive = true
        }
        var shown by remember { mutableStateOf(true) }
        var raiseRequest by remember { mutableStateOf(0) }
        fun quit() {
            model.rememberPlayback()
            model.player.stop()
            exitApplication()
        }
        fun reveal() { shown = true; raiseRequest++ }
        // Application level: these keep working while the main window is hidden in the tray.
        val current = model.player.current
        val playing = model.player.isPlaying
        LaunchedEffect(Unit) {
            TaskbarMedia.bind { command ->
                when (command) {
                    TaskbarMedia.PREVIOUS -> model.player.previous()
                    TaskbarMedia.TOGGLE -> model.player.toggle()
                    TaskbarMedia.NEXT -> model.player.next()
                    TaskbarMedia.WIDGET -> model.setWidget(!model.widgetOn)
                    TaskbarMedia.SHOW -> reveal()
                    TaskbarMedia.EXIT -> quit()
                }
            }
            while (true) {
                kotlinx.coroutines.delay(700)
                if (instance.takeShowRequest()) reveal()
            }
        }
        LaunchedEffect(playing, current) {
            TaskbarMedia.sync(playing, current != null, current?.title.orEmpty())
            MediaKeys.sync(playing, current != null)
            MediaKeys.describe(current)
        }
        if (!showSplash) {
            var opened by remember { mutableStateOf(true) }
            LaunchedEffect(current?.id, model.toastOn) {
                if (opened) {
                    opened = false
                    return@LaunchedEffect
                }
                if (!model.toastOn || current == null) {
                    model.toastShown = false
                    return@LaunchedEffect
                }
                model.toastShown = true
                kotlinx.coroutines.delay(model.toastSeconds * 1000L)
                if (!model.toastHold) model.toastShown = false
            }
        }
        if (showSplash) {
            Window(
                onCloseRequest = ::exitApplication,
                title = "Metrolist",
                transparent = true,
                undecorated = true,
                resizable = false,
                alwaysOnTop = true,
                state = rememberWindowState(
                    width = 520.dp,
                    height = 200.dp,
                    position = WindowPosition(Alignment.Center),
                ),
            ) {
                BounceSplash(model.look.palette.accent) { showSplash = false }
            }
        } else {
            val saved = model.store.settings
            val windowState = rememberWindowState(
                placement = if (saved.windowMaximized) WindowPlacement.Maximized else WindowPlacement.Floating,
                position = if (model.placeVisible(saved.windowX, saved.windowY)) WindowPosition(saved.windowX.dp, saved.windowY.dp)
                    else WindowPosition(Alignment.Center),
                width = saved.windowWidth.coerceIn(720, 8000).dp,
                height = saved.windowHeight.coerceIn(480, 5000).dp,
            )
            fun close() {
                // Closing keeps the music playing in the tray when there is one.
                if (TaskbarMedia.trayAvailable()) shown = false else quit()
            }
            Window(
                onCloseRequest = ::close,
                title = "Metrolist",
                undecorated = true,
                visible = shown,
                state = windowState,
                onKeyEvent = { shortcut(model, it) },
            ) {
                LaunchedEffect(raiseRequest) {
                    if (raiseRequest == 0) return@LaunchedEffect
                    windowState.isMinimized = false
                    window.isVisible = true
                    window.toFront()
                    window.requestFocus()
                }
                LaunchedEffect(Unit) {
                    window.minimumSize = java.awt.Dimension(720, 480)
                    androidx.compose.runtime.snapshotFlow { Triple(windowState.position, windowState.size, windowState.placement) }
                        .collectLatest { (position, size, placement) ->
                            kotlinx.coroutines.delay(600)
                            if (windowState.isMinimized || !position.isSpecified) return@collectLatest
                            model.saveWindow(
                                position.x.value.toInt(), position.y.value.toInt(),
                                size.width.value.toInt(), size.height.value.toInt(),
                                placement != WindowPlacement.Floating,
                            )
                        }
                }
                LaunchedEffect(Unit) {
                    TaskbarMedia.attach(window)
                    MediaKeys.install(model.player, window)
                    TaskbarMedia.customFrame(window)
                }
                LaunchedEffect(Unit) {
                    TaskbarMedia.sync(model.player.isPlaying, model.player.current != null, model.player.current?.title.orEmpty())
                }
                val track = model.player.current
                val barTitle = if (model.captionText == "song" && track != null) {
                    listOf(track.title, track.artists).filter { it.isNotBlank() }.joinToString(" — ").ifBlank { "Metrolist" }
                } else {
                    "Metrolist"
                }
                val colors = captionColors(model.caption, model.look.palette.accent, model.captionStart, model.captionEnd)
                val density = LocalDensity.current
                SideEffect {
                    TaskbarMedia.captionBarPx = with(density) { 40.dp.roundToPx() }
                    TaskbarMedia.captionButtonsPx = with(density) { 138.dp.roundToPx() }
                }
                LaunchedEffect(barTitle) { window.title = barTitle }
                val edge = model.look.palette.ink.toArgb()
                val awtEdge = java.awt.Color((edge shr 16) and 0xFF, (edge shr 8) and 0xFF, edge and 0xFF)
                SideEffect {
                    window.background = awtEdge
                    window.rootPane.background = awtEdge
                    window.rootPane.border = null
                    window.contentPane.background = awtEdge
                }
                LaunchedEffect(colors, model.captionCorner, edge) {
                    TaskbarMedia.paintCaption(window, colors.first().toArgb())
                    TaskbarMedia.paintEdge(window, edge)
                    TaskbarMedia.windowCorner(window, model.captionCorner)
                }
                Column(Modifier.fillMaxSize().background(model.look.palette.ink)) {
                    CaptionBar(
                        title = barTitle,
                        colors = colors,
                        maximized = windowState.placement == WindowPlacement.Maximized,
                        onMinimize = { windowState.isMinimized = true },
                        onMaximize = {
                            windowState.placement = if (windowState.placement == WindowPlacement.Maximized) {
                                WindowPlacement.Floating
                            } else {
                                WindowPlacement.Maximized
                            }
                        },
                        onClose = ::close,
                    )
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        MetrolistTheme(model.look) {
                            MetrolistApp(model)
                        }
                    }
                }
            }
            if (model.widgetOn) {
                Window(
                    onCloseRequest = {},
                    title = "",
                    transparent = true,
                    undecorated = true,
                    resizable = false,
                    alwaysOnTop = false,
                    state = rememberWindowState(width = 430.dp, height = 112.dp),
                ) {
                    LaunchedEffect(Unit) {
                        val area = TaskbarMedia.desktopBounds()
                        val saved = model.widgetX in area.x..(area.x + area.width - 80) && model.widgetY in area.y..(area.y + area.height - 40)
                        val x = if (saved) model.widgetX else (area.x + area.width - 460).coerceAtLeast(area.x + 24)
                        val y = if (saved) model.widgetY else (area.y + area.height - 220).coerceAtLeast(area.y + 24)
                        window.setLocation(x, y)
                        TaskbarMedia.asDesktopWidget(window)
                        window.setLocation(x, y)
                        kotlinx.coroutines.delay(200)
                        window.setLocation(x, y)
                        model.placeWidget(window.x, window.y)
                    }
                    DesktopWidget(model)
                }
            }
            if (toastLive) {
                val scale = model.toastScale
                Window(
                    onCloseRequest = { model.toastShown = false },
                    title = "",
                    transparent = true,
                    undecorated = true,
                    resizable = false,
                    alwaysOnTop = true,
                    state = rememberWindowState(width = (460 * scale).dp, height = (118 * scale).dp),
                ) {
                    val fade = remember { Animatable(0f) }
                    LaunchedEffect(model.toastShown) {
                        if (model.toastShown) fade.animateTo(1f, tween(650))
                        else {
                            fade.animateTo(0f, tween(650))
                            toastLive = false
                        }
                    }
                    LaunchedEffect(scale, model.toastX, model.toastY) {
                        if (TaskbarMedia.toastDrag) return@LaunchedEffect
                        val width = window.width
                        val height = window.height
                        val area = TaskbarMedia.desktopBounds()
                        if (model.toastX >= area.x && model.toastY >= area.y) {
                            window.setLocation(model.toastX, model.toastY)
                        } else {
                            val bar = TaskbarMedia.taskbarBounds()
                            window.setLocation(((bar.x + bar.width - width) / 2).coerceAtLeast(8), (bar.y - height - 16).coerceAtLeast(8))
                        }
                        TaskbarMedia.raise(window)
                    }
                    Box(
                        Modifier.fillMaxSize().graphicsLayer {
                            alpha = fade.value
                            translationY = (1f - fade.value) * 24f
                        },
                    ) {
                        TrackToast(model)
                    }
                }
            }
            if (model.barOn) {
                Window(
                    onCloseRequest = {},
                    title = "",
                    undecorated = true,
                    resizable = false,
                    alwaysOnTop = true,
                    state = rememberWindowState(width = 320.dp, height = 48.dp),
                ) {
                    fun stick() {
                        val bar = TaskbarMedia.taskbarBounds()
                        val horizontal = bar.width >= bar.height
                        val length = (280 * model.barScale).toInt().coerceIn(180, if (horizontal) bar.width else bar.height)
                        val thick = (if (horizontal) bar.height else bar.width).coerceIn(24, 96)
                        val span = (if (horizontal) bar.width else bar.height) - length
                        val start = if (horizontal) bar.x else bar.y
                        val along = if (model.barX >= 0) model.barX.coerceIn(start, start + span.coerceAtLeast(0)) else start + span.coerceAtLeast(0) / 2
                        if (horizontal) TaskbarMedia.cling(window, along, bar.y, length, thick)
                        else TaskbarMedia.cling(window, bar.x, along, thick, length)
                    }
                    LaunchedEffect(model.barScale, model.barX) {
                        // Showing the window resets its place once, and the taskbar itself can move or resize later.
                        while (true) {
                            if (!TaskbarMedia.barDrag) runCatching { stick() }
                            kotlinx.coroutines.delay(1_500)
                        }
                    }
                    TaskbarDock(model)
                }
            }
        }
    }
}

@Composable
private fun BounceSplash(color: Color, onFinished: () -> Unit) {
    val travel = remember { Animatable(0f) }
    val title = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        travel.animateTo(1f, tween(1500, easing = LinearEasing))
        title.animateTo(1f, tween(320))
        kotlinx.coroutines.delay(700)
        onFinished()
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Transparent), contentAlignment = Alignment.Center) {
        val hops = 4
        val played = travel.value.coerceIn(0f, 1f)
        val span = (played * hops).coerceAtMost(hops - 0.0001f)
        val hop = span.toInt()
        val step = span - hop
        val across = (hop + step) / hops
        val arc = kotlin.math.sin(step * Math.PI).toFloat()
        val landed = step < 0.14f || step > 0.86f
        val widthPx = with(LocalDensity.current) { (maxWidth - 48.dp).toPx() }
        val liftPx = with(LocalDensity.current) { 48.dp.toPx() }
        if (title.value < 1f) {
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = (across - 0.5f) * widthPx
                        translationY = -arc * liftPx
                        val squash = if (landed) 1f else 0f
                        scaleX = 1f + squash * 0.38f
                        scaleY = 1f - squash * 0.3f + arc * 0.18f
                        alpha = 1f - title.value
                    }
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Text(
            "TEKİLA MUSİC",
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.graphicsLayer { alpha = title.value },
        )
    }
}
