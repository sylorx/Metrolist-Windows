package com.metrolist.desktop

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

fun loadImage(path: String): ImageBitmap? = runCatching {
    val bytes = Thread.currentThread().contextClassLoader.getResourceAsStream(path)?.use { it.readBytes() } ?: return null
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
}.getOrNull()

data class Genre(val id: String, val title: String, val query: String, val art: String)

val exploreGenres = listOf(
    Genre("pop", "Pop", "türkçe pop", "genres/pop.jpg"),
    Genre("rap", "Rap", "türkçe rap", "genres/rap.jpg"),
    Genre("rock", "Rock", "türkçe rock", "genres/rock.jpg"),
    Genre("arabesk", "Arabesk", "arabesk", "genres/arabesk.jpg"),
    Genre("halk", "Halk", "türk halk müziği", "genres/halk.jpg"),
    Genre("caz", "Caz", "caz", "genres/caz.jpg"),
    Genre("elektronik", "Elektronik", "elektronik müzik", "genres/elektronik.jpg"),
    Genre("akustik", "Akustik", "akustik", "genres/akustik.jpg"),
)
