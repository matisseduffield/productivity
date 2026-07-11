package com.bento.calendar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Natural-language quick add: "Dentist tuesday 3pm", "Gym in 2 days #fitness",
 * "Standup every week mon 9-9:30am", "Buy milk 2026-07-15". A time makes it
 * an event; date-only (or bare text) makes it a task. Recognized tokens are
 * stripped from the title.
 */
data class QuickParsed(
    val title: String,
    /** Parsed date, or null when none was mentioned. */
    val date: LocalDate?,
    /** "HH:MM" start — non-null means "this is an event". */
    val start: String?,
    /** "HH:MM" end; non-null iff [start] is. */
    val end: String?,
    /** [Priority] from a "!high"/"!med"/"!low" token; tasks only. */
    val priority: Int = 0,
    /** Simple recurrence requested with "every day/week/month". */
    val recur: String = Recur.NONE,
    /** Matched category id from a #category token, or null for the default. */
    val categoryId: String? = null,
) {
    val isEvent: Boolean get() = start != null
}

// "!high" / "!h" / "!3", "!med(ium)" / "!m" / "!2", "!low" / "!l" / "!1"
private val PRIORITY_RE = Regex(
    """(?:^|\s)!(high|hi|h|3|medium|med|m|2|low|l|1)\b""",
    RegexOption.IGNORE_CASE,
)

private fun priorityOf(token: String): Int = when (token.lowercase()) {
    "high", "hi", "h", "3" -> 3
    "medium", "med", "m", "2" -> 2
    else -> 1
}

private val RECURRENCE_RE = Regex(
    """\b(?:every|repeat)\s+(day|daily|week|weekly|month|monthly)\b""",
    RegexOption.IGNORE_CASE,
)

private fun recurrenceOf(token: String): String = when (token.lowercase()) {
    "day", "daily" -> Recur.DAILY
    "week", "weekly" -> Recur.WEEKLY
    else -> Recur.MONTHLY
}

// Category labels are compared without spaces/punctuation: a custom
// "Deep Work" category is addressable as #deepwork (or by its id).
private val CATEGORY_RE = Regex("""(?:^|\s)#([\p{L}\p{N}_-]+)\b""")
private fun categoryKey(value: String): String =
    value.lowercase().filter { it.isLetterOrDigit() }

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

// Time range: "3pm-5pm", "3pm - 5pm", "3-5pm", "9am-5pm", "15:00-16:30",
// "3.30pm-5pm", "3pm to 5pm". Meridiems are optional on both sides here;
// [rangeOf] decides which combinations are believable. The digit-hyphen
// lookarounds keep the bare-bare branch off hyphenated dates: "2026-07-15"
// must not read its "07-15" as 07:00-15:00 (nor "05-10-2026" its "05-10").
private val RANGE_RE = Regex(
    """(?<![\d-])\b(?:at\s+)?(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?(\s*-\s*|\s+to\s+)(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)?\b(?![-\d])""",
    RegexOption.IGNORE_CASE,
)

/**
 * Resolve a [RANGE_RE] match's groups to start/end minutes, or null when the
 * candidate isn't a believable range. Rules:
 * - Both meridiems explicit: read each side directly ("3pm-5pm", "9am-5pm").
 * - Only the end has one: the start inherits it ("3-5pm" = 15:00-17:00); when
 *   inheriting would read backwards ("11-1pm" -> 23:00-13:00) the range
 *   crosses noon, so fall back to the opposite meridiem (11:00-13:00).
 * - Only the start has one: mirror image — the end inherits, crossing noon
 *   forward on conflict ("11am-1" = 11:00-13:00).
 * - Neither: both sides read as 24h clock ("15:00-16:30", "9-11"). To keep
 *   "9 to 5 grind" and "buy 3 - 5 apples" as plain text, bare-bare ranges are
 *   only accepted with a tight hyphen. ("buy 3-5 apples" still false-positives
 *   into an event — accepted; the quick-add preview chips make it visible.)
 * - A side without a meridiem but with an hour outside 1..12 is fixed 24h
 *   ("15-5pm" = 15:00-17:00) — no inheritance possible.
 *
 * Backwards/degenerate results ("5pm-3pm") return null: the range is ignored
 * entirely rather than wrapped to the next day, leaving the text intact for
 * the single-time pass.
 */
