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
import com.bento.calendar.data.toIso
import com.bento.calendar.data.toTime
import com.bento.calendar.data.BlockState
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Single-alarm chain: we always keep exactly one exact alarm armed for the
 * next upcoming reminder across all events (recurrence expanded) and tasks
 * (remindAt on the due date). When it fires, [ReminderReceiver] notifies and
 * re-arms for the one after. This avoids alarm-id bookkeeping entirely and
 * survives reboots via BootReceiver.
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
            description = "Reminders for calendar events and tasks"
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
     * Several events/tasks sharing one minute are handled together at fire
     * time. All-day events store start 00:00, so they remind relative to
     * midnight ("At start" = 00:00 that day, "1 day before" = the prior
     * midnight). Open tasks with a remindAt fire ON their due date.
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
            val iso = date.toIso()
            for (t in data.tasks) {
                val remindAt = t.remindAt ?: continue
                if (t.done || t.due != iso) continue
                val fireAt = date.atTime(remindAt.toTime())
                if (fireAt.isAfter(after) && (best == null || fireAt.isBefore(best))) {
                    best = fireAt
                }
            }
            val blockRemind = data.prefs.blockReminderMin
            if (blockRemind != null) {
                for (block in data.taskBlocks) {
                    if (block.state != BlockState.PLANNED || block.date != iso) continue
                    val task = data.tasks.firstOrNull { it.id == block.taskId && !it.done } ?: continue
                    val fireAt = date.atTime(LocalTime.of(block.startMin / 60, block.startMin % 60))
                        .minusMinutes(blockRemind.toLong())
                    if (fireAt.isAfter(after) && (best == null || fireAt.isBefore(best))) best = fireAt
                }
            }
            // A reminder fires at most 1 day (1440 min) before its occurrence
            // (task reminders fire ON their day, well inside that bound), so a
            // later occurrence day can still fire earlier today — only stop
            // once the best fire time is strictly before this day.
            if (best != null && best.toLocalDate().isBefore(date)) return best
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
