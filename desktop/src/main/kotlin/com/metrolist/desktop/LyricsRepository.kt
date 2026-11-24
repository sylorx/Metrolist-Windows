package com.metrolist.desktop

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Serializable data class LyricLine(val timeMs: Long, val text: String)
@Serializable data class LyricsDocument(
    val plain: String = "",
    val lines: List<LyricLine> = emptyList(),
    val source: String = "",
    val savedAt: Long = System.currentTimeMillis(),
)

internal fun parseLrc(text: String): List<LyricLine> {
    val stamp = Regex("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]")
    // A positive offset makes the lyrics appear sooner.
    val offset = Regex("\\[offset:([+-]?\\d+)]", RegexOption.IGNORE_CASE).find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0
    return text.lineSequence().flatMap { line ->
        val words = line.replace(stamp, "").trim()
        stamp.findAll(line).mapNotNull { match ->
            val seconds = match.groupValues[2].toLong()
            if (seconds >= 60) return@mapNotNull null
            val fraction = match.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0
            LyricLine((match.groupValues[1].toLong() * 60_000 + seconds * 1000 + fraction - offset).coerceAtLeast(0), words)
        }
    }.sortedBy { it.timeMs }.toList()
}

/** Older Turkish LRC files are often Windows-1254 rather than UTF-8. */
internal fun decodeText(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
} catch (_: java.nio.charset.CharacterCodingException) {
    String(bytes, java.nio.charset.Charset.forName("windows-1254"))
}

/** Video titles carry words that lyric databases do not know. */
internal fun lyricTitle(title: String): String = title
    .replace(Regex("[(\\[][^)\\]]*(official|video|audio|lyric|visuali[sz]er|klip|remaster|feat\\.?|ft\\.)[^)\\]]*[)\\]]", RegexOption.IGNORE_CASE), "")
    .replace(Regex("\\s+"), " ").trim().ifBlank { title }

internal class LyricsRepository {
    @Serializable private data class RemoteLyrics(
        val plainLyrics: String? = null, val syncedLyrics: String? = null, val instrumental: Boolean = false,
        val duration: Double? = null,
    ) {
        fun document() = LyricsDocument(plainLyrics ?: if (instrumental) "Enstrümantal" else "", parseLrc(syncedLyrics.orEmpty()), "LRCLIB")
    }
    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS).build()
    private val mutex = Mutex()
    private var nextRequestAt = 0L

    private fun request(url: okhttp3.HttpUrl): String? = runCatching {
        client.newCall(Request.Builder().url(url)
            .header("User-Agent", "Metrolist/${AppVersion.NAME} (https://github.com/${AppVersion.REPO})").build()).execute().use { response ->
            nextRequestAt = System.currentTimeMillis() + 500
            if (response.code == 429 || response.code == 503) {
                val seconds = response.header("Retry-After")?.toLongOrNull() ?: 60
                nextRequestAt = System.currentTimeMillis() + seconds.coerceAtLeast(1) * 1000 + kotlin.random.Random.nextLong(500, 1500)
            }
            if (response.isSuccessful) response.body?.string() else null
        }
    }.getOrNull()

    suspend fun load(track: Track): LyricsDocument = withContext(Dispatchers.IO) {
        track.localPath?.let { path ->
            val file = File(path)
            val lrc = File(file.parentFile, file.nameWithoutExtension + ".lrc")
            if (lrc.isFile) {
                val text = decodeText(lrc.readBytes())
                return@withContext LyricsDocument(text, parseLrc(text), "Yerel LRC")
            }
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(track.id.toByteArray()).joinToString("") { "%02x".format(it) }
        val cache = File(appDir(), "lyrics/$hash.json")
        runCatching { json.decodeFromString<LyricsDocument>(cache.readText()) }.getOrNull()?.let {
            if (System.currentTimeMillis() - it.savedAt < 7L * 24 * 60 * 60 * 1000) return@withContext it
        }
        val synced = mutex.withLock {
            // Honor throttling without holding a request open or blocking subsequent playback.
            if (nextRequestAt - System.currentTimeMillis() > 2000) return@withLock null
            delay((nextRequestAt - System.currentTimeMillis()).coerceAtLeast(0))
            val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", track.title)
                .addQueryParameter("artist_name", track.artists)
            if (track.album.isNotBlank()) url.addQueryParameter("album_name", track.album)
            if (track.durationMs in 1000..3_600_000) url.addQueryParameter("duration", (track.durationMs / 1000).toString())
            val exact = request(url.build())?.let { runCatching { json.decodeFromString<RemoteLyrics>(it) }.getOrNull() }
            if (exact != null) return@withLock exact.document()
            // The exact lookup misses extra artists, "(Official Video)" and album differences; search tolerates them.
            if (nextRequestAt - System.currentTimeMillis() > 2000) return@withLock null
            delay((nextRequestAt - System.currentTimeMillis()).coerceAtLeast(0))
            val search = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", lyricTitle(track.title))
                .addQueryParameter("artist_name", leadArtist(track.artists)).build()
            val candidates = request(search)?.let { runCatching { json.decodeFromString<List<RemoteLyrics>>(it) }.getOrNull() }.orEmpty()
            val seconds = track.durationMs / 1000.0
            candidates.filter { seconds < 1 || it.duration == null || kotlin.math.abs(it.duration - seconds) <= 4 }
                .sortedByDescending { !it.syncedLyrics.isNullOrBlank() }.firstOrNull()?.document()
        }
        val result = if (synced != null && (synced.plain.isNotBlank() || synced.lines.isNotEmpty())) synced else {
            val plain = if (track.localPath != null) null else runCatching {
                YouTube.next(WatchEndpoint(videoId = track.id)).getOrNull()?.lyricsEndpoint?.let { YouTube.lyrics(it).getOrNull() }
            }.getOrNull()
            LyricsDocument(plain.orEmpty(), source = if (plain.isNullOrBlank()) "" else "YouTube Music")
        }
        if (result.plain.isNotBlank() || result.lines.isNotEmpty()) runCatching { atomicWrite(cache, json.encodeToString(result), backup = false) }
        result
    }
}
