package com.bento.calendar.reminders

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
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
import org.json.JSONException
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ReminderReceiver : BroadcastReceiver() {
    companion object {
        const val ACTION_SNOOZE = "com.bento.calendar.SNOOZE"
        const val ACTION_SHOW_SNOOZED = "com.bento.calendar.SHOW_SNOOZED"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_TEXT = "text"
        private const val EXTRA_NOTIF_ID = "notifId"
        private const val SNOOZE_MS = 10 * 60_000L
        private const val PREFS_NAME = "reminders"
        private const val KEY_PENDING_SNOOZES = "pendingSnoozes"
        /** Snoozes this far past due at restore time are dropped as stale. */
        private const val SNOOZE_STALE_MS = 3 * 60 * 60_000L

        /**
         * Re-arms snoozes persisted by [handleSnooze]. AlarmManager alarms do
         * not survive a reboot (or app update / force-stop), so [BootReceiver]
         * calls this after re-arming the main chain. Future entries are
         * re-armed exactly as [handleSnooze] armed them; entries that came due
         * during the downtime are posted immediately — the user explicitly
         * asked to be re-nagged — unless they are more than [SNOOZE_STALE_MS]
         * past due (a long power-off), in which case they are dropped.
         */
        fun restorePendingSnoozes(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val map = parsePendingSnoozes(prefs.getString(KEY_PENDING_SNOOZES, null))
            if (map.length() == 0) return
            val now = System.currentTimeMillis()
            val nm = context.getSystemService(NotificationManager::class.java)
            val keep = JSONObject()
            for (key in map.keys()) {
                val entry = map.optJSONObject(key) ?: continue
                val notifId = key.toIntOrNull() ?: continue
                val fireAt = entry.optLong("fireAtMillis")
                val title = entry.optString("title")
                val text = entry.optString("text")
                when {
                    fireAt > now -> {
                        armSnoozeAlarm(context, notifId, title, text, fireAt)
                        keep.put(key, entry)
                    }
                    now - fireAt <= SNOOZE_STALE_MS ->
                        nm?.notify(notifId, buildNotification(context, title, text, notifId))
                    // else: too stale to be useful — drop.
                }
            }
            if (keep.length() == 0) prefs.edit().remove(KEY_PENDING_SNOOZES).apply()
            else prefs.edit().putString(KEY_PENDING_SNOOZES, keep.toString()).apply()
        }

        /**
         * Arms the one-shot snooze alarm. The distinct action string plus
         * per-event requestCode keeps this PendingIntent's identity separate
         * from [ReminderScheduler]'s single-alarm chain (Intent.filterEquals
         * compares actions; the chain's intent has none).
         */
        private fun armSnoozeAlarm(
            context: Context,
            notifId: Int,
            title: String?,
            text: String?,
            at: Long,
        ) {
            val am = context.getSystemService(AlarmManager::class.java) ?: return
            val pi = PendingIntent.getBroadcast(
                context,
                notifId,
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ACTION_SHOW_SNOOZED)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_TEXT, text)
                    .putExtra(EXTRA_NOTIF_ID, notifId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
            if (canExact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setWindow(AlarmManager.RTC_WAKEUP, at, 10 * 60_000L, pi)
            }
        }

        /** Read-modify-write of the persisted notifId → snooze-entry map. */
        private fun editPendingSnoozes(context: Context, edit: (JSONObject) -> Unit) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val map = parsePendingSnoozes(prefs.getString(KEY_PENDING_SNOOZES, null))
            edit(map)
            if (map.length() == 0) prefs.edit().remove(KEY_PENDING_SNOOZES).apply()
            else prefs.edit().putString(KEY_PENDING_SNOOZES, map.toString()).apply()
        }

        private fun parsePendingSnoozes(raw: String?): JSONObject =
            try {
                JSONObject(raw ?: "{}")
            } catch (_: JSONException) {
                JSONObject()
            }

        private fun buildNotification(
            context: Context,
            title: String,
            text: String,
            notifId: Int,
        ): Notification {
            val tapIntent = PendingIntent.getActivity(
                context,
                0,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val snoozeIntent = PendingIntent.getBroadcast(
                context,
                notifId,
                Intent(context, ReminderReceiver::class.java)
                    .setAction(ACTION_SNOOZE)
                    .putExtra(EXTRA_TITLE, title)
                    .putExtra(EXTRA_TEXT, text)
                    .putExtra(EXTRA_NOTIF_ID, notifId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val snoozeAction = Notification.Action.Builder(
                Icon.createWithResource(context, R.drawable.ic_stat_bell),
                "Snooze 10 min",
                snoozeIntent,
            ).build()
            return Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_bell)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(tapIntent)
                .setAutoCancel(true)
                .addAction(snoozeAction)
                .build()
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_SNOOZE -> {
                handleSnooze(context, intent)
                return
            }
            ACTION_SHOW_SNOOZED -> {
                showSnoozed(context, intent)
                return
            }
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = AppGraph.repository(context).data.first()
                val now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES)
                val nm = context.getSystemService(NotificationManager::class.java)

                // Alarms can be delivered late (Doze, inexact fallback). Fire
                // everything whose reminder time falls in (lastRun, now+1min]
                // — covers arbitrary lateness without ever double-notifying.
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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
                            val notifId = (e.id + date).hashCode()
                            nm.notify(
                                notifId,
                                buildNotification(
                                    context, e.title, e.start, e.loc, remind,
                                    data.prefs.use24h, notifId,
                                    allDay = e.allDay, date = date, now = now,
                                ),
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

    /**
     * "Snooze 10 min" tapped: dismiss the notification and arm a one-shot
     * alarm that re-posts it via [ACTION_SHOW_SNOOZED]. The pending snooze is
     * also persisted — alarms don't survive a reboot — so that
     * [restorePendingSnoozes] can re-arm it. Keying by notifId (overwriting on
     * re-snooze) matches the PendingIntent replacement semantics of
     * FLAG_UPDATE_CURRENT.
     */
    private fun handleSnooze(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        context.getSystemService(NotificationManager::class.java)?.cancel(notifId)
        val title = intent.getStringExtra(EXTRA_TITLE)
        val text = intent.getStringExtra(EXTRA_TEXT)
        val at = System.currentTimeMillis() + SNOOZE_MS
        editPendingSnoozes(context) { map ->
            map.put(
                notifId.toString(),
                JSONObject()
                    .put("fireAtMillis", at)
                    .put("title", title.orEmpty())
                    .put("text", text.orEmpty()),
            )
        }
        armSnoozeAlarm(context, notifId, title, text, at)
    }

    /**
     * Snooze alarm fired: re-post the notification straight from the extras
     * (no data scan — the original text is already final), snoozable again.
     */
    private fun showSnoozed(context: Context, intent: Intent) {
        val notifId = intent.getIntExtra(EXTRA_NOTIF_ID, 0)
        // Fired — no longer pending, nothing to restore after a reboot.
        editPendingSnoozes(context) { it.remove(notifId.toString()) }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: return
        val text = intent.getStringExtra(EXTRA_TEXT) ?: ""
        context.getSystemService(NotificationManager::class.java)
            ?.notify(notifId, buildNotification(context, title, text, notifId))
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
        notifId: Int,
        allDay: Boolean,
        date: LocalDate,
        now: LocalDateTime,
    ): Notification {
        // All-day wording is derived from the occurrence date vs the actual
        // delivery date — NOT from remind or the 00:00 clock time: alarms can
        // be delivered late (Doze, restore), so a 23:50-armed "10 min before"
        // alarm may only land after midnight, when the event is already today.
        val whenText = when {
            allDay -> when (date) {
                now.toLocalDate() -> "All day today"
                now.toLocalDate().plusDays(1) -> "All day tomorrow"
                // Very late/stale delivery: name the day instead.
                else -> "All day · " + Fmt.dayShort(date)
            }
            remind <= 0 -> "Starting now"
            else -> "Starts at " + Fmt.time(startHm, use24h)
        }
        val text = whenText + if (loc.isNotEmpty()) " · $loc" else ""
        return buildNotification(context, title, text, notifId)
    }
}
