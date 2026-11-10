package com.metrolist.desktop

import com.sun.jna.Function
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.BaseTSD.ULONG_PTR
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.Guid.CLSID
import com.sun.jna.platform.win32.Guid.IID
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.win32.W32APIOptions
import com.sun.jna.platform.win32.WinDef.HBITMAP
import com.sun.jna.platform.win32.WinDef.HICON
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.ptr.PointerByReference
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.RenderingHints
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.Window
import java.awt.image.BufferedImage
import com.sun.jna.Library
import com.sun.jna.win32.StdCallLibrary

internal object TaskbarMedia {
    const val PREVIOUS = 1
    const val TOGGLE = 2
    const val NEXT = 3
    const val WIDGET = 4

    private const val WM_COMMAND = 0x0111
    private const val THBN_CLICKED = 0x1800
    private const val THB_ICON = 0x2
    private const val THB_TOOLTIP = 0x4
    private const val THB_FLAGS = 0x8
    private const val THBF_ENABLED = 0
    private const val THBF_DISABLED = 0x1

    private var commands: (Int) -> Unit = {}
    private var list: Pointer? = null
    private var proc: SubclassProc? = null
    private val windows = mutableListOf<HWND>()
    private var previousIcon: HICON? = null
    private var playIcon: HICON? = null
    private var pauseIcon: HICON? = null
    private var nextIcon: HICON? = null
    private var tray: TrayIcon? = null
    private var frameProc: SubclassProc? = null

    fun bind(onCommand: (Int) -> Unit) {
        commands = onCommand
        runCatching { ensureTray() }
    }

    fun announce(title: String, message: String) {
        javax.swing.SwingUtilities.invokeLater {
            runCatching {
                ensureTray()
                tray?.displayMessage(title.take(64), message.take(240), TrayIcon.MessageType.INFO)
            }
        }
    }

    fun attach(frame: Window) {
        runCatching { attachNative(frame) }.onFailure { println("Metrolist: görev çubuğu düğmeleri kurulamadı (${it.message})") }
    }

    fun asDesktopWidget(frame: Window) {
        runCatching { stripChrome(frame) }
    }

    fun sync(playing: Boolean, enabled: Boolean, title: String) {
        runCatching {
            tray?.toolTip = if (title.isBlank()) "Metrolist" else title.take(60)
            val taskbar = list ?: return
            if (windows.isEmpty()) return
            val buttons = buttons(playing, enabled)
            windows.forEach { hwnd -> update(taskbar, hwnd, buttons) }
        }
    }

    private fun stripChrome(frame: Window) {
        val raw = Native.getComponentPointer(frame) ?: return
        val hwnd = HWND(raw)
        val user = User32.INSTANCE
        val extended = user.GetWindowLongPtr(hwnd, -20).toLong()
        val desktop = (extended or 0x80L) and 0x00040000L.inv()
        user.SetWindowLongPtr(hwnd, -20, Pointer(desktop))
        user.SetWindowPos(hwnd, HWND(Pointer.createConstant(-2)), 0, 0, 0, 0, 0x0033)
    }

    var barDrag: Boolean = false
    var toastDrag: Boolean = false
    var captionBarPx: Int = 48
    var captionButtonsPx: Int = 150

    fun customFrame(frame: Window) {
        runCatching {
            val raw = Native.getComponentPointer(frame) ?: return
            val hwnd = HWND(raw)
            val user = User32.INSTANCE
            val style = user.GetWindowLongPtr(hwnd, -16).toLong()
            val thick = style or 0x00040000L or 0x00010000L or 0x00020000L
            user.SetWindowLongPtr(hwnd, -16, Pointer(thick))
            val extended = user.GetWindowLongPtr(hwnd, -20).toLong()
            val shown = (extended or 0x40000L) and 0x80L.inv()
            user.SetWindowLongPtr(hwnd, -20, Pointer(shown))
            ensureFrameHook()
            Comctl.INSTANCE.SetWindowSubclass(hwnd, frameProc, ULONG_PTR(91), ULONG_PTR(0))
            val rect = com.sun.jna.platform.win32.WinDef.RECT()
            user.GetWindowRect(hwnd, rect)
            val width = rect.right - rect.left
            val height = rect.bottom - rect.top
            user.SetWindowPos(hwnd, HWND(Pointer.NULL), rect.left, rect.top, width, height, 0x0034)
            attribute(hwnd, 20, 1)
            attribute(hwnd, 34, 0x000C0B0B)
            val flat = com.sun.jna.Memory(16)
            Dwm.INSTANCE.DwmExtendFrameIntoClientArea(hwnd, flat)
            frame.invalidate()
            frame.repaint()
        }
    }

