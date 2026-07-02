package com.bento.calendar

import com.bento.calendar.data.EventItem
import com.bento.calendar.data.Recur
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.occursOn
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.activeReminder
import com.bento.calendar.ui.sortedOpenTasks
import com.bento.calendar.ui.startOfWeek
import com.bento.calendar.ui.taskSections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

class RecurrenceTest {
    private fun ev(date: String, recur: String) =
        EventItem(id = "e", title = "t", date = date, start = "09:00", end = "10:00", recur = recur)

    @Test
    fun `non-recurring only on its date`() {
        val e = ev("2026-07-02", Recur.NONE)
        assertTrue(e.occursOn(LocalDate.parse("2026-07-02")))
        assertFalse(e.occursOn(LocalDate.parse("2026-07-03")))
    }

    @Test
    fun `daily from base onwards`() {
        val e = ev("2026-07-02", Recur.DAILY)
        assertFalse(e.occursOn(LocalDate.parse("2026-07-01")))
        assertTrue(e.occursOn(LocalDate.parse("2026-07-02")))
        assertTrue(e.occursOn(LocalDate.parse("2026-08-15")))
    }

    @Test
    fun `weekly same weekday`() {
        val e = ev("2026-07-02", Recur.WEEKLY) // Thursday
        assertTrue(e.occursOn(LocalDate.parse("2026-07-09")))
        assertFalse(e.occursOn(LocalDate.parse("2026-07-08")))
        assertTrue(e.occursOn(LocalDate.parse("2026-07-02")))
    }

    @Test
    fun `monthly skips short months`() {
        val e = ev("2026-01-31", Recur.MONTHLY)
        assertTrue(e.occursOn(LocalDate.parse("2026-03-31")))
        assertFalse(e.occursOn(LocalDate.parse("2026-02-28")))
    }

    @Test
    fun `occurrences re-dated and sorted`() {
        val events = listOf(
            ev("2026-07-02", Recur.NONE).copy(id = "b", start = "14:00", end = "15:00"),
            ev("2026-07-02", Recur.NONE).copy(id = "a", start = "09:00", end = "10:00"),
        )
        val occ = occurrencesOn(events, LocalDate.parse("2026-07-02"))
        assertEquals(listOf("a", "b"), occ.map { it.id })
    }
}

class FormatTest {
    @Test
    fun `24h and 12h time`() {
        assertEquals("14:05", Fmt.time("14:05", true))
        assertEquals("2:05 pm", Fmt.time("14:05", false))
        assertEquals("12:00 am", Fmt.time("00:00", false))
        assertEquals("12:30 pm", Fmt.time("12:30", false))
    }

    @Test
    fun `hour labels`() {
        assertEquals("07:00", Fmt.hourLabel(7, true))
        assertEquals("7 am", Fmt.hourLabel(7, false))
        assertEquals("12 pm", Fmt.hourLabel(12, false))
    }

    @Test
    fun duration() {
        assertEquals("45 min", Fmt.duration("11:30", "12:15"))
        assertEquals("2 h", Fmt.duration("19:00", "21:00"))
        assertEquals("1 h 30", Fmt.duration("09:00", "10:30"))
    }

    @Test
    fun countdown() {
        assertEquals("now", Fmt.countdown(0))
        assertEquals("in 45 min", Fmt.countdown(45))
        assertEquals("in 2 h", Fmt.countdown(120))
        assertEquals("in 2 h 5 min", Fmt.countdown(125))
    }

    @Test
    fun `due labels`() {
        val today = LocalDate.parse("2026-07-02")
        assertEquals("Today", Fmt.dueLabel("2026-07-02", today))
        assertEquals("Tomorrow", Fmt.dueLabel("2026-07-03", today))
        assertEquals("8 Jul", Fmt.dueLabel("2026-07-08", today))
    }

    @Test
    fun titles() {
        val d = LocalDate.parse("2026-07-02")
        assertEquals("Thursday, Jul 2", Fmt.todayTitle(d))
        assertEquals("Thu 2 Jul", Fmt.dayShort(d))
        assertEquals("July 2026", Fmt.monthTitle(YearMonth.of(2026, 7)))
        assertEquals("Thu, 2 July", Fmt.dayTitle(d))
        assertEquals("29 Jun – 5 Jul", Fmt.weekTitle(LocalDate.parse("2026-06-29")))
    }

    @Test
    fun `week start respects monday pref`() {
        val thu = LocalDate.parse("2026-07-02")
        assertEquals(LocalDate.parse("2026-06-29"), startOfWeek(thu, true))
        assertEquals(LocalDate.parse("2026-06-28"), startOfWeek(thu, false))
    }

