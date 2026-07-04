package com.bento.calendar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Natural-language quick add: "Dentist tuesday 3pm", "Gym tomorrow 7am for
 * 45min", "Buy milk fri". A time makes it an event; date-only (or bare text)
 * makes it a task. Recognized tokens are stripped from the title.
 */
data class QuickParsed(
    val title: String,
    /** Parsed date, or null when none was mentioned. */
    val date: LocalDate?,
    /** "HH:MM" start — non-null means "this is an event". */
    val start: String?,
    /** "HH:MM" end; non-null iff [start] is. */
    val end: String?,
) {
    val isEvent: Boolean get() = start != null
}

private val WEEKDAYS = mapOf(
    "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
    "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
    "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
    "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
    "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
    "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
    "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY,
)

private val MONTHS = mapOf(
    "january" to 1, "jan" to 1, "february" to 2, "feb" to 2, "march" to 3, "mar" to 3,
    "april" to 4, "apr" to 4, "may" to 5, "june" to 6, "jun" to 6, "july" to 7, "jul" to 7,
    "august" to 8, "aug" to 8, "september" to 9, "sep" to 9, "sept" to 9,
    "october" to 10, "oct" to 10, "november" to 11, "nov" to 11, "december" to 12, "dec" to 12,
)

// Time: "3pm", "3.30pm", "3:30 pm", "15:00", "noon". Bare hours need am/pm so
// "buy 3 apples" keeps its 3.
private val TIME_RE = Regex(
    """\b(?:(?:at\s+)?(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)|(?:at\s+)(\d{1,2})(?:[:.](\d{2}))?|(\d{1,2}):(\d{2})|(noon))\b""",
    RegexOption.IGNORE_CASE,
)

// Duration: "for 45min" / "45 min" / "for 1h" / "1.5h" / "for 2 hours".
private val DUR_RE = Regex(
    """\b(?:for\s+)?(?:(\d+)\s*m(?:in|ins|inutes)?|(\d+(?:\.\d+)?)\s*h(?:r|rs|our|ours)?)\b""",
    RegexOption.IGNORE_CASE,
)

// "jul 15", "15 jul", "july 15th"
private val MONTH_DAY_RE = Regex(
    """\b(?:([a-z]{3,9})\s+(\d{1,2})(?:st|nd|rd|th)?|(\d{1,2})(?:st|nd|rd|th)?\s+([a-z]{3,9}))\b""",
    RegexOption.IGNORE_CASE,
)

// "15/7" or "15/07/2026" (day first, matching the app's non-US formats).
private val SLASH_DATE_RE = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")

private val RELATIVE_RE = Regex(
    """\b(today|tod|tomorrow|tmrw|tmr|tonight|next\s+week)\b""",
    RegexOption.IGNORE_CASE,
)

private val NEXT_WEEKDAY_RE = Regex(
    """\b(next\s+)?(monday|mon|tuesday|tues|tue|wednesday|wed|thursday|thurs|thur|thu|friday|fri|saturday|sat|sunday|sun)\b""",
    RegexOption.IGNORE_CASE,
)

/**
 * Parse [raw] against [today]. Returns null for blank input. A stripped-empty
 * title falls back to "Untitled". Events default to [defaultDurMin] long and
 * to [today] when a time is given without a date.
 */
