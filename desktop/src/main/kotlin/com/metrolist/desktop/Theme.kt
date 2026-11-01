package com.metrolist.desktop

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal val LocalPalette = staticCompositionLocalOf { paletteOf("mint", "ink", "soft", "normal") }

internal data class Palette(
    val ink: Color,
    val panel: Color,
    val card: Color,
    val accent: Color,
    val muted: Color,
    val corner: Dp,
    val cardWidth: Dp,
)

internal data class Look(
    val accentId: String = "mint",
    val surfaceId: String = "ink",
    val cornerId: String = "soft",
    val cardId: String = "normal",
) {
    val palette: Palette
        get() = paletteOf(accentId, surfaceId, cornerId, cardId)

    companion object {
        fun from(settings: Settings) = Look(
            accentId = settings.accent.ifBlank { "mint" },
            surfaceId = settings.surface.ifBlank { "ink" },
            cornerId = settings.corners.ifBlank { "soft" },
            cardId = settings.cards.ifBlank { "normal" },
        )
    }
}

internal data class AccentChoice(val id: String, val label: String, val color: Color)

internal val accentChoices = listOf(
    AccentChoice("mint", "Nane", Color(0xFF1ED760)),
    AccentChoice("rose", "Gül", Color(0xFFFF4D6D)),
    AccentChoice("amber", "Kehribar", Color(0xFFF5A524)),
    AccentChoice("lime", "Yeşil", Color(0xFF7CDE6A)),
    AccentChoice("sky", "Gök", Color(0xFF4CC9F0)),
    AccentChoice("violet", "Mor", Color(0xFFA78BFA)),
    AccentChoice("coral", "Mercan", Color(0xFFFF7A59)),
)

internal data class SurfaceChoice(val id: String, val label: String, val ink: Color, val panel: Color, val card: Color)

internal val surfaceChoices = listOf(
    SurfaceChoice("ink", "Mürekkep", Color(0xFF0B0C0E), Color(0xFF121316), Color(0xFF1A1C20)),
    SurfaceChoice("coal", "Kömür", Color(0xFF070708), Color(0xFF101012), Color(0xFF1C1C22)),
    SurfaceChoice("slate", "Arduvaz", Color(0xFF12151C), Color(0xFF1A1F2B), Color(0xFF242B3A)),
    SurfaceChoice("navy", "Lacivert", Color(0xFF0C1220), Color(0xFF141B2D), Color(0xFF1C2740)),
    SurfaceChoice("plum", "Erik", Color(0xFF140E14), Color(0xFF1E141C), Color(0xFF2A1C28)),
)

internal val cornerChoices = listOf(
    "sharp" to "Keskin",
    "soft" to "Yumuşak",
    "round" to "Yuvarlak",
)

internal val cardChoices = listOf(
    "compact" to "Kompakt",
    "normal" to "Normal",
    "wide" to "Geniş",
)

internal fun paletteOf(accentId: String, surfaceId: String, cornerId: String, cardId: String): Palette {
    val accent = accentChoices.firstOrNull { it.id == accentId }?.color ?: accentChoices.first().color
    val surface = surfaceChoices.firstOrNull { it.id == surfaceId } ?: surfaceChoices.first()
    val corner = when (cornerId) {
        "sharp" -> 4.dp
        "round" -> 18.dp
        else -> 12.dp
    }
    val cardWidth = when (cardId) {
        "compact" -> 120.dp
        "wide" -> 188.dp
        else -> 150.dp
    }
    return Palette(
        ink = surface.ink,
        panel = surface.panel,
        card = surface.card,
        accent = accent,
        muted = Color(0xFFA1A1AA),
        corner = corner,
        cardWidth = cardWidth,
    )
}
