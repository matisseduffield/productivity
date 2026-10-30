package com.bento.calendar.reminders

import com.bento.calendar.data.AppData
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.data.toIso
import com.bento.calendar.ui.Fmt
import java.time.LocalDate

/** The optional morning notification: what [date] holds, as short lines. */
data class DailySummary(val title: String, val text: String, val lines: List<String>)

/** Lines past this are folded into "+N more" so the notification stays readable. */
private const val MAX_LINES = 6

fun dailySummary(data: AppData, date: LocalDate): DailySummary {
    val events = occurrencesOn(data.events, date)
    val iso = date.toIso()
    val open = data.tasks.filter { !it.done && it.due != null }
    val dueToday = open.filter { it.due == iso }
    val overdue = open.count { it.due!! < iso }

    val eventLines = events.map { e ->
        val time = if (e.allDay) "All day" else Fmt.time(e.start, data.prefs.use24h)
        "$time  ${e.title.ifBlank { "Untitled" }}"
    }
    val taskLines = dueToday.sortedByDescending { it.priority }.map { "☐  ${it.title.ifBlank { "Untitled" }}" }
    val all = eventLines + taskLines
    val lines = if (all.size <= MAX_LINES) all else all.take(MAX_LINES - 1) + "+${all.size - (MAX_LINES - 1)} more"

    val parts = buildList {
        if (events.isNotEmpty()) add(plural(events.size, "event"))
        if (dueToday.isNotEmpty()) add(plural(dueToday.size, "task") + " due")
        if (overdue > 0) add("$overdue overdue")
    }
    val text = if (parts.isEmpty()) "Nothing scheduled — a clear day" else parts.joinToString(" · ")
    return DailySummary(title = "Today · " + Fmt.dayShort(date), text = text, lines = lines)
}

private fun plural(n: Int, word: String) = "$n $word" + if (n == 1) "" else "s"
