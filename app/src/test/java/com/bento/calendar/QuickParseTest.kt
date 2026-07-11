package com.bento.calendar

import com.bento.calendar.data.parseQuickAdd
import com.bento.calendar.data.Category
import com.bento.calendar.data.Recur
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
    fun `duration without a time becomes a task estimate`() {
        val p = parse("write proposal for 1.5h")
        assertFalse(p.isEvent)
        assertEquals("write proposal", p.title)
        assertEquals(90, p.estimateMin)
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

    @Test
    fun `ISO date parses while other hyphenated dates are not time ranges`() {
        // The explicit ISO form is now a first-class date. A non-ISO
        // day-month-year string still stays ordinary title text rather than
        // having its "05-10" misread as a time range.
        val p1 = parse("review 2026-07-15")
        assertFalse(p1.isEvent)
        assertEquals("review", p1.title)
        assertEquals(LocalDate.of(2026, 7, 15), p1.date)
        val p2 = parse("ship 05-10-2026")
        assertFalse(p2.isEvent)
        assertEquals("ship 05-10-2026", p2.title)
    }

    // --- Time ranges ---

    @Test
    fun `range with both meridiems sets both ends`() {
        val p = parse("meet 3pm-5pm")
        assertTrue(p.isEvent)
        assertEquals("meet", p.title)
        assertEquals("15:00", p.start)
        assertEquals("17:00", p.end)
    }

    @Test
    fun `range tolerates spaces around the hyphen`() {
        val p = parse("meet 3pm - 5pm")
        assertEquals("meet", p.title)
        assertEquals("15:00", p.start)
        assertEquals("17:00", p.end)
    }

    @Test
    fun `range start inherits the end meridiem`() {
        val p = parse("meet 3-5pm")
        assertEquals("meet", p.title)
        assertEquals("15:00", p.start)
        assertEquals("17:00", p.end)
    }

    @Test
    fun `cross-noon range prefers the am start`() {
        // Inheriting pm would read 23:00-13:00 backwards, so 11 stays am.
        val p = parse("lunch 11-1pm")
        assertEquals("lunch", p.title)
        assertEquals("11:00", p.start)
        assertEquals("13:00", p.end)
    }

    @Test
    fun `full workday range`() {
        val p = parse("conference 9am-5pm")
        assertEquals("conference", p.title)
        assertEquals("09:00", p.start)
        assertEquals("17:00", p.end)
    }

    @Test
    fun `24h range with minutes`() {
        val p = parse("review 15:00-16:30")
        assertEquals("review", p.title)
        assertEquals("15:00", p.start)
        assertEquals("16:30", p.end)
    }

    @Test
    fun `dotted minutes in a range`() {
        val p = parse("tea 3.30pm-5pm")
        assertEquals("tea", p.title)
        assertEquals("15:30", p.start)
        assertEquals("17:00", p.end)
    }

    @Test
    fun `to works as a range separator`() {
        val p = parse("meet 3pm to 5pm")
        assertEquals("meet", p.title)
        assertEquals("15:00", p.start)
        assertEquals("17:00", p.end)
    }

    @Test
    fun `backwards range is ignored, single time takes over`() {
        // "5pm-3pm" resolves end <= start, so the range is dropped whole and
        // the text left intact — the single-time pass then claims "5pm" with
        // the default duration, leaving the dangling "-3pm" in the title.
        // Pinned actual behavior, not aspiration.
        val p = parse("dinner 5pm-3pm")
        assertEquals("dinner -3pm", p.title)
        assertEquals("17:00", p.start)
        assertEquals("18:00", p.end)
    }

    @Test
    fun `range combines with a weekday date`() {
        val p = parse("standup mon 9-9:30am")
        assertTrue(p.isEvent)
        assertEquals("standup", p.title)
        assertEquals(LocalDate.of(2026, 7, 6), p.date)
        assertEquals("09:00", p.start)
        assertEquals("09:30", p.end)
    }

    @Test
    fun `tonight shifts a bare range into the evening`() {
        // Same rule as single bare hours: both ends in 01:00..11:59 → +12h.
        val p = parse("drinks tonight 9-11")
        assertEquals("drinks", p.title)
        assertEquals(today, p.date)
        assertEquals("21:00", p.start)
        assertEquals("23:00", p.end)
    }

    // --- Priority tokens ---

    @Test
    fun `priority token on a dated task`() {
        val p = parse("pay rent !high fri")
        assertFalse(p.isEvent)
        assertEquals("pay rent", p.title)
        assertEquals(LocalDate.of(2026, 7, 10), p.date)
        assertEquals(3, p.priority)
    }

    @Test
    fun `short priority token is medium and stripped`() {
        val p = parse("email bob !m")
        assertEquals("email bob", p.title)
        assertEquals(2, p.priority)
    }

    @Test
    fun `priority token in an event still parses and carries through`() {
        val p = parse("meet bob 3pm !low")
        assertTrue(p.isEvent)
        assertEquals("meet bob", p.title)
        assertEquals("15:00", p.start)
        assertEquals(1, p.priority)
    }

    // --- Relative offsets, recurrence and category tags ---

    @Test
    fun `in two days creates the expected due date`() {
        val p = parse("send report in 2 days")
        assertEquals("send report", p.title)
        assertEquals(LocalDate.of(2026, 7, 6), p.date)
        assertFalse(p.isEvent)
    }

    @Test
    fun `in three weeks combines with an event time`() {
        val p = parse("follow up in 3 weeks at 14:30")
        assertEquals("follow up", p.title)
        assertEquals(LocalDate.of(2026, 7, 25), p.date)
        assertEquals("14:30", p.start)
    }

    @Test
    fun `weekly recurring event is parsed and stripped`() {
        val p = parse("standup every week mon 9am")
        assertEquals("standup", p.title)
        assertEquals(Recur.WEEKLY, p.recur)
        assertEquals(LocalDate.of(2026, 7, 6), p.date)
        assertEquals("09:00", p.start)
    }

    @Test
    fun `monthly recurring task can remain undated`() {
        val p = parse("review budget every month")
        assertEquals("review budget", p.title)
        assertEquals(Recur.MONTHLY, p.recur)
        assertNull(p.date)
        assertFalse(p.isEvent)
    }

    @Test
    fun `default category hashtag maps by label`() {
        val p = parse("gym tomorrow #fitness")
        assertEquals("gym", p.title)
        assertEquals("fitness", p.categoryId)
    }

    @Test
    fun `custom category hashtag ignores spaces and case`() {
        val categories = listOf(Category("deep-work-id", "Deep Work", "#123456"))
        val p = parseQuickAdd("plan launch #DeepWork", today, categories = categories)!!
        assertEquals("plan launch", p.title)
        assertEquals("deep-work-id", p.categoryId)
    }

    @Test
    fun `category id itself can be used as a hashtag`() {
        val categories = listOf(Category("client_alpha", "Client A", "#123456"))
        val p = parseQuickAdd("send update #client_alpha", today, categories = categories)!!
        assertEquals("send update", p.title)
        assertEquals("client_alpha", p.categoryId)
    }

    @Test
    fun `unknown hashtag remains in the task title`() {
        val p = parse("research #later")
        assertEquals("research #later", p.title)
        assertNull(p.categoryId)
    }
}