fun parseQuickAdd(raw: String, today: LocalDate, defaultDurMin: Int = 60): QuickParsed? {
    if (raw.isBlank()) return null
    var text = " ${raw.trim()} "
    var date: LocalDate? = null
    var startMin: Int? = null
    var durMin: Int? = null

    // Duration first: its "1h" would otherwise be half-eaten by TIME_RE's
    // bare-hour branch when written as "at 1h".
    DUR_RE.find(text)?.let { m ->
        durMin = m.groupValues[1].toIntOrNull()
            ?: m.groupValues[2].toDoubleOrNull()?.let { (it * 60).toInt() }
        if (durMin != null && durMin!! in 1..1439) text = text.removeRange(m.range) else durMin = null
    }

    // True when the time had no am/pm marker ("at 9", "9:30") — "tonight"
    // may shift such times into the evening.
    var bareHour = false
    TIME_RE.find(text)?.let { m ->
        val g = m.groupValues
        val parsed: Int? = when {
            g[8].isNotEmpty() -> 12 * 60 // noon
            g[1].isNotEmpty() -> { // h(:mm) am/pm
                val h = g[1].toInt()
                val mm = g[2].toIntOrNull() ?: 0
                val pm = g[3].lowercase() == "pm"
                if (h in 1..12 && mm in 0..59) {
                    ((h % 12) + if (pm) 12 else 0) * 60 + mm
                } else null
            }
            g[4].isNotEmpty() -> { // "at 15" / "at 9:30" (24h, needs the "at")
                val h = g[4].toInt()
                val mm = g[5].toIntOrNull() ?: 0
                if (h in 0..23 && mm in 0..59) { bareHour = true; h * 60 + mm } else null
            }
            g[6].isNotEmpty() -> { // bare 15:00
                val h = g[6].toInt()
                val mm = g[7].toInt()
                if (h in 0..23 && mm in 0..59) { bareHour = true; h * 60 + mm } else null
            }
            else -> null
        }
        if (parsed != null) {
            startMin = parsed
            text = text.removeRange(m.range)
        }
    }

    // "tue next week": the "next week" match alone would date this to
    // today+7 and leave "tue" dangling in the title — remember it so the
    // weekday scan below can still claim the weekday ("tue next week" ≡
    // "next tue").
    var fromNextWeek = false
    RELATIVE_RE.find(text)?.let { m ->
        val word = m.groupValues[1].lowercase().replace(Regex("""\s+"""), " ")
        date = when (word) {
            "today", "tod" -> today
            "tomorrow", "tmrw", "tmr" -> today.plusDays(1)
            "tonight" -> today.also {
                val s = startMin
                // No time → evening default; a bare daytime hour ("tonight
                // at 9") shifts to the evening. Explicit am/pm is respected.
                if (s == null) {
                    startMin = 20 * 60
                } else if (bareHour && s in 60..719) {
                    startMin = s + 12 * 60
                }
            }
            "next week" -> today.plusWeeks(1).also { fromNextWeek = true }
            else -> null
        }
        if (date != null) text = text.removeRange(m.range)
    }

    if (date == null || fromNextWeek) {
        NEXT_WEEKDAY_RE.find(text)?.let { m ->
            val dow = WEEKDAYS[m.groupValues[2].lowercase()] ?: return@let
            // Plain weekday = the next one, today included ("sat" typed on a
            // Saturday means today); "next sat" — or a weekday alongside
            // "next week" — jumps a further week.
            var d = today.with(TemporalAdjusters.nextOrSame(dow))
            if (m.groupValues[1].isNotEmpty() || fromNextWeek) d = d.plusWeeks(1)
            date = d
            text = text.removeRange(m.range)
        }
    }

    if (date == null) {
        // Manual re-scan, not findAll: findAll is non-overlapping, so a false
        // word-number candidate ("mom 15") would swallow the day of a real
        // day-first date right behind it ("call mom 15 jul"). On a miss,
        // resume one character past the failed match's start.
        var searchFrom = 0
        while (true) {
            val m = MONTH_DAY_RE.find(text, searchFrom) ?: break
            val g = m.groupValues
            val month = MONTHS[(g[1].ifEmpty { g[4] }).lowercase()]
            val day = (g[2].ifEmpty { g[3] }).toIntOrNull()
            val resolved = if (month != null && day != null) {
                runCatching { LocalDate.of(today.year, month, day) }.getOrNull()
            } else {
                null
            }
            if (resolved != null) {
                date = if (resolved.isBefore(today)) resolved.plusYears(1) else resolved
                text = text.removeRange(m.range)
                break
            }
            searchFrom = m.range.first + 1
        }
    }

    if (date == null) {
        SLASH_DATE_RE.find(text)?.let { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val year = m.groupValues[3].toIntOrNull()?.let { if (it < 100) 2000 + it else it }
            val candidate = runCatching {
                LocalDate.of(year ?: today.year, month, day)
            }.getOrNull() ?: return@let
            date = if (year == null && candidate.isBefore(today)) candidate.plusYears(1) else candidate
            text = text.removeRange(m.range)
        }
    }

    val title = text
        .replace(Regex("""\s+(at|on)\s*$""", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .ifEmpty { "Untitled" }

    val start = startMin?.coerceAtMost(1438)
    val end = start?.let { (it + (durMin ?: defaultDurMin)).coerceAtMost(1439) }
    return QuickParsed(
        title = title,
        date = date,
        start = start?.let(::minsToHm),
        end = end?.let(::minsToHm),
    )
}