    fun paintEdge(frame: Window, argb: Int) {
        val raw = Native.getComponentPointer(frame) ?: return
        val red = (argb shr 16) and 0xFF
        val green = (argb shr 8) and 0xFF
        val blue = argb and 0xFF
        attribute(HWND(raw), 34, (blue shl 16) or (green shl 8) or red)
    }

    fun raise(frame: Window) {
        val raw = Native.getComponentPointer(frame) ?: return
        User32.INSTANCE.SetWindowPos(HWND(raw), HWND(Pointer.createConstant(-1)), 0, 0, 0, 0, 0x0013)
    }

    fun paintCaption(frame: Window, argb: Int?) {
        runCatching {
            val raw = Native.getComponentPointer(frame) ?: return
            val hwnd = HWND(raw)
            if (argb == null) {
                attribute(hwnd, 35, -1)
                attribute(hwnd, 36, -1)
                attribute(hwnd, 34, -1)
                attribute(hwnd, 20, if (appsUseLightTheme()) 0 else 1)
                return
            }
            val red = (argb shr 16) and 0xFF
            val green = (argb shr 8) and 0xFF
            val blue = argb and 0xFF
            val light = (0.2126 * red + 0.7152 * green + 0.0722 * blue) > 150
            attribute(hwnd, 20, if (light) 0 else 1)
            val ref = (blue shl 16) or (green shl 8) or red
            attribute(hwnd, 35, ref)
            attribute(hwnd, 36, if (light) 0x00121212 else 0x00FFFFFF)
        }
    }

    fun windowCorner(frame: Window, id: String) {
        val raw = Native.getComponentPointer(frame) ?: return
        val pref = when (id) {
            "sharp" -> 1
            "small" -> 3
            else -> 2
        }
        attribute(HWND(raw), 33, pref)
    }

    private fun attribute(hwnd: HWND, id: Int, value: Int) {
        val memory = com.sun.jna.Memory(4)
        memory.setInt(0, value)
        Dwm.INSTANCE.DwmSetWindowAttribute(hwnd, id, memory, 4)
    }

    private fun appsUseLightTheme(): Boolean = runCatching {
        com.sun.jna.platform.win32.Advapi32Util.registryGetIntValue(
            com.sun.jna.platform.win32.WinReg.HKEY_CURRENT_USER,
            "Software\\Microsoft\\Windows\\CurrentVersion\\Themes\\Personalize",
            "AppsUseLightTheme",
        ) != 0
    }.getOrDefault(true)

    fun cling(frame: Window, x: Int, y: Int, width: Int, height: Int) {
        frame.focusableWindowState = false
        val raw = Native.getComponentPointer(frame) ?: return
        val hwnd = HWND(raw)
        val user = User32.INSTANCE
        val extended = user.GetWindowLongPtr(hwnd, -20).toLong()
        val tool = (extended or 0x80L or 0x08000000L) and 0x00040000L.inv()
        if (extended != tool) {
            user.SetWindowLongPtr(hwnd, -20, Pointer(tool))
            user.SetWindowPos(hwnd, HWND(Pointer.createConstant(-1)), x, y, width, height, 0x0070)
        } else {
            user.SetWindowPos(hwnd, HWND(Pointer.createConstant(-1)), x, y, width, height, 0x0050)
        }
    }

