package com.metrolist.desktop

import com.metrolist.innertube.YouTube
import com.metrolist.innertubex.InnerTubeLogger
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.AudioQuality
import com.metrolist.innertubex.extraction.ContentHints
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.PoTokenResult
import com.metrolist.innertubex.extraction.TokenProvider
import com.metrolist.innertubex.extraction.TokenProviderCapabilities
import com.metrolist.innertubex.extraction.YtConfigParser
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import com.metrolist.innertubex.extraction.generateClientPlaybackNonce
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

class StreamResolver(dir: File) : PlaybackStreams {
    data class Stream(val url: String, val headers: Map<String, String>)

    private val configRepository = FilePlayerConfigRepository(dir)
    private val logger = InnerTubeLogger { _ -> }
    private val tokenProvider = object : TokenProvider {
        override val capabilities = TokenProviderCapabilities(providers = emptySet(), usesWebView = false)
        override suspend fun getPoToken(videoId: String, visitorData: String, cookie: String?): PoTokenResult? = null
        override suspend fun close() = Unit
    }
    private val mutex = Mutex()
    private var bundle: Bundle? = null
    private val cache = ConcurrentHashMap<String, CachedStream>()

    suspend fun prepare() {
        bundle()
    }

    override fun forget(videoId: String, quality: String) {
        cache.remove("$videoId|$quality")
    }

    override fun keep(videoId: String, quality: String, stream: Stream) {
        remember("$videoId|$quality", stream)
    }

    override suspend fun openers(videoId: String, quality: String): List<Stream> {
        val saved = cached("$videoId|$quality")
        val free = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            FreeStreams.resolveAll(videoId, quality)
        }
        return (listOfNotNull(saved) + free).distinctBy { it.url }
    }

    override suspend fun innertube(videoId: String, quality: String): Stream? {
        val audioQuality = when (quality) {
            "HIGH" -> AudioQuality.HIGH
            "LOW" -> AudioQuality.LOW
            else -> AudioQuality.AUTO
        }
        val guest = YouTube.cookie.isNullOrBlank()
        val overrides = if (guest) {
            listOf("ANDROID_VR_NO_AUTH", null, "ANDROID_VR_1_65_10")
        } else {
            listOf(null, "ANDROID_VR_NO_AUTH")
        }
        for (override in overrides) {
            val stream = runCatching { extract(videoId, audioQuality, override) }.getOrNull()
            if (stream != null) return stream
        }
        return null
    }

    override suspend fun resolve(videoId: String, quality: String): Stream {
        val key = "$videoId|$quality"
        cached(key)?.let { return it }
        FreeStreams.resolve(videoId, quality)?.let { stream ->
            return stream
        }
        val audioQuality = when (quality) {
            "HIGH" -> AudioQuality.HIGH
            "LOW" -> AudioQuality.LOW
            else -> AudioQuality.AUTO
        }
        val guest = YouTube.cookie.isNullOrBlank()
        val overrides = if (guest) {
            listOf("ANDROID_VR_NO_AUTH", null, "ANDROID_VR_1_65_10")
        } else {
            listOf(null, "ANDROID_VR_NO_AUTH")
        }
        var last: Exception? = null
        for (override in overrides) {
            try {
                val stream = extract(videoId, audioQuality, override) ?: continue
                remember(key, stream)
                return stream
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                last = error
            }
        }
        throw last ?: IllegalStateException("Çalınabilir akış bulunamadı")
    }

    private suspend fun extract(videoId: String, audioQuality: AudioQuality, override: String?): Stream? {
        val hints = ContentHints()
            .withStreamCapabilities(allowHls = false, allowSabr = false, allowBoundedRange = true)
            .let { base ->
                if (override == null) base else base.copy(playbackClientOverrideId = override)
            }
        val stream = bundle().extractor.extract(
            videoId = videoId,
            hints = hints,
            excludedClients = emptySet(),
            audioQuality = audioQuality,
            clientPlaybackNonce = generateClientPlaybackNonce(),
        ) ?: return null
        check(stream.sabrBootstrap == null) { "Bu akış türü masaüstünde desteklenmiyor" }
        var url = stream.audioUrl
        val length = stream.contentLengthBytes
        if (stream.requireBoundedRange && length != null && length > 0 && "&range=" !in url) {
            url = "$url&range=0-${length - 1}"
        }
        return Stream(url, stream.headers)
    }

    private fun remember(key: String, stream: Stream) {
        cache[key] = CachedStream(stream, streamCacheDeadline(stream.url, System.currentTimeMillis()))
        if (cache.size > 48) cache.keys.firstOrNull()?.let { cache.remove(it) }
    }

    private fun cached(key: String): Stream? {
        val hit = cache[key] ?: return null
        if (hit.untilMs <= System.currentTimeMillis()) {
            cache.remove(key, hit)
            return null
        }
        return hit.stream
    }

    private class CachedStream(val stream: Stream, val untilMs: Long)

    private suspend fun bundle(): Bundle {
        val transport = YouTube.extractionTransport()
        bundle?.takeIf { it.generation == transport.generation }?.let { return it }
        return mutex.withLock {
            val latest = YouTube.extractionTransport()
            bundle?.takeIf { it.generation == latest.generation }?.let { return@withLock it }
            runCatching { bundle?.cipher?.dispose() }
            val remoteStore = RemotePlayerConfigStore(latest.httpClient, configRepository, logger)
            val cipher = YouTubeCipherService(latest.httpClient, remoteStore, logger)
            val parser = YtConfigParserImpl(latest.httpClient, latest.innerTube, remoteStore, logger)
                .withEmbeddedConfigFallback()
            val extractor = InnerTubeExtractor(
                configParser = parser,
                cipherService = cipher,
                innerTube = latest.innerTube,
                tokenProvider = tokenProvider,
                logger = logger,
            )
            Bundle(latest.generation, cipher, extractor).also { bundle = it }
        }
    }

    private class Bundle(
        val generation: Long,
        val cipher: YouTubeCipherService,
        val extractor: InnerTubeExtractor,
    )
}

