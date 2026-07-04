package com.bento.calendar.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Every icon in the app, ported 1:1 from the prototype's inline stroke SVGs
 * (24x24 viewBox, fill none, round caps/joins, stroke widths 1.8–2.2).
 *
 * Paths are drawn in white so `Icon(..., tint = ...)` recolors them freely.
 * SVG `<circle>`/`<rect rx>` elements are converted to equivalent arc paths.
 */
object BentoIcons {

    /** Sun with a filled center dot — Today tab (prototype line ~219, sw 1.8). */
    val TabToday: ImageVector by lazy {
        icon("TabToday", 1.8f) { sw ->
            // <circle cx="12" cy="12" r="3.6" fill="currentColor" stroke="none">
            fillPath("M8.4 12a3.6 3.6 0 1 0 7.2 0a3.6 3.6 0 1 0 -7.2 0")
            strokePath(
                "M12 3.2v2.3M12 18.5v2.3M3.2 12h2.3M18.5 12h2.3" +
                    "M5.9 5.9l1.6 1.6M16.5 16.5l1.6 1.6M18.1 5.9l-1.6 1.6M7.5 16.5l-1.6 1.6",
                sw,
            )
        }
    }

    /** Rounded-rect calendar — Calendar tab (~220, sw 1.8). */
    val TabCalendar: ImageVector by lazy {
        icon("TabCalendar", 1.8f) { sw ->
            // <rect x="3.5" y="5" width="17" height="15.5" rx="3.5">
            strokePath(
                "M7 5h10a3.5 3.5 0 0 1 3.5 3.5v8.5a3.5 3.5 0 0 1 -3.5 3.5" +
                    "H7a3.5 3.5 0 0 1 -3.5 -3.5v-8.5a3.5 3.5 0 0 1 3.5 -3.5z",
                sw,
            )
            strokePath("M3.5 9.8h17M8.2 3v3.6M15.8 3v3.6", sw)
        }
    }

    /** [TabCalendar] glyph at sw 1.9 — settings 'Week starts Monday' row (~235). */
    val SettingsCalendar: ImageVector by lazy {
        icon("SettingsCalendar", 1.9f) { sw ->
            // <rect x="3.5" y="5" width="17" height="15.5" rx="3.5">
            strokePath(
                "M7 5h10a3.5 3.5 0 0 1 3.5 3.5v8.5a3.5 3.5 0 0 1 -3.5 3.5" +
                    "H7a3.5 3.5 0 0 1 -3.5 -3.5v-8.5a3.5 3.5 0 0 1 3.5 -3.5z",
                sw,
            )
            strokePath("M3.5 9.8h17M8.2 3v3.6M15.8 3v3.6", sw)
        }
    }

    /** Document with folded corner — Notes tab (~221, sw 1.8). */
    val TabNotes: ImageVector by lazy {
        icon("TabNotes", 1.8f) { sw ->
            strokePath("M6.2 3.5h7.9l4.4 4.4v12.6H6.2z", sw)
            strokePath("M14 3.5v4.5h4.6M9.3 13h5.4M9.3 16.2h3.6", sw)
        }
    }

    /** Checklist — Tasks tab (~222, sw 1.8). */
    val TabTasks: ImageVector by lazy {
        icon("TabTasks", 1.8f) { sw ->
            strokePath(
                "M4 6.2l1.7 1.7L8.6 5M12 6.5h8" +
                    "M4 12.7l1.7 1.7 2.9 -2.9M12 13h8M12 19.5h8M4.2 19.5h3.4",
                sw,
            )
        }
    }

    /** [TabTasks] glyph at sw 1.9 — settings 'Clear completed' row (~241). */
    val SettingsChecklist: ImageVector by lazy {
        icon("SettingsChecklist", 1.9f) { sw ->
            strokePath(
                "M4 6.2l1.7 1.7L8.6 5M12 6.5h8" +
                    "M4 12.7l1.7 1.7 2.9 -2.9M12 13h8M12 19.5h8M4.2 19.5h3.4",
                sw,
            )
        }
    }

    /** Plus — create buttons and FAB (~173, sw 2.2). */
    val Plus: ImageVector by lazy {
        icon("Plus", 2.2f) { sw ->
            strokePath("M12 5.5v13M5.5 12h13", sw)
        }
    }

    /** [Plus] at sw 2.0 — Notes (~203) and Tasks (~211) header create buttons. */
    val PlusLight: ImageVector by lazy {
        icon("PlusLight", 2.0f) { sw ->
            strokePath("M12 5.5v13M5.5 12h13", sw)
        }
    }

