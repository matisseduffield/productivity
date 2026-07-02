package com.bento.calendar.reminders

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bento.calendar.MainActivity
import com.bento.calendar.R
import com.bento.calendar.data.AppGraph
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.data.toTime
import com.bento.calendar.ui.Fmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = AppGraph.repository(context).data.first()
                val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                val nm = context.getSystemService(NotificationManager::class.java)

                // Alarms can be delivered late (Doze, inexact fallback). Fire
                // everything whose reminder time falls in (lastRun, now+1min]
                // — covers arbitrary lateness without ever double-notifying.
                val prefs = context.getSharedPreferences("reminders", Context.MODE_PRIVATE)
                val windowEnd = now.plusMinutes(1)
                val windowEndMin = toEpochMinutes(windowEnd)
                // First-ever fire has no marker: bound the window to a few
                // minutes so stale same-day reminders don't burst-notify.
                val lastRun = prefs.getLong("lastRunMin", 0L)
                    .let { if (it == 0L) windowEndMin - 3 else it }

                for (dayOffset in -1..1L) {
                    val date = now.toLocalDate().plusDays(dayOffset)
                    for (e in occurrencesOn(data.events, date)) {
                        val remind = e.remind ?: continue
                        val fireAt = date.atTime(e.start.toTime()).minusMinutes(remind.toLong())
                        val fireMin = toEpochMinutes(fireAt)
                        if (fireMin in (lastRun + 1)..windowEndMin) {
                            nm.notify(
                                (e.id + date).hashCode(),
                                buildNotification(context, e.title, e.start, e.loc, remind, data.prefs.use24h),
                            )
                        }
                    }
                }
                prefs.edit().putLong("lastRunMin", windowEndMin).apply()
                ReminderScheduler.reschedule(context, data)
            } finally {
                pending.finish()
            }
        }
    }

    private fun toEpochMinutes(t: LocalDateTime): Long =
        t.atZone(java.time.ZoneId.systemDefault()).toEpochSecond() / 60

    private fun buildNotification(
        context: Context,
        title: String,
        startHm: String,
        loc: String,
        remind: Int,
        use24h: Boolean,
    ): Notification {
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val whenText = when {
            remind <= 0 -> "Starting now"
            else -> "Starts at " + Fmt.time(startHm, use24h)
        }
        val text = whenText + if (loc.isNotEmpty()) " · $loc" else ""
        return Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
    }
}
