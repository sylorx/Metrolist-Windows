package com.metrolist.desktop

import java.awt.Color
import java.awt.Font
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon
import java.awt.image.BufferedImage

internal object AppTray {
    private var icon: TrayIcon? = null

    fun install(onShow: () -> Unit, onExit: () -> Unit): Boolean {
        if (icon != null) return true
        if (!SystemTray.isSupported()) return false
        return runCatching {
            val menu = PopupMenu()
            menu.add(MenuItem("Göster").apply { addActionListener { onShow() } })
            menu.add(MenuItem("Çık").apply { addActionListener { onExit() } })
            val created = TrayIcon(image(), "Metrolist", menu).apply {
                isImageAutoSize = true
                addActionListener { onShow() }
            }
            SystemTray.getSystemTray().add(created)
            icon = created
            true
        }.getOrDefault(false)
    }

    fun hint(title: String, artists: String) {
        val text = if (title.isBlank()) "Metrolist" else "$title — $artists".take(60)
        if (icon?.toolTip != text) icon?.toolTip = text
    }

    fun remove() {
        val current = icon ?: return
        icon = null
        runCatching { SystemTray.getSystemTray().remove(current) }
    }

    private fun image(): BufferedImage {
        val size = 16
        val image = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
        val canvas = image.createGraphics()
        canvas.color = Color(30, 215, 96)
        canvas.fillOval(1, 1, size - 2, size - 2)
        canvas.color = Color(6, 24, 16)
        canvas.font = Font("Dialog", Font.BOLD, 11)
        canvas.drawString("M", 3, 12)
        canvas.dispose()
        return image
    }
}
