package com.bento.calendar

import com.bento.calendar.data.AppData
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.Prefs
import com.bento.calendar.data.TaskItem
import com.bento.calendar.reminders.ReminderScheduler
import com.bento.calendar.reminders.dailySummary
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DailySummaryTest {
    private val day = LocalDate.of(2026, 10, 30)

    @Test
    fun `summary lists the day's events then tasks and counts overdue work`() {
        val data = AppData(
            events = listOf(
                EventItem(id = "a", title = "Gym", date = "2026-10-30", start = "18:00", end = "19:00"),
                EventItem(id = "b", title = "Standup", date = "2026-10-30", start = "09:00", end = "09:15"),
            ),
            tasks = listOf(
                TaskItem(id = "t", title = "Pay rent", due = "2026-10-30"),
                TaskItem(id = "o", title = "Old thing", due = "2026-10-20"),
                TaskItem(id = "d", title = "Done", due = "2026-10-30", done = true),
            ),
        )
        val s = dailySummary(data, day)
        assertEquals("2 events · 1 task due · 1 overdue", s.text)
        assertEquals(listOf("09:00  Standup", "18:00  Gym", "☐  Pay rent"), s.lines)
    }

    @Test
    fun `an empty day says so and long days are folded`() {
        assertEquals("Nothing scheduled — a clear day", dailySummary(AppData(), day).text)
        val busy = AppData(tasks = (1..9).map { TaskItem(id = "t$it", title = "Task $it", due = "2026-10-30") })
        val lines = dailySummary(busy, day).lines
        assertEquals(6, lines.size)
        assertEquals("+4 more", lines.last())
    }

    @Test
    fun `summary time joins the reminder chain`() {
        val data = AppData(prefs = Prefs(dailySummaryAt = "07:30"))
        assertEquals(
            LocalDateTime.of(2026, 10, 31, 7, 30),
            ReminderScheduler.nextReminderTime(data, LocalDateTime.of(2026, 10, 30, 8, 0)),
        )
    }
}
