package com.metrolist.desktop

import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.ln

class FfmpegPlayer(
    private val onEnded: () -> Unit,
    private val onError: (String) -> Unit,
) : PlaybackEngine {
    @Volatile override var positionMs: Long = 0
    @Volatile override var durationMs: Long = 0
    @Volatile override var volume: Float = 1f
    @Volatile var playing: Boolean = false
    @Volatile override var ready: Boolean = false
    @Volatile override var envelope: Float = 1f
    @Volatile override var audio: AudioSettings = AudioSettings()

    private val gate = Any()
    @Volatile private var generation = 0
    @Volatile private var paused = false
    private val pauseLock = Object()
    @Volatile private var line: SourceDataLine? = null
    private var thread: Thread? = null

    override fun play(url: String, headers: Map<String, String>, startMs: Long, duration: Long, startPaused: Boolean) {
        val gen: Int
        val worker: Thread?
        synchronized(gate) {
            gen = ++generation
            paused = false
            synchronized(pauseLock) { pauseLock.notifyAll() }
            worker = thread
        }
        if (worker != null && worker != Thread.currentThread()) worker.join(1200)
        synchronized(gate) {
            if (gen != generation) return
            durationMs = duration
            positionMs = startMs
            paused = startPaused
            ready = false
            playing = !startPaused
            thread = Thread({
                try {
                    decode(gen, url, headers, startMs)
                    if (gen == generation) {
                        playing = false
                        onEnded()
                    }
                } catch (error: Exception) {
                    if (gen == generation) {
                        playing = false
                        onError(error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName)
                    }
                }
            }, "metrolist-audio").also {
                it.isDaemon = true
                it.start()
            }
        }
    }

    override fun pause() {
        paused = true
        playing = false
        line?.stop()
    }

    override fun resume() {
        paused = false
        playing = true
        line?.start()
        synchronized(pauseLock) { pauseLock.notifyAll() }
    }

    override fun stop() {
        val worker: Thread?
        synchronized(gate) {
            generation++
            paused = false
            synchronized(pauseLock) { pauseLock.notifyAll() }
            worker = thread
            playing = false
            ready = false
            positionMs = 0
        }
        // A paused SourceDataLine.write can otherwise keep a decoder alive indefinitely.
        runCatching { line?.flush(); line?.close() }
        if (worker != null && worker != Thread.currentThread()) worker.join(1200)
    }

    private fun waitIfPaused(gen: Int) {
        synchronized(pauseLock) {
            while (paused && gen == generation) pauseLock.wait(200)
        }
    }

    private fun decode(gen: Int, url: String, headers: Map<String, String>, startMs: Long) {
        val opened = FFmpegFrameGrabber(url)
        var audioLine: SourceDataLine? = null
        var pcm = ByteArray(8192)
        var processor: AudioProcessor? = null
        try {
            configureStreamInput(opened, headers)
            opened.setAudioOption("threads", "1")
            // A single interleaved layout makes PCM processing correct for every source format.
            opened.sampleFormat = 1 // AV_SAMPLE_FMT_S16
            opened.audioChannels = 2
            opened.sampleRate = 48000
            opened.start()
            val probed = opened.lengthInTime
            if (probed > 0) durationMs = probed / 1000
            if (startMs > 0) opened.timestamp = startMs * 1000
            val startedAt = System.currentTimeMillis()
            var heard = false
            var misses = 0
            while (gen == generation) {
                if (audioLine != null) waitIfPaused(gen)
                if (gen != generation) break
                val frame = opened.grab() ?: break
                if (frame.samples == null) {
                    if (!heard && ++misses > 90) break
                    continue
                }
                misses = 0
                if (audioLine == null) {
                    audioLine = openLine(frame).also { line = it }
                    processor = AudioProcessor(frame.sampleRate, frame.audioChannels)
                    ready = true
                }
                waitIfPaused(gen)
                if (gen != generation) break
                val written = copyPcm(frame, pcm) { grown -> pcm = grown }
                if (written > 0) {
                    heard = true
                    processor?.process(pcm, written, audio, volume * envelope)
                    audioLine.write(pcm, 0, written)
                }
                positionMs = (opened.timestamp / 1000).coerceAtLeast(0)
                if (durationMs <= 0 && opened.lengthInTime > 0) durationMs = opened.lengthInTime / 1000
            }
            if (gen == generation && !heard) throw IllegalStateException("Ses akışı boş")
            if (gen == generation && durationMs > 0 && positionMs + 2000 < durationMs) {
                throw IllegalStateException("Akış parça bitmeden kapandı")
            }
            if (gen == generation) audioLine?.drain()
            val wall = System.currentTimeMillis() - startedAt
            if (gen == generation && wall < 4_000 && positionMs < 1_500) {
                throw IllegalStateException("Akış erken kapandı")
            }
        } finally {
            runCatching { opened.stop() }
            runCatching { opened.release() }
            if (line == audioLine) line = null
            runCatching { audioLine?.stop() }
            runCatching { audioLine?.close() }
        }
    }

    internal fun configureStreamInput(opened: FFmpegFrameGrabber, headers: Map<String, String>) {
        val userAgent = headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value
            ?.takeUnless { '\r' in it || '\n' in it } ?: USER_AGENT
        opened.setOption("user_agent", userAgent)
        // Preserve the extractor's request identity, including Origin and Referer.
        val extraHeaders = headers.entries.filterNot { it.key.equals("User-Agent", true) }
            .filter { (name, value) -> HEADER_NAME.matches(name) && '\r' !in value && '\n' !in value }
            .joinToString("") { (name, value) -> "$name: $value\r\n" }
        if (extraHeaders.isNotEmpty()) opened.setOption("headers", extraHeaders)
        opened.setOption("reconnect", "1")
        opened.setOption("reconnect_streamed", "1")
        opened.setOption("reconnect_delay_max", "2")
        opened.setOption("rw_timeout", "15000000")
    }

    private fun openLine(frame: Frame): SourceDataLine {
        val channels = frame.audioChannels.coerceAtLeast(1)
        val rate = frame.sampleRate.coerceAtLeast(8000)
        val format = AudioFormat(rate.toFloat(), 16, channels, true, false)
        val info = javax.sound.sampled.DataLine.Info(SourceDataLine::class.java, format)
        val selected = AudioSystem.getMixerInfo().find { it.name == audio.device }
        val opened = if (selected != null) AudioSystem.getMixer(selected).getLine(info) as SourceDataLine
            else AudioSystem.getLine(info) as SourceDataLine
        opened.open(format, 16_384)
        if (!paused) opened.start()
        return opened
    }

    private fun copyPcm(frame: Frame, pcm: ByteArray, replace: (ByteArray) -> Unit): Int {
        val samples = frame.samples ?: return 0
        if (samples.isEmpty()) return 0
        return when (val buffer = samples[0]) {
            is ShortBuffer -> {
                buffer.rewind()
                val count = buffer.remaining() * 2
                val data = ensure(pcm, count, replace)
                var offset = 0
                while (buffer.hasRemaining()) {
                    val sample = buffer.get().toInt()
                    data[offset++] = (sample and 0xff).toByte()
                    data[offset++] = (sample shr 8 and 0xff).toByte()
                }
                count
            }
            is FloatBuffer -> {
                buffer.rewind()
                val count = buffer.remaining() * 2
                val data = ensure(pcm, count, replace)
                var offset = 0
                while (buffer.hasRemaining()) {
                    val sample = (buffer.get() * Short.MAX_VALUE).toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    data[offset++] = (sample and 0xff).toByte()
                    data[offset++] = (sample shr 8 and 0xff).toByte()
                }
                count
            }
            is ByteBuffer -> {
                buffer.rewind()
                val count = buffer.remaining()
                val data = ensure(pcm, count, replace)
                buffer.get(data, 0, count)
                count
            }
            else -> 0
        }
    }

    private fun ensure(pcm: ByteArray, count: Int, replace: (ByteArray) -> Unit): ByteArray {
        if (pcm.size >= count) return pcm
        val grown = ByteArray(count)
        replace(grown)
        return grown
    }

    private companion object {
        val HEADER_NAME = Regex("^[!#$%&'*+.^_`|~0-9A-Za-z-]+$")
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
}
