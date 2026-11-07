package com.metrolist.desktop

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClosePolicyTest {
    @Test
    fun closeHidesOnlyWhenOptedInAndTrayWorks() {
        val cases = listOf(
            Input(optIn = true, supported = true, installed = true) to CloseAction.MinimizeToTray,
            Input(optIn = true, supported = true, installed = false) to CloseAction.Exit,
            Input(optIn = true, supported = false, installed = true) to CloseAction.Exit,
            Input(optIn = true, supported = false, installed = false) to CloseAction.Exit,
            Input(optIn = false, supported = true, installed = true) to CloseAction.Exit,
            Input(optIn = false, supported = true, installed = false) to CloseAction.Exit,
            Input(optIn = false, supported = false, installed = true) to CloseAction.Exit,
            Input(optIn = false, supported = false, installed = false) to CloseAction.Exit,
        )
        for ((input, expected) in cases) {
            assertEquals(
                expected,
                decideWindowClose(input.optIn, input.supported, input.installed),
                input.toString(),
            )
        }
    }

    @Test
    fun leftClickRestoresAndPopupClickDoesNot() {
        assertTrue(trayClickRestoresWindow(button = 1, popupTrigger = false))
        assertFalse(trayClickRestoresWindow(button = 1, popupTrigger = true))
        assertFalse(trayClickRestoresWindow(button = 3, popupTrigger = false))
        assertFalse(trayClickRestoresWindow(button = 3, popupTrigger = true))
        assertFalse(trayClickRestoresWindow(button = 2, popupTrigger = false))
    }

    @Test
    fun trayMenuLabels() {
        assertEquals("Pencereyi gizle", trayWindowItemLabel(windowVisible = true))
        assertEquals("Pencereyi göster", trayWindowItemLabel(windowVisible = false))
        assertEquals("Duraklat", trayPlaybackItemLabel(playing = true))
        assertEquals("Oynat", trayPlaybackItemLabel(playing = false))
        assertEquals("Önceki", TRAY_PREVIOUS)
        assertEquals("Sonraki", TRAY_NEXT)
        assertEquals("Çıkış", TRAY_EXIT)
        assertEquals("Metrolist", trayTooltip(null, "Candan Erçetin"))
        assertEquals("Metrolist", trayTooltip("  ", null))
        assertEquals("Metrolist — Gamsız Hayat", trayTooltip(" Gamsız Hayat ", " "))
        assertEquals(
            "Metrolist — Gamsız Hayat — Candan Erçetin",
            trayTooltip("Gamsız Hayat", "Candan Erçetin"),
        )
    }

    @Test
    fun trayIconLoadsWithoutADisplay() {
        val image = loadTrayImage()
        assertTrue(image.getWidth(null) > 0)
        assertTrue(image.getHeight(null) > 0)
    }

    @Test
    fun settingDefaultsOffAndSurvivesRewrite() {
        assertFalse(Settings().minimizeToTray)
        val json = Json { ignoreUnknownKeys = true }
        val legacy = """{"cookie":"x","quality":"HIGH","accent":"rose","extra":1}"""
        val decoded = json.decodeFromString<Settings>(legacy)
        assertEquals("x", decoded.cookie)
        assertEquals("HIGH", decoded.quality)
        assertEquals("rose", decoded.accent)
        assertFalse(decoded.minimizeToTray)
        val saved = json.decodeFromString<Settings>(json.encodeToString(decoded.copy(minimizeToTray = true)))
        assertTrue(saved.minimizeToTray)
        assertEquals("HIGH", saved.quality)
        assertEquals("x", saved.cookie)
        assertEquals("rose", saved.accent)
        val cleared = json.decodeFromString<Settings>(json.encodeToString(saved.copy(minimizeToTray = false)))
        assertFalse(cleared.minimizeToTray)
        assertEquals("HIGH", cleared.quality)
    }

    @Test
    fun shutdownStopsReleasesThenExitsOnce() {
        val calls = mutableListOf<String>()
        val shutdown = shutdown(calls)
        shutdown.run()
        assertEquals(listOf("stop", "release", "cancel", "tray", "exit"), calls)
        calls.clear()
        shutdown.run()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun shutdownContinuesWhenEarlierStepsFail() {
        val calls = mutableListOf<String>()
        val shutdown = shutdown(calls, fail = setOf("stop", "release", "tray"))
        shutdown.run()
        assertEquals(listOf("stop", "release", "cancel", "tray", "exit"), calls)
        calls.clear()
        shutdown.run()
        assertEquals(emptyList(), calls)
    }

    @Test
    fun exitFailureFollowsCleanupAndCanBeRetried() {
        val calls = mutableListOf<String>()
        var failExit = true
        val shutdown = PlayerShutdown(object : PlayerResources {
            override fun stopPlayback() = step(calls, emptySet(), "stop")
            override fun releasePlayer() = step(calls, emptySet(), "release")
            override fun cancelWork() = step(calls, emptySet(), "cancel")
            override fun removeTray() = step(calls, emptySet(), "tray")
            override fun exit() {
                calls += "exit"
                if (failExit) error("exit")
            }
        })
        val error = assertFailsWith<IllegalStateException> { shutdown.run() }
        assertEquals("exit", error.message)
        assertEquals(listOf("stop", "release", "cancel", "tray", "exit"), calls)
        calls.clear()
        failExit = false
        shutdown.run()
        assertEquals(listOf("stop", "release", "cancel", "tray", "exit"), calls)
        calls.clear()
        shutdown.run()
        assertEquals(emptyList(), calls)
    }

    private fun shutdown(calls: MutableList<String>, fail: Set<String> = emptySet()): PlayerShutdown {
        return PlayerShutdown(object : PlayerResources {
            override fun stopPlayback() = step(calls, fail, "stop")
            override fun releasePlayer() = step(calls, fail, "release")
            override fun cancelWork() = step(calls, fail, "cancel")
            override fun removeTray() = step(calls, fail, "tray")
            override fun exit() = step(calls, fail, "exit")
        })
    }

    private fun step(calls: MutableList<String>, fail: Set<String>, name: String) {
        calls += name
        if (name in fail) error(name)
    }

    private data class Input(val optIn: Boolean, val supported: Boolean, val installed: Boolean)
}