    @Test
    fun greeting() {
        assertEquals("Good night", Fmt.greeting(3))
        assertEquals("Good morning", Fmt.greeting(9))
        assertEquals("Good afternoon", Fmt.greeting(14))
        assertEquals("Good evening", Fmt.greeting(20))
    }
}

class DeriveTest {
    private val today = LocalDate.parse("2026-07-02")

    private fun task(id: String, due: String?, done: Boolean = false) =
        TaskItem(id = id, title = id, done = done, due = due)

    @Test
    fun `open tasks sorted overdue-today-upcoming-someday`() {
        val tasks = listOf(
            task("someday", null),
            task("upcoming", "2026-07-08"),
            task("done", "2026-07-01", done = true),
            task("today", "2026-07-02"),
            task("overdue", "2026-06-30"),
        )
        assertEquals(
            listOf("overdue", "today", "upcoming", "someday"),
            sortedOpenTasks(tasks, today).map { it.id },
        )
    }

    @Test
    fun `sections drop empties`() {
        val tasks = listOf(task("today", "2026-07-02"), task("someday", null))
        val secs = taskSections(tasks, today)
        assertEquals(listOf("Today", "Someday"), secs.map { it.label })
    }

    @Test
    fun `reminder banner within window, dismissible`() {
        val e = EventItem(
            id = "e2", title = "Design review", date = "2026-07-02",
            start = "11:30", end = "12:15", remind = 10,
        )
        // 11:22 -> 8 min out, inside the 10-min window
        assertEquals("e2", activeReminder(listOf(e), today, 11 * 60 + 22, emptySet())?.event?.id)
        // 11:10 -> 20 min out, outside
        assertNull(activeReminder(listOf(e), today, 11 * 60 + 10, emptySet()))
        // dismissed
        assertNull(activeReminder(listOf(e), today, 11 * 60 + 22, setOf("e2" + "2026-07-02")))
    }
}

class UpdateVersionTest {
    private fun newer(a: String, b: String) = com.bento.calendar.updates.UpdateManager.isNewer(a, b)

    @Test
    fun `semver comparison`() {
        assertTrue(newer("1.0.2", "1.0.1"))
        assertTrue(newer("1.1.0", "1.0.9"))
        assertTrue(newer("1.10.0", "1.9.9"))
        assertTrue(newer("2.0", "1.9.9"))
        assertFalse(newer("1.0.1", "1.0.1"))
        assertFalse(newer("1.0.0", "1.0.1"))
        assertFalse(newer("0.9.9", "1.0.0"))
    }

    @Test
    fun `handles junk segments gracefully`() {
        assertTrue(newer("1.0.2-beta", "1.0.1"))
        assertFalse(newer("abc", "1.0.0"))
    }
}

class SchedulerLogicTest {
    @Test
    fun `next reminder picks earliest upcoming`() {
        val data = com.bento.calendar.data.AppData(
            events = listOf(
                EventItem(id = "a", title = "a", date = "2026-07-02", start = "11:30", end = "12:15", remind = 10),
                EventItem(id = "b", title = "b", date = "2026-07-02", start = "14:00", end = "14:30", remind = 60),
            ),
        )
        val after = LocalDateTime.parse("2026-07-02T10:00:00")
        assertEquals(
            LocalDateTime.parse("2026-07-02T11:20:00"),
            com.bento.calendar.reminders.ReminderScheduler.nextReminderTime(data, after),
        )
        // after the first fires, next is 13:00 (14:00 - 60)
        assertEquals(
            LocalDateTime.parse("2026-07-02T13:00:00"),
            com.bento.calendar.reminders.ReminderScheduler.nextReminderTime(
                data, LocalDateTime.parse("2026-07-02T11:20:00"),
            ),
        )
    }

    @Test
    fun `day-before reminder of tomorrow's event beats today's later reminder`() {
        val data = com.bento.calendar.data.AppData(
            events = listOf(
                EventItem(id = "a", title = "a", date = "2026-07-02", start = "23:00", end = "23:30", remind = 10),
                EventItem(id = "b", title = "b", date = "2026-07-03", start = "09:00", end = "10:00", remind = 1440),
            ),
        )
        // b's "1 day before" fires today 09:00, well before a's 22:50
        assertEquals(
            LocalDateTime.parse("2026-07-02T09:00:00"),
            com.bento.calendar.reminders.ReminderScheduler.nextReminderTime(
                data, LocalDateTime.parse("2026-07-02T08:00:00"),
            ),
        )
    }
}
