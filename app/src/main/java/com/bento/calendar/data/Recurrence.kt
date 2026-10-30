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
                // Older monthly tasks have no stored day: adopt the current
                // due day once so later short months can't erode it.
                val monthDay = t.recurDay ?: t.due?.toDate()?.dayOfMonth ?: anchor.dayOfMonth
                val next = when (t.recur) {
                    Recur.DAILY -> anchor.plusDays(1)
                    Recur.WEEKLY -> anchor.plusWeeks(1)
                    Recur.MONTHLY -> nextMonthlyDue(anchor, monthDay)
                    else -> anchor
                }
                // The next occurrence starts with a fresh checklist.
                t.copy(
                    due = next.toIso(),
                    subs = t.subs.map { it.copy(done = false) },
                    recurDay = if (t.recur == Recur.MONTHLY) monthDay else t.recurDay,
                )
            }
        },
    )

/**
 * First date strictly after [after] falling on [day] of its month, clamped to
 * the month's last day. Clamping uses the stored day rather than the previous
 * due date, so Jan 31 → Feb 28 → Mar 31 instead of drifting to the 28th.
 */
fun nextMonthlyDue(after: LocalDate, day: Int): LocalDate {
    val wanted = day.coerceIn(1, 31)
    val thisMonth = after.withDayOfMonth(minOf(wanted, after.lengthOfMonth()))
    if (thisMonth.isAfter(after)) return thisMonth
    val nextMonth = after.plusMonths(1)
    return nextMonth.withDayOfMonth(minOf(wanted, nextMonth.lengthOfMonth()))
}

/**
 * [TaskItem.recurDay] for a saved task. Picking a new due date sets the day;
 * re-saving with the same (possibly clamped) due date keeps the stored one,
 * so editing a Feb 28 occurrence of a 31st task doesn't move it to the 28th.
 */
fun monthlyRecurDay(previous: TaskItem?, recur: String, due: LocalDate?): Int? = when {
    recur != Recur.MONTHLY -> null
    due == null -> previous?.recurDay
    previous?.recur == Recur.MONTHLY && previous.due == due.toIso() -> previous.recurDay ?: due.dayOfMonth
    else -> due.dayOfMonth
}

/** Completion plus the planner lifecycle for every block in this task cycle. */
fun completeTaskWithBlocks(
    data: AppData,
    taskId: String,
    today: LocalDate,
    now: Long = System.currentTimeMillis(),
): AppData {
    val before = data.tasks.firstOrNull { it.id == taskId } ?: return data
    val next = completeTask(data, taskId, today)
    if (before.done) {
        // Un-completing (often an accidental tap) restores the future schedule
        // that completion skipped. Rollover "Discard" only ever skips expired
        // blocks, so a skipped block dated after today was skipped here.
        return next.copy(taskBlocks = next.taskBlocks.map { block ->
            if (block.taskId != taskId || block.state != BlockState.SKIPPED || block.date <= today.toIso()) block
            else block.copy(state = BlockState.PLANNED, updatedAt = now)
        })
    }
    val cycle = before.takeIf { it.recur != Recur.NONE }?.due
    return next.copy(taskBlocks = next.taskBlocks.map { block ->
        val sameCycle = block.taskId == taskId &&
            (before.recur == Recur.NONE || block.occurrenceKey == cycle)
        if (!sameCycle || block.state != BlockState.PLANNED) block else block.copy(
            state = if (block.date <= today.toIso()) BlockState.COMPLETED else BlockState.SKIPPED,
            updatedAt = now,
        )
    })
}

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
