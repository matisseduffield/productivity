package com.bento.calendar

import com.bento.calendar.data.AppData
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.Recur
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.completeTask
import com.bento.calendar.reminders.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class TaskReminderTest {
    private fun task(
        id: String,
        due: String?,
        remindAt: String? = null,
        done: Boolean = false,
        recur: String = Recur.NONE,
    ) = TaskItem(id = id, title = id, done = done, due = due, recur = recur, remindAt = remindAt)

    private fun event(id: String, date: String, start: String, remind: Int) =
        EventItem(id = id, title = id, date = date, start = start, end = "23:59", remind = remind)

    private fun next(data: AppData, after: String): LocalDateTime? =
        ReminderScheduler.nextReminderTime(data, LocalDateTime.parse(after))

    @Test
    fun `picks earliest across events and tasks`() {
        val data = AppData(
            events = listOf(event("e", "2026-07-02", "11:30", 10)), // fires 11:20
            tasks = listOf(task("t", "2026-07-02", remindAt = "09:00")),
        )
        assertEquals(LocalDateTime.parse("2026-07-02T09:00:00"), next(data, "2026-07-02T08:00:00"))
        // After the task fires, the event's 11:20 is next in the chain.
        assertEquals(LocalDateTime.parse("2026-07-02T11:20:00"), next(data, "2026-07-02T09:00:00"))
    }

    @Test
    fun `done tasks are skipped`() {
        val data = AppData(tasks = listOf(task("t", "2026-07-02", remindAt = "09:00", done = true)))
        assertNull(next(data, "2026-07-02T08:00:00"))
    }

    @Test
    fun `tasks without a reminder time are skipped`() {
        val data = AppData(tasks = listOf(task("t", "2026-07-02")))
        assertNull(next(data, "2026-07-02T08:00:00"))
    }

    @Test
    fun `task later today beats an event tomorrow`() {
        val data = AppData(
            events = listOf(event("e", "2026-07-03", "09:00", 10)), // fires tomorrow 08:50
            tasks = listOf(task("t", "2026-07-02", remindAt = "18:00")),
        )
        assertEquals(LocalDateTime.parse("2026-07-02T18:00:00"), next(data, "2026-07-02T08:00:00"))
    }

    @Test
    fun `day-before event reminder still beats a later task today`() {
        val data = AppData(
            events = listOf(event("e", "2026-07-03", "09:00", 1440)), // fires today 09:00
            tasks = listOf(task("t", "2026-07-02", remindAt = "18:00")),
        )
        assertEquals(LocalDateTime.parse("2026-07-02T09:00:00"), next(data, "2026-07-02T08:00:00"))
    }

    @Test
    fun `repeating task advanced by completeTask fires on the next occurrence`() {
        val before = AppData(
            tasks = listOf(task("t", "2026-07-02", remindAt = "09:00", recur = Recur.WEEKLY)),
        )
        val after = completeTask(before, "t", LocalDate.parse("2026-07-02"))
        assertEquals("2026-07-09", after.tasks[0].due)
        // remindAt survives the advance and re-fires on the new due date.
        assertEquals(LocalDateTime.parse("2026-07-09T09:00:00"), next(after, "2026-07-02T10:00:00"))
    }

    @Test
    fun `task at exactly after is excluded`() {
        val data = AppData(tasks = listOf(task("t", "2026-07-02", remindAt = "09:00")))
        // Strictly-after semantics, matching events: the fired minute never re-arms itself.
        assertNull(next(data, "2026-07-02T09:00:00"))
    }
}
