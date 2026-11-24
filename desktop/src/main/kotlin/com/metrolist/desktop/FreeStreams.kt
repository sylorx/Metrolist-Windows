package com.metrolist.desktop

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.math.abs

/**
 * Asks every source at once and returns results in arrival order. One slow or dead server never delays
 * the others: after the first answer only [graceMs] is spent collecting alternatives, and [deadlineMs]
 * bounds the whole wait.
 */
internal suspend fun <T : Any> raceSources(
    sources: List<suspend () -> T?>,
    want: Int,
    graceMs: Long,
    deadlineMs: Long,
): List<T> = coroutineScope {
    if (sources.isEmpty()) return@coroutineScope emptyList()
    val answers = Channel<T?>(Channel.UNLIMITED)
    sources.forEach { source ->
        launch {
            answers.send(
                try { source() } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { null },
            )
        }
    }
    val found = mutableListOf<T>()
    var waiting = sources.size
    withTimeoutOrNull(deadlineMs) {
        while (waiting > 0 && found.isEmpty()) { answers.receive()?.let(found::add); waiting-- }
    }
    if (found.isNotEmpty()) withTimeoutOrNull(graceMs) {
        while (waiting > 0 && found.size < want) { answers.receive()?.let(found::add); waiting-- }
    }
    coroutineContext.cancelChildren()
    found
}

/**
 * Public Invidious, then Piped. Both hand back a proxy URL that plays without a YouTube session.
 */
internal object FreeStreams {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val pipedHosts = listOf(
        "https://pipedapi.dedyn.io",
        "https://api.piped.private.coffee",
    )
    private val fallbackInvidious = listOf("https://yt.chocolatemoo53.com")
    @Volatile private var invidious: List<String> = fallbackInvidious
    @Volatile private var invidiousUntilMs = 0L
    private val discovery = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val discovering = AtomicBoolean()

    suspend fun resolveAll(videoId: String, quality: String): List<StreamResolver.Stream> {
        if (!VIDEO_ID.matches(videoId)) return emptyList()
        refreshInvidious()
        val sources = invidious.map<String, suspend () -> StreamResolver.Stream?> { host -> { fromInvidious(host, videoId, quality) } } +
            pipedHosts.map<String, suspend () -> StreamResolver.Stream?> { host -> { fromPiped(host, videoId, quality) } }
        return raceSources(sources, want = 3, graceMs = 500, deadlineMs = 8_000).distinctBy { it.url }
    }

    /** The instance directory is optional and often unreachable, so playback never waits for it. */
    private fun refreshInvidious() {
        if (System.currentTimeMillis() < invidiousUntilMs || !discovering.compareAndSet(false, true)) return
        discovery.launch {
            try {
                val discovered = runCatching { discoverInvidious() }.getOrDefault(emptyList())
                invidious = (discovered + fallbackInvidious).distinct()
                invidiousUntilMs = System.currentTimeMillis() + 30 * 60 * 1000L
            } finally {
                discovering.set(false)
            }
        }
    }

    private suspend fun discoverInvidious(): List<String> {
        val body = get("https://api.invidious.io/instances.json?sort_by=health") ?: return emptyList()
        return json.parseToJsonElement(body).jsonArray.mapNotNull { row ->
            val info = row.jsonArray.getOrNull(1)?.jsonObject ?: return@mapNotNull null
            val uri = info["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val open = info["api"]?.jsonPrimitive?.booleanOrNull == true
            val https = info["type"]?.jsonPrimitive?.contentOrNull == "https"
            if (open && https) uri.trimEnd('/') else null
        }.take(4)
    }

    private suspend fun fromInvidious(host: String, videoId: String, quality: String): StreamResolver.Stream? {
        val body = get("$host/api/v1/videos/$videoId?local=true") ?: return null
        val formats = json.parseToJsonElement(body).jsonObject["adaptiveFormats"]?.jsonArray.orEmpty()
        val audio = formats.mapNotNull { element ->
            val item = element.jsonObject
            val type = item["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (!type.startsWith("audio/")) return@mapNotNull null
            val url = absolute(host, item["url"]?.jsonPrimitive?.contentOrNull) ?: return@mapNotNull null
            Candidate(url, type.substringBefore(';'), item["bitrate"]?.jsonPrimitive?.intOrNull ?: 0, false)
        }
        val picked = pick(audio.filter { it.mime.startsWith("audio/mp4") }, quality)
            ?: pick(audio, quality)
            ?: return null
        return stream(picked.url)
    }

    private suspend fun fromPiped(host: String, videoId: String, quality: String): StreamResolver.Stream? {
        val body = get("$host/streams/$videoId") ?: return null
        val root = json.parseToJsonElement(body).jsonObject
        val audio = candidates(root, "audioStreams")
        val progressive = candidates(root, "videoStreams").filter { !it.videoOnly && it.mime.startsWith("video/") }
        val picked = pick(audio, quality) ?: pick(progressive, quality) ?: return null
        return stream(picked.url)
    }

    private suspend fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        val call = http.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resume(null)
                }

                override fun onResponse(call: Call, response: Response) {
                    val body = runCatching { response.use { if (it.isSuccessful) it.body?.string() else null } }.getOrNull()
                    if (continuation.isActive) continuation.resume(body?.takeIf { it.length >= 20 })
                }
            })
        }
    }

    private fun candidates(root: kotlinx.serialization.json.JsonObject, name: String): List<Candidate> =
        root[name]?.jsonArray.orEmpty().mapNotNull { element ->
            val item = element.jsonObject
            val url = item["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val mime = item["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (mime.contains("mpegurl") || url.contains(".m3u8")) return@mapNotNull null
            Candidate(
                url = url,
                mime = mime.substringBefore(';'),
                bitrate = item["bitrate"]?.jsonPrimitive?.intOrNull ?: 0,
                videoOnly = item["videoOnly"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        }

    private fun pick(items: List<Candidate>, quality: String): Candidate? {
        val usable = items.filter { it.bitrate > 0 }.ifEmpty { items }
        if (usable.isEmpty()) return null
        return when (quality) {
            "LOW" -> usable.minBy { it.bitrate }
            "HIGH" -> usable.maxBy { it.bitrate }
            else -> usable.minBy { abs(it.bitrate - 128_000) }
        }
    }

    private fun absolute(host: String, url: String?): String? = when {
        url.isNullOrBlank() -> null
        url.startsWith("http") -> url
        url.startsWith("/") -> host.trimEnd('/') + url
        else -> null
    }

    private fun stream(url: String) = StreamResolver.Stream(
        url = url,
        headers = mapOf("User-Agent" to USER_AGENT),
    )

    private data class Candidate(val url: String, val mime: String, val bitrate: Int, val videoOnly: Boolean)

    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{11}$")
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
}