private fun rangeOf(g: List<String>): Pair<Int, Int>? {
    val h1 = g[1].toIntOrNull() ?: return null
    val m1 = g[2].toIntOrNull() ?: 0
    val mer1 = g[3].lowercase()
    val h2 = g[5].toIntOrNull() ?: return null
    val m2 = g[6].toIntOrNull() ?: 0
    val mer2 = g[7].lowercase()
    if (m1 !in 0..59 || m2 !in 0..59) return null

    // h must be in 1..12 when mer is used; callers guard.
    fun mer12(h: Int, mm: Int, mer: String): Int = ((h % 12) + if (mer == "pm") 12 else 0) * 60 + mm
    fun h24(h: Int, mm: Int): Int? = if (h in 0..23) h * 60 + mm else null
    fun opposite(mer: String) = if (mer == "pm") "am" else "pm"

    val range: Pair<Int, Int>? = when {
        mer1.isNotEmpty() && mer2.isNotEmpty() -> {
            if (h1 in 1..12 && h2 in 1..12) mer12(h1, m1, mer1) to mer12(h2, m2, mer2) else null
        }
        mer2.isNotEmpty() -> {
            if (h2 !in 1..12) return null
            val e = mer12(h2, m2, mer2)
            val s = if (h1 in 1..12) {
                val inherited = mer12(h1, m1, mer2)
                if (inherited < e) inherited else mer12(h1, m1, opposite(mer2))
            } else {
                h24(h1, m1) ?: return null
            }
            s to e
        }
        mer1.isNotEmpty() -> {
            if (h1 !in 1..12) return null
            val s = mer12(h1, m1, mer1)
            val e = if (h2 in 1..12) {
                val inherited = mer12(h2, m2, mer1)
                if (inherited > s) inherited else mer12(h2, m2, opposite(mer1))
            } else {
                h24(h2, m2) ?: return null
            }
            s to e
        }
        else -> {
            if (g[4] != "-") return null // bare-bare: tight hyphen only
            val s = h24(h1, m1) ?: return null
            val e = h24(h2, m2) ?: return null
            s to e
        }
    }
    return range?.takeIf { it.first < it.second }
}

// "jul 15", "15 jul", "july 15th"
private val MONTH_DAY_RE = Regex(
    """\b(?:([a-z]{3,9})\s+(\d{1,2})(?:st|nd|rd|th)?|(\d{1,2})(?:st|nd|rd|th)?\s+([a-z]{3,9}))\b""",
    RegexOption.IGNORE_CASE,
)

// "15/7" or "15/07/2026" (day first, matching the app's non-US formats).
private val SLASH_DATE_RE = Regex("""\b(\d{1,2})/(\d{1,2})(?:/(\d{2,4}))?\b""")

private val ISO_DATE_RE = Regex("""\b(\d{4})-(\d{2})-(\d{2})\b""")

private val OFFSET_DATE_RE = Regex(
    """\bin\s+(\d{1,3})\s+(day|days|week|weeks)\b""",
    RegexOption.IGNORE_CASE,
)

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
 * to [today] when a time is given without a date; an explicit range
 * ("3pm-5pm") fixes both ends instead.
 */