    fun desktopBounds(): java.awt.Rectangle {
        val devices = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        var area: java.awt.Rectangle? = null
        devices.forEach { device ->
            val bounds = device.defaultConfiguration.bounds
            area = area?.union(bounds) ?: java.awt.Rectangle(bounds)
        }
        return area ?: java.awt.Rectangle(java.awt.Toolkit.getDefaultToolkit().screenSize)
    }

    fun taskbarBounds(): java.awt.Rectangle {
        val screen = java.awt.Toolkit.getDefaultToolkit().screenSize
        val fallback = java.awt.Rectangle(0, screen.height - 48, screen.width, 48)
        return runCatching {
            val hwnd = User32.INSTANCE.FindWindow("Shell_TrayWnd", null) ?: return fallback
            val rect = com.sun.jna.platform.win32.WinDef.RECT()
            if (!User32.INSTANCE.GetWindowRect(hwnd, rect)) return fallback
            val width = (rect.right - rect.left).coerceAtLeast(1)
            val height = (rect.bottom - rect.top).coerceAtLeast(1)
            java.awt.Rectangle(rect.left, rect.top, width, height)
        }.getOrDefault(fallback)
    }

    private fun attachNative(frame: Window) {
        val raw = Native.getComponentPointer(frame) ?: return
        val hwnd = HWND(raw)
        if (windows.any { it.pointer == hwnd.pointer }) return
        val taskbar = ensureTaskbar() ?: return
        ensureIcons()
        ensureHook()
        val buttons = buttons(playing = false, enabled = false)
        val hr = call(taskbar, 15, hwnd, 3, buttons[0].pointer)
        if (hr < 0) return
        val id = ULONG_PTR((windows.size + 1).toLong())
        val hooked = Comctl.INSTANCE.SetWindowSubclass(hwnd, proc, id, ULONG_PTR(0)) != 0
        if (hooked) windows += hwnd
    }

    private fun ensureFrameHook() {
        if (frameProc != null) return
        frameProc = object : SubclassProc {
            override fun callback(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM, id: ULONG_PTR, data: ULONG_PTR): LRESULT {
                if (msg == 0x0083) {
                    if (wParam.toInt() != 0) coverFrame(hwnd, lParam)
                    return LRESULT(0)
                }
                if (msg == 0x0085 || msg == 0x00AE) return LRESULT(0)
                if (msg == 0x0086) return Comctl.INSTANCE.DefSubclassProc(hwnd, msg, wParam, LPARAM(-1))
                if (msg == 0x0084) {
                    val hit = captionHit(hwnd, lParam)
                    if (hit != null) return LRESULT(hit.toLong())
                }
                return Comctl.INSTANCE.DefSubclassProc(hwnd, msg, wParam, lParam)
            }
        }
    }

    private fun coverFrame(hwnd: HWND, lParam: LPARAM) {
        val zoomed = (User32.INSTANCE.GetWindowLong(hwnd, -16) and 0x01000000) != 0
        if (!zoomed) return
        val params = Pointer(lParam.toLong())
        val user = User32.INSTANCE
        val frame = user.GetSystemMetrics(32) + user.GetSystemMetrics(92)
        params.setInt(0, params.getInt(0) + frame)
        params.setInt(4, params.getInt(4) + frame)
        params.setInt(8, params.getInt(8) - frame)
        params.setInt(12, params.getInt(12) - frame)
    }

    private fun captionHit(hwnd: HWND, lParam: LPARAM): Int? {
        val user = User32.INSTANCE
        val rect = com.sun.jna.platform.win32.WinDef.RECT()
        if (!user.GetWindowRect(hwnd, rect)) return null
        val packed = lParam.toLong().toInt()
        val x = (packed and 0xFFFF).toShort().toInt()
        val y = ((packed ushr 16) and 0xFFFF).toShort().toInt()
        val zoomed = (user.GetWindowLong(hwnd, -16) and 0x01000000) != 0
        val border = if (zoomed) 0 else 8
        val left = border > 0 && x < rect.left + border
        val right = border > 0 && x >= rect.right - border
        val top = border > 0 && y < rect.top + border
        val bottom = border > 0 && y >= rect.bottom - border
        if (top && left) return 13
        if (top && right) return 14
        if (bottom && left) return 16
        if (bottom && right) return 17
        if (left) return 10
        if (right) return 11
        if (top) return 12
        if (bottom) return 15
        return null
    }

