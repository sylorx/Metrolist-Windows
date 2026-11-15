package com.metrolist.desktop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiscordPresenceTest {
    @Test
    fun frameIsLittleEndianOpcodeAndLength() {
        val json = """{"v":1}"""
        val frame = ipcFrame(1, json)
        val buffer = ByteBuffer.wrap(frame).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(1, buffer.int)
        assertEquals(json.toByteArray(Charsets.UTF_8).size, buffer.int)
        assertEquals(json, frame.copyOfRange(8, frame.size).toString(Charsets.UTF_8))
    }

    @Test
    fun activityCarriesSongArtistAndListeningType() {
        val activity = listeningActivity("Gamsız Hayat", "Candan Erçetin", 100, 200)
        assertEquals("Gamsız Hayat", activity["details"]!!.jsonPrimitive.content)
        assertEquals("Candan Erçetin", activity["state"]!!.jsonPrimitive.content)
        assertEquals(2, activity["type"]!!.jsonPrimitive.content.toInt())
        val command = Json.parseToJsonElement(setActivityCommand(7, activity, "3")).jsonObject
        assertEquals("SET_ACTIVITY", command["cmd"]!!.jsonPrimitive.content)
        assertEquals("3", command["nonce"]!!.jsonPrimitive.content)
    }

    @Test
    fun presenceSendsOnceThenClearsWhenHidden() {
        val transport = FakeDiscord()
        val session = DiscordSession(transport, pid = 4, clock = { 1_800_000 })
        val track = Track("abc", "Şarkı", "Sanatçı", null, 180_000)
        session.sync(" 123 ", visible = true, track = track, positionMs = 20_000, durationMs = 180_000)
        session.sync("123", visible = true, track = track, positionMs = 25_000, durationMs = 180_000)
        assertEquals(1, transport.sent.size)
        assertTrue(transport.sent.single().contains("Şarkı"))
        assertEquals(listOf("123"), transport.handshakes)
        session.sync("123", visible = false, track = track, positionMs = 25_000, durationMs = 180_000)
        assertEquals(2, transport.sent.size)
        assertTrue(transport.sent.last().contains("\"activity\":null"))
    }

    @Test
    fun closedDiscordDoesNotThrowOrRepeatHandshake() {
        val transport = FakeDiscord(accepts = false)
        var now = 10_000L
        val session = DiscordSession(transport, pid = 4, clock = { now })
        val track = Track("abc", "Şarkı", "Sanatçı", null, 1_000)
        session.sync("99", visible = true, track = track, positionMs = 0, durationMs = 1_000)
        session.sync("99", visible = true, track = track, positionMs = 0, durationMs = 1_000)
        assertEquals(1, transport.handshakes.size)
        assertTrue(transport.sent.isEmpty())
        now += 16_000
        session.sync("99", visible = true, track = track, positionMs = 0, durationMs = 1_000)
        assertEquals(2, transport.handshakes.size)
        assertFalse(transport.sent.isNotEmpty())
    }

    private class FakeDiscord(private val accepts: Boolean = true) : DiscordTransport {
        val handshakes = mutableListOf<String>()
        val sent = mutableListOf<String>()
        override fun handshake(clientId: String): Boolean {
            handshakes += clientId
            return accepts
        }
        override fun send(json: String) {
            sent += json
        }
        override fun close() = Unit
    }
}
