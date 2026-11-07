package com.metrolist.desktop

/**
 * Closing the window hides it only when the user opted in and a tray icon is actually installed.
 * Otherwise the process exits, so a missing tray cannot leave the player running with no window.
 */
fun decideWindowClose(
    minimizeToTray: Boolean,
    platformTraySupported: Boolean,
    trayInstalled: Boolean,
): CloseAction {
    if (minimizeToTray && platformTraySupported && trayInstalled) return CloseAction.MinimizeToTray
    return CloseAction.Exit
}

enum class CloseAction {
    MinimizeToTray,
    Exit,
}

/**
 * Left click and double-click restore the window. The button that opens the menu does not,
 * so the menu can still be used while the window is hidden.
 *
 * [button] is [java.awt.event.MouseEvent.getButton]. `1` is the left button.
 */
fun trayClickRestoresWindow(button: Int, popupTrigger: Boolean): Boolean {
    if (popupTrigger) return false
    return button == 1
}

fun trayWindowItemLabel(windowVisible: Boolean): String =
    if (windowVisible) "Pencereyi gizle" else "Pencereyi göster"

fun trayPlaybackItemLabel(playing: Boolean): String =
    if (playing) "Duraklat" else "Oynat"

const val TRAY_PREVIOUS = "Önceki"
const val TRAY_NEXT = "Sonraki"
const val TRAY_EXIT = "Çıkış"

fun trayTooltip(title: String?, artists: String?): String {
    val name = title?.trim().orEmpty()
    if (name.isEmpty()) return "Metrolist"
    val who = artists?.trim().orEmpty()
    return if (who.isEmpty()) "Metrolist — $name" else "Metrolist — $name — $who"
}

interface PlayerResources {
    fun stopPlayback()
    fun releasePlayer()
    fun cancelWork()
    fun removeTray()
    fun exit()
}

/**
 * Stops playback, releases the player, cancels background work, removes the tray, then exits.
 * A failing step does not skip the ones after it. Once exit has been requested, another call
 * does nothing. If exit itself fails, a later call can try again.
 */
class PlayerShutdown(private val resources: PlayerResources) {
    private val gate = Any()
    private var state = 0

    fun run() {
        synchronized(gate) {
            if (state != 0) return
            state = 1
        }
        val failure = firstFailure(
            resources::stopPlayback,
            resources::releasePlayer,
            resources::cancelWork,
            resources::removeTray,
        )
        try {
            resources.exit()
        } catch (exitError: Throwable) {
            synchronized(gate) { state = 0 }
            if (failure != null) exitError.addSuppressed(failure)
            throw exitError
        }
        synchronized(gate) { state = 2 }
    }

    private fun firstFailure(vararg steps: () -> Unit): Throwable? {
        var failure: Throwable? = null
        for (step in steps) {
            try {
                step()
            } catch (error: Throwable) {
                if (failure == null) failure = error else failure.addSuppressed(error)
            }
        }
        return failure
    }
}
