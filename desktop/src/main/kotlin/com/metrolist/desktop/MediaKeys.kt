package com.metrolist.desktop

import com.sun.jna.CallbackReference
import com.sun.jna.Function
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Window
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

internal object MediaKeys {
    private const val WM_APPCOMMAND = 0x0319
    private const val WM_HOTKEY = 0x0312
    private const val WM_KEYDOWN = 0x0100
    private const val WM_SYSKEYDOWN = 0x0104
    private const val WH_KEYBOARD_LL = 13
    private const val LLKHF_UP = 0x80
    private const val MOD_NOREPEAT = 0x4000
    private const val VK_MEDIA_NEXT_TRACK = 0xB0
    private const val VK_MEDIA_PREV_TRACK = 0xB1
    private const val VK_MEDIA_STOP = 0xB2
    private const val VK_MEDIA_PLAY_PAUSE = 0xB3

    private var started = false
    private var player: PlaybackController? = null
    @Volatile private var controls: Pointer? = null
    @Volatile private var display: Pointer? = null
    private var lastDescription: List<String?>? = null
    private val worker = java.util.concurrent.Executors.newSingleThreadExecutor { task ->
        thread(start = false, isDaemon = true, name = "Metrolist-media-info") {
            runCatching { Winrt.INSTANCE.RoInitialize(1) }
            task.run()
        }
    }
    private var hook: Pointer? = null
    private var lastFire = 0L
    private var lastPlaying = false
    private var lastActive = false
    private val held = HashSet<Int>()
    private val pins = mutableListOf<Any>()

    // Windows rejects the handler when it claims interfaces such as INoMarshal, so only the real ones are answered:
    // IUnknown, IAgileObject and TypedEventHandler<SystemMediaTransportControls, ButtonPressedEventArgs>.
    private val supported = listOf(
        "00000000-0000-0000-C000-000000000046",
        "94EA2B94-E9CC-49E0-C0FF-EE64CA8F5B90",
        "0557E996-7B23-5BAE-AA81-EA0D671143A4",
    ).map { guid ->
        val id = IID(guid)
        id.write()
        id.pointer.getByteArray(0, 16)
    }

    fun install(target: PlaybackController, frame: Window) {
        player = target
        if (started) return
        val raw = Native.getComponentPointer(frame) ?: return
        started = true
        val hwnd = HWND(raw)
        runCatching { subclass(hwnd) }
        thread(isDaemon = true, name = "Metrolist-media") {
            val session = runCatching { bind(hwnd) }.onFailure { println("Metrolist: medya oturumu kurulamadı (${it.message})") }
            // With a Windows media session the system routes the keys, so other players keep theirs.
            // The global hook remains only as a fallback.
            if (session.isSuccess) {
                // This thread owns the COM apartment of the session and has to outlive it.
                while (true) Thread.sleep(Long.MAX_VALUE)
            }
            runCatching { listen() }.onFailure { println("Metrolist: medya tuşları kurulamadı (${it.message})") }
        }
    }

    fun sync(playing: Boolean, active: Boolean) {
        lastPlaying = playing
        lastActive = active
        val session = controls ?: return
        val status = when {
            !active -> 2
            playing -> 3
            else -> 4
        }
        runCatching {
            synchronized(this) {
                invoke(session, 11, if (active) 1 else 0)
                invoke(session, 7, status)
            }
        }
    }

