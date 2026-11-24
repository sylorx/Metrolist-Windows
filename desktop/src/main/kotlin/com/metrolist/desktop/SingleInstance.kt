package com.metrolist.desktop

import java.io.File
import java.io.RandomAccessFile
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException

/**
 * Two running copies would overwrite each other's library files. The first copy holds a lock for its whole
 * life; a second one only asks it to come to the front.
 */
internal class SingleInstance(private val directory: File) {
    private var lock: FileLock? = null
    private val request = File(directory, "show.request")

    fun acquire(): Boolean {
        val held = try {
            directory.mkdirs()
            val channel = RandomAccessFile(File(directory, "instance.lock"), "rw").channel
            try {
                channel.tryLock().also { if (it == null) channel.close() }
            } catch (_: OverlappingFileLockException) {
                channel.close()
                null
            }
        } catch (_: Exception) {
            // A file system without locking must not prevent the app from opening.
            return true
        }
        if (held == null) {
            runCatching { request.writeText("show") }
            return false
        }
        lock = held
        runCatching { request.delete() }
        return true
    }

    fun takeShowRequest(): Boolean = request.exists() && request.delete()

    fun release() {
        runCatching { lock?.release(); lock?.channel()?.close() }
        lock = null
    }
}
