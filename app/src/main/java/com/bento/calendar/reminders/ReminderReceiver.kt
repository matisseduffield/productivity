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

                // Fire everything due within a minute of now (alarm delivery jitter).
                for (dayOffset in 0..1L) {
                    val date = now.toLocalDate().plusDays(dayOffset)
                    for (e in occurrencesOn(data.events, date)) {
                        val remind = e.remind ?: continue
                        val fireAt = date.atTime(e.start.toTime()).minusMinutes(remind.toLong())
                        val delta = ChronoUnit.MINUTES.between(fireAt, now)
                        if (delta in -1..1) {
                            nm.notify(
                                (e.id + date).hashCode(),
                                buildNotification(context, e.title, e.start, e.loc, remind, data.prefs.use24h),
                            )
                        }
                    }
                }
                ReminderScheduler.reschedule(context, data)
            } finally {
                pending.finish()
            }
        }
    }

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
