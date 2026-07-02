package com.bento.calendar.reminders

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.bento.calendar.data.AppData
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.data.toTime
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Single-alarm chain: we always keep exactly one exact alarm armed for the
 * next upcoming reminder across all events (recurrence expanded). When it
 * fires, [ReminderReceiver] notifies and re-arms for the one after. This
 * avoids alarm-id bookkeeping entirely and survives reboots via BootReceiver.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "reminders"
    private const val REQUEST_CODE = 1001

    /** How far ahead we search for the next occurrence. */
    private const val LOOKAHEAD_DAYS = 60L

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Event reminders",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Reminders for calendar events"
        }
        nm.createNotificationChannel(channel)
    }

    fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Next reminder fire time strictly after [after], or null.
     * Several events sharing one minute are handled together at fire time.
     */
    fun nextReminderTime(data: AppData, after: LocalDateTime): LocalDateTime? {
        var best: LocalDateTime? = null
        for (dayOffset in 0..LOOKAHEAD_DAYS) {
            val date = after.toLocalDate().plusDays(dayOffset)
            for (e in occurrencesOn(data.events, date)) {
                val remind = e.remind ?: continue
                val fireAt = date.atTime(e.start.toTime()).minusMinutes(remind.toLong())
                if (fireAt.isAfter(after) && (best == null || fireAt.isBefore(best))) {
                    best = fireAt
                }
            }
            // Occurrences are per-day; once we have a hit, later days can't beat it.
            if (best != null && best.toLocalDate() <= date) return best
        }
        return best
    }

    fun reschedule(context: Context, data: AppData) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context)
        am.cancel(pi)
        val next = nextReminderTime(data, LocalDateTime.now()) ?: return
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, millis, 10 * 60_000L, pi)
        }
    }
}
