package com.bento.calendar.reminders

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * The system settings that decide whether reminders arrive, and on time.
 * Read live (never stored): the user can change any of them in system
 * settings while Bento is in the background.
 */
data class ReminderHealth(
    /** App notifications and the reminders channel are both on. */
    val notifications: Boolean,
    /** Exact alarms allowed; otherwise reminders use a 10-minute window. */
    val exactAlarms: Boolean,
    /** Background use restricted by the user: alarms can be held for hours. */
    val backgroundRestricted: Boolean,
    /** Exempt from battery optimisation ("Unrestricted"). */
    val unrestrictedBattery: Boolean,
) {
    companion object {
        fun read(context: Context): ReminderHealth {
            val nm = context.getSystemService(NotificationManager::class.java)
            // A channel that doesn't exist yet is created enabled on first use.
            val channelOn = ReminderScheduler.CHANNEL_IDS.all { id ->
                nm.getNotificationChannel(id)?.importance != NotificationManager.IMPORTANCE_NONE
            }
            val am = context.getSystemService(AlarmManager::class.java)
            val activity = context.getSystemService(ActivityManager::class.java)
            val power = context.getSystemService(PowerManager::class.java)
            return ReminderHealth(
                notifications = nm.areNotificationsEnabled() && channelOn,
                exactAlarms = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms(),
                backgroundRestricted = Build.VERSION.SDK_INT >= 28 && activity.isBackgroundRestricted,
                unrestrictedBattery = power.isIgnoringBatteryOptimizations(context.packageName),
            )
        }

        fun notificationSettings(context: Context): Intent =
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

        /** Null below Android 12, where exact alarms need no permission. */
        fun exactAlarmSettings(context: Context): Intent? =
            if (Build.VERSION.SDK_INT >= 31) {
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            } else null

        /**
         * App info, where every phone (Oppo/ColorOS included) puts the
         * battery choice. Bento deliberately avoids the restricted
         * REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission.
         */
        fun appDetails(context: Context): Intent =
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
    }
}
