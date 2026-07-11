package com.bento.calendar.ui

import com.bento.calendar.data.EventItem
import com.bento.calendar.data.AppData
import com.bento.calendar.data.DeviceEvent
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.data.toIso
import com.bento.calendar.data.toMins
import java.time.LocalDate

/**
 * Prototype task ordering: overdue (by date) -> due today -> upcoming (by date)
 * -> no due date. Applied to open tasks everywhere they're listed.
 */
fun sortScore(t: TaskItem, todayIso: String): String = when {
    t.due == null -> "3"
    t.due < todayIso -> "0" + t.due
    t.due == todayIso -> "1"
    else -> "2" + t.due
}

fun sortedOpenTasks(tasks: List<TaskItem>, today: LocalDate): List<TaskItem> {
    val ti = today.toIso()
    return tasks.filter { !it.done }.sortedBy { sortScore(it, ti) }
}

data class TaskSection(val label: String, val tasks: List<TaskItem>)

/** One non-empty date group in Calendar's rolling Agenda view. */
data class AgendaDay(
    val date: LocalDate,
    val events: List<EventItem>,
    val deviceEvents: List<DeviceEvent>,
    val tasks: List<TaskItem>,
)

/**
 * Build a chronological rolling agenda. Empty dates are omitted; recurring
 * and multi-day Bento events expand through [occurrencesOn], device events are
 * already occurrence-expanded by Calendar Provider, and open due tasks honor
 * the user's tasks-on-calendar setting.
 */
fun agendaDays(
    data: AppData,
    deviceEvents: Map<String, List<DeviceEvent>>,
    start: LocalDate,
    dayCount: Int = 30,
): List<AgendaDay> {
    require(dayCount in 1..366)
    val tasksByDue = if (data.prefs.tasksOnCalendar) {
        data.tasks.filter { !it.done && it.due != null }.groupBy { it.due }
    } else {
        emptyMap()
    }
    return (0 until dayCount).mapNotNull { offset ->
        val date = start.plusDays(offset.toLong())
        val iso = date.toIso()
        val events = occurrencesOn(data.events, date)
        val device = deviceEvents[iso].orEmpty().sortedBy { it.start }
        val tasks = tasksByDue[iso].orEmpty().sortedByDescending { it.priority }
        if (events.isEmpty() && device.isEmpty() && tasks.isEmpty()) null
        else AgendaDay(date, events, device, tasks)
    }
}

/** Overdue / Today / Upcoming / Someday, empty sections dropped. */
fun taskSections(tasks: List<TaskItem>, today: LocalDate): List<TaskSection> {
    val ti = today.toIso()
    val sorted = sortedOpenTasks(tasks, today)
    return listOf(
        TaskSection("Overdue", sorted.filter { it.due != null && it.due < ti }),
        TaskSection("Today", sorted.filter { it.due == ti }),
        TaskSection("Upcoming", sorted.filter { it.due != null && it.due > ti }),
        TaskSection("Someday", sorted.filter { it.due == null }),
    ).filter { it.tasks.isNotEmpty() }
}

data class ReminderBanner(val key: String, val event: EventItem, val minsUntil: Int)

/**
 * The Today-tab reminder banner: first of today's timed events whose reminder
 * window is open (start - remind <= now <= start, with 1 min of grace) and
 * that hasn't been dismissed today. All-day events are skipped: their
 * reminders anchor to a stored start of 00:00, so the window would only be
 * open in the first minute of the day — a nonsense "starting now" banner at
 * midnight that shadows any open timed banner (all-day occurrences sort
 * first) and is unreachable during waking hours. Their system notification
 * still fires via the scheduler.
 */
fun activeReminder(
    events: List<EventItem>,
    today: LocalDate,
    nowMin: Int,
    dismissed: Set<String>,
): ReminderBanner? {
    for (e in occurrencesOn(events, today)) {
        if (e.allDay) continue
        val remind = e.remind ?: continue
        val diff = e.start.toMins() - nowMin
        val key = e.id + today.toIso()
        if (diff <= remind && diff >= -1 && key !in dismissed) {
            return ReminderBanner(key, e, diff)
        }
    }
    return null
}
