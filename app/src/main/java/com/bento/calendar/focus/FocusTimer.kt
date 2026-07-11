package com.bento.calendar.focus

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.SystemClock
import com.bento.calendar.MainActivity
import com.bento.calendar.R
import com.bento.calendar.data.AppData
import com.bento.calendar.data.AppGraph
import com.bento.calendar.data.FocusOutcome
import com.bento.calendar.data.activeFocus
import com.bento.calendar.data.extendFocus
import com.bento.calendar.data.finishFocus
import com.bento.calendar.data.focusElapsedSeconds
import com.bento.calendar.data.pauseFocus
import com.bento.calendar.data.resumeFocus
import com.bento.calendar.data.completeTaskWithBlocks
import com.bento.calendar.widget.WidgetSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

object FocusTimer {
    private const val CHANNEL_ID = "focus_timer"
    private const val NOTIFICATION_ID = 0x2B3E70
    private const val ALARM_REQUEST = 3001
    const val ACTION_PAUSE = "com.bento.calendar.focus.PAUSE"
    const val ACTION_START = "com.bento.calendar.focus.START"
    const val ACTION_RESUME = "com.bento.calendar.focus.RESUME"
    const val ACTION_FINISH = "com.bento.calendar.focus.FINISH"
    const val ACTION_COMPLETE = "com.bento.calendar.focus.COMPLETE"
    const val ACTION_EXTEND = "com.bento.calendar.focus.EXTEND"
    const val ACTION_EXPIRED = "com.bento.calendar.focus.EXPIRED"
    const val EXTRA_TASK_ID = "focusTaskId"
    const val EXTRA_BLOCK_ID = "focusBlockId"
    const val EXTRA_SOURCE_NOTIFICATION_ID = "focusSourceNotificationId"

    fun sync(context: Context, data: AppData, now: Long = System.currentTimeMillis()) {
        ensureChannel(context)
        val session = activeFocus(data)
        if (session == null) {
            cancelAlarm(context)
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
            return
        }
        val elapsed = focusElapsedSeconds(session, now, SystemClock.elapsedRealtime())
        val remaining = (session.targetSeconds - elapsed).coerceAtLeast(0)
        val expired = remaining == 0L
        context.getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(context, session.taskTitleSnapshot, session.outcome, elapsed, remaining, expired, now),
        )
        if (session.outcome == FocusOutcome.ACTIVE && !expired) armAlarm(context, now + remaining * 1000L)
        else cancelAlarm(context)
    }

    private fun ensureChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Focus timer", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Ongoing Bento focus sessions"
                setSound(null, null)
            },
        )
    }

    private fun buildNotification(
        context: Context,
        title: String,
        outcome: String,
        elapsed: Long,
        remaining: Long,
        expired: Boolean,
        now: Long,
    ): Notification {
        val builder = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle(if (expired) "Focus block complete" else title)
            .setContentText(if (expired) title else if (outcome == FocusOutcome.PAUSED) "Paused" else "Stay with the plan")
            .setContentIntent(PendingIntent.getActivity(
                context, NOTIFICATION_ID,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ))
            .setOngoing(!expired)
            .setOnlyAlertOnce(!expired)
            // Literal avoids referencing the API-31 CATEGORY_STOPWATCH field
            // on the app's API-27 minimum; notification category strings are
            // forward-compatible.
            .setCategory("stopwatch")

        if (!expired && outcome == FocusOutcome.ACTIVE) {
            builder.setWhen(now + remaining * 1000L).setUsesChronometer(true).setChronometerCountDown(true)
            builder.addAction(action(context, "Pause", ACTION_PAUSE))
        } else if (!expired) {
            builder.setWhen(now - elapsed * 1000L).setUsesChronometer(true)
            builder.addAction(action(context, "Resume", ACTION_RESUME))
        } else {
            builder.addAction(action(context, "+15 min", ACTION_EXTEND))
            builder.addAction(action(context, "Complete task", ACTION_COMPLETE))
        }
        builder.addAction(action(context, "Finish", ACTION_FINISH))
        return builder.build()
    }

    private fun action(context: Context, label: String, action: String): Notification.Action =
        Notification.Action.Builder(
            Icon.createWithResource(context, R.drawable.ic_stat_bell),
            label,
            PendingIntent.getBroadcast(
                context, action.hashCode(),
                Intent(context, FocusTimerReceiver::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        ).build()

    private fun alarmIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, ALARM_REQUEST,
        Intent(context, FocusTimerReceiver::class.java).setAction(ACTION_EXPIRED),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun armAlarm(context: Context, at: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context)
        alarm.cancel(pending)
        if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } else {
            alarm.setWindow(AlarmManager.RTC_WAKEUP, at, 60_000L, pending)
        }
    }

    private fun cancelAlarm(context: Context) {
        context.getSystemService(AlarmManager::class.java)?.cancel(alarmIntent(context))
    }
}

class FocusTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = System.currentTimeMillis()
                val elapsedNow = SystemClock.elapsedRealtime()
                val repo = AppGraph.repository(context)
                val data = when (intent.action) {
                    FocusTimer.ACTION_START -> {
                        val taskId = intent.getStringExtra(FocusTimer.EXTRA_TASK_ID)
                        val blockId = intent.getStringExtra(FocusTimer.EXTRA_BLOCK_ID)
                        repo.update { current ->
                            val block = current.taskBlocks.firstOrNull { it.id == blockId }
                            if (taskId == null || block == null) current else com.bento.calendar.data.startFocus(
                                current, taskId, blockId, now, block.durationMin * 60L, elapsedNow,
                            )
                        }
                    }
                    FocusTimer.ACTION_PAUSE -> repo.update { pauseFocus(it, now, elapsedNow) }
                    FocusTimer.ACTION_RESUME -> repo.update { resumeFocus(it, now, elapsedNow) }
                    FocusTimer.ACTION_FINISH -> repo.update { finishFocus(it, now, elapsedNow) }
                    FocusTimer.ACTION_COMPLETE -> repo.update { current ->
                        val session = activeFocus(current) ?: return@update current
                        val finished = finishFocus(current, now, elapsedNow)
                        session.taskId?.let { completeTaskWithBlocks(finished, it, java.time.LocalDate.now()) }
                            ?: finished
                    }
                    FocusTimer.ACTION_EXTEND -> repo.update { extendFocus(it) }
                    else -> repo.data.first()
                }
                intent.getIntExtra(FocusTimer.EXTRA_SOURCE_NOTIFICATION_ID, 0)
                    .takeIf { it != 0 }
                    ?.let { context.getSystemService(NotificationManager::class.java).cancel(it) }
                FocusTimer.sync(context, data, now)
                WidgetSync.pushNow(context)
            } finally {
                pending.finish()
            }
        }
    }
}