    /** Magnifier — search (~173, sw 1.9). */
    val Search: ImageVector by lazy {
        icon("Search", 1.9f) { sw ->
            // <circle cx="11" cy="11" r="6.5">
            strokePath("M4.5 11a6.5 6.5 0 1 0 13 0a6.5 6.5 0 1 0 -13 0", sw)
            strokePath("M16 16l4.5 4.5", sw)
        }
    }

    /**
     * Settings glyph: three lines with offset knob circles (~173, sw 1.8).
     * The source fills the knobs with var(--tile) to knock out the lines;
     * with a single tint we reproduce the knockout by segmenting each rail at
     * the knob's fill edge (r 2.4) so the gap under the ring reads identically.
     */
    val Sliders: ImageVector by lazy {
        icon("Sliders", 1.8f) { sw ->
            strokePath("M4.5 7.2H7M11.8 7.2H19.5M4.5 12H12.6M17.4 12H19.5M4.5 16.8H5.6M10.4 16.8H19.5", sw)
            // <circle cx="9.4" cy="7.2" r="2.4">, <circle cx="15" cy="12" r="2.4">,
            // <circle cx="8" cy="16.8" r="2.4">
            strokePath(
                "M7 7.2a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0" +
                    "M12.6 12a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0" +
                    "M5.6 16.8a2.4 2.4 0 1 0 4.8 0a2.4 2.4 0 1 0 -4.8 0",
                sw,
            )
        }
    }

    /** Bell — notification banner and reminder badge (~175, sw 1.9). */
    val Bell: ImageVector by lazy {
        icon("Bell", 1.9f) { sw ->
            strokePath(
                "M18 9.5a6 6 0 1 0 -12 0c0 5 -2 6 -2 6h16s-2 -1 -2 -6" +
                    "M10 19.5a2.2 2.2 0 0 0 4 0",
                sw,
            )
        }
    }

    /** X — dismiss (~175, sw 2.2). */
    val Close: ImageVector by lazy {
        icon("Close", 2.2f) { sw ->
            strokePath("M6 6l12 12M18 6L6 18", sw)
        }
    }

    /** Chevron left — back / prev (~184, sw 2.1). */
    val ChevronLeft: ImageVector by lazy {
        icon("ChevronLeft", 2.1f) { sw ->
            strokePath("M14.5 5.5L8 12l6.5 6.5", sw)
        }
    }

    /** Chevron right — next (~184, sw 2.1). */
    val ChevronRight: ImageVector by lazy {
        icon("ChevronRight", 2.1f) { sw ->
            strokePath("M9.5 5.5L16 12l-6.5 6.5", sw)
        }
    }

    /** Chevron down — completed-tasks disclosure (~215, sw 2.2). */
    val ChevronDown: ImageVector by lazy {
        icon("ChevronDown", 2.2f) { sw ->
            strokePath("M6 9.5l6 6 6 -6", sw)
        }
    }

    /** Map pin — event location (~177, sw 2.0). */
    val LocationPin: ImageVector by lazy {
        icon("LocationPin", 2.0f) { sw ->
            strokePath("M12 21s-7 -5.5 -7 -11a7 7 0 0 1 14 0c0 5.5 -7 11 -7 11z", sw)
            // <circle cx="12" cy="10" r="2.5">
            strokePath("M9.5 10a2.5 2.5 0 1 0 5 0a2.5 2.5 0 1 0 -5 0", sw)
        }
    }

    /** Padlock — locked notes (~179, sw 2.2). */
    val Lock: ImageVector by lazy {
        icon("Lock", 2.2f) { sw ->
            // <rect x="5" y="10.5" width="14" height="9.5" rx="3">
            strokePath(
                "M8 10.5h8a3 3 0 0 1 3 3v3.5a3 3 0 0 1 -3 3" +
                    "H8a3 3 0 0 1 -3 -3v-3.5a3 3 0 0 1 3 -3z",
                sw,
            )
            strokePath("M8.3 10.5V7.8a3.7 3.7 0 0 1 7.4 0v2.7", sw)
        }
    }

    /** [Lock] at sw 2.0 — note rows (~207), note editor (~225), settings PIN row (~245). */
    val LockLight: ImageVector by lazy {
        icon("LockLight", 2.0f) { sw ->
            // <rect x="5" y="10.5" width="14" height="9.5" rx="3">
            strokePath(
                "M8 10.5h8a3 3 0 0 1 3 3v3.5a3 3 0 0 1 -3 3" +
                    "H8a3 3 0 0 1 -3 -3v-3.5a3 3 0 0 1 3 -3z",
                sw,
            )
            strokePath("M8.3 10.5V7.8a3.7 3.7 0 0 1 7.4 0v2.7", sw)
        }
    }

