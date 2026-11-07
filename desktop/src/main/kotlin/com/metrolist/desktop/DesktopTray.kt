package com.metrolist.desktop

import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

data class TrayMenuState(
    val windowVisible: Boolean,
    val playing: Boolean,
    val hasTrack: Boolean,
    val tooltip: String,
)

interface TrayActions {
    fun restoreWindow()
    fun toggleWindow()
    fun togglePlayback()
    fun previous()
    fun next()
    fun exit()
    fun snapshot(): TrayMenuState
}

fun systemTraySupported(): Boolean = try {
    !GraphicsEnvironment.isHeadless() && SystemTray.isSupported()
} catch (_: Throwable) {
    false
}

fun loadTrayImage(): java.awt.Image {
    val loaded = runCatching {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream("icons/logo.jpg")
            ?: DesktopTray::class.java.classLoader?.getResourceAsStream("icons/logo.jpg")
            ?: return@runCatching null
        stream.use { ImageIO.read(it) }
    }.getOrNull()
    if (loaded != null) return scaleTrayImage(loaded)
    return fallbackTrayImage()
}

internal fun scaleTrayImage(source: BufferedImage, size: Int = 32): BufferedImage {
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.drawImage(source, 0, 0, size, size, null)
    graphics.dispose()
    return image
}

internal fun fallbackTrayImage(): BufferedImage {
    val size = 32
    val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    graphics.color = Color(30, 215, 96)
    graphics.fillOval(2, 2, size - 4, size - 4)
    graphics.dispose()
    return image
}

/**
 * AWT tray, so a left click and a double-click can both restore the window.
 * Compose Desktop's Tray only exposes the platform action, which on Windows is a double-click.
 */
class DesktopTray private constructor(
    private val icon: TrayIcon,
    private val showHide: MenuItem,
    private val playPause: MenuItem,
    private val previous: MenuItem,
    private val next: MenuItem,
    private val actions: TrayActions,
) {
    @Volatile
    var isInstalled: Boolean = true
        private set

    fun update(state: TrayMenuState) {
        if (!isInstalled) return
        showHide.label = trayWindowItemLabel(state.windowVisible)
        playPause.label = trayPlaybackItemLabel(state.playing)
        val enabled = state.hasTrack
        playPause.isEnabled = enabled
        previous.isEnabled = enabled
        next.isEnabled = enabled
        icon.toolTip = state.tooltip
    }

    fun uninstall() {
        if (!isInstalled) return
        isInstalled = false
        runCatching { SystemTray.getSystemTray().remove(icon) }
    }

    private fun restoreFromClick(event: MouseEvent) {
        runCatching { update(actions.snapshot()) }
        if (!trayClickRestoresWindow(event.button, event.isPopupTrigger)) return
        restoreSoon()
    }

    private var lastRestoreAt = 0L

    private fun restoreSoon() {
        val now = System.currentTimeMillis()
        if (now - lastRestoreAt < 300) return
        lastRestoreAt = now
        SwingUtilities.invokeLater { actions.restoreWindow() }
    }

    companion object {
        fun tryInstall(image: java.awt.Image, actions: TrayActions): DesktopTray? {
            if (!systemTraySupported()) return null
            var trayIcon: TrayIcon? = null
            var installed: DesktopTray? = null
            return try {
                val showHide = MenuItem(trayWindowItemLabel(windowVisible = true))
                val playPause = MenuItem(trayPlaybackItemLabel(playing = false))
                val previousItem = MenuItem(TRAY_PREVIOUS)
                val nextItem = MenuItem(TRAY_NEXT)
                val exit = MenuItem(TRAY_EXIT)
                val popup = PopupMenu().apply {
                    add(showHide)
                    addSeparator()
                    add(playPause)
                    add(previousItem)
                    add(nextItem)
                    addSeparator()
                    add(exit)
                }
                val icon = TrayIcon(image, "Metrolist", popup).apply { isImageAutoSize = true }
                trayIcon = icon
                val tray = DesktopTray(icon, showHide, playPause, previousItem, nextItem, actions)
                installed = tray
                showHide.addActionListener { SwingUtilities.invokeLater { actions.toggleWindow() } }
                playPause.addActionListener { SwingUtilities.invokeLater { actions.togglePlayback() } }
                previousItem.addActionListener { SwingUtilities.invokeLater { actions.previous() } }
                nextItem.addActionListener { SwingUtilities.invokeLater { actions.next() } }
                exit.addActionListener { SwingUtilities.invokeLater { actions.exit() } }
                icon.addActionListener { tray.restoreSoon() }
                icon.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(event: MouseEvent) = tray.restoreFromClick(event)
                    override fun mouseReleased(event: MouseEvent) = tray.restoreFromClick(event)
                    override fun mouseClicked(event: MouseEvent) = tray.restoreFromClick(event)
                })
                SystemTray.getSystemTray().add(icon)
                tray.update(actions.snapshot())
                tray
            } catch (_: Throwable) {
                installed?.uninstall()
                val orphan = trayIcon
                if (installed == null && orphan != null) {
                    runCatching { SystemTray.getSystemTray().remove(orphan) }
                }
                null
            }
        }
    }
}
