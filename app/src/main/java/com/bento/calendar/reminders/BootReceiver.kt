package com.bento.calendar.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bento.calendar.data.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Re-arms the reminder alarm after reboot, app update, clock/timezone change,
 * or the user toggling the exact-alarm permission (API 31/32 kills pending
 * exact alarms when it changes).
 */
class BootReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIME_CHANGED,
        Intent.ACTION_TIMEZONE_CHANGED,
        "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
    )

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in actions) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.reschedule(context, AppGraph.repository(context).data.first())
                // The scheduler chain only covers upcoming reminders; snoozes
                // armed by ReminderReceiver.handleSnooze are separate one-shot
                // alarms that a reboot silently discards. Re-arm the persisted
                // ones (posting any that came due while the device was down).
                ReminderReceiver.restorePendingSnoozes(context)
            } finally {
                pending.finish()
            }
        }
    }
}
