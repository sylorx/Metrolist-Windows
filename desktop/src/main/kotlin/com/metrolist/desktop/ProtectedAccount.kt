package com.metrolist.desktop

import com.sun.jna.platform.win32.Crypt32Util
import java.io.File
import java.util.Base64

/** Windows DPAPI binds the secret to the current Windows user. No plaintext fallback. */
internal object ProtectedAccount {
    fun write(file: File, cookie: String) {
        val encrypted = Crypt32Util.cryptProtectData(cookie.toByteArray(Charsets.UTF_8))
        atomicWrite(file, Base64.getEncoder().encodeToString(encrypted), backup = false)
    }
    fun read(file: File): String = String(Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(file.readText())), Charsets.UTF_8)
}
