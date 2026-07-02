package com.bento.calendar.data

import java.time.LocalDate

/**
 * Recurrence expands at read time, exactly like the prototype:
 * daily = every day on/after the base date, weekly = same weekday,
 * monthly = same day-of-month (months without that day are skipped).
 * Editing/deleting a recurring event affects the whole series.
 */
fun EventItem.occursOn(date: LocalDate): Boolean {
    val base = this.date.toDate()
    return when (recur) {
        Recur.DAILY -> !date.isBefore(base)
        Recur.WEEKLY -> !date.isBefore(base) && base.dayOfWeek == date.dayOfWeek
        Recur.MONTHLY -> !date.isBefore(base) && base.dayOfMonth == date.dayOfMonth
        else -> base == date
    }
}

/** All occurrences on [date], re-dated to it and sorted by start time. */
fun occurrencesOn(events: List<EventItem>, date: LocalDate): List<EventItem> =
    events.filter { it.occursOn(date) }
        .map { it.copy(date = date.toIso()) }
        .sortedBy { it.start }
