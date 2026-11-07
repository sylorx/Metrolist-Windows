package com.metrolist.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

class PlaybackController(
    private val resolver: StreamResolver,
    private val scope: CoroutineScope,
    quality: () -> String,
    private val onPlayed: (Track) -> Unit,
) {
    private val qualityOf = quality
    private val playGeneration = AtomicInteger()
    private val ffmpeg = FfmpegPlayer(
        onEnded = { scope.launch { onTrackEnded() } },
        onError = { message -> scope.launch { onStreamFailed(message) } },
    )

    var queue by mutableStateOf<List<Track>>(emptyList())
    var index by mutableStateOf(-1)
    var shuffle by mutableStateOf(false)
    var repeat by mutableStateOf(RepeatMode.Off)
    var positionMs by mutableStateOf(0L)
    var durationMs by mutableStateOf(0L)
    var isPlaying by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var volume by mutableStateOf(1f)
    var showQueue by mutableStateOf(false)
    var showLyrics by mutableStateOf(false)
    var lyrics by mutableStateOf<String?>(null)
    private var volumeBeforeMute = 1f
    private var activeGeneration = 0
    private var activeStartMs = 0L
    private var attempt = 0
    private var streams = emptyList<StreamResolver.Stream>()
    private var triedInnertube = false
    private var activeStream: StreamResolver.Stream? = null
    private var kept = false
    private var radioJob: Job? = null
    private var radioFor: String? = null

    val current: Track? get() = queue.getOrNull(index)

    fun playAll(tracks: List<Track>, start: Int) {
        if (tracks.isEmpty()) return
        queue = tracks
        index = start.coerceIn(0, tracks.lastIndex)
        startCurrent(0)
    }

    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        shuffle = true
        queue = tracks.shuffled()
        index = 0
        startCurrent(0)
    }

    fun toggle() {
        if (current == null) return
        if (isPlaying) {
            ffmpeg.pause()
            isPlaying = false
        } else {
            ffmpeg.resume()
            isPlaying = true
        }
    }

    fun next() = move(1)

    fun previous() {
        if (positionMs > 3_000) {
            seek(0)
            return
        }
        move(-1)
    }

    fun seek(ms: Long) {
        val track = current ?: return
        val limit = maxOf(track.durationMs, durationMs, ms)
        startCurrent(ms.coerceIn(0, limit))
    }

    fun jumpTo(queueIndex: Int) {
        if (queueIndex !in queue.indices) return
        index = queueIndex
        startCurrent(0)
    }

    fun changeVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
        ffmpeg.volume = volume
    }

    fun toggleMute() {
        if (volume > 0.001f) {
            volumeBeforeMute = volume
            changeVolume(0f)
        } else {
            changeVolume(volumeBeforeMute.coerceAtLeast(0.2f))
        }
    }

    fun toggleShuffle() {
        shuffle = !shuffle
    }

    fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.Off -> RepeatMode.All
            RepeatMode.All -> RepeatMode.One
            RepeatMode.One -> RepeatMode.Off
        }
    }

    fun toggleQueue() {
        showQueue = !showQueue
    }

    fun toggleLyrics() {
        showLyrics = !showLyrics
        if (showLyrics) current?.let { loadLyrics(it) }
    }

    fun poll() {
        val next = ffmpeg.positionMs
        if (kotlin.math.abs(next - positionMs) >= 250) positionMs = next
        val duration = ffmpeg.durationMs
        if (duration > 0 && duration != durationMs) durationMs = duration
        if (!kept && next > 3_000) {
            val track = current
            val stream = activeStream
            if (track != null && stream != null) {
                resolver.keep(track.id, qualityOf(), stream)
                kept = true
            }
        }
    }

    fun stop() {
        scope.launch(Dispatchers.IO) { ffmpeg.stop() }
        isPlaying = false
    }

    fun release() {
        radioJob?.cancel()
        radioJob = null
        playGeneration.incrementAndGet()
        activeGeneration = -1
        isPlaying = false
        busy = false
        ffmpeg.release()
    }

    private fun move(delta: Int) {
        if (queue.isEmpty()) return
        if (delta > 0 && repeat == RepeatMode.One) {
            startCurrent(0)
            return
        }
        val nextIndex = if (shuffle && queue.size > 1) {
            var pick = Random.nextInt(queue.size)
            if (pick == index) pick = (pick + 1) % queue.size
            pick
        } else {
            val raw = index + delta
            when {
                raw in queue.indices -> raw
                delta > 0 && repeat == RepeatMode.All -> 0
                delta < 0 && repeat == RepeatMode.All -> queue.lastIndex
                else -> return
            }
        }
        index = nextIndex
        startCurrent(0)
    }

    private fun onTrackEnded() {
        if (repeat == RepeatMode.One) {
            startCurrent(0)
            return
        }
        if (hasUpcoming()) {
            move(1)
            return
        }
        val job = radioJob
        if (job != null && job.isActive) {
            job.invokeOnCompletion {
                scope.launch {
                    if (hasUpcoming()) move(1) else isPlaying = false
                }
            }
        } else {
            isPlaying = false
        }
    }

    private fun hasUpcoming(): Boolean = when {
        queue.isEmpty() -> false
        shuffle -> queue.size > 1
        repeat == RepeatMode.All -> true
        else -> index < queue.lastIndex
    }

    private fun startCurrent(startMs: Long) {
        val track = current ?: return
        val generation = playGeneration.incrementAndGet()
        activeGeneration = generation
        activeStartMs = startMs
        attempt = 0
        streams = emptyList()
        triedInnertube = false
        activeStream = null
        kept = false
        busy = true
        error = null
        isPlaying = false
        positionMs = startMs
        durationMs = track.durationMs
        scope.launch {
            val found = runCatching {
                withContext(Dispatchers.IO) { resolver.openers(track.id, qualityOf()) }
            }.getOrDefault(emptyList())
            if (generation != playGeneration.get()) return@launch
            streams = found
            if (!launchAttempt(generation, track, startMs)) {
                if (!launchInnertube(generation, track, startMs)) giveUp("Çalınabilir akış bulunamadı")
            }
        }
    }

    private fun launchAttempt(generation: Int, track: Track, startMs: Long): Boolean {
        val stream = streams.getOrNull(attempt) ?: return false
        playStream(generation, track, stream, startMs)
        return true
    }

    private suspend fun launchInnertube(generation: Int, track: Track, startMs: Long): Boolean {
        if (triedInnertube) return false
        triedInnertube = true
        busy = true
        val stream = runCatching {
            withContext(Dispatchers.IO) { resolver.innertube(track.id, qualityOf()) }
        }.getOrNull() ?: return false
        if (generation != playGeneration.get()) return true
        streams = streams + stream
        attempt = streams.lastIndex
        playStream(generation, track, stream, startMs)
        return true
    }

    private fun playStream(generation: Int, track: Track, stream: StreamResolver.Stream, startMs: Long) {
        activeStream = stream
        scope.launch {
            val started = runCatching {
                withContext(Dispatchers.IO) {
                    if (generation != playGeneration.get()) return@withContext false
                    ffmpeg.volume = volume
                    ffmpeg.play(stream.url, stream.headers, startMs, track.durationMs)
                    true
                }
            }
            if (generation != playGeneration.get()) return@launch
            started.onSuccess { playing ->
                if (!playing) return@launch
                isPlaying = true
                busy = false
                error = null
                onPlayed(track)
                ensureRadio()
                if (showLyrics) loadLyrics(track) else lyrics = null
            }.onFailure { failure ->
                onStreamFailed(failure.message ?: "Akış açılamadı")
            }
        }
    }

    private fun onStreamFailed(message: String) {
        if (activeGeneration != playGeneration.get()) return
        val early = ffmpeg.positionMs < 2_000
        current?.let { resolver.forget(it.id, qualityOf()) }
        kept = false
        if (!early) {
            error = message
            busy = false
            isPlaying = false
            onTrackEnded()
            return
        }
        attempt++
        val track = current
        if (track != null && launchAttempt(activeGeneration, track, activeStartMs)) return
        if (track != null && !triedInnertube) {
            scope.launch {
                if (activeGeneration != playGeneration.get()) return@launch
                if (!launchInnertube(activeGeneration, track, activeStartMs)) giveUp(message)
            }
            return
        }
        giveUp(message)
    }

    private fun giveUp(message: String) {
        error = message
        busy = false
        isPlaying = false
        ensureRadio()
        val generation = activeGeneration
        scope.launch {
            delay(400)
            if (generation != playGeneration.get()) return@launch
            onTrackEnded()
        }
    }

    private fun ensureRadio() {
        val track = current ?: return
        if (queue.size - index - 1 >= 8) return
        if (radioFor == track.id && radioJob?.isActive == true) return
        radioFor = track.id
        val seed = track
        radioJob = scope.launch {
            val extra = runCatching { withContext(Dispatchers.IO) { similarTracks(seed) } }.getOrDefault(emptyList())
            if (extra.isEmpty() || current == null) return@launch
            val known = queue.map { it.id }.toSet()
            val fresh = extra.filter { it.id !in known }
            if (fresh.isNotEmpty()) queue = queue + fresh
        }
    }

    private suspend fun similarTracks(track: Track): List<Track> {
        val fromNext = YouTube.next(WatchEndpoint(videoId = track.id)).getOrNull()?.items.orEmpty().map { it.toTrack() }
        if (fromNext.count { it.id != track.id } >= 4) return fromNext
        val query = listOf(track.title, track.artists).filter { it.isNotBlank() }.joinToString(" ")
        val searched = if (query.isBlank()) {
            emptyList()
        } else {
            YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items?.mapNotNull { it.asTrack() }.orEmpty()
        }
        return fromNext + searched
    }

    private fun loadLyrics(track: Track) {
        lyrics = "Sözler yükleniyor…"
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                val endpoint = YouTube.next(WatchEndpoint(videoId = track.id)).getOrNull()?.lyricsEndpoint
                if (endpoint == null) null else YouTube.lyrics(endpoint).getOrNull()
            }
            if (current?.id == track.id) {
                lyrics = text?.takeIf { it.isNotBlank() } ?: "Söz bulunamadı"
            }
        }
    }
}
