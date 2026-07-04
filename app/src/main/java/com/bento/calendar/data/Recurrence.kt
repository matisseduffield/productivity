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
        else -> base == date
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
                t.copy(due = next.toIso())
            }
        },
    )

/** All occurrences on [date], re-dated to it and sorted by start time. */
fun occurrencesOn(events: List<EventItem>, date: LocalDate): List<EventItem> =
    events.filter { it.occursOn(date) }
        .map { it.copy(date = date.toIso()) }
        .sortedBy { it.start }
