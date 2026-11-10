package com.metrolist.desktop

import kotlinx.serialization.Serializable

internal enum class ShortcutSlot { PlayPause, Previous, Next, VolumeUp, VolumeDown, Mute }

@Serializable
data class ShortcutBook(
    val scrollVolume: Boolean = true,
    val playPauseOn: Boolean = true,
    val playPause: String = "space",
    val previousOn: Boolean = true,
    val previous: String = "left",
    val nextOn: Boolean = true,
    val next: String = "right",
    val volumeUpOn: Boolean = true,
    val volumeUp: String = "up",
    val volumeDownOn: Boolean = true,
    val volumeDown: String = "down",
    val muteOn: Boolean = true,
    val mute: String = "ctrl+m",
)

internal data class PressedKey(val code: Long, val ctrl: Boolean = false, val alt: Boolean = false, val shift: Boolean = false)

internal fun ShortcutBook.enabled(slot: ShortcutSlot) = when (slot) {
    ShortcutSlot.PlayPause -> playPauseOn
    ShortcutSlot.Previous -> previousOn
    ShortcutSlot.Next -> nextOn
    ShortcutSlot.VolumeUp -> volumeUpOn
    ShortcutSlot.VolumeDown -> volumeDownOn
    ShortcutSlot.Mute -> muteOn
}

internal fun ShortcutBook.keyOf(slot: ShortcutSlot) = when (slot) {
    ShortcutSlot.PlayPause -> playPause
    ShortcutSlot.Previous -> previous
    ShortcutSlot.Next -> next
    ShortcutSlot.VolumeUp -> volumeUp
    ShortcutSlot.VolumeDown -> volumeDown
    ShortcutSlot.Mute -> mute
}

internal fun ShortcutBook.setEnabled(slot: ShortcutSlot, on: Boolean) = when (slot) {
    ShortcutSlot.PlayPause -> copy(playPauseOn = on)
    ShortcutSlot.Previous -> copy(previousOn = on)
    ShortcutSlot.Next -> copy(nextOn = on)
    ShortcutSlot.VolumeUp -> copy(volumeUpOn = on)
    ShortcutSlot.VolumeDown -> copy(volumeDownOn = on)
    ShortcutSlot.Mute -> copy(muteOn = on)
}

internal fun ShortcutBook.setKey(slot: ShortcutSlot, key: String) = when (slot) {
    ShortcutSlot.PlayPause -> copy(playPause = key)
    ShortcutSlot.Previous -> copy(previous = key)
    ShortcutSlot.Next -> copy(next = key)
    ShortcutSlot.VolumeUp -> copy(volumeUp = key)
    ShortcutSlot.VolumeDown -> copy(volumeDown = key)
    ShortcutSlot.Mute -> copy(mute = key)
}

internal fun ShortcutBook.assign(slot: ShortcutSlot, key: String): ShortcutBook {
    val cleaned = key.trim()
    val previousKey = keyOf(slot)
    val clash = ShortcutSlot.entries.firstOrNull { it != slot && cleaned.isNotEmpty() && keyOf(it) == cleaned }
    var next = setKey(slot, cleaned)
    if (clash != null) next = next.setKey(clash, previousKey)
    return next
}

internal fun ShortcutBook.actionFor(key: PressedKey, names: Map<String, Long>): ShortcutSlot? =
    ShortcutSlot.entries.firstOrNull { slot -> enabled(slot) && sameBinding(keyOf(slot), key, names) }

internal fun encodePressed(key: PressedKey) = buildString {
    if (key.ctrl) append("ctrl+")
    if (key.alt) append("alt+")
    if (key.shift) append("shift+")
    append(key.code)
}

internal fun sameBinding(binding: String, key: PressedKey, names: Map<String, Long>): Boolean {
    val parts = binding.split('+').filter { it.isNotBlank() }
    if (parts.isEmpty()) return false
    val token = parts.last()
    val code = names[token] ?: token.toLongOrNull() ?: return false
    val mods = parts.dropLast(1).toSet()
    return key.code == code && key.ctrl == ("ctrl" in mods) && key.alt == ("alt" in mods) && key.shift == ("shift" in mods)
}

internal fun shortcutLabel(binding: String): String {
    if (binding.isBlank()) return "Atanmadı"
    val parts = binding.split('+').filter { it.isNotBlank() }
    val token = parts.last()
    val name = when (token) {
        "space" -> "Space"
        "left" -> "Sol"
        "right" -> "Sağ"
        "up" -> "Yukarı"
        "down" -> "Aşağı"
        "m" -> "M"
        else -> token.toLongOrNull()?.toInt()?.let { java.awt.event.KeyEvent.getKeyText(it) } ?: token
    }
    val mods = parts.dropLast(1).joinToString("+") {
        when (it) {
            "ctrl" -> "Ctrl"
            "alt" -> "Alt"
            "shift" -> "Shift"
            else -> it
        }
    }
    return if (mods.isEmpty()) name else "$mods+$name"
}