    private fun ensureHook() {
        if (proc != null) return
        proc = object : SubclassProc {
            override fun callback(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM, id: ULONG_PTR, data: ULONG_PTR): LRESULT {
                if (msg == WM_COMMAND) {
                    val packed = wParam.toLong().toInt()
                    val event = (packed ushr 16) and 0xFFFF
                    val command = packed and 0xFFFF
                    if (event == THBN_CLICKED && command in PREVIOUS..NEXT) {
                        commands(command)
                    }
                }
                return Comctl.INSTANCE.DefSubclassProc(hwnd, msg, wParam, lParam)
            }
        }
    }

    private fun ensureTaskbar(): Pointer? {
        list?.let { return it }
        Ole32.INSTANCE.CoInitializeEx(null, 2)
        val out = PointerByReference()
        Ole32.INSTANCE.CoCreateInstance(
            CLSID("56FDF344-FD6D-11d0-958A-006097C9A090"),
            null,
            1,
            IID("EA1AFB91-9E28-4B86-90E9-9E9F8A5EEFAF"),
            out,
        )
        if (out.value == null) return null
        val pointer = out.value
        if (call(pointer, 3) < 0) return null
        list = pointer
        return pointer
    }

    private fun ensureIcons() {
        if (playIcon != null) return
        previousIcon = glyph { fillRect(1, 3, 2, 10); fillPolygon(intArrayOf(5, 14, 5), intArrayOf(3, 8, 13), 3) }
        playIcon = glyph { fillPolygon(intArrayOf(4, 13, 4), intArrayOf(2, 8, 14), 3) }
        pauseIcon = glyph { fillRect(4, 2, 3, 12); fillRect(9, 2, 3, 12) }
        nextIcon = glyph { fillPolygon(intArrayOf(2, 11, 2), intArrayOf(3, 8, 13), 3); fillRect(12, 3, 2, 10) }
    }

    private fun buttons(playing: Boolean, enabled: Boolean): Array<ThumbButton> {
        val first = ThumbButton()
        val rows = first.toArray(3) as Array<ThumbButton>
        val icons = listOf(previousIcon, if (playing) pauseIcon else playIcon, nextIcon)
        val tips = listOf("Önceki", if (playing) "Duraklat" else "Oynat", "Sonraki")
        val flags = if (enabled) THBF_ENABLED else THBF_DISABLED
        rows.forEachIndexed { index, button ->
            button.dwMask = THB_ICON or THB_TOOLTIP or THB_FLAGS
            button.iId = index + 1
            button.hIcon = icons[index]?.pointer
            button.szTip = ShortArray(260).also { tip ->
                tips[index].forEachIndexed { at, char -> if (at < tip.lastIndex) tip[at] = char.code.toShort() }
            }
            button.dwFlags = flags
            button.write()
        }
        return rows
    }

    private fun update(taskbar: Pointer, hwnd: HWND, buttons: Array<ThumbButton>) {
        call(taskbar, 16, hwnd, 3, buttons[0].pointer)
    }

