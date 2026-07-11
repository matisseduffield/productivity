package com.bento.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.bento.calendar.security.Biometrics
import com.bento.calendar.ui.AppRoot
import com.bento.calendar.ui.AppViewModel
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// FragmentActivity (not ComponentActivity): androidx.biometric's
// BiometricPrompt requires it. Everything Compose/launcher-related is
// unaffected — FragmentActivity is a ComponentActivity.
class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    /** elapsedRealtime when the app last left the foreground; 0 = never. */
    private var lastStoppedAt = 0L

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** READ_CALENDAR for the device-calendar overlay (Settings toggle). */
    private val calendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            vm.onCalendarPermissionResult(granted)
        }

    /** Settings > Data > Export: SAF "save as" for the backup JSON. */
    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri == null) return@registerForActivityResult
            val json = vm.exportJson() ?: return@registerForActivityResult
            lifecycleScope.launch {
                val saved = writeUtf8(uri, json)
                Toast.makeText(
                    this@MainActivity,
                    if (saved) "Backup saved" else "Couldn't save backup",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

    /** Settings > Data > Import: SAF picker; replaces the whole store. */
    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                // The picker's MIME filter admits arbitrary files, so read on IO
                // with a size cap; the store mutation and toast stay on Main.
                val text = readUtf8Bounded(uri, MAX_IMPORT_BYTES)
                val restored = try {
                    text?.let { vm.importFromJson(it) } ?: false
                } catch (_: Exception) {
                    false
                }
                Toast.makeText(
                    this@MainActivity,
                    if (restored) "Backup restored" else "Not a valid backup file",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

    /** Settings > Data > Export calendar: interoperable RFC 5545 file. */
    private val exportCalendarLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/calendar")) { uri ->
            if (uri == null) return@registerForActivityResult
            val calendar = vm.exportCalendarIcs() ?: return@registerForActivityResult
            lifecycleScope.launch {
                val saved = writeUtf8(uri, calendar)
                Toast.makeText(
                    this@MainActivity,
                    if (saved) "Calendar exported" else "Couldn't export calendar",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }

    /** Settings > Data > Import calendar: merges VEVENTs; never replaces data. */
    private val importCalendarLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                val text = readUtf8Bounded(uri, MAX_IMPORT_BYTES)
                val result = try {
                    if (text != null) vm.importCalendarIcs(text) else null
                } catch (_: Exception) {
                    null
                }
                val message = when {
                    result == null || !result.validCalendar -> "Not a valid calendar file"
                    result.events.isNotEmpty() -> buildString {
                        append("Imported ${result.events.size} event")
                        if (result.events.size != 1) append('s')
                        if (result.duplicates > 0) append(" · ${result.duplicates} already there")
                        if (result.skipped > 0) append(" · ${result.skipped} skipped")
                    }
                    result.duplicates > 0 && result.skipped == 0 -> "Those events are already imported"
                    result.sourceEvents == 0 -> "No events in that calendar file"
                    else -> "No compatible events found"
                }
                Toast.makeText(this@MainActivity, message, Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot(
                vm,
                onExport = { exportLauncher.launch("bento-backup.json") },
                onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
                onCalendarExport = { exportCalendarLauncher.launch("bento-calendar.ics") },
                onCalendarImport = { importCalendarLauncher.launch(ICS_MIME_TYPES) },
            )
        }
        requestNotificationPermission()
        // Play builds update through the store; only GitHub builds self-check.
        if (BuildConfig.SELF_UPDATER) vm.checkForUpdates()
        vm.requestCalendarPermission = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }
        vm.bioPrompt = { title, subtitle, allowCredential, onSuccess ->
            Biometrics.prompt(
                activity = this,
                title = title,
                subtitle = subtitle,
                allowDeviceCredential = allowCredential,
                onSuccess = onSuccess,
            )
        }
        // Availability is also refreshed in onStart; seeding here keeps the
        // cold-start lock check below from racing the first onStart.
        vm.bioAvailable = Biometrics.available(this)
        vm.credentialAvailable = Biometrics.anyCredential(this)
        // Cold start ONLY: recreation (rotation, theme change) re-runs
        // onCreate with the VM's appLocked already settled — re-arming here
        // would throw the lock up mid-use every time the phone rotates.
        if (savedInstanceState == null) {
            lifecycleScope.launch {
                val d = vm.data.first { it != null }!!
                if (d.prefs.appLock && vm.credentialAvailable) {
                    vm.lockApp()
                    vm.requestAppUnlock()
                }
            }
        }
        if (vm.hasCalendarOverlayEnabled()) vm.refreshDeviceCalendarData()
        // Only on a genuinely fresh launch: on recreation (rotation, theme
        // change) getIntent() still carries the old shortcut action and would
        // re-fire it — reopening a dismissed editor or spawning another note.
        if (savedInstanceState == null) handleShortcutAction(intent)
    }

    /**
     * Receives action-carrying intents when the singleTask activity is warm:
     * the widget's quick-add chips launch with only FLAG_ACTIVITY_NEW_TASK
     * (BentoWidget), so their NEW_EVENT/NEW_TASK land here rather than in
     * onCreate. (Static launcher shortcuts never do — the platform forces
     * NEW_TASK|CLEAR_TASK on them, tearing down the task so they go through
     * the onCreate path.) handleShortcutAction routes them through the
     * clobber-safe quickAdd* paths, so a warm chip tap can't discard an
     * in-progress draft. Also receives the action-less reminder-notification
     * tap intent, for which handleShortcutAction is a no-op. setIntent keeps
     * getIntent() fresh for later recreations.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShortcutAction(intent)
    }

    private fun handleShortcutAction(intent: Intent?) {
        val action = intent?.action ?: return
        if (action !in WIDGET_ACTIONS) return
        val dateExtra = intent.getStringExtra(EXTRA_DATE)
        val noteIdExtra = intent.getStringExtra(EXTRA_NOTE_ID)
        // On a cold start the DataStore read hasn't landed yet (vm.data is null
        // for the first frames); building drafts or opening notes then would
        // act on defaults instead of the user's real data.
        lifecycleScope.launch {
            vm.data.first { it != null }
            when (action) {
                // quickAdd* (not newEvent/newTask): they keep a dirty draft
                // instead of clobbering it and always prefill today's date,
                // ignoring a stale Calendar-tab selection. Notes have no
                // clobber risk — newNote always creates a fresh note.
                ACTION_NEW_EVENT -> vm.quickAddEvent()
                ACTION_NEW_TASK -> vm.quickAddTask()
                ACTION_NEW_NOTE -> vm.newNote()
                ACTION_OPEN_SEARCH -> vm.openSearch()
                ACTION_OPEN_TASKS -> vm.setTab(com.bento.calendar.ui.Tab.Tasks)
                ACTION_OPEN_DAY -> {
                    val date = try {
                        java.time.LocalDate.parse(dateExtra)
                    } catch (_: Exception) {
                        java.time.LocalDate.now()
                    }
                    vm.weekStripTap(date)
                }
                ACTION_OPEN_NOTE -> {
                    vm.setTab(com.bento.calendar.ui.Tab.Notes)
                    // openNote runs the PIN flow for locked notes; a stale id
                    // (note deleted since the widget rendered) is a no-op.
                    noteIdExtra?.let { vm.openNote(it) }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Enrollment can change in system settings while backgrounded.
        vm.bioAvailable = Biometrics.available(this)
        vm.credentialAvailable = Biometrics.anyCredential(this)
        // Re-lock when coming back after the grace window. The device-
        // credential fallback bounces through the keyguard (which stops this
        // activity), so a short grace keeps that round-trip from re-locking.
        // elapsedRealtime, not wall clock: winding the clock back must not
        // hold the grace window open indefinitely.
        val away = android.os.SystemClock.elapsedRealtime() - lastStoppedAt
        if (!vm.appLocked && lastStoppedAt != 0L && away > APP_LOCK_GRACE_MS &&
            vm.data.value?.prefs?.appLock == true && vm.credentialAvailable
        ) {
            vm.lockApp()
            vm.requestAppUnlock()
        }
    }

    override fun onStop() {
        super.onStop()
        lastStoppedAt = android.os.SystemClock.elapsedRealtime()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshNow()
    }

    /** Write a UTF-8 document, explicitly truncating when the provider allows. */
    private suspend fun writeUtf8(uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // Plain "w" is not guaranteed to truncate on every provider; a
            // shorter replacement could otherwise retain corrupt tail bytes.
            val out = try {
                contentResolver.openOutputStream(uri, "wt")
            } catch (_: Exception) {
                contentResolver.openOutputStream(uri)
            }
            if (out == null) false
            else {
                out.use { it.write(text.toByteArray(Charsets.UTF_8)) }
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun readUtf8Bounded(uri: Uri, max: Int): String? = withContext(Dispatchers.IO) {
        try {
            contentResolver.openInputStream(uri)?.use { it.readBounded(max) }
        } catch (_: Exception) {
            null
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private companion object {
        /** Returning within this window skips the app-lock re-prompt. */
        const val APP_LOCK_GRACE_MS = 60_000L

        /**
         * Actions declared by the static launcher shortcuts
         * (res/xml/shortcuts.xml) and sent by the widget's quick-add chips
         * (widget/BentoWidget.kt).
         */
        const val ACTION_NEW_EVENT = "com.bento.calendar.NEW_EVENT"
        const val ACTION_NEW_TASK = "com.bento.calendar.NEW_TASK"
        const val ACTION_NEW_NOTE = "com.bento.calendar.NEW_NOTE"
        const val ACTION_OPEN_SEARCH = "com.bento.calendar.OPEN_SEARCH"
        const val ACTION_OPEN_TASKS = "com.bento.calendar.OPEN_TASKS"
        const val ACTION_OPEN_DAY = "com.bento.calendar.OPEN_DAY"
        const val ACTION_OPEN_NOTE = "com.bento.calendar.OPEN_NOTE"
        const val EXTRA_DATE = "date"
        const val EXTRA_NOTE_ID = "noteId"

        val WIDGET_ACTIONS = setOf(
            ACTION_NEW_EVENT, ACTION_NEW_TASK, ACTION_NEW_NOTE,
            ACTION_OPEN_SEARCH, ACTION_OPEN_TASKS, ACTION_OPEN_DAY, ACTION_OPEN_NOTE,
        )

        // Some file managers serve .json as text or octet-stream MIME types.
        val IMPORT_MIME_TYPES = arrayOf("application/json", "text/*", "application/octet-stream")
        val ICS_MIME_TYPES = arrayOf("text/calendar", "text/*", "application/octet-stream")

        /** Bound on imported backup size; anything larger is not our backup. */
        const val MAX_IMPORT_BYTES = 10 * 1024 * 1024

        /**
         * Reads at most [max] bytes as UTF-8, or returns null if the stream is
         * longer (a bounded read: some DocumentsProviders report no SIZE, so a
         * pre-check via OpenableColumns can't be relied on).
         */
        fun InputStream.readBounded(max: Int): String? {
            val buf = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var total = 0
            while (true) {
                val n = read(chunk)
                if (n < 0) break
                total += n
                if (total > max) return null
                buf.write(chunk, 0, n)
            }
            return String(buf.toByteArray(), Charsets.UTF_8)
        }
    }
}
