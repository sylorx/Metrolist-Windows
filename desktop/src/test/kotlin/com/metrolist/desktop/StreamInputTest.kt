package com.metrolist.desktop

import com.sun.net.httpserver.HttpServer
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import javax.sound.sampled.*

class StreamInputTest {
    @Test fun signedStreamCacheRespectsExpiryAndSafetyMargin() {
        val now = 1_000_000L
        assertEquals(1_040_000L, streamCacheDeadline("https://example.com/audio?expire=1100", now))
        assertTrue(streamCacheDeadline("https://example.com/audio?expire=1000", now) < now)
        assertEquals(now + 7_200_000, streamCacheDeadline("https://example.com/audio", now))
        assertEquals(now + 7_200_000, streamCacheDeadline("https://example.com/audio?expire=9223372036854775807", now))
    }

    @Test fun decoderErrorsAreReadableAndDoNotExposeSignedUrls() {
        val raw = "avformat_open_input() error -138: Could not open input https://example.com/audio?secret=token"
        val readable = playbackErrorMessage(raw)
        assertTrue(readable.contains("zaman aşımı"))
        assertFalse(readable.contains("secret"))
        assertFalse(playbackErrorMessage("avformat_open_input https://example.com/audio?secret=token").contains("secret"))
        assertTrue(playbackErrorMessage("HTTP error 403").contains("erişim reddedildi"))
    }

    @Test fun protectedAudioOpensWithTheResolversHeaders() {
        val bytes = ByteArrayOutputStream().also { out ->
            AudioInputStream(ByteArrayInputStream(ByteArray(48000 * 4)), AudioFormat(48000f, 16, 2, true, false), 48000).use {
                AudioSystem.write(it, AudioFileFormat.Type.WAVE, out)
            }
        }.toByteArray()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/audio") { request ->
            val allowed = request.requestHeaders.getFirst("Origin") == "https://music.youtube.com" &&
                request.requestHeaders.getFirst("Referer") == "https://music.youtube.com/" &&
                request.requestHeaders.getFirst("User-Agent") == "Metrolist-test"
            request.responseHeaders.set("Content-Type", "audio/wav")
            request.sendResponseHeaders(if (allowed) 200 else 403, if (allowed) bytes.size.toLong() else -1)
            if (allowed) request.responseBody.use { it.write(bytes) }
            request.close()
        }
        server.start()
        try {
            FFmpegFrameGrabber("http://127.0.0.1:${server.address.port}/audio").use { grabber ->
                FfmpegPlayer({}, {}).configureStreamInput(grabber, mapOf(
                    "user-agent" to "Metrolist-test", "Origin" to "https://music.youtube.com", "Referer" to "https://music.youtube.com/"
                ))
                grabber.start()
                assertNotNull(grabber.grabSamples()?.samples)
            }
        } finally { server.stop(0) }
    }
}
