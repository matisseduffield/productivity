package com.bento.calendar

import com.bento.calendar.data.Cats
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.Recur
import com.bento.calendar.data.exportEventsToIcs
import com.bento.calendar.data.importEventsFromIcs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class IcsTest {
    @Test
    fun `timed recurring event round trips with exception category location and reminder`() {
        val source = EventItem(
            id = "xroundtrip",
            title = "Team ☕, sync; plan\nQ3",
            date = "2026-07-13",
            start = "09:30",
            end = "10:15",
            cat = Cats.FITNESS,
            recur = Recur.WEEKLY,
            remind = 10,
            loc = "Room 1, HQ",
            exDates = listOf("2026-07-20"),
        )
        val text = exportEventsToIcs(
            listOf(source), Cats.DEFAULTS, Instant.parse("2026-07-11T00:00:00Z"),
        )
        val result = importEventsFromIcs(text, Cats.DEFAULTS, localZone = ZoneOffset.UTC)

        assertTrue(result.validCalendar)
        assertEquals(1, result.events.size)
        with(result.events.single()) {
            assertEquals(source.id, id)
            assertEquals(source.title, title)
            assertEquals(source.date, date)
            assertEquals(source.start, start)
            assertEquals(source.end, end)
            assertEquals(source.cat, cat)
            assertEquals(source.recur, recur)
            assertEquals(source.remind, remind)
            assertEquals(source.loc, loc)
            assertEquals(source.exDates, exDates)
        }
        // RFC line folding is byte-based, including Unicode-safe continuation.
        assertTrue(text.split("\r\n").filter { it.isNotEmpty() }
            .all { it.toByteArray(Charsets.UTF_8).size <= 75 })
    }

    @Test
    fun `all day multi day event uses exclusive DTEND and round trips`() {
        val source = EventItem(
            id = "xholiday", title = "Long weekend", date = "2026-07-10",
            start = "00:00", end = "23:59", allDay = true, endDate = "2026-07-12",
        )
        val text = exportEventsToIcs(listOf(source), Cats.DEFAULTS, Instant.EPOCH)
        assertTrue(text.contains("DTSTART;VALUE=DATE:20260710"))
        assertTrue(text.contains("DTEND;VALUE=DATE:20260713"))

        val event = importEventsFromIcs(text, Cats.DEFAULTS, localZone = ZoneOffset.UTC)
            .events.single()
        assertTrue(event.allDay)
        assertEquals("2026-07-10", event.date)
        assertEquals("2026-07-12", event.endDate)
        assertEquals("00:00", event.start)
        assertEquals("23:59", event.end)
    }

    @Test
    fun `third party UID creates a stable id so repeat import is a duplicate`() {
        val text = calendar(
            """BEGIN:VEVENT
UID:third-party-42@example.com
DTSTART:20260712T090000
DTEND:20260712T100000
SUMMARY:Imported event
END:VEVENT""",
        )
        val first = importEventsFromIcs(text, Cats.DEFAULTS, localZone = ZoneOffset.UTC)
        val id = first.events.single().id
        val second = importEventsFromIcs(text, Cats.DEFAULTS, setOf(id), ZoneOffset.UTC)

        assertTrue(id.startsWith("xics"))
        assertTrue(second.events.isEmpty())
        assertEquals(1, second.duplicates)
        assertEquals(0, second.skipped)
    }

    @Test
    fun `UTC timestamps convert into the phone timezone`() {
        val text = calendar(
            """BEGIN:VEVENT
UID:utc@example.com
DTSTART:20260711T233000Z
DTEND:20260712T003000Z
SUMMARY:UTC call
END:VEVENT""",
        )
        val event = importEventsFromIcs(
            text, Cats.DEFAULTS, localZone = ZoneId.of("Australia/Adelaide"),
        ).events.single()

        assertEquals("2026-07-12", event.date)
        assertEquals("09:00", event.start)
        assertEquals("10:00", event.end)
    }

    @Test
    fun `external category label maps to the matching Bento category`() {
        val text = calendar(
            """BEGIN:VEVENT
UID:fitness@example.com
DTSTART:20260712T090000
DTEND:20260712T100000
SUMMARY:Run
CATEGORIES:Fitness
END:VEVENT""",
        )
        val event = importEventsFromIcs(text, Cats.DEFAULTS, localZone = ZoneOffset.UTC)
            .events.single()
        assertEquals(Cats.FITNESS, event.cat)
    }

    @Test
    fun `bounded recurrence degrades to its concrete first event instead of becoming infinite`() {
        val text = calendar(
            """BEGIN:VEVENT
UID:bounded@example.com
DTSTART:20260712T090000
DTEND:20260712T100000
SUMMARY:Five sessions
RRULE:FREQ=DAILY;COUNT=5
END:VEVENT""",
        )
        val event = importEventsFromIcs(text, Cats.DEFAULTS, localZone = ZoneOffset.UTC)
            .events.single()
        assertEquals(Recur.NONE, event.recur)
    }

    @Test
    fun `DURATION and UTF8 BOM from third party calendars are supported`() {
        val text = "\uFEFF" + calendar(
            """BEGIN:VEVENT
UID:duration@example.com
DTSTART:20260712T234500
DURATION:PT45M
SUMMARY:Late call
END:VEVENT""",
        )
        val event = importEventsFromIcs(text, Cats.DEFAULTS, localZone = ZoneOffset.UTC)
            .events.single()
        assertEquals("2026-07-12", event.date)
        assertEquals("23:45", event.start)
        assertEquals("2026-07-13", event.endDate)
        assertEquals("00:30", event.end)
    }

    @Test
    fun `folded and escaped text is restored`() {
        val text = calendar(
            """BEGIN:VEVENT
UID:folded@example.com
DTSTART:20260712T090000
DTEND:20260712T100000
SUMMARY:Roadmap\, planning\; and a very long
 continuation
LOCATION:Room A\, North
END:VEVENT""",
        )
        val event = importEventsFromIcs(text, Cats.DEFAULTS, localZone = ZoneOffset.UTC)
            .events.single()
        assertEquals("Roadmap, planning; and a very longcontinuation", event.title)
        assertEquals("Room A, North", event.loc)
    }

    @Test
    fun `invalid VEVENT is skipped without rejecting valid neighbors`() {
        val text = calendar(
            """BEGIN:VEVENT
UID:bad@example.com
DTSTART:garbage
SUMMARY:Bad
END:VEVENT
BEGIN:VEVENT
UID:good@example.com
DTSTART;VALUE=DATE:20260712
DTEND;VALUE=DATE:20260713
SUMMARY:Good
END:VEVENT""",
        )
        val result = importEventsFromIcs(text, Cats.DEFAULTS, localZone = ZoneOffset.UTC)
        assertTrue(result.validCalendar)
        assertEquals(2, result.sourceEvents)
        assertEquals(1, result.events.size)
        assertEquals(1, result.skipped)
        assertEquals("Good", result.events.single().title)
    }

    @Test
    fun `modified and cancelled recurring instances become exceptions without duplicates`() {
        val text = calendar(
            """BEGIN:VEVENT
UID:series@example.com
DTSTART:20260706T090000
DTEND:20260706T100000
SUMMARY:Weekly planning
RRULE:FREQ=WEEKLY;BYDAY=MO
END:VEVENT
BEGIN:VEVENT
UID:series@example.com
RECURRENCE-ID:20260713T090000
DTSTART:20260713T110000
DTEND:20260713T120000
SUMMARY:Weekly planning — moved
END:VEVENT
BEGIN:VEVENT
UID:series@example.com
RECURRENCE-ID:20260720T090000
STATUS:CANCELLED
END:VEVENT""",
        )
        val result = importEventsFromIcs(text, Cats.DEFAULTS, localZone = ZoneOffset.UTC)
        assertEquals(3, result.sourceEvents)
        assertEquals(0, result.skipped)
        assertEquals(2, result.events.size)

        val series = result.events.single { it.recur == Recur.WEEKLY }
        assertEquals(listOf("2026-07-13", "2026-07-20"), series.exDates)
        val moved = result.events.single { it.recur == Recur.NONE }
        assertEquals("Weekly planning — moved", moved.title)
        assertEquals("2026-07-13", moved.date)
        assertEquals("11:00", moved.start)
    }

    @Test
    fun `plain text is not accepted as a calendar`() {
        val result = importEventsFromIcs("hello", Cats.DEFAULTS)
        assertFalse(result.validCalendar)
        assertTrue(result.events.isEmpty())
    }

    private fun calendar(events: String): String =
        "BEGIN:VCALENDAR\r\nVERSION:2.0\r\n" +
            events.trimIndent().replace("\n", "\r\n") +
            "\r\nEND:VCALENDAR\r\n"
}
