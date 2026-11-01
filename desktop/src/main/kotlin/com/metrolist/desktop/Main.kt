package com.metrolist.desktop

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
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
            Window(
                onCloseRequest = {
                    model.player.stop()
                    exitApplication()
                },
                title = "Metrolist",
                state = rememberWindowState(width = 1280.dp, height = 820.dp),
            ) {
                MetrolistTheme(model.look) {
                    MetrolistApp(model)
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
