package com.bento.calendar.updates

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import com.bento.calendar.R

/**
 * PackageInstaller status callback. When the system needs the user to confirm
 * the update it hands us a confirmation intent: we launch it directly if the
 * app is in the foreground, otherwise post a notification whose tap carries the
 * background-activity-launch grant. Terminal failures are surfaced to the UI
 * instead of being swallowed.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_INSTALL_STATUS = "com.bento.calendar.INSTALL_STATUS"
        private const val CHANNEL_ID = "updates"
        private const val NOTIF_ID = 4201
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                launchConfirm(context, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> {
                // App process is about to be replaced; nothing to do.
                UpdateManager.installError.value = null
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                // User cancelled the confirm dialog — not an error.
            }
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                UpdateManager.installError.value = failureText(status, msg)
            }
        }
    }

    private fun launchConfirm(context: Context, confirm: Intent) {
        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (isForeground()) {
            runCatching { context.startActivity(confirm) }
                .onFailure { notifyConfirm(context, confirm) }
        } else {
            notifyConfirm(context, confirm)
        }
    }

    /** A notification tap carries a background-activity-launch grant. */
    private fun notifyConfirm(context: Context, confirm: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_HIGH),
        )
        val pending = PendingIntent.getActivity(
            context,
            0,
            confirm,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_bell)
            .setContentTitle("Update ready to install")
            .setContentText("Tap to finish updating Bento Calendar")
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_ID, notif) }
    }

    private fun isForeground(): Boolean {
        val state = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(state)
        return state.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun failureText(status: Int, message: String?): String = when (status) {
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Update blocked — a conflicting version is installed"
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "This update isn't compatible with your device"
        PackageInstaller.STATUS_FAILURE_STORAGE ->
            "Not enough storage to install the update"
        else -> "Update failed" + if (!message.isNullOrBlank()) " — $message" else ""
    }
}
