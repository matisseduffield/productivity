package com.bento.calendar.ui

import com.bento.calendar.data.toDate
import com.bento.calendar.data.toMins
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth

/**
 * Pure text formatting, ported 1:1 from the prototype so every label matches
 * the design. Fixed English month/weekday names, exactly like the design files.
 */
object Fmt {
    val MN = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    val MS = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val WD = listOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
    val WS = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    /** Sunday=0 index, matching JS Date.getDay(). */
    fun dow(date: LocalDate): Int = date.dayOfWeek.value % 7

    fun time(t: LocalTime, use24h: Boolean): String {
        if (use24h) return "%02d:%02d".format(t.hour, t.minute)
        val ap = if (t.hour >= 12) "pm" else "am"
        val h = t.hour % 12
        return "${if (h == 0) 12 else h}:${"%02d".format(t.minute)} $ap"
    }

    fun time(hm: String, use24h: Boolean): String = time(LocalTime.parse(hm), use24h)

    fun hourLabel(h: Int, use24h: Boolean): String {
        if (use24h) return "%02d:00".format(h)
        val x = h % 12
        return "${if (x == 0) 12 else x}${if (h < 12) " am" else " pm"}"
    }

    /** "45 min", "2 h", "1 h 30" — the prototype's dur(). */
    fun duration(startHm: String, endHm: String): String {
        val m = endHm.toMins() - startHm.toMins()
        if (m < 60) return "$m min"
        val h = m / 60
        val r = m % 60
        return "$h h" + if (r > 0) " $r" else ""
    }

    /** "now", "in 5 min", "in 2 h 5 min" — the prototype's cnt(). */
    fun countdown(mins: Int): String {
        if (mins <= 0) return "now"
        if (mins < 60) return "in $mins min"
        val h = mins / 60
        val r = mins % 60
        return "in $h h" + if (r > 0) " $r min" else ""
    }

    fun greeting(hour: Int): String = when {
        hour < 5 -> "Good night"
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }

    /** "Thursday, Jul 2" — Today header title. */
    fun todayTitle(d: LocalDate): String = "${WD[dow(d)]}, ${MS[d.monthValue - 1]} ${d.dayOfMonth}"

    /** "Thu 2 Jul". */
    fun dayShort(d: LocalDate): String = "${WS[dow(d)]} ${d.dayOfMonth} ${MS[d.monthValue - 1]}"

    /** "July 2026" — Month view title. */
    fun monthTitle(m: YearMonth): String = "${MN[m.monthValue - 1]} ${m.year}"

    /** "30 Jun – 6 Jul" — Week view title. */
    fun weekTitle(weekStart: LocalDate): String {
        val e = weekStart.plusDays(6)
        return "${weekStart.dayOfMonth} ${MS[weekStart.monthValue - 1]} – ${e.dayOfMonth} ${MS[e.monthValue - 1]}"
    }

    /** "Thu, 2 July" — Day view title. */
    fun dayTitle(d: LocalDate): String = "${WS[dow(d)]}, ${d.dayOfMonth} ${MN[d.monthValue - 1]}"

    /** "Today", "Tomorrow", "8 Jul" — task due chips. */
    fun dueLabel(due: String, today: LocalDate): String {
        val d = due.toDate()
        if (d == today) return "Today"
        if (d == today.plusDays(1)) return "Tomorrow"
        return "${d.dayOfMonth} ${MS[d.monthValue - 1]}"
    }

    /** "Edited 14:02", "Edited yesterday", "Edited 2 Jul". */
    fun relEdit(updatedMs: Long, now: LocalDateTime, use24h: Boolean): String {
        val d = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(updatedMs), java.time.ZoneId.systemDefault())
        if (d.toLocalDate() == now.toLocalDate()) return "Edited " + time(d.toLocalTime(), use24h)
        if (d.toLocalDate() == now.toLocalDate().minusDays(1)) return "Edited yesterday"
        return "Edited ${d.dayOfMonth} ${MS[d.monthValue - 1]}"
    }

    /** "14:02" if edited today, else "2 Jul" — note list timestamps. */
    fun editStamp(updatedMs: Long, today: LocalDate, use24h: Boolean): String {
        val d = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(updatedMs), java.time.ZoneId.systemDefault())
        if (d.toLocalDate() == today) return time(d.toLocalTime(), use24h)
        return "${d.dayOfMonth} ${MS[d.monthValue - 1]}"
    }
}

/** Week start for a date respecting the "week starts Monday" preference. */
fun startOfWeek(d: LocalDate, monday: Boolean): LocalDate {
    val dow = Fmt.dow(d)
    val off = if (monday) (dow + 6) % 7 else dow
    return d.minusDays(off.toLong())
}