    /** Shows the song in the Windows media panel, on the lock screen and above the volume slider. */
    fun describe(track: Track?) {
        val wanted = listOf(track?.title, track?.artists, coverUrl(track?.thumbnail)?.takeIf { it.startsWith("https://") })
        worker.execute {
            val updater = display ?: return@execute
            if (wanted == lastDescription) return@execute
            runCatching {
                invoke(updater, 16)
                if (wanted[0] != null) {
                    invoke(updater, 7, 1)
                    val slot = Memory(Native.POINTER_SIZE.toLong())
                    if (invoke(updater, 12, slot) >= 0) slot.getPointer(0)?.let { music ->
                        withString(wanted[0].orEmpty()) { invoke(music, 7, it) }
                        withString(wanted[1].orEmpty()) { invoke(music, 11, it) }
                        invoke(music, 2)
                    }
                    wanted[2]?.let { address -> runCatching { picture(address) { invoke(updater, 11, it) } } }
                }
                invoke(updater, 17)
                lastDescription = wanted
            }.onFailure { println("Metrolist: medya bilgisi yazılamadı (${it.message})") }
        }
    }

    private fun picture(address: String, use: (Pointer) -> Unit) {
        val slot = Memory(Native.POINTER_SIZE.toLong())
        activation("Windows.Foundation.Uri", "44A9796F-723E-4FDF-A218-033E75B0C084") { uris ->
            withString(address) { check(invoke(uris, 6, it, slot) >= 0) { "adres" } }
        }
        val uri = slot.getPointer(0) ?: return
        try {
            activation("Windows.Storage.Streams.RandomAccessStreamReference", "857309DC-3FBF-4E7D-986F-EF3B1A07A964") { references ->
                val stream = Memory(Native.POINTER_SIZE.toLong())
                check(invoke(references, 7, uri, stream) >= 0) { "kapak" }
                stream.getPointer(0)?.let { use(it); invoke(it, 2) }
            }
        } finally { invoke(uri, 2) }
    }

    private fun activation(name: String, iid: String, use: (Pointer) -> Unit) {
        val out = PointerByReference()
        val id = IID(iid).apply { write() }
        withString(name) { check(Winrt.INSTANCE.RoGetActivationFactory(it, id.pointer, out) >= 0) { name } }
        val factory = out.value ?: return
        try { use(factory) } finally { invoke(factory, 2) }
    }

    private fun <T> withString(text: String, use: (Pointer?) -> T): T {
        val wide = Memory((text.length + 1L) * 2)
        wide.write(0, text.toCharArray(), 0, text.length)
        wide.setShort(text.length * 2L, 0)
        val out = PointerByReference()
        check(Winrt.INSTANCE.WindowsCreateString(wide, text.length, out) >= 0) { "metin" }
        try { return use(out.value) } finally { Winrt.INSTANCE.WindowsDeleteString(out.value) }
    }

    private fun bind(hwnd: HWND) {
        val winrt = Winrt.INSTANCE
        winrt.RoInitialize(1)
        val name = "Windows.Media.SystemMediaTransportControls"
        val wide = Memory((name.length + 1L) * 2)
        wide.write(0, name.toCharArray(), 0, name.length)
        wide.setShort(name.length * 2L, 0)
        val className = PointerByReference()
        check(winrt.WindowsCreateString(wide, name.length, className) >= 0) { "sınıf adı" }
        val factoryOut = PointerByReference()
        val interop = IID("DDB0472D-C911-4A1F-86D9-DC3D71A95F5A").apply { write() }
        val factoryHr = winrt.RoGetActivationFactory(className.value, interop.pointer, factoryOut)
        winrt.WindowsDeleteString(className.value)
        val factory = factoryOut.value
        check(factoryHr >= 0 && factory != null) { "oturum ${hex(factoryHr)}" }
        val controlsOut = Memory(Native.POINTER_SIZE.toLong())
        val face = IID("99FA3FF4-1742-42A6-902E-087D41F965EC").apply { write() }
        val sessionHr = invoke(factory, 6, hwnd.pointer, face.pointer, controlsOut)
        val session = controlsOut.getPointer(0)
        check(sessionHr >= 0 && session != null) { "pencere ${hex(sessionHr)}" }
        val handler = handler()
        val token = Memory(8)
        val listenHr = invoke(session, 32, handler, token)
        check(listenHr >= 0) { "tuşlar ${hex(listenHr)}" }
        intArrayOf(13, 15, 17, 25, 27).forEach { invoke(session, it, 1) }
        runCatching {
            val updater = Memory(Native.POINTER_SIZE.toLong())
            if (invoke(session, 8, updater) >= 0) {
                val panel = updater.getPointer(0)
                if (panel != null) {
                    invoke(panel, 7, 1)
                    invoke(panel, 17)
                    display = panel
                }
            }
        }
        controls = session
        sync(lastPlaying, lastActive)
        SwingUtilities.invokeLater { describe(player?.current) }
    }

