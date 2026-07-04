package com.bento.calendar.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.bento.calendar.data.Category

fun hexColor(hex: String): Color {
    // Defense in depth: a malformed persisted hex (e.g. from a hand-edited
    // backup) must never crash the theme root — fall back to the default
    // accent (Accents.DEFAULT, #8B6FE8) instead of throwing.
    val v = hex.removePrefix("#").toLongOrNull(16) ?: return Color(0xFF8B6FE8)
    return Color(0xFF000000L or v)
}

/**
 * Design tokens from the handoff README. Everything reads these via
 * [LocalBento]; nothing hardcodes theme colors.
 */
@Immutable
data class BentoColors(
    val bg: Color,
    val tile: Color,
    val bd: Color,
    val line: Color,
    val tx: Color,
    val sub: Color,
    val faint: Color,
    val inp: Color,
    val cbb: Color,
    val scrim: Color,
    val dng: Color,
    val acc: Color,
    val isLight: Boolean,
) {
    /** Tinted fill: accent at [pct] opacity over transparent. */
    fun accTint(pct: Float) = acc.copy(alpha = pct)
}

val DarkColors = BentoColors(
    bg = Color(0xFF0C0D12),
    tile = Color(0xFF14161D),
    bd = Color(0xFF1E212B),
    line = Color(0xFF191C25),
    tx = Color(0xFFEDEEF4),
    sub = Color(0xFF7E8494),
    faint = Color(0xFF565D6E),
    inp = Color(0xFF191C25),
    cbb = Color(0xFF333846),
    scrim = Color(0xFF040509).copy(alpha = 0.62f),
    dng = Color(0xFFF26D6D),
    acc = hexColor("#8B6FE8"),
    isLight = false,
)

val LightColors = BentoColors(
    bg = Color(0xFFF3F4F8),
    tile = Color(0xFFFFFFFF),
    bd = Color(0xFFE7E8F0),
    line = Color(0xFFEBECF2),
    tx = Color(0xFF1C1D24),
    sub = Color(0xFF6B7080),
    faint = Color(0xFFA3A7B5),
    inp = Color(0xFFEDEEF4),
    cbb = Color(0xFFC6C9D6),
    scrim = Color(0xFF191923).copy(alpha = 0.38f),
    dng = Color(0xFFF26D6D),
    acc = hexColor("#8B6FE8"),
    isLight = true,
)

val LocalBento = staticCompositionLocalOf { DarkColors }

/** The header create (+) buttons stay fixed blue regardless of accent. */
val CreateBlue = Color(0xFF5B8DEF)

/** Text on solid category blocks (week/day view). */
val OnCategory = Color(0xFF12141B)

val Category.color: Color get() = hexColor(colorHex)