    private fun call(taskbar: Pointer, index: Int, vararg args: Any?): Int {
        val table = taskbar.getPointer(0)
        val function = Function.getFunction(table.getPointer(index.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
        return function.invokeInt(arrayOf(taskbar, *args))
    }

    private fun ensureTray() {
        if (tray != null || !SystemTray.isSupported()) return
        val menu = PopupMenu()
        menu.add(item("Önceki", PREVIOUS))
        menu.add(item("Oynat / Duraklat", TOGGLE))
        menu.add(item("Sonraki", NEXT))
        menu.addSeparator()
        menu.add(item("Widget aç / kapat", WIDGET))
        val icon = TrayIcon(trayImage(), "Metrolist", menu).apply { isImageAutoSize = true }
        SystemTray.getSystemTray().add(icon)
        tray = icon
    }

    private fun item(label: String, command: Int) = MenuItem(label).apply {
        addActionListener { commands(command) }
    }

    private fun trayImage(): BufferedImage {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color(0x1E, 0xD7, 0x60)
        g.fillOval(0, 0, 16, 16)
        g.color = Color.BLACK
        g.fillPolygon(intArrayOf(5, 12, 5), intArrayOf(3, 8, 13), 3)
        g.dispose()
        return image
    }

    private fun glyph(draw: Graphics2D.() -> Unit): HICON? {
        val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.color = Color.WHITE
        g.stroke = BasicStroke(1.4f)
        g.draw()
        g.dispose()
        return icon(image)
    }

    private fun icon(image: BufferedImage): HICON? {
        val color = dib(image) ?: return null
        val mask = dib(BufferedImage(image.width, image.height, BufferedImage.TYPE_INT_ARGB)) ?: return null
        val info = WinGDI.ICONINFO()
        info.fIcon = true
        info.hbmColor = color
        info.hbmMask = mask
        info.write()
        return UserIcon.INSTANCE.CreateIconIndirect(info)
    }

    private fun dib(image: BufferedImage): HBITMAP? {
        val info = WinGDI.BITMAPINFO()
        info.bmiHeader.biSize = 40
        info.bmiHeader.biWidth = image.width
        info.bmiHeader.biHeight = -image.height
        info.bmiHeader.biPlanes = 1
        info.bmiHeader.biBitCount = 32
        info.bmiHeader.biCompression = WinGDI.BI_RGB
        val bits = PointerByReference()
        val bitmap = GDI32.INSTANCE.CreateDIBSection(null, info, WinGDI.DIB_RGB_COLORS, bits, null, 0) ?: return null
        val pixels = IntArray(image.width * image.height)
        image.getRGB(0, 0, image.width, image.height, pixels, 0, image.width)
        val memory = bits.value ?: return null
        pixels.forEachIndexed { index, pixel -> memory.setInt(index * 4L, pixel) }
        return bitmap
    }
}

private interface SubclassProc : StdCallLibrary.StdCallCallback {
    fun callback(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM, id: ULONG_PTR, data: ULONG_PTR): LRESULT
}

private interface Comctl : Library {
    fun SetWindowSubclass(hwnd: HWND, proc: SubclassProc?, id: ULONG_PTR, data: ULONG_PTR): Int
    fun DefSubclassProc(hwnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT

    companion object {
        val INSTANCE: Comctl = Native.load("comctl32", Comctl::class.java)
    }
}

private interface Dwm : StdCallLibrary {
    fun DwmSetWindowAttribute(hwnd: HWND, attribute: Int, value: Pointer, size: Int): Int
    fun DwmExtendFrameIntoClientArea(hwnd: HWND, margins: Pointer): Int

    companion object {
        val INSTANCE: Dwm = Native.load("dwmapi", Dwm::class.java)
    }
}

private interface UserIcon : StdCallLibrary {
    fun CreateIconIndirect(info: WinGDI.ICONINFO): HICON?

    companion object {
        val INSTANCE: UserIcon = Native.load("user32", UserIcon::class.java, W32APIOptions.UNICODE_OPTIONS)
    }
}

private open class ThumbButton : Structure() {
    @JvmField var dwMask: Int = 0
    @JvmField var iId: Int = 0
    @JvmField var iBitmap: Int = 0
    @JvmField var hIcon: Pointer? = null
    @JvmField var szTip: ShortArray = ShortArray(260)
    @JvmField var dwFlags: Int = 0

    override fun getFieldOrder() = listOf("dwMask", "iId", "iBitmap", "hIcon", "szTip", "dwFlags")
}