    private fun handler(): Pointer {
        val query = object : QueryProc {
            override fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int {
                val raw = riid.getByteArray(0, 16)
                if (supported.none { it.contentEquals(raw) }) {
                    out.setPointer(0, null)
                    return 0x80004002.toInt()
                }
                out.setPointer(0, self)
                return 0
            }
        }
        val retain = object : CountProc {
            override fun invoke(self: Pointer) = 1
        }
        val press = object : PressProc {
            override fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int {
                val event = args ?: return 0
                runCatching {
                    val button = Memory(4)
                    if (invoke(event, 6, button) >= 0) {
                        when (button.getInt(0)) {
                            0 -> fire(Command.PLAY)
                            1, 2 -> fire(Command.PAUSE)
                            6 -> fire(Command.NEXT)
                            7 -> fire(Command.PREV)
                        }
                    }
                }
                return 0
            }
        }
        pin(query)
        pin(retain)
        pin(press)
        val table = Memory(4L * Native.POINTER_SIZE)
        table.setPointer(0, CallbackReference.getFunctionPointer(query))
        table.setPointer(Native.POINTER_SIZE.toLong(), CallbackReference.getFunctionPointer(retain))
        table.setPointer(2L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(retain))
        table.setPointer(3L * Native.POINTER_SIZE, CallbackReference.getFunctionPointer(press))
        val objectPointer = Memory(Native.POINTER_SIZE.toLong())
        objectPointer.setPointer(0, table)
        pin(table)
        pin(objectPointer)
        return objectPointer
    }

    private fun listen() {
        val user = UserEx.INSTANCE
        val callback = object : HookProc {
            override fun callback(nCode: Int, wParam: Long, lParam: Long): Long {
                val forward = { user.CallNextHookEx(hook, nCode, wParam, lParam) }
                if (nCode < 0 || lParam == 0L) return forward()
                return try {
                    if (onKey(wParam, lParam)) 1L else forward()
                } catch (_: Throwable) {
                    forward()
                }
            }
        }
        pin(callback)
        hook = user.SetWindowsHookEx(WH_KEYBOARD_LL, callback, null, 0)
        user.RegisterHotKey(null, 1, MOD_NOREPEAT, VK_MEDIA_PLAY_PAUSE)
        user.RegisterHotKey(null, 2, MOD_NOREPEAT, VK_MEDIA_NEXT_TRACK)
        user.RegisterHotKey(null, 3, MOD_NOREPEAT, VK_MEDIA_PREV_TRACK)
        user.RegisterHotKey(null, 4, MOD_NOREPEAT, VK_MEDIA_STOP)
        val message = WinUser.MSG()
        while (true) {
            val code = user.GetMessage(message, null, 0, 0)
            if (code == 0 || code == -1) break
            if (message.message == WM_HOTKEY) {
                when (message.wParam.toInt()) {
                    1 -> fire(Command.TOGGLE)
                    4 -> fire(Command.PAUSE)
                    2 -> fire(Command.NEXT)
                    3 -> fire(Command.PREV)
                }
                continue
            }
            user.TranslateMessage(message)
            user.DispatchMessage(message)
        }
    }