fun parseQuickAdd(
    raw: String,
    today: LocalDate,
    defaultDurMin: Int = 60,
    categories: List<Category> = Cats.DEFAULTS,
): QuickParsed? {
    if (raw.isBlank()) return null
    var text = " ${raw.trim()} "
    var date: LocalDate? = null
    var startMin: Int? = null
    var endMin: Int? = null
    var durMin: Int? = null
    var priority = 0
    var recur = Recur.NONE
    var categoryId: String? = null

    PRIORITY_RE.find(text)?.let { m ->
        priority = priorityOf(m.groupValues[1])
        text = text.removeRange(m.range)
    }

    RECURRENCE_RE.find(text)?.let { m ->
        recur = recurrenceOf(m.groupValues[1])
        text = text.removeRange(m.range)
    }

    CATEGORY_RE.find(text)?.let { m ->
        val wanted = categoryKey(m.groupValues[1])
        val match = categories.firstOrNull {
            categoryKey(it.id) == wanted || categoryKey(it.label) == wanted
        }
        // Unknown hashtags are ordinary title text; never silently discard a
        // tag merely because the user has not created that category yet.
        if (match != null) {
            categoryId = match.id
            text = text.removeRange(m.range)
        }
    }

    // Time range before the duration and single-time passes: "3pm-5pm" would
    // otherwise have its start eaten by TIME_RE with "-5pm" left in the title.
    // A matched range fixes both ends explicitly (durations and the default
    // duration don't apply). Manual re-scan like MONTH_DAY below: a rejected
    // candidate ("3 to 5" in "buy 3 to 5 apples 2pm-4pm") must not hide a real
    // range behind it.
    // bareRange = accepted range had no am/pm on either side ("9-11",
    // "15:00-16:30") — "tonight" may shift such ranges into the evening.
    var bareRange = false
    run {
        var searchFrom = 0
        while (true) {
            val m = RANGE_RE.find(text, searchFrom) ?: break
            val resolved = rangeOf(m.groupValues)
            if (resolved != null) {
                startMin = resolved.first
                endMin = resolved.second
                bareRange = m.groupValues[3].isEmpty() && m.groupValues[7].isEmpty()
                text = text.removeRange(m.range)
                break
            }
            searchFrom = m.range.first + 1
        }
    }

    // Duration next: its "1h" would otherwise be half-eaten by TIME_RE's
    // bare-hour branch when written as "at 1h". Still stripped when a range
    // matched, but the range's explicit end wins over the duration value.
    DUR_RE.find(text)?.let { m ->
        durMin = m.groupValues[1].toIntOrNull()
            ?: m.groupValues[2].toDoubleOrNull()?.let { (it * 60).toInt() }
        if (durMin != null && durMin!! in 1..1439) text = text.removeRange(m.range) else durMin = null
    }

    // True when the time had no am/pm marker ("at 9", "9:30") — "tonight"
    // may shift such times into the evening. Skipped when a range already
    // claimed the times.
    var bareHour = false
    if (startMin == null) TIME_RE.find(text)?.let { m ->
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

    ISO_DATE_RE.find(text)?.let { m ->
        val parsed = runCatching { LocalDate.parse(m.value) }.getOrNull()
        if (parsed != null) {
            date = parsed
            text = text.removeRange(m.range)
        }
    }

    if (date == null) OFFSET_DATE_RE.find(text)?.let { m ->
        val amount = m.groupValues[1].toLongOrNull() ?: return@let
        date = if (m.groupValues[2].startsWith("week", ignoreCase = true)) {
            today.plusWeeks(amount)
        } else {
            today.plusDays(amount)
        }
        text = text.removeRange(m.range)
    }

    // "tue next week": the "next week" match alone would date this to
    // today+7 and leave "tue" dangling in the title — remember it so the
    // weekday scan below can still claim the weekday ("tue next week" ≡
    // "next tue").
    var fromNextWeek = false
    if (date == null) RELATIVE_RE.find(text)?.let { m ->
        val word = m.groupValues[1].lowercase().replace(Regex("""\s+"""), " ")
        date = when (word) {
            "today", "tod" -> today
            "tomorrow", "tmrw", "tmr" -> today.plusDays(1)
            "tonight" -> today.also {
                val s = startMin
                val e = endMin
                // No time → evening default; a bare daytime hour ("tonight
                // at 9") shifts to the evening. Explicit am/pm is respected.
                // A bare range ("tonight 9-11") shifts the same way, but only
                // when BOTH ends sit in the morning window — a range that
                // already straddles noon ("tonight 9-12:30") stays put.
                if (s == null) {
                    startMin = 20 * 60
                } else if (e != null) {
                    if (bareRange && s in 60..719 && e in 60..719) {
                        startMin = s + 12 * 60
                        endMin = e + 12 * 60
                    }
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
    // An explicit range end beats any duration; otherwise duration/default.
    val end = start?.let { s -> (endMin ?: (s + (durMin ?: defaultDurMin))).coerceAtMost(1439) }
    return QuickParsed(
        title = title,
        date = date,
        start = start?.let(::minsToHm),
        end = end?.let(::minsToHm),
        priority = priority,
        recur = recur,
        categoryId = categoryId,
    )
}
