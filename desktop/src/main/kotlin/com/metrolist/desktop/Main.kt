package com.metrolist.desktop

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
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