    private fun onKey(wParam: Long, lParam: Long): Boolean {
        val info = Pointer(lParam)
        val vk = info.getInt(0)
        if (vk != VK_MEDIA_NEXT_TRACK && vk != VK_MEDIA_PREV_TRACK && vk != VK_MEDIA_STOP && vk != VK_MEDIA_PLAY_PAUSE) return false
        val up = info.getInt(8) and LLKHF_UP != 0
        if (up) {
            held.remove(vk)
            return false
        }
        val message = wParam.toInt()
        if (message != WM_KEYDOWN && message != WM_SYSKEYDOWN) return false
        if (!held.add(vk)) return true
        if (player?.current == null) {
            held.remove(vk)
            return false
        }
        when (vk) {
            VK_MEDIA_NEXT_TRACK -> fire(Command.NEXT)
            VK_MEDIA_PREV_TRACK -> fire(Command.PREV)
            VK_MEDIA_STOP -> fire(Command.PAUSE)
            else -> fire(Command.TOGGLE)
        }
        return true
    }

    private fun subclass(hwnd: HWND) {
        WindowHook.install(hwnd) { _, message, _, lParam ->
            if (message != WM_APPCOMMAND || player?.current == null) return@install null
            val command = when ((lParam.toLong() ushr 16).toInt() and 0xFFF) {
                11 -> Command.NEXT
                12 -> Command.PREV
                14 -> Command.TOGGLE
                46 -> Command.PLAY
                13, 47 -> Command.PAUSE
                else -> return@install null
            }
            fire(command)
            LRESULT(1)
        }
    }

    private fun fire(command: Command) {
        val target = player ?: return
        if (target.current == null) return
        val now = System.currentTimeMillis()
        if (now - lastFire < 180) return
        lastFire = now
        SwingUtilities.invokeLater {
            runCatching {
                when (command) {
                    Command.TOGGLE -> target.toggle()
                    Command.PLAY -> if (!target.isPlaying) target.toggle()
                    Command.PAUSE -> target.pause()
                    Command.NEXT -> target.next()
                    Command.PREV -> target.previous()
                }
            }
        }
    }

    private fun invoke(target: Pointer, index: Int, vararg args: Any?): Int {
        val table = target.getPointer(0) ?: return -1
        val function = Function.getFunction(table.getPointer(index.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return function.invokeInt(arrayOf(target, *args))
    }

    private fun pin(value: Any) {
        pins += value
    }

    private fun hex(value: Int) = "0x${Integer.toHexString(value)}"

    private enum class Command { TOGGLE, PLAY, PAUSE, NEXT, PREV }

    private interface QueryProc : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int
    }

    private interface CountProc : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer): Int
    }

    private interface PressProc : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int
    }

    private interface HookProc : StdCallLibrary.StdCallCallback {
        fun callback(nCode: Int, wParam: Long, lParam: Long): Long
    }

    private interface Winrt : StdCallLibrary {
        fun RoInitialize(kind: Int): Int
        fun WindowsCreateString(source: Pointer, length: Int, out: PointerByReference): Int
        fun WindowsDeleteString(value: Pointer?): Int
        fun RoGetActivationFactory(classId: Pointer?, iid: Pointer, factory: PointerByReference): Int

        companion object {
            val INSTANCE: Winrt = Native.load("combase", Winrt::class.java)
        }
    }

    private interface UserEx : StdCallLibrary {
        fun SetWindowsHookEx(idHook: Int, proc: HookProc, module: Pointer?, threadId: Int): Pointer?
        fun CallNextHookEx(hook: Pointer?, code: Int, wParam: Long, lParam: Long): Long
        fun RegisterHotKey(hwnd: Pointer?, id: Int, modifiers: Int, vk: Int): Boolean
        fun GetMessage(message: WinUser.MSG, hwnd: HWND?, min: Int, max: Int): Int
        fun TranslateMessage(message: WinUser.MSG): Boolean
        fun DispatchMessage(message: WinUser.MSG): Long

        companion object {
            val INSTANCE: UserEx = Native.load("user32", UserEx::class.java, W32APIOptions.UNICODE_OPTIONS)
        }
    }
}
