package com.metrolist.desktop

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal interface DiscordTransport {
    fun handshake(clientId: String): Boolean
    fun send(json: String)
    fun close()
}

internal fun discordPipePaths(): List<String> = (0..9).map { "\\\\.\\pipe\\discord-ipc-$it" }

internal fun handshakeJson(clientId: String): String =
    buildJsonObject {
        put("v", 1)
        put("client_id", clientId)
    }.toString()

internal fun ipcFrame(opcode: Int, json: String): ByteArray {
    val body = json.toByteArray(Charsets.UTF_8)
    val buffer = ByteBuffer.allocate(8 + body.size).order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(opcode)
    buffer.putInt(body.size)
    buffer.put(body)
    return buffer.array()
}

internal fun listeningActivity(title: String, artists: String, startSec: Long, endSec: Long?): JsonObject =
    buildJsonObject {
        put("details", title.take(128))
        put("state", artists.take(128))
        put("type", 2)
        put(
            "timestamps",
            buildJsonObject {
                put("start", startSec)
                if (endSec != null && endSec > startSec) put("end", endSec)
            },
        )
    }

internal fun setActivityCommand(pid: Long, activity: JsonElement?, nonce: String): String =
    buildJsonObject {
        put("cmd", "SET_ACTIVITY")
        put("nonce", nonce)
        put(
            "args",
            buildJsonObject {
                put("pid", pid)
                put("activity", activity ?: JsonNull)
            },
        )
    }.toString()

internal class DiscordSession(
    private val transport: DiscordTransport,
    private val pid: Long = ProcessHandle.current().pid(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var open = false
    private var shown = false
    private var clientId = ""
    private var lastKey = ""
    private var nonce = 0
    private var failedAt = 0L

    fun sync(appId: String, visible: Boolean, track: Track?, positionMs: Long, durationMs: Long) {
        synchronized(this) { syncLocked(appId, visible, track, positionMs, durationMs) }
    }

    private fun syncLocked(appId: String, visible: Boolean, track: Track?, positionMs: Long, durationMs: Long) {
        val id = appId.trim()
        if (!visible || id.isEmpty() || track == null) {
            if (shown && open) transport.send(setActivityCommand(pid, null, nextNonce()))
            shown = false
            lastKey = ""
            return
        }
        val key = "$id|${track.id}"
        if (shown && open && key == lastKey && clientId == id) return
        if (!open || clientId != id) {
            if (clock() - failedAt < 15_000 && clientId == id) return
            transport.close()
            open = false
            shown = false
            clientId = id
            open = transport.handshake(id)
            if (!open) {
                failedAt = clock()
                return
            }
        }
        val now = clock()
        val start = (now - positionMs.coerceAtLeast(0)) / 1000
        val end = if (durationMs > 1_000) start + durationMs / 1000 else null
        transport.send(setActivityCommand(pid, listeningActivity(track.title, track.artists, start, end), nextNonce()))
        shown = true
        lastKey = key
    }

    fun close() {
        synchronized(this) {
            if (shown && open) runCatching { transport.send(setActivityCommand(pid, null, nextNonce())) }
            shown = false
            lastKey = ""
            open = false
            transport.close()
        }
    }

    private fun nextNonce(): String = (++nonce).toString()
}

internal class WindowsDiscordTransport : DiscordTransport {
    private val lock = Any()
    private var pipe: RandomAccessFile? = null
    private var reader: Thread? = null

    override fun handshake(clientId: String): Boolean = synchronized(lock) {
        closeLocked()
        val hello = handshakeJson(clientId)
        for (path in discordPipePaths()) {
            val opened = runCatching { RandomAccessFile(path, "rw") }.getOrNull() ?: continue
            val wrote = runCatching {
                opened.write(ipcFrame(0, hello))
                true
            }.getOrDefault(false)
            if (!wrote) {
                runCatching { opened.close() }
                continue
            }
            pipe = opened
            reader = Thread({ drain(opened) }, "discord-ipc").apply {
                isDaemon = true
                start()
            }
            return true
        }
        false
    }

    override fun send(json: String) {
        synchronized(lock) {
            val opened = pipe ?: return
            runCatching { opened.write(ipcFrame(1, json)) }.onFailure { closeLocked() }
        }
    }

    override fun close() {
        synchronized(lock) { closeLocked() }
    }

    private fun closeLocked() {
        reader?.interrupt()
        reader = null
        pipe?.let { runCatching { it.close() } }
        pipe = null
    }

    private fun drain(opened: RandomAccessFile) {
        val header = ByteArray(8)
        while (!Thread.currentThread().isInterrupted) {
            val read = runCatching { opened.read(header) }.getOrDefault(-1)
            if (read < 0) break
            if (read < 8) continue
            val length = ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            if (length <= 0 || length > 1_000_000) continue
            runCatching { opened.readFully(ByteArray(length)) }
        }
    }
}