private fun YtConfigParser.withEmbeddedConfigFallback(): YtConfigParser =
    object : YtConfigParser by this {
        override suspend fun fetchConfig(videoId: String, useLoginCookies: Boolean) =
            try {
                this@withEmbeddedConfigFallback.fetchConfig(videoId, useLoginCookies)
            } catch (_: IllegalStateException) {
                this@withEmbeddedConfigFallback.fetchEmbeddedConfig(videoId, useLoginCookies = false)
            }
    }

private class FilePlayerConfigRepository(dir: File) : PlayerConfigRepository {
    private val file = File(dir, "player-config.properties")
    private val props = Properties().also { properties ->
        if (file.exists()) file.inputStream().use { properties.load(it) }
    }

    override val enabled: Boolean = true
    override val sourceUrl: String = CONFIG_URL
    override val defaultSourceUrl: String = CONFIG_URL
    override var cachedJson: String
        get() = props.getProperty("json", "")
        set(value) = put("json", value)
    override var cachedAtMs: Long
        get() = props.getProperty("cached_at_ms")?.toLongOrNull() ?: 0L
        set(value) = put("cached_at_ms", value.toString())
    override var cachedSourceUrl: String
        get() = props.getProperty("source_url", "")
        set(value) = put("source_url", value)
    override var cachedEtag: String
        get() = props.getProperty("etag", "")
        set(value) = put("etag", value)

    private fun put(key: String, value: String) {
        props.setProperty(key, value)
        file.parentFile?.mkdirs()
        file.outputStream().use { props.store(it, null) }
    }

    private companion object {
        const val CONFIG_URL =
            "https://raw.githubusercontent.com/ZemerTeam/zemer-cipher/master/library/src/main/assets/player_configs.json"
    }
}

/** Signed URLs may expire before the normal cache lifetime. Leave time to open them. */
internal fun streamCacheDeadline(url: String, nowMs: Long): Long {
    val normal = nowMs + 2 * 60 * 60 * 1000L
    val expirySeconds = url.toHttpUrlOrNull()?.queryParameter("expire")?.toLongOrNull()
        ?.takeIf { it in 1..(Long.MAX_VALUE / 1000) } ?: return normal
    return minOf(normal, expirySeconds * 1000 - 60_000)
}
