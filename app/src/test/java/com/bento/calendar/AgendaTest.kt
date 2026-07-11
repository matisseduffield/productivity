package com.bento.calendar

import com.bento.calendar.data.AppData
import com.bento.calendar.data.DeviceEvent
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.Prefs
import com.bento.calendar.data.Recur
import com.bento.calendar.data.TaskItem
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.agendaDays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AgendaTest {
    private val start = LocalDate.of(2026, 7, 11)

    @Test
    fun `agenda combines events device events and sorted open due tasks`() {
        val data = AppData(
            events = listOf(
                EventItem("one", "Launch", "2026-07-11", "10:00", "11:00"),
                EventItem(
                    "weekly", "Planning", "2026-07-06", "09:00", "09:30",
                    recur = Recur.WEEKLY,
                ),
            ),
            tasks = listOf(
                TaskItem("low", "Low", due = "2026-07-12", priority = 1),
                TaskItem("high", "High", due = "2026-07-12", priority = 3),
                TaskItem("done", "Done", done = true, due = "2026-07-12", priority = 3),
            ),
        )
        val device = mapOf(
            "2026-07-11" to listOf(
                DeviceEvent(
                    id = 1, title = "Device", date = "2026-07-11",
                    start = "08:00", end = "09:00", allDay = false,
                    colorHex = "#123456", calName = "Work", loc = "",
                ),
            ),
        )

        val days = agendaDays(data, device, start, dayCount = 3)
        assertEquals(
            listOf("2026-07-11", "2026-07-12", "2026-07-13"),
            days.map { it.date.toString() },
        )
        assertEquals(listOf("one"), days[0].events.map { it.id })
        assertEquals(listOf(1L), days[0].deviceEvents.map { it.id })
        assertEquals(listOf("high", "low"), days[1].tasks.map { it.id })
        assertEquals(listOf("weekly"), days[2].events.map { it.id })
    }

    @Test
    fun `tasks preference removes task only days`() {
        val data = AppData(
            tasks = listOf(TaskItem("t", "Task", due = "2026-07-12")),
            prefs = Prefs(tasksOnCalendar = false),
        )
        assertTrue(agendaDays(data, emptyMap(), start, 30).isEmpty())
    }

    @Test
    fun `thirty day window is inclusive through day twenty nine only`() {
        val data = AppData(
            events = listOf(
                EventItem("inside", "Inside", "2026-08-09", "09:00", "10:00"),
                EventItem("outside", "Outside", "2026-08-10", "09:00", "10:00"),
            ),
        )
        val days = agendaDays(data, emptyMap(), start)
        assertEquals(listOf("inside"), days.flatMap { it.events }.map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `agenda rejects an unbounded range`() {
        agendaDays(AppData(), emptyMap(), start, 367)
    }

    @Test
    fun `agenda title shows the inclusive window`() {
        assertEquals("11 Jul – 9 Aug", Fmt.agendaTitle(start))
    }
}
