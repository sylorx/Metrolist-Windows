package com.metrolist.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * Public Invidious, then Piped. Both hand back a proxy URL that plays without a YouTube session.
 */
internal object FreeStreams {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val pipedHosts = mutableListOf(
        "https://pipedapi.dedyn.io",
        "https://api.piped.private.coffee",
    )
    private val fallbackInvidious = listOf("https://yt.chocolatemoo53.com")
    @Volatile private var invidious: List<String> = fallbackInvidious
    @Volatile private var invidiousUntilMs = 0L

    fun resolve(videoId: String, quality: String): StreamResolver.Stream? = resolveAll(videoId, quality).firstOrNull()

    fun resolveAll(videoId: String, quality: String): List<StreamResolver.Stream> {
        if (!VIDEO_ID.matches(videoId)) return emptyList()
        val found = LinkedHashMap<String, StreamResolver.Stream>()
        for (host in invidiousHosts()) {
            val stream = runCatching { fromInvidious(host, videoId, quality) }.getOrNull() ?: continue
            found.putIfAbsent(stream.url, stream)
            if (found.size >= 2) break
        }
        val piped = synchronized(pipedHosts) { pipedHosts.toList() }
        for (host in piped) {
            val stream = runCatching { fromPiped(host, videoId, quality) }.getOrNull() ?: continue
            synchronized(pipedHosts) {
                pipedHosts.remove(host)
                pipedHosts.add(0, host)
            }
            found.putIfAbsent(stream.url, stream)
            if (found.size >= 3) break
        }
        return found.values.toList()
    }

    private fun invidiousHosts(): List<String> {
        val now = System.currentTimeMillis()
        if (now < invidiousUntilMs && invidious.isNotEmpty()) return invidious
        val discovered = runCatching { discoverInvidious() }.getOrDefault(emptyList())
        val hosts = (discovered + fallbackInvidious).distinct()
        invidious = hosts
        invidiousUntilMs = now + 30 * 60 * 1000L
        return hosts
    }

    private fun discoverInvidious(): List<String> {
        val body = get("https://api.invidious.io/instances.json?sort_by=health") ?: return emptyList()
        return json.parseToJsonElement(body).jsonArray.mapNotNull { row ->
            val info = row.jsonArray.getOrNull(1)?.jsonObject ?: return@mapNotNull null
            val uri = info["uri"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val open = info["api"]?.jsonPrimitive?.booleanOrNull == true
            val https = info["type"]?.jsonPrimitive?.contentOrNull == "https"
            if (open && https) uri.trimEnd('/') else null
        }.take(4)
    }

    private fun fromInvidious(host: String, videoId: String, quality: String): StreamResolver.Stream? {
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

    private fun fromPiped(host: String, videoId: String, quality: String): StreamResolver.Stream? {
        val body = get("$host/streams/$videoId") ?: return null
        val root = json.parseToJsonElement(body).jsonObject
        val audio = candidates(root, "audioStreams")
        val progressive = candidates(root, "videoStreams").filter { !it.videoOnly && it.mime.startsWith("video/") }
        val picked = pick(audio, quality) ?: pick(progressive, quality) ?: return null
        return stream(picked.url)
    }

    private fun get(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use null
            val body = response.body?.string().orEmpty()
            if (body.length < 20) null else body
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
