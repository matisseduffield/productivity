package com.bento.calendar

import com.bento.calendar.data.AppData
import com.bento.calendar.data.Cats
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.Recur
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.completeTask
import com.bento.calendar.data.endSeriesBefore
import com.bento.calendar.data.exportEventsToIcs
import com.bento.calendar.data.importEventsFromIcs
import com.bento.calendar.data.occursOn
import com.bento.calendar.data.parseQuickAdd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class RepeatRulesTest {
    private fun ev(date: String, recur: String, until: String? = null) = EventItem(
        id = "e", title = "t", date = date, start = "09:00", end = "10:00", recur = recur, recurUntil = until,
    )

    private fun d(iso: String) = LocalDate.parse(iso)

    @Test
    fun `weekdays skip the weekend`() {
        val e = ev("2026-07-03", Recur.WEEKDAYS) // Friday
        assertTrue(e.occursOn(d("2026-07-03")))
        assertFalse(e.occursOn(d("2026-07-04")))
        assertFalse(e.occursOn(d("2026-07-05")))
        assertTrue(e.occursOn(d("2026-07-06")))
    }

    @Test
    fun `fortnightly alternates weeks`() {
        val e = ev("2026-07-06", Recur.FORTNIGHTLY)
        assertTrue(e.occursOn(d("2026-07-20")))
        assertFalse(e.occursOn(d("2026-07-13")))
        assertFalse(e.occursOn(d("2026-06-22")))
    }

    @Test
    fun `yearly keeps month and day`() {
        val e = ev("2026-03-14", Recur.YEARLY)
        assertTrue(e.occursOn(d("2027-03-14")))
        assertFalse(e.occursOn(d("2027-04-14")))
    }

    @Test
    fun `repeat until stops the series`() {
        val e = ev("2026-07-01", Recur.DAILY, until = "2026-07-03")
        assertTrue(e.occursOn(d("2026-07-03")))
        assertFalse(e.occursOn(d("2026-07-04")))
    }

    @Test
    fun `ending a series before a date keeps earlier occurrences and their exceptions`() {
        val e = ev("2026-07-01", Recur.DAILY).copy(exDates = listOf("2026-07-02", "2026-07-09"))
        val cut = e.endSeriesBefore(d("2026-07-05"))
        assertEquals("2026-07-04", cut.recurUntil)
        assertEquals(listOf("2026-07-02"), cut.exDates)
        assertTrue(cut.occursOn(d("2026-07-04")))
        assertFalse(cut.occursOn(d("2026-07-05")))
        // An earlier existing end is never extended.
        assertEquals("2026-07-03", ev("2026-07-01", Recur.DAILY, "2026-07-03").endSeriesBefore(d("2026-07-10")).recurUntil)
    }

    @Test
    fun `weekday task done on Friday is next due Monday`() {
        val data = AppData(tasks = listOf(TaskItem(id = "t", title = "Standup", due = "2026-07-03", recur = Recur.WEEKDAYS)))
        val next = completeTask(data, "t", d("2026-07-03")).tasks.single()
        assertEquals("2026-07-06", next.due)
        assertFalse(next.done)
    }

    @Test
    fun `fortnightly and yearly tasks advance by their interval`() {
        val data = AppData(
            tasks = listOf(
                TaskItem(id = "f", title = "Bins", due = "2026-07-06", recur = Recur.FORTNIGHTLY),
                TaskItem(id = "y", title = "Renew", due = "2026-07-06", recur = Recur.YEARLY),
            ),
        )
        val today = d("2026-07-06")
        assertEquals("2026-07-20", completeTask(data, "f", today).tasks.first { it.id == "f" }.due)
        assertEquals("2027-07-06", completeTask(data, "y", today).tasks.first { it.id == "y" }.due)
    }

    @Test
    fun `new repeats and end dates survive a calendar file round trip`() {
        val source = listOf(
            ev("2026-07-06", Recur.WEEKDAYS, until = "2026-08-28").copy(id = "wk"),
            ev("2026-07-06", Recur.FORTNIGHTLY).copy(id = "fn"),
            ev("2026-03-14", Recur.YEARLY).copy(id = "yr", allDay = true, start = "00:00", end = "23:59", recurUntil = "2030-03-14"),
        )
        val text = exportEventsToIcs(source, Cats.DEFAULTS, Instant.EPOCH)
        val back = importEventsFromIcs(text, Cats.DEFAULTS, localZone = ZoneOffset.UTC).events.associateBy { it.id }
        source.forEach { e ->
            assertEquals(e.recur, back.getValue(e.id).recur)
            assertEquals(e.recurUntil, back.getValue(e.id).recurUntil)
        }
    }

    @Test
    fun `quick add understands the new repeat words`() {
        val today = d("2026-07-04")
        assertEquals(Recur.WEEKDAYS, parseQuickAdd("standup every weekday 9am", today)!!.recur)
        assertEquals(Recur.FORTNIGHTLY, parseQuickAdd("bins fortnightly", today)!!.recur)
        assertEquals(Recur.FORTNIGHTLY, parseQuickAdd("payday every 2 weeks", today)!!.recur)
        assertEquals(Recur.YEARLY, parseQuickAdd("renew licence every year", today)!!.recur)
        assertEquals("bins", parseQuickAdd("bins fortnightly", today)!!.title)
    }
}
