package com.bento.calendar.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Read-only view over the device's calendar provider (Google Calendar,
 * Samsung Calendar, Exchange…). Bento never writes to it — device events are
 * an overlay in the calendar views, visually distinct and non-editable.
 */
data class DeviceCal(val id: Long, val name: String, val colorHex: String)

data class DeviceEvent(
    val id: Long,
    val title: String,
    /** Occurrence date (ISO) this instance renders on. */
    val date: String,
    val start: String,
    val end: String,
    val allDay: Boolean,
    val colorHex: String,
    val calName: String,
    val loc: String,
)

object DeviceCalendars {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun colorHexOf(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** Visible, synced calendars on the device. */
    fun calendars(context: Context): List<DeviceCal> {
        if (!hasPermission(context)) return emptyList()
        val out = mutableListOf<DeviceCal>()
        runCatching {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(
                    CalendarContract.Calendars._ID,
                    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Calendars.CALENDAR_COLOR,
                    CalendarContract.Calendars.VISIBLE,
                ),
                null,
                null,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(3) != 1) continue
                    out += DeviceCal(
                        id = c.getLong(0),
                        name = c.getString(1) ?: "Calendar",
                        colorHex = colorHexOf(c.getInt(2)),
                    )
                }
            }
        }
        return out
    }

    /**
     * Expanded instances (provider handles recurrence) between [from] and [to]
     * inclusive, for [enabledIds] (empty = all visible calendars), grouped by
     * occurrence date. Multi-day instances appear on each day they span.
     */
    fun eventsBetween(
        context: Context,
        from: LocalDate,
        to: LocalDate,
        enabledIds: List<Long>,
    ): Map<String, List<DeviceEvent>> {
        if (!hasPermission(context)) return emptyMap()
        val zone = ZoneId.systemDefault()
        val startMs = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = to.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val byDate = mutableMapOf<String, MutableList<DeviceEvent>>()
        runCatching {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
                .let { b ->
                    ContentUris.appendId(b, startMs)
                    ContentUris.appendId(b, endMs)
                    b.build()
                }
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                    CalendarContract.Instances.DISPLAY_COLOR,
                    CalendarContract.Instances.CALENDAR_ID,
                    CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
                    CalendarContract.Instances.EVENT_LOCATION,
                ),
                null,
                null,
                CalendarContract.Instances.BEGIN,
            )?.use { c ->
                while (c.moveToNext()) {
                    val calId = c.getLong(6)
                    if (enabledIds.isNotEmpty() && calId !in enabledIds) continue
                    val allDay = c.getInt(4) == 1
                    // All-day instances are stored in UTC; timed ones are epoch.
                    val beginZone = if (allDay) ZoneId.of("UTC") else zone
                    val begin = Instant.ofEpochMilli(c.getLong(2)).atZone(beginZone)
                    val endInst = Instant.ofEpochMilli(c.getLong(3)).atZone(beginZone)
                    val ev = DeviceEvent(
                        id = c.getLong(0),
                        title = c.getString(1)?.ifBlank { null } ?: "(No title)",
                        date = begin.toLocalDate().toString(),
                        start = if (allDay) "00:00" else "%02d:%02d".format(begin.hour, begin.minute),
                        end = if (allDay) "23:59" else "%02d:%02d".format(endInst.hour, endInst.minute),
                        allDay = allDay,
                        colorHex = colorHexOf(c.getInt(5)),
                        calName = c.getString(7) ?: "Calendar",
                        loc = c.getString(8) ?: "",
                    )
                    // Fan multi-day instances out to each day they cover.
                    deviceDaySegments(ev, begin.toLocalDateTime(), endInst.toLocalDateTime(), from, to)
                        .forEach { segment ->
                            byDate.getOrPut(segment.date) { mutableListOf() } += segment
                        }
                }
            }
        }
        return byDate.mapValues { (_, v) -> v.sortedBy { it.start } }
    }
}

/**
 * Provider ranges for one overlay load: a generous window around what the
 * caller shows, plus today's week whenever that window misses it. Today, its
 * conflict checks and today's planning read the same map, so browsing a
 * distant month or week must not drop today's device events. Each range
 * returns complete days, so overlapping results merge safely.
 */
internal fun deviceLoadRanges(
    anchorFrom: LocalDate,
    anchorTo: LocalDate,
    today: LocalDate,
): List<Pair<LocalDate, LocalDate>> {
    val window = anchorFrom.minusMonths(1) to anchorTo.plusMonths(2)
    val todayWeek = today to today.plusDays(6)
    val covered = !todayWeek.first.isBefore(window.first) && !todayWeek.second.isAfter(window.second)
    return if (covered) listOf(window) else listOf(window, todayWeek)
}

/**
 * Split one provider instance into the per-day rows Bento renders, clipped to
 * [from]..[to]. Mirrors [occurrencesOn] for Bento's own multi-day events: the
 * first day runs start→23:59, days between are all-day and the last day runs
 * 00:00→end. Copying the raw times onto every day made an overnight
 * 22:00–02:00 meeting an inverted interval that conflict detection and the
 * planners could never see.
 */
internal fun deviceDaySegments(
    base: DeviceEvent,
    begin: LocalDateTime,
    end: LocalDateTime,
    from: LocalDate,
    to: LocalDate,
): List<DeviceEvent> {
    val first = begin.toLocalDate()
    // All-day ends are exclusive midnights; a timed event ending exactly at
    // 00:00 doesn't claim the next day either.
    val endsAtMidnight = end.toLocalTime() == LocalTime.MIDNIGHT && end.toLocalDate().isAfter(first)
    val last = (if (base.allDay || endsAtMidnight) end.toLocalDate().minusDays(1) else end.toLocalDate())
        .coerceAtLeast(first)
    val segments = mutableListOf<DeviceEvent>()
    var d = maxOf(first, from)
    while (!d.isAfter(last) && !d.isAfter(to)) {
        val iso = d.toString()
        segments += when {
            base.allDay -> base.copy(date = iso)
            d == first -> base.copy(date = iso, end = if (d == last && !endsAtMidnight) base.end else "23:59")
            d == last && !endsAtMidnight -> base.copy(date = iso, start = "00:00")
            else -> base.copy(date = iso, start = "00:00", end = "23:59", allDay = true)
        }
        d = d.plusDays(1)
    }
    return segments
}
