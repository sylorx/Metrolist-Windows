package com.metrolist.desktop

import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid.CLSID
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.ptr.PointerByReference

/** The Windows default playback endpoint and whether the previous one is still plugged in. */
internal data class OutputSnapshot(val defaultId: String?, val previousActive: Boolean)

internal enum class OutputChange { None, Reopen, PauseAndReopen }

/**
 * Decides what a change of the Windows default output means for playback. A device that disappeared (headphones
 * pulled out) pauses when the listener wants that; any other switch moves the sound to the new default, but only
 * when no specific device was chosen in the settings.
 */
internal fun outputChange(previousId: String?, now: OutputSnapshot, followsDefault: Boolean, pauseOnUnplug: Boolean): OutputChange {
    if (previousId == null || now.defaultId == null || now.defaultId == previousId) return OutputChange.None
    val unplugged = !now.previousActive
    return when {
        unplugged && pauseOnUnplug -> OutputChange.PauseAndReopen
        followsDefault -> OutputChange.Reopen
        else -> OutputChange.None
    }
}

/** Reads the default render endpoint through Core Audio (IMMDeviceEnumerator). */
internal object CoreAudio {
    private const val DEVICE_STATE_ACTIVE = 1
    private var enumerator: Pointer? = null

    @Synchronized
    fun snapshot(previousId: String?): OutputSnapshot? = runCatching {
        val devices = enumerator ?: create() ?: return null
        val defaultId = PointerByReference().let { out ->
            if (call(devices, 4, 0, 1, out) < 0) null else out.value?.let { device -> idOf(device).also { release(device) } }
        }
        val active = previousId == null || PointerByReference().let { out ->
            if (call(devices, 5, WString(previousId), out) < 0) false
            else out.value?.let { device ->
                val state = Memory(4)
                (call(device, 6, state) >= 0 && state.getInt(0) == DEVICE_STATE_ACTIVE).also { release(device) }
            } ?: false
        }
        OutputSnapshot(defaultId, active)
    }.getOrNull()

    private fun create(): Pointer? {
        Ole32.INSTANCE.CoInitializeEx(null, 0)
        val out = PointerByReference()
        val result = Ole32.INSTANCE.CoCreateInstance(
            CLSID("BCDE0395-E52F-467C-8E3D-C4579291692E"), null, 1,
            IID("A95664D2-9614-4F35-A746-DE8DB63617E6"), out,
        )
        if (result.toInt() < 0) return null
        return out.value.also { enumerator = it }
    }

    private fun idOf(device: Pointer): String? {
        val out = PointerByReference()
        if (call(device, 5, out) < 0) return null
        val text = out.value ?: return null
        return text.getWideString(0).also { Ole32.INSTANCE.CoTaskMemFree(text) }
    }

    private fun release(target: Pointer) { call(target, 2) }

    private fun call(target: Pointer, index: Int, vararg args: Any?): Int {
        val table = target.getPointer(0)
        val function = Function.getFunction(table.getPointer(index.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return function.invokeInt(arrayOf(target, *args))
    }
}
