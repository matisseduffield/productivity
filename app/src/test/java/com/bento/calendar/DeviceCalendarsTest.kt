package com.bento.calendar

import com.bento.calendar.data.BusyInterval
import com.bento.calendar.data.DeviceEvent
import com.bento.calendar.data.TaskBlock
import com.bento.calendar.data.WorkHours
import com.bento.calendar.data.deviceDaySegments
import com.bento.calendar.data.deviceLoadRanges
import com.bento.calendar.data.planningConflicts
import com.bento.calendar.data.toMins
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class DeviceCalendarsTest {
    private val mon = LocalDate.of(2026, 7, 13)
    private val wide = mon.minusDays(30) to mon.plusDays(30)

    private fun event(start: String, end: String, allDay: Boolean = false) = DeviceEvent(
        id = 1, title = "Shift", date = mon.toString(), start = start, end = end,
        allDay = allDay, colorHex = "#7BA7F7", calName = "Work", loc = "",
    )

    private fun segments(base: DeviceEvent, begin: LocalDateTime, end: LocalDateTime, range: Pair<LocalDate, LocalDate> = wide) =
        deviceDaySegments(base, begin, end, range.first, range.second)

    @Test fun `overnight event splits into valid per-day intervals`() {
        val result = segments(event("22:00", "02:00"), mon.atTime(22, 0), mon.plusDays(1).atTime(2, 0))
        assertEquals(listOf(mon.toString(), mon.plusDays(1).toString()), result.map { it.date })
        assertEquals("22:00" to "23:59", result[0].start to result[0].end)
        assertEquals("00:00" to "02:00", result[1].start to result[1].end)
        assertTrue(result.all { it.end.toMins() > it.start.toMins() && !it.allDay })
    }

    @Test fun `overnight device event now conflicts with an early task block`() {
        val tue = mon.plusDays(1)
        val busy = segments(event("22:00", "10:00"), mon.atTime(22, 0), tue.atTime(10, 0))
            .groupBy({ LocalDate.parse(it.date) }) { BusyInterval(it.start.toMins(), it.end.toMins()) }
        val block = TaskBlock("b", "t", date = tue.toString(), startMin = 540, durationMin = 60)
        val conflicts = planningConflicts(tue, tue, listOf(block), busy, WorkHours.defaults())
        assertEquals("b", conflicts.single().blockId)
    }

    @Test fun `event ending at midnight stays on its own day`() {
        val result = segments(event("21:00", "00:00"), mon.atTime(21, 0), mon.plusDays(1).atStartOfDay())
        assertEquals("21:00" to "23:59", result.single().start to result.single().end)
        assertEquals(mon.toString(), result.single().date)
    }

    @Test fun `long timed span has an all-day middle and clips to the range`() {
        val base = event("09:00", "17:00")
        val result = segments(base, mon.atTime(9, 0), mon.plusDays(2).atTime(17, 0))
        assertEquals(listOf("09:00", "00:00", "00:00"), result.map { it.start })
        assertEquals(listOf(false, true, false), result.map { it.allDay })

        val clipped = segments(base, mon.atTime(9, 0), mon.plusDays(2).atTime(17, 0), mon.plusDays(1) to mon.plusDays(1))
        assertEquals(mon.plusDays(1).toString(), clipped.single().date)
    }

    @Test fun `all-day instances use an exclusive end date`() {
        val result = segments(event("00:00", "23:59", allDay = true), mon.atStartOfDay(), mon.plusDays(2).atStartOfDay())
        assertEquals(listOf(mon.toString(), mon.plusDays(1).toString()), result.map { it.date })
        assertTrue(result.all { it.allDay && it.start == "00:00" && it.end == "23:59" })
    }

    @Test fun `browsing far from today still loads today's week`() {
        val today = mon
        val ranges = deviceLoadRanges(LocalDate.of(2027, 3, 1), LocalDate.of(2027, 3, 31), today)
        assertEquals(2, ranges.size)
        assertEquals(today to today.plusDays(6), ranges[1])
    }

    @Test fun `a window that covers today needs no extra query`() {
        val ranges = deviceLoadRanges(mon.withDayOfMonth(1), mon.withDayOfMonth(31), mon)
        assertEquals(1, ranges.size)
        assertFalse(ranges.single().first.isAfter(mon))
    }
}
