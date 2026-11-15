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
    fun stop()
}

interface PlaybackStreams {
    fun forget(videoId: String, quality: String)
    fun keep(videoId: String, quality: String, stream: StreamResolver.Stream)
    suspend fun openers(videoId: String, quality: String): List<StreamResolver.Stream>
    suspend fun innertube(videoId: String, quality: String): StreamResolver.Stream?
    suspend fun resolve(videoId: String, quality: String): StreamResolver.Stream
}
