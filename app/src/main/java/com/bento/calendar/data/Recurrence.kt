package com.bento.calendar.data

import java.time.LocalDate

// (toIso/toDate bridges come from Models.kt in this package.)

/**
 * Recurrence expands at read time, exactly like the prototype:
 * daily = every day on/after the base date, weekly = same weekday,
 * monthly = same day-of-month (months without that day are skipped).
 * Dates in [EventItem.exDates] are skipped — "this event only" deletes and
 * edits carve occurrences out of the series.
 */
fun EventItem.occursOn(date: LocalDate): Boolean {
    val base = this.date.toDate()
    val hit = when (recur) {
        Recur.DAILY -> !date.isBefore(base)
        Recur.WEEKLY -> !date.isBefore(base) && base.dayOfWeek == date.dayOfWeek
        Recur.MONTHLY -> !date.isBefore(base) && base.dayOfMonth == date.dayOfMonth
        // Multi-day spans (non-recurring only) cover base..spanEnd.
        else -> !date.isBefore(base) && !date.isAfter(spanEnd() ?: base)
    }
    return hit && (recur == Recur.NONE || date.toIso() !in exDates)
}

/**
 * Completing a repeating task advances its due date to the next occurrence
 * (anchored to today when it has none or is overdue) instead of marking it
 * done. Shared by the app's toggle and the widget's checkbox action.
 */
fun completeTask(data: AppData, taskId: String, today: LocalDate): AppData =
    data.copy(
        tasks = data.tasks.map { t ->
            if (t.id != taskId) return@map t
            if (t.done || t.recur == Recur.NONE) {
                t.copy(done = !t.done)
            } else {
                val anchor = t.due?.toDate()?.takeIf { !it.isBefore(today) } ?: today
                val next = when (t.recur) {
                    Recur.DAILY -> anchor.plusDays(1)
                    Recur.WEEKLY -> anchor.plusWeeks(1)
                    Recur.MONTHLY -> anchor.plusMonths(1)
                    else -> anchor
                }
                // The next occurrence starts with a fresh checklist.
                t.copy(due = next.toIso(), subs = t.subs.map { it.copy(done = false) })
            }
        },
    )

/**
 * All occurrences on [date], re-dated to it and sorted by start time.
 * Multi-day events fan out into per-day segments: first day runs start→23:59,
 * days between are all-day, the last day runs 00:00→end. Continuation
 * segments (anything past the first day) carry `remind = null` so the
 * reminder chain — which walks these occurrences — only ever fires off the
 * real start; render surfaces are unaffected (they never read remind).
 */
fun occurrencesOn(events: List<EventItem>, date: LocalDate): List<EventItem> =
    events.filter { it.occursOn(date) }
        .map { e ->
            val last = e.spanEnd()
            when {
                last == null -> e.copy(date = date.toIso())
                date == e.date.toDate() -> e.copy(date = date.toIso(), end = "23:59")
                date == last -> e.copy(date = date.toIso(), start = "00:00", remind = null)
                else -> e.copy(
                    date = date.toIso(),
                    start = "00:00",
                    end = "23:59",
                    allDay = true,
                    remind = null,
                )
            }
        }
        .sortedBy { it.start }
