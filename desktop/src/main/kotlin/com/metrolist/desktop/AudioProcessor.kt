package com.metrolist.desktop

import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.*

internal val equalizerFrequencies = listOf(31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
internal val equalizerPresets = linkedMapOf(
    "Düz" to List(10) { 0f },
    "Bas" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
    "Vokal" to listOf(-2f, -2f, -1f, 0f, 2f, 4f, 4f, 2f, 0f, -1f),
    "Rock" to listOf(4f, 3f, 1f, -1f, -2f, 1f, 3f, 4f, 4f, 3f),
)

internal fun audioDevices(): List<String> = AudioSystem.getMixerInfo().filter { info ->
    runCatching { AudioSystem.getMixer(info).sourceLineInfo.any { SourceDataLine::class.java.isAssignableFrom(it.lineClass) } }.getOrDefault(false)
}.map { it.name }.distinct()

/** RBJ peaking filters operating on interleaved signed 16-bit PCM. One state per channel. */
internal class AudioProcessor(private val rate: Int, private val channels: Int) {
    private class Biquad(frequency: Int, gain: Float, rate: Int, channels: Int) {
        private val a = 10.0.pow(gain / 40.0)
        private val w = 2 * PI * frequency.coerceAtMost((rate * 0.45).toInt()) / rate
        private val alpha = sin(w) / 2 // Q = 1
        private val a0 = 1 + alpha / a
        private val b0 = (1 + alpha * a) / a0
        private val b1 = -2 * cos(w) / a0
        private val b2 = (1 - alpha * a) / a0
        private val a1 = b1
        private val a2 = (1 - alpha / a) / a0
        private val z1 = DoubleArray(channels)
        private val z2 = DoubleArray(channels)
        fun sample(x: Double, channel: Int): Double {
            val y = b0 * x + z1[channel]
            z1[channel] = b1 * x - a1 * y + z2[channel]
            z2[channel] = b2 * x - a2 * y
            return y
        }
    }
    private var lastBands = emptyList<Float>()
    private var filters = emptyList<Biquad>()
    private var rms = 0.0225
    private var normalization = 1.0
    fun process(data: ByteArray, count: Int, settings: AudioSettings, gain: Float) {
        if (lastBands != settings.bands) {
            lastBands = settings.bands.toList()
            filters = equalizerFrequencies.mapIndexedNotNull { index, hz ->
                val db = lastBands.getOrElse(index) { 0f }.coerceIn(-12f, 12f)
                if (abs(db) < 0.01f || hz >= rate / 2) null else Biquad(hz, db, rate, channels)
            }
        }
        val headroom = 10.0.pow(-(lastBands.maxOrNull() ?: 0f).coerceAtLeast(0f) / 20.0)
        val smoothing = 1 - exp(-1.0 / (rate * channels * 2.0))
        for (offset in 0 until count - 1 step 2) {
            val raw = ((data[offset].toInt() and 255) or (data[offset + 1].toInt() shl 8)).toShort().toDouble() / 32768.0
            rms += smoothing * (raw * raw - rms)
            val desired = if (settings.normalize && rms > 0.00001) (0.15 / sqrt(rms)).coerceIn(0.25, 3.0) else 1.0
            normalization += smoothing * (desired - normalization)
            var sample = raw
            val channel = (offset / 2) % channels
            filters.forEach { sample = it.sample(sample, channel) }
            val scaled = sample * headroom * normalization * gain
            val value = (scaled.coerceIn(-1.0, 0.999969) * 32768).toInt()
            data[offset] = value.toByte()
            data[offset + 1] = (value shr 8).toByte()
        }
    }
}
