package com.metrolist.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.WatchEndpoint
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class PlaybackController(
    private val resolver: PlaybackStreams,
    private val scope: CoroutineScope,
    private val quality: () -> String,
    private val onPlayed: (Track) -> Unit,
    private val onListening: (ListeningSession) -> Unit = {},
    private val autoplay: () -> Boolean = { true },
    private val engineFactory: (() -> Unit, (String) -> Unit) -> PlaybackEngine = { ended, failed -> FfmpegPlayer(ended, failed) },
    private val clock: () -> Long = System::currentTimeMillis,
    private val autoSkipped: (Track) -> Boolean = { false },
    private val report: (PlaybackEvent) -> Unit = {},
) {
    private val radioIds = mutableSetOf<String>()
    private val generation = AtomicInteger()
    private var ffmpeg = newAudio()
    private var prepared: Prepared? = null
    private var preparing: Job? = null
    private var fading: PlaybackEngine? = null
    private var fadeStartedAt = 0L
    private var fadeDurationMs = 0L
    private var resolution: Job? = null
    private var radioJob: Job? = null
    private val lyricsRepository = LyricsRepository()
    private var lyricsJob: Job? = null
    private var live = false
    private var streams = emptyList<StreamResolver.Stream>()
    private var fromCache = false
    /** What the user wants once the pending source opens. Survives retries, so a pause is never undone. */
    private var holdPaused by mutableStateOf(false)
    private var lyricsTrackId: String? = null
    private var attempt = 0
    private var triedInnertube = false
    private var activeStream: StreamResolver.Stream? = null
    private var startAt = 0L
    private var recoveryCount = 0
    private var startedSessionAt = 0L
    private var sessionTrack: Track? = null
    private var listenedMs = 0L
    private var lastPollAt = clock()
    private var lastAudioPosition = 0L
    private var fadePauseAt = 0L
    private var volumeBeforeMute = 1f
    private var sleepFadeActive = false
    private var prefetchFailedFor: String? = null
    private var cachedActive = false
    private var openingSince = 0L

    private companion object { const val OPEN_LIMIT_MS = 9_000L }

    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var index by mutableStateOf(-1)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableStateOf(RepeatMode.Off)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    var durationMs by mutableStateOf(0L)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    /** True while a source is opening and the user already asked for it to stay paused. */
    var pendingPause by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var volume by mutableStateOf(1f)
        private set
    var showQueue by mutableStateOf(false)
    var showLyrics by mutableStateOf(false)
    var lyrics by mutableStateOf<String?>(null)
        private set
    var lyricDocument by mutableStateOf<LyricsDocument?>(null)
        private set
    var audio by mutableStateOf(AudioSettings())
        private set
    var sleepDeadline by mutableStateOf<Long?>(null)
        private set
    var sleepAfterTrack by mutableStateOf(false)
        private set
    var sleepRemainingMs by mutableStateOf(0L)
        private set
    /** What the play button should show: the intention, even while the source is still opening. */
    val wantsPlay: Boolean get() = isPlaying || (busy && !holdPaused)
    val current: Track? get() = queue.getOrNull(index)
    private data class Prepared(val index: Int, val track: Track, val player: PlaybackEngine, val stream: StreamResolver.Stream)

    private fun newAudio(): PlaybackEngine {
        lateinit var engine: PlaybackEngine
        engine = engineFactory(
            { scope.launch { if (ffmpeg === engine) onTrackEnded() } },
            { message -> scope.launch {
                if (ffmpeg === engine) onStreamFailed(message)
                else if (prepared?.player === engine) {
                    prefetchFailedFor = prepared?.track?.id
                    prepared = null
                }
            } },
        )
        return engine
    }

    fun playAll(tracks: List<Track>, start: Int) {
        if (tracks.isEmpty()) return
        radioIds.clear()
        val selected = start.coerceIn(tracks.indices)
        if (shuffle) {
            queue = listOf(tracks[selected]) + tracks.filterIndexed { i, _ -> i != selected }.shuffled()
            index = 0
        } else { queue = tracks.toList(); index = selected }
        startCurrent(0)
    }
    fun playShuffled(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        radioIds.clear()
        shuffle = true; queue = tracks.shuffled(); index = 0
        startCurrent(0)
    }
    fun restore(memory: PlaybackMemory) {
        if (memory.tracks.isEmpty()) return
        queue = memory.tracks.map { it.toTrack() }
        index = memory.index.coerceIn(queue.indices)
        positionMs = memory.positionMs.coerceAtLeast(0)
        durationMs = current?.durationMs ?: 0
        shuffle = memory.shuffle
        repeat = runCatching { RepeatMode.valueOf(memory.repeat) }.getOrDefault(RepeatMode.Off)
        changeVolume(memory.volume)
    }
    fun memory() = PlaybackMemory(queue.map { it.toSaved().copy(addedAt = 0) }, index.coerceAtLeast(0), positionMs, shuffle, repeat.name, volume)

    fun applyBlocks() {
        cancelPrepared()
        val edit = queueAfterBlock(queue, index, radioIds, autoSkipped)
        queue = edit.tracks; index = edit.index
        radioIds.retainAll(edit.tracks.map { it.id }.toSet())
        when { edit.restart -> startCurrent(0); edit.tracks.isEmpty() -> stop() }
    }
    fun enqueue(tracks: List<Track>, next: Boolean = false) {
        if (tracks.isEmpty()) return
        if (queue.isEmpty()) { queue = tracks.toList(); index = 0; durationMs = current?.durationMs ?: 0; return }
        cancelPrepared()
        val at = if (next) index + 1 else queue.size
        queue = queue.toMutableList().apply { addAll(at, tracks) }
    }
    fun moveQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        cancelPrepared()
        queue = queue.toMutableList().apply { add(to, removeAt(from)) }
        index = movedIndex(index, from, to)
    }
    fun removeQueue(at: Int) {
        if (at !in queue.indices) return
        cancelPrepared()
        val removedCurrent = at == index
        queue = queue.toMutableList().apply { removeAt(at) }
        if (queue.isEmpty()) { stop(); index = -1; durationMs = 0; positionMs = 0; return }
        if (at < index) index--
        else if (removedCurrent) {
            index = index.coerceAtMost(queue.lastIndex)
            startCurrent(0, pauseOnReady = !isPlaying)
        }
    }
    fun clearQueue() {
        stop(); queue = emptyList(); index = -1; positionMs = 0; durationMs = 0; error = null
        forgetLyrics()
    }
    /** Removes everything after the current track. */
    fun clearUpcoming() {
        if (index < 0 || index >= queue.lastIndex) return
        cancelPrepared()
        radioJob?.cancel(); radioJob = null
        queue = queue.take(index + 1)
    }
    fun pause() { if (isPlaying || (busy && !holdPaused)) toggle() }
    fun toggle() {
        if (current == null) return
        if (!live) {
            // A source is still being opened: change what happens when it is ready instead of starting over.
            if (busy) { holdPaused = !holdPaused; pendingPause = holdPaused; return }
            startCurrent(positionMs); return
        }
        if (isPlaying) {
            poll(); ffmpeg.pause(); fading?.pause(); isPlaying = false; holdPaused = true
            fadePauseAt = clock()
        } else {
            if (fadePauseAt > 0) fadeStartedAt += clock() - fadePauseAt
            fadePauseAt = 0
            ffmpeg.resume(); fading?.resume(); isPlaying = true; holdPaused = false
            lastPollAt = clock(); lastAudioPosition = ffmpeg.positionMs
        }
    }
    fun next() {
        val next = upcomingIndex() ?: return
        if (!activatePrepared(next)) { index = next; startCurrent(0) }
    }
    fun previous() {
        if (positionMs > 3000) { seek(0); return }
        val previous = if (index > 0) index - 1 else if (repeat == RepeatMode.All) queue.lastIndex else return
        index = previous; startCurrent(0)
    }
    fun seek(ms: Long) {
        if (current == null) return
        val limit = maxOf(durationMs, current?.durationMs ?: 0)
        val target = if (limit > 0) ms.coerceIn(0, limit) else ms.coerceAtLeast(0)
        if (live) {
            // Count what was heard before the jump; polling may also end or switch the track.
            val before = current
            poll()
            if (!live || current !== before) return
        }
        if (live && ffmpeg.seek(target)) {
            cancelFade()
            positionMs = target; startAt = target
            lastAudioPosition = target; lastPollAt = clock()
            return
        }
        startCurrent(target, pauseOnReady = if (busy && !live) holdPaused else !isPlaying, reuse = true)
    }
    fun seekBy(deltaMs: Long) = seek((positionMs + deltaMs).coerceAtLeast(0))
    fun jumpTo(queueIndex: Int) { if (queueIndex in queue.indices) { index = queueIndex; startCurrent(0) } }
    fun changeVolume(value: Float) {
        volume = if (value.isFinite()) value.coerceIn(0f, 1f) else 1f
        syncAudio()
    }
    fun toggleMute() {
        if (volume > 0.001f) { volumeBeforeMute = volume; changeVolume(0f) }
        else changeVolume(volumeBeforeMute.coerceAtLeast(0.2f))
    }
    fun toggleShuffle() {
        shuffle = !shuffle
        if (shuffle && index >= 0) {
            cancelPrepared()
            queue = queue.take(index + 1) + queue.drop(index + 1).shuffled()
        }
    }
    fun cycleRepeat() {
        repeat = RepeatMode.entries[(repeat.ordinal + 1) % RepeatMode.entries.size]
        cancelPrepared()
    }
    fun toggleQueue() { showQueue = !showQueue }
    fun requestLyrics() { current?.let { loadLyrics(it) } }
    fun toggleLyrics() { showLyrics = !showLyrics; if (showLyrics) current?.let { loadLyrics(it) } }
    /** Opens the sound again on the current output device, at the same place and in the same play state. */
    fun reopenOutput(pause: Boolean = false) {
        if (current == null) return
        if (pause && wantsPlay) toggle()
        if (live || busy) startCurrent(positionMs, pauseOnReady = !wantsPlay, reuse = true)
    }
    fun configureAudio(value: AudioSettings) {
        val previousDevice = audio.device
        audio = Settings(audio = value).validated().audio
        cancelPrepared()
        syncAudio()
        if (previousDevice != audio.device && live) startCurrent(positionMs, pauseOnReady = !isPlaying)
    }
    private fun syncAudio() {
        val gain = if (sleepFadeActive) (sleepRemainingMs / 10_000f).coerceIn(0f, 1f) else 1f
        ffmpeg.audio = audio; ffmpeg.volume = volume * gain
        prepared?.player?.let { it.audio = audio; it.volume = volume * gain }
        fading?.let { it.audio = audio; it.volume = volume * gain }
    }

    fun setSleep(minutes: Int? = null, afterTrack: Boolean = false) {
        sleepDeadline = minutes?.takeIf { it > 0 }?.let { clock() + it * 60_000L }
        sleepAfterTrack = afterTrack
        sleepRemainingMs = minutes?.times(60_000L) ?: 0L
        sleepFadeActive = false
        syncAudio()
        cancelPrepared()
    }
    private fun pollSleep(now: Long): Boolean {
        val deadline = sleepDeadline ?: return false
        sleepRemainingMs = (deadline - now).coerceAtLeast(0)
        sleepFadeActive = sleepRemainingMs <= 10_000
        syncAudio()
        if (sleepRemainingMs > 0) return false
        stop(); setSleep(); return true
    }
    fun poll() {
        val now = clock()
        if (pollSleep(now)) return
        val next = ffmpeg.positionMs
        val elapsed = (now - lastPollAt).coerceIn(0, 1500)
        val advanced = next - lastAudioPosition
        if (isPlaying && live && advanced in 1..2000) listenedMs += minOf(elapsed, advanced)
        lastPollAt = now; lastAudioPosition = next
        if (live && abs(next - positionMs) >= 100) positionMs = next
        if (ffmpeg.durationMs > 0) durationMs = ffmpeg.durationMs
        if (live && !ffmpeg.ready && busy && current?.localPath == null && now - openingSince > OPEN_LIMIT_MS) {
            onStreamFailed("timeout")
            return
        }
        if (live && ffmpeg.ready) {
            if (busy) report(PlaybackEvent(now, current?.title.orEmpty(), "Çalıyor", sourceLabel(activeStream), now - openingSince))
            busy = false
            val track = current
            val stream = activeStream
            if (!cachedActive && track != null && track.localPath == null && stream != null && positionMs > 3000) {
                resolver.keep(track.id, quality(), stream); cachedActive = true
            }
        }
        if (isPlaying && fading != null) {
            val fraction = ((now - fadeStartedAt).toFloat() / fadeDurationMs.coerceAtLeast(1)).coerceIn(0f, 1f)
            // Equal power keeps the loudness steady through the middle of the overlap.
            ffmpeg.envelope = if (fraction >= 1f) 1f else sin(fraction * PI.toFloat() / 2)
            fading?.envelope = cos(fraction * PI.toFloat() / 2)
            if (fraction >= 1) { val old = fading; fading = null; scope.launch(Dispatchers.IO) { old?.stop() } }
        }
        val remaining = durationMs - positionMs
        if (isPlaying && live && repeat != RepeatMode.One && !sleepAfterTrack) {
            if (remaining in 1..maxOf(30_000L, audio.crossfadeSeconds * 1000L + 5000)) prepareNext()
            if (audio.crossfadeSeconds > 0 && remaining in 1..audio.crossfadeSeconds * 1000L) {
                upcomingIndex()?.let { activatePrepared(it, remaining) }
            }
        }
    }
    fun flushListening() {
        val track = sessionTrack
        if (track != null && listenedMs > 0) onListening(ListeningSession(track.toSaved(), startedSessionAt, listenedMs))
        listenedMs = 0
        startedSessionAt = clock()
    }
    fun stop() {
        generation.incrementAndGet(); resolution?.cancel(); resolution = null
        cancelPrepared(); flushListening(); sessionTrack = null
        val old = ffmpeg
        ffmpeg = newAudio(); syncAudio()
        scope.launch(Dispatchers.IO) { old.stop() }
        radioJob?.cancel(); radioJob = null
        live = false; busy = false; isPlaying = false; pendingPause = false
    }
    private fun upcomingIndex(): Int? = when {
        index < 0 || queue.isEmpty() -> null
        index < queue.lastIndex -> index + 1
        repeat == RepeatMode.All -> 0
        else -> null
    }
    private fun onTrackEnded() {
        if (sleepAfterTrack) { stop(); setSleep(); return }
        if (repeat == RepeatMode.One) { startCurrent(0); return }
        val next = upcomingIndex()
        if (next != null) { if (!activatePrepared(next)) { index = next; startCurrent(0) }; return }
        if (radioJob?.isActive == true) {
            val token = generation.get()
            scope.launch { radioJob?.join(); if (token == generation.get()) {
                val upcoming = upcomingIndex()
                if (upcoming != null) { index = upcoming; startCurrent(0) } else { flushListening(); isPlaying = false; live = false }
            } }
        } else { flushListening(); isPlaying = false; live = false }
    }
    private fun startCurrent(startMs: Long, pauseOnReady: Boolean = false, recovering: Boolean = false, reuse: Boolean = false) {
        val track = current ?: return
        flushListening()
        sessionTrack = track; startedSessionAt = clock()
        val token = generation.incrementAndGet()
        resolution?.cancel(); cancelPrepared()
        radioJob?.cancel(); radioJob = null
        val old = ffmpeg; ffmpeg = newAudio(); syncAudio()
        scope.launch(Dispatchers.IO) { old.stop() }
        if (!recovering) recoveryCount = 0
        cachedActive = false
        holdPaused = pauseOnReady; pendingPause = pauseOnReady
        val known = activeStream?.takeIf { reuse && track.localPath == null }
        attempt = 0; streams = emptyList(); triedInnertube = false; activeStream = null; fromCache = false; startAt = startMs
        busy = true; error = null; isPlaying = false; live = false
        positionMs = startMs; durationMs = track.durationMs; lastAudioPosition = startMs
        prefetchFailedFor = null
        if (lyricsTrackId != track.id) forgetLyrics()
        val openedAt = clock()
        resolution = scope.launch {
            try {
                val quick = if (track.localPath != null) StreamResolver.Stream(track.localPath, emptyMap())
                    else known ?: resolver.saved(track.id, quality())
                fromCache = quick != null && track.localPath == null
                val found = if (quick != null) listOf(quick) else withContext(Dispatchers.IO) { resolver.openers(track.id, quality()) }
                if (token != generation.get()) return@launch
                streams = found
                report(PlaybackEvent(clock(), track.title, "Kaynak arandı", "${found.size} kaynak", clock() - openedAt))
                if (!launchAttempt(token, track) && !launchInnertube(token, track)) giveUp("Çalınabilir akış bulunamadı")
            } catch (cancel: CancellationException) { throw cancel }
            catch (failure: Exception) { if (token == generation.get()) giveUp(failure.message ?: "Akış açılamadı") }
        }
    }
    private fun launchAttempt(token: Int, track: Track): Boolean {
        val stream = streams.getOrNull(attempt) ?: return false
        playStream(token, track, stream)
        return true
    }
    /** A remembered address can die early; ask the servers again before falling back further. */
    private suspend fun launchFresh(token: Int, track: Track): Boolean {
        if (token != generation.get()) return true
        if (!fromCache) return false
        fromCache = false
        val fresh = withContext(Dispatchers.IO) { resolver.openers(track.id, quality()) }
        if (token != generation.get()) return true
        streams = streams + fresh.filterNot { it in streams }
        return launchAttempt(token, track)
    }
    private suspend fun launchInnertube(token: Int, track: Track): Boolean {
        if (token != generation.get()) return true
        if (triedInnertube || track.localPath != null) return false
        triedInnertube = true
        val stream = withContext(Dispatchers.IO) { resolver.innertube(track.id, quality()) } ?: return false
        if (token != generation.get()) return true
        streams = streams + stream; attempt = streams.lastIndex
        playStream(token, track, stream)
        return true
    }
    private fun playStream(token: Int, track: Track, stream: StreamResolver.Stream) {
        activeStream = stream
        val engine = ffmpeg
        scope.launch {
            val opening = holdPaused
            val outcome = runCatching { withContext(Dispatchers.IO) {
                if (token == generation.get()) engine.play(stream.url, stream.headers, startAt, track.durationMs, opening)
            } }
            if (token != generation.get()) return@launch
            outcome.onSuccess {
                // The user may have changed their mind while the source was opening.
                if (holdPaused != opening) { if (holdPaused) engine.pause() else engine.resume() }
                live = true; isPlaying = !holdPaused; pendingPause = false; error = null
                lastPollAt = clock(); lastAudioPosition = startAt
                openingSince = clock()
                if (startAt == 0L) onPlayed(track)
                if (!holdPaused) ensureRadio()
                if (showLyrics) loadLyrics(track)
            }.onFailure { onStreamFailed(it.message ?: "Akış açılamadı") }
        }
    }
    private fun onStreamFailed(message: String) {
        val track = current ?: return
        val token = generation.get()
        report(PlaybackEvent(clock(), track.title, "Akış hatası", playbackErrorMessage(message), 0, failed = true))
        if (track.localPath != null) { giveUp("Yerel dosya açılamadı: $message"); return }
        resolver.forget(track.id, quality())
        val reached = ffmpeg.positionMs
        if (reached > startAt + 2000 && recoveryCount < 2) {
            recoveryCount++
            startCurrent(reached, pauseOnReady = holdPaused, recovering = true)
            error = "Bağlantı yenileniyor (${recoveryCount}/2)…"
            return
        }
        startAt = maxOf(startAt, reached)
        attempt++
        busy = true; isPlaying = false; live = false; pendingPause = holdPaused
        if (launchAttempt(token, track)) return
        scope.launch {
            try {
                if (!launchFresh(token, track) && !launchInnertube(token, track) && token == generation.get()) giveUp(message)
            }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (token == generation.get()) giveUp(message) }
        }
    }
    private fun giveUp(message: String) {
        // A source that opens after all must not start sounding behind an error message.
        val late = ffmpeg
        ffmpeg = newAudio(); syncAudio()
        scope.launch(Dispatchers.IO) { late.stop() }
        busy = false; isPlaying = false; live = false; pendingPause = false; error = playbackErrorMessage(message)
        flushListening()
        report(PlaybackEvent(clock(), current?.title.orEmpty(), "Çalınamadı", playbackErrorMessage(message), 0, failed = true))
        // Explicit retry remains available; a failure never spins through a repeat-one loop.
    }
    fun retry() {
        val track = current ?: return
        if (track.localPath == null) resolver.forget(track.id, quality())
        startCurrent(positionMs)
    }

    private fun cancelPrepared() {
        preparing?.cancel(); preparing = null
        val old = prepared?.player; prepared = null
        if (old != null) scope.launch(Dispatchers.IO) { old.stop() }
        cancelFade()
    }
    private fun cancelFade() {
        val fadingOut = fading ?: return
        fading = null
        ffmpeg.envelope = 1f
        scope.launch(Dispatchers.IO) { fadingOut.stop() }
    }
    private fun forgetLyrics() {
        lyricsJob?.cancel()
        lyricDocument = null; lyrics = null; lyricsTrackId = null
    }
    private fun prepareNext() {
        if ((!audio.gapless && audio.crossfadeSeconds == 0) || preparing?.isActive == true || prepared != null || fading != null) return
        val at = upcomingIndex() ?: return
        val track = queue[at]
        if (prefetchFailedFor == track.id) return
        val token = generation.get()
        preparing = scope.launch {
            try {
                val stream = withContext(Dispatchers.IO) {
                    if (track.localPath != null) StreamResolver.Stream(track.localPath, emptyMap()) else resolver.resolve(track.id, quality())
                }
                if (token != generation.get() || queue.getOrNull(at) != track) return@launch
                val engine = newAudio().apply { audio = this@PlaybackController.audio; volume = this@PlaybackController.volume }
                prepared = Prepared(at, track, engine, stream)
                withContext(Dispatchers.IO) { engine.play(stream.url, stream.headers, 0, track.durationMs, startPaused = true) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { prefetchFailedFor = track.id }
        }
    }
    private fun activatePrepared(at: Int, overlapMs: Long = 0): Boolean {
        val next = prepared ?: return false
        if (next.index != at || next.track != queue.getOrNull(at) || !next.player.ready || sleepAfterTrack) return false
        flushListening()
        cancelFade()
        val old = ffmpeg
        prepared = null; preparing = null; resolution?.cancel()
        radioJob?.cancel(); radioJob = null
        generation.incrementAndGet()
        index = at; ffmpeg = next.player
        positionMs = 0; durationMs = next.track.durationMs; busy = false; live = true; isPlaying = true; error = null
        cachedActive = false; fromCache = false; holdPaused = false; pendingPause = false; fadePauseAt = 0
        recoveryCount = 0; startAt = 0; activeStream = next.stream
        streams = listOf(next.stream); attempt = 0; triedInnertube = false
        sessionTrack = next.track; startedSessionAt = clock()
        lastPollAt = startedSessionAt; lastAudioPosition = 0
        prefetchFailedFor = null
        if (overlapMs > 0) {
            fading = old; fadeStartedAt = clock(); fadeDurationMs = overlapMs
            ffmpeg.envelope = 0f
        } else { scope.launch(Dispatchers.IO) { old.stop() }; ffmpeg.envelope = 1f }
        syncAudio(); ffmpeg.resume()
        onPlayed(next.track); ensureRadio()
        forgetLyrics()
        if (showLyrics) loadLyrics(next.track)
        return true
    }
    private fun ensureRadio() {
        val seed = current ?: return
        if (!autoplay() || seed.localPath != null || repeat != RepeatMode.Off || queue.size - index - 1 >= 8 || radioJob?.isActive == true) return
        val token = generation.get()
        radioJob = scope.launch {
            val extra = withContext(Dispatchers.IO) { related(seed) }
            if (token != generation.get()) return@launch
            val known = queue.map { it.id }.toSet()
            val fresh = diversify(radioAdditions(extra, known, autoSkipped), queue.takeLast(3))
            radioIds.addAll(fresh.map { it.id })
            queue = queue + fresh
        }
    }
    private suspend fun related(seed: Track): List<Track> = try {
        val related = YouTube.next(WatchEndpoint(videoId = seed.id)).getOrNull()?.items.orEmpty().map { it.toTrack() }
        if (related.count { it.id != seed.id } >= 4) related else related +
            YouTube.search("${seed.title} ${seed.artists}", YouTube.SearchFilter.FILTER_SONG).getOrNull()?.items.orEmpty().mapNotNull { it.asTrack() }
    } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { emptyList() }

    /** Replaces the queue with [seed] followed by songs like it. Returns false when nothing similar was found. */
    suspend fun startRadio(seed: Track): Boolean {
        val found = withContext(Dispatchers.IO) { related(seed) }
        val songs = diversify(radioAdditions(found, setOf(seed.id), autoSkipped), listOf(seed))
        if (songs.isEmpty()) return false
        shuffle = false
        playAll(listOf(seed) + songs, 0)
        // Counted as automatic, so a later block removes them like any radio song.
        radioIds.addAll(songs.map { it.id })
        return true
    }
    private fun loadLyrics(track: Track) {
        if (lyricsTrackId == track.id && (lyricDocument != null || lyricsJob?.isActive == true)) return
        lyricsJob?.cancel()
        lyricsTrackId = track.id
        lyrics = "Sözler yükleniyor…"
        lyricsJob = scope.launch {
            try {
                val result = lyricsRepository.load(track)
                if (current?.id == track.id) {
                    lyricDocument = result
                    lyrics = result.plain.ifBlank { if (result.lines.isEmpty()) "Söz bulunamadı" else result.lines.joinToString("\n") { it.text } }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { if (current?.id == track.id) { lyrics = "Sözler alınamadı"; lyricsTrackId = null } }
        }
    }
}

/** Names the kind of source without revealing its address. */
internal fun sourceLabel(stream: StreamResolver.Stream?): String = when {
    stream == null -> "Hazırlanmış kaynak"
    !stream.url.startsWith("http", ignoreCase = true) -> "Yerel dosya"
    ".googlevideo.com/" in stream.url -> "Doğrudan kaynak"
    else -> "Genel ara sunucu"
}

/** One step of opening or playing a source. Never contains an address, header or account data. */
data class PlaybackEvent(
    val at: Long,
    val track: String,
    val stage: String,
    val detail: String,
    val tookMs: Long,
    val failed: Boolean = false,
)

/**
 * Keeps the recommended order but pushes a song back when the same artist would play again within two songs.
 * Nothing is dropped, so a single-artist result still plays completely.
 */
internal fun diversify(tracks: List<Track>, before: List<Track> = emptyList()): List<Track> {
    val waiting = tracks.distinctBy { it.id }.toMutableList()
    val result = mutableListOf<Track>()
    fun lead(track: Track) = track.artists.substringBefore(',').trim().lowercase()
    while (waiting.isNotEmpty()) {
        val recent = (before + result).takeLast(2).map(::lead)
        val next = waiting.firstOrNull { lead(it) !in recent } ?: waiting.first()
        waiting.remove(next)
        result += next
    }
    return result
}

/** Decoder exceptions contain signed URLs and can be thousands of characters long. */
internal fun playbackErrorMessage(message: String): String = when {
    message.startsWith("Yerel dosya açılamadı:") -> "Yerel dosya açılamadı. Dosyanın yerini ve ses aygıtını kontrol edin."
    message.contains("-138") || message.contains("timed out", true) || message.contains("timeout", true) ->
        "Ses sunucusuna bağlantı zaman aşımına uğradı. Yeniden deneyin; sürerse ağ veya VPN bağlantınızı kontrol edin."
    message.contains("403") || message.contains("401") ->
        "Ses bağlantısına erişim reddedildi. Yeni bağlantı almak için yeniden deneyin."
    message.contains("avformat_open_input", true) || message.contains("http://", true) || message.contains("https://", true) ->
        "Ses akışı açılamadı. Yeni bağlantı almak için yeniden deneyin."
    else -> message.take(240)
}
