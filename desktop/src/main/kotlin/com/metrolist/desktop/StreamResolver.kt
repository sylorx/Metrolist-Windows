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
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
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

    override fun saved(videoId: String, quality: String): Stream? = cached("$videoId|$quality")

    /**
     * Public proxies and the direct source are asked at the same time, because either one can be blocked or
     * overloaded on a given network. A direct source is only offered after its media server has answered.
     */
    override suspend fun openers(videoId: String, quality: String): List<Stream> = coroutineScope {
        val free = async { FreeStreams.resolveAll(videoId, quality) }
        val direct = async { withTimeoutOrNull(7_000) { innertube(videoId, quality) } }
        val first = select<List<Stream>> {
            free.onAwait { it }
            direct.onAwait { listOfNotNull(it) }
        }
        val fast = if (first.isEmpty()) null else if (direct.isCompleted) direct.await() else withTimeoutOrNull(1_200) { direct.await() }
        val proxies = if (free.isCompleted) free.await() else if (fast == null) free.await() else emptyList()
        val late = if (fast == null && proxies.isEmpty()) direct.await() else null
        coroutineContext.cancelChildren()
        (listOfNotNull(fast ?: late) + proxies).distinctBy { it.url }
    }

    override suspend fun innertube(videoId: String, quality: String): Stream? {
        for (override in clientOverrides()) {
            try {
                extract(videoId, audioQuality(quality), override)?.let { found -> MediaHosts.reachable(found)?.let { return it } }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                // The next client may still succeed.
            }
        }
        return null
    }

    private fun audioQuality(quality: String) = when (quality) {
        "HIGH" -> AudioQuality.HIGH
        "LOW" -> AudioQuality.LOW
        else -> AudioQuality.AUTO
    }

    private fun clientOverrides(): List<String?> =
        if (YouTube.cookie.isNullOrBlank()) listOf("ANDROID_VR_NO_AUTH", null, "ANDROID_VR_1_65_10")
        else listOf(null, "ANDROID_VR_NO_AUTH")

    override suspend fun resolve(videoId: String, quality: String): Stream {
        val key = "$videoId|$quality"
        cached(key)?.let { return it }
        return openers(videoId, quality).firstOrNull() ?: throw IllegalStateException("Çalınabilir akış bulunamadı")
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
        // Drop the entry closest to expiry, never the one just added.
        if (cache.size > 48) cache.entries.filter { it.key != key }.minByOrNull { it.value.untilMs }?.let { cache.remove(it.key) }
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

/**
 * YouTube names two media servers in every address. The first is often a cache inside the internet provider's
 * network and can be unreachable while the second one works, so both are tried at once.
 */
internal object MediaHosts {
    private const val COOL_DOWN_MS = 10 * 60 * 1000L
    private val unreachable = ConcurrentHashMap<String, Long>()
    private val http = okhttp3.OkHttpClient.Builder()
        .connectTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(4, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /** The same signed address on each server it is valid for, preferred one first. */
    fun candidates(url: String): List<String> {
        val parsed = url.toHttpUrlOrNull() ?: return listOf(url)
        if (!parsed.host.endsWith(".googlevideo.com")) return listOf(url)
        val number = parsed.queryParameter("fvip")?.takeIf { it.toIntOrNull() != null } ?: "1"
        val hosts = parsed.queryParameter("mn").orEmpty().split(',').filter { NODE.matches(it) }
            .map { "rr$number---$it.googlevideo.com" }
            .filterNot { it.substringAfter("---") == parsed.host.substringAfter("---") }
        // The address is signed, so only the host part of the text may change.
        return (listOf(url) + hosts.map { url.replaceFirst("://${parsed.host}", "://$it") }).distinct()
    }

    suspend fun reachable(stream: StreamResolver.Stream): StreamResolver.Stream? {
        val all = candidates(stream.url)
        if (all.size == 1 && all.single().toHttpUrlOrNull()?.host?.endsWith(".googlevideo.com") != true) return stream
        val now = System.currentTimeMillis()
        val ordered = all.sortedBy { (unreachable[node(it)] ?: 0L) > now }
        val answered = raceSources(
            ordered.mapIndexed<String, suspend () -> String?> { at, url ->
                {
                    // The preferred server keeps a short head start, so a healthy one is always used.
                    delay(at * 350L)
                    url.takeIf { answers(it, stream.headers) }
                }
            },
            want = 1, graceMs = 0, deadlineMs = 5_500,
        ).firstOrNull() ?: return null
        ordered.takeWhile { it != answered }.forEach { unreachable[node(it)] = now + COOL_DOWN_MS }
        unreachable.remove(node(answered))
        return stream.copy(url = answered)
    }

    private fun node(url: String) = url.toHttpUrlOrNull()?.host?.substringAfter("---").orEmpty()

    private suspend fun answers(url: String, headers: Map<String, String>): Boolean {
        val request = runCatching {
            okhttp3.Request.Builder().url(url).apply { headers.forEach { (name, value) -> header(name, value) } }
                .header("Range", "bytes=0-1").build()
        }.getOrNull() ?: return false
        val call = http.newCall(request)
        return suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.success(false))
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    val ok = response.use { it.code == 206 || it.code == 200 }
                    if (continuation.isActive) continuation.resumeWith(Result.success(ok))
                }
            })
        }
    }

    private val NODE = Regex("^sn-[a-z0-9-]+$")
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