    /** Push-pin tack — pin note action (~225, sw 2.0). */
    val PinTack: ImageVector by lazy {
        icon("PinTack", 2.0f) { sw ->
            strokePath("M9 4h6l1 7 2.5 2.5H5.5L8 11l1 -7zM12 13.5V20", sw)
        }
    }

    /** Trash can — delete actions (~225, sw 1.9). */
    val Trash: ImageVector by lazy {
        icon("Trash", 1.9f) { sw ->
            strokePath(
                "M4 6.5h16M9.5 6V4.5h5V6M6.5 6.5l1 13h9l1 -13M10 10v6M14 10v6",
                sw,
            )
        }
    }

    /** Document — note rows / search results; identical to [TabNotes]. */
    val Doc: ImageVector by lazy { TabNotes }

    /**
     * Tick — task checkboxes (~178). Source is viewBox 0 0 12 12,
     * path "M2.6 6.4l2.4 2.4 4.6-5.2" at sw 2.2; ported to the 24x24
     * viewport by scaling both the path and the stroke width by 2.
     */
    val Check: ImageVector by lazy {
        icon("Check", 4.4f) { sw ->
            strokePath("M5.2 12.8l4.8 4.8 9.2 -10.4", sw)
        }
    }

    /** Crescent moon — theme setting (~229, sw 2.0). */
    val Moon: ImageVector by lazy {
        icon("Moon", 2.0f) { sw ->
            strokePath("M20 13.5A8.5 8.5 0 0 1 10.5 4a7 7 0 1 0 9.5 9.5z", sw)
        }
    }

    /** Droplet — accent colour setting (~230, sw 2.0). */
    val Droplet: ImageVector by lazy {
        icon("Droplet", 2.0f) { sw ->
            strokePath(
                "M12 3.5s6 6.3 6 10.5a6 6 0 0 1 -12 0C6 9.8 12 3.5 12 3.5z",
                sw,
            )
        }
    }

    /** Clock — 24-hour time setting (~234, sw 2.0). */
    val Clock: ImageVector by lazy {
        icon("Clock", 2.0f) { sw ->
            // <circle cx="12" cy="12" r="8">
            strokePath("M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0", sw)
            strokePath("M12 7.5V12l3 2", sw)
        }
    }

    /** Stopwatch — event length setting (~237, sw 2.0). */
    val Timer: ImageVector by lazy {
        icon("Timer", 2.0f) { sw ->
            // <circle cx="12" cy="13.5" r="7">
            strokePath("M5 13.5a7 7 0 1 0 14 0a7 7 0 1 0 -14 0", sw)
            strokePath("M12 13.5V10M9.5 3h5", sw)
        }
    }

    /** Down arrow into a tray — app-update banner and settings row (new, drawn
     *  in the prototype's stroke style; no source SVG exists for it). */
    val Download: ImageVector by lazy {
        icon("Download", 1.9f) { sw ->
            strokePath("M12 4v11M7 10.5l5 5 5 -5M4.5 19.5h15", sw)
        }
    }

    /** Two curved arrows chasing each other — repeating-task badge and editor
     *  field (new, drawn in the prototype's stroke style; no source SVG). */
    val Repeat: ImageVector by lazy {
        icon("Repeat", 1.9f) { sw ->
            strokePath("M4 12a8 8 0 0 1 13.6 -5.6M20 12a8 8 0 0 1 -13.6 5.6", sw)
            strokePath("M17.6 2.6v3.8h-3.8M6.4 21.4v-3.8h3.8", sw)
        }
    }
}

/** Builds a 24x24 dp / 24x24 viewport [ImageVector]; [sw] is passed to the block. */
private fun icon(
    name: String,
    sw: Float = 1.9f,
    builder: ImageVector.Builder.(Float) -> Unit,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply { builder(sw) }.build()

/** Stroke-only path (fill none, round cap/join) — recolored by Icon tint. */
private fun ImageVector.Builder.strokePath(d: String, sw: Float) {
    addPath(
        pathData = addPathNodes(d),
        pathFillType = PathFillType.NonZero,
        fill = null,
        stroke = SolidColor(Color.White),
        strokeLineWidth = sw,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
    )
}

/** Filled subpath (e.g. the sun's center dot) — recolored by Icon tint. */
private fun ImageVector.Builder.fillPath(d: String) {
    addPath(
        pathData = addPathNodes(d),
        pathFillType = PathFillType.NonZero,
        fill = SolidColor(Color.White),
        stroke = null,
    )
}
