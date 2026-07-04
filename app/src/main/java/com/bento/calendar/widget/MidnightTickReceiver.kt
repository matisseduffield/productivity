package com.bento.calendar.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Refreshes the widget at local midnight so the "Today" header and event list
 * roll over even when no store write or 30-min periodic update lands
 * overnight. A one-shot alarm re-armed on every fire; [BentoWidgetReceiver]
 * arms it from onEnabled/onUpdate (onUpdate also covers reboot, since the
 * system delivers APPWIDGET_UPDATE to placed widgets at boot) and cancels it
 * from onDisabled. updatePeriodMillis stays as a backstop.
 */
class MidnightTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // Per-widget runCatching inside pushNow: a Glance hiccup must
                // never take the process down, and the alarm re-arms regardless.
                WidgetSync.pushNow(context)
            } finally {
                arm(context)
                pending.finish()
            }
        }
    }

    companion object {
        private const val REQUEST_CODE = 1002

        /** One fixed-requestCode PendingIntent so arming is idempotent. */
        private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, MidnightTickReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Arm (or re-arm) the one-shot alarm for the next local midnight. */
        fun arm(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val millis = LocalDate.now()
                .plusDays(1)
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            // Same exact-alarm pattern as ReminderScheduler.reschedule: exact
            // while permitted, else a 10-minute window.
            val pi = pendingIntent(context)
            val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, millis, 10 * 60_000L, pi)
            }
        }

        /** Cancel the midnight alarm (called when the last widget is removed). */
        fun cancel(context: Context) {
            context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context))
        }
    }
}
