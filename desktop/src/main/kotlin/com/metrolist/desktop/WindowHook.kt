package com.metrolist.desktop

import com.sun.jna.CallbackReference
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.win32.StdCallLibrary
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Sees a window's messages before AWT does. comctl32's SetWindowSubclass refuses to work from any thread other
 * than the one that owns the window (AWT's toolkit thread), so the window procedure is replaced instead.
 */
internal object WindowHook {
    private const val GWLP_WNDPROC = -4

    internal interface Procedure : StdCallLibrary.StdCallCallback {
        fun callback(hwnd: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    }

    private class Entry(val previous: Pointer) {
        val handlers = CopyOnWriteArrayList<(HWND, Int, WPARAM, LPARAM) -> LRESULT?>()
        lateinit var procedure: Procedure
    }

    private val entries = HashMap<Long, Entry>()

    /** A handler returns null to let the message continue to the next one and finally to AWT. */
    @Synchronized
    fun install(hwnd: HWND, handler: (HWND, Int, WPARAM, LPARAM) -> LRESULT?): Boolean {
        val key = Pointer.nativeValue(hwnd.pointer)
        entries[key]?.let { it.handlers += handler; return true }
        val previous = User32.INSTANCE.GetWindowLongPtr(hwnd, GWLP_WNDPROC)?.toPointer() ?: return false
        val entry = Entry(previous)
        entry.handlers += handler
        entry.procedure = object : Procedure {
            override fun callback(hwnd: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): LRESULT {
                for (next in entry.handlers) {
                    val answer = try { next(hwnd, message, wParam, lParam) } catch (_: Throwable) { null }
                    if (answer != null) return answer
                }
                return User32.INSTANCE.CallWindowProc(entry.previous, hwnd, message, wParam, lParam)
            }
        }
        // Kept for the life of the process: the window may call it until the very end.
        entries[key] = entry
        User32.INSTANCE.SetWindowLongPtr(hwnd, GWLP_WNDPROC, CallbackReference.getFunctionPointer(entry.procedure))
        return true
    }

    /** Passes a message on with changed parameters. */
    fun forward(hwnd: HWND, message: Int, wParam: WPARAM, lParam: LPARAM): LRESULT? {
        val entry = synchronized(this) { entries[Pointer.nativeValue(hwnd.pointer)] } ?: return null
        return User32.INSTANCE.CallWindowProc(entry.previous, hwnd, message, wParam, lParam)
    }
}
