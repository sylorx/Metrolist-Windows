package com.metrolist.desktop

/** Playback policy can be exercised without opening a Windows sound device. */
interface PlaybackEngine {
    val positionMs: Long
    val durationMs: Long
    val ready: Boolean
    var volume: Float
    var envelope: Float
    var audio: AudioSettings
    fun play(url: String, headers: Map<String, String>, startMs: Long, duration: Long, startPaused: Boolean = false)
    fun pause()
    fun resume()
    /** Moves inside the open source. Returns false when the source has to be reopened instead. */
    fun seek(ms: Long): Boolean = false
    /** Terminal: a stopped engine never plays again, even if a late [play] call arrives. */
    fun stop()
}

interface PlaybackStreams {
    /** A previously working stream that can be opened without asking any server. */
    fun saved(videoId: String, quality: String): StreamResolver.Stream? = null
    fun forget(videoId: String, quality: String)
    fun keep(videoId: String, quality: String, stream: StreamResolver.Stream)
    suspend fun openers(videoId: String, quality: String): List<StreamResolver.Stream>
    suspend fun innertube(videoId: String, quality: String): StreamResolver.Stream?
    suspend fun resolve(videoId: String, quality: String): StreamResolver.Stream
}
