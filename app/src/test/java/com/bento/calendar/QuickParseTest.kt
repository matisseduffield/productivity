package com.bento.calendar

import com.bento.calendar.data.parseQuickAdd
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Pins the natural-language quick-add contract (QuickParse.kt): a time makes
 * an event, date-only or bare text makes a task, recognized tokens are
 * stripped from the title.
 */
class QuickParseTest {
    /** Fixed anchor: Saturday 4 July 2026. */
    private val today = LocalDate.of(2026, 7, 4)

    private fun parse(raw: String, defaultDurMin: Int = 60) =
        parseQuickAdd(raw, today, defaultDurMin)!!

    @Test
    fun `bare text stays an undated task`() {
        val p = parse("buy milk")
        assertEquals("buy milk", p.title)
        assertNull(p.date)
        assertNull(p.start)
        assertNull(p.end)
        assertFalse(p.isEvent)
    }

    @Test
    fun `tomorrow dates a task`() {
        val p = parse("buy milk tomorrow")
        assertEquals("buy milk", p.title)
        assertEquals(LocalDate.of(2026, 7, 5), p.date)
        assertFalse(p.isEvent)
    }

    @Test
    fun `weekday plus time makes an event with the default duration`() {
        val p = parse("dentist tue 3pm")
        assertTrue(p.isEvent)
        assertEquals("dentist", p.title)
        assertEquals(LocalDate.of(2026, 7, 7), p.date)
        assertEquals("15:00", p.start)
        assertEquals("16:00", p.end)
    }

    @Test
    fun `explicit minute duration overrides the default`() {
        val p = parse("gym tomorrow 7am for 45min")
        assertEquals("gym", p.title)
        assertEquals(LocalDate.of(2026, 7, 5), p.date)
        assertEquals("07:00", p.start)
        assertEquals("07:45", p.end)
    }

    @Test
    fun `fractional hour duration is 90 minutes`() {
        val p = parse("study at 9 for 1.5h")
        assertEquals("study", p.title)
        assertEquals("09:00", p.start)
        assertEquals("10:30", p.end)
    }

    @Test
    fun `time without date is an event with a null date`() {
        // The caller (commitQuickAdd) defaults the date to today.
        val p = parse("lunch at 12:30")
        assertTrue(p.isEvent)
        assertEquals("lunch", p.title)
        assertNull(p.date)
        assertEquals("12:30", p.start)
        assertEquals("13:30", p.end)
    }

    @Test
    fun `noon is twelve o clock`() {
        val p = parse("lunch noon")
        assertEquals("lunch", p.title)
        assertEquals("12:00", p.start)
        assertEquals("13:00", p.end)
    }

    @Test
    fun `bare 24h hour needs at`() {
        val p = parse("standup at 15")
        assertEquals("standup", p.title)
        assertEquals("15:00", p.start)
        assertEquals("16:00", p.end)
    }

    @Test
    fun `month day after a word does not lose the month`() {
        val p = parse("call mom jul 15")
        assertEquals("call mom", p.title)
        assertEquals(LocalDate.of(2026, 7, 15), p.date)
        assertFalse(p.isEvent)
    }

    @Test
    fun `day month right after a word still parses`() {
        // "mom 15" is a false word-number candidate overlapping the real
        // "15 jul"; the parser must re-scan past it, not stop at it.
        val p = parse("call mom 15 jul")
        assertEquals("call mom", p.title)
        assertEquals(LocalDate.of(2026, 7, 15), p.date)
        assertFalse(p.isEvent)
    }

    @Test
    fun `tonight with a bare hour lands in the evening`() {
        // "at 9" alone is 09:00, but "tonight" shifts bare daytime hours.
        val p = parse("movie tonight at 9")
        assertEquals("movie", p.title)
        assertEquals(today, p.date)
        assertEquals("21:00", p.start)
    }

    @Test
    fun `tonight respects an explicit am time`() {
        val p = parse("flight tonight at 9am")
        assertEquals(today, p.date)
        assertEquals("09:00", p.start)
    }

    @Test
    fun `weekday alongside next week reads as next weekday`() {
        // "tue next week" ≡ "next tue" — the weekday must not be left
        // dangling in the title with the date stuck on today+7.
        val p = parse("dentist tue next week")
        assertEquals("dentist", p.title)
        assertEquals(LocalDate.of(2026, 7, 14), p.date)
    }

    @Test
    fun `plain weekday includes today`() {
        // "sat" typed on a Saturday means today, not next week.
        val p = parse("haircut sat")
        assertEquals("haircut", p.title)
        assertEquals(today, p.date)
        assertFalse(p.isEvent)
    }

    @Test
    fun `next weekday jumps a further week`() {
        val p = parse("haircut next sat")
        assertEquals("haircut", p.title)
        assertEquals(LocalDate.of(2026, 7, 11), p.date)
    }

    @Test
    fun `next week is plus seven days`() {
        val p = parse("report next week")
        assertEquals("report", p.title)
        assertEquals(LocalDate.of(2026, 7, 11), p.date)
        assertFalse(p.isEvent)
    }

    @Test
    fun `slash date is day first`() {
        val p = parse("pay rent 15/7")
        assertEquals("pay rent", p.title)
        assertEquals(LocalDate.of(2026, 7, 15), p.date)
    }

    @Test
    fun `past month-day rolls to next year`() {
        val p = parse("ski trip jan 5")
        assertEquals("ski trip", p.title)
        assertEquals(LocalDate.of(2027, 1, 5), p.date)
    }

    @Test
    fun `tonight implies an 8pm event when no time is given`() {
        val p = parse("movie tonight")
        assertTrue(p.isEvent)
        assertEquals("movie", p.title)
        assertEquals(today, p.date)
        assertEquals("20:00", p.start)
        assertEquals("21:00", p.end)
    }

    @Test
    fun `tonight keeps an explicit time`() {
        val p = parse("dinner tonight 7pm")
        assertEquals(today, p.date)
        assertEquals("19:00", p.start)
    }

    @Test
    fun `bare number is not a time`() {
        val p = parse("buy 3 apples")
        assertEquals("buy 3 apples", p.title)
        assertNull(p.date)
        assertFalse(p.isEvent)
    }

    @Test
    fun `blank input returns null`() {
        assertNull(parseQuickAdd("", today))
        assertNull(parseQuickAdd("   ", today))
    }

    @Test
    fun `stripped-empty title falls back to Untitled`() {
        val p = parse("tomorrow 3pm")
        assertEquals("Untitled", p.title)
        assertEquals(LocalDate.of(2026, 7, 5), p.date)
        assertEquals("15:00", p.start)
    }

    @Test
    fun `12am is midnight and 12pm is noon`() {
        assertEquals("00:00", parse("flight 12am").start)
        assertEquals("12:00", parse("flight 12pm").start)
    }
}
