package com.bento.calendar.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.bento.calendar.data.AppGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-arms the reminder alarm after reboot or app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.reschedule(context, AppGraph.repository(context).data.first())
            } finally {
                pending.finish()
            }
        }
    }
}
