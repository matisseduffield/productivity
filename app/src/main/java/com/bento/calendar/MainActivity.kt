package com.bento.calendar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bento.calendar.ui.AppRoot
import com.bento.calendar.ui.AppViewModel
import java.io.ByteArrayOutputStream
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

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
                val saved = withContext(Dispatchers.IO) {
                    try {
                        // "wt" truncates explicitly: plain "w" is not guaranteed to
                        // truncate on all DocumentsProviders, so overwriting a longer
                        // old backup could leave trailing garbage that corrupts it.
                        val out = try {
                            contentResolver.openOutputStream(uri, "wt")
                        } catch (_: Exception) {
                            // Some providers reject "wt"; fall back to plain "w".
                            contentResolver.openOutputStream(uri)
                        }
                        if (out == null) {
                            false
                        } else {
                            out.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                            true
                        }
                    } catch (_: Exception) {
                        false
                    }
                }
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
                val text = withContext(Dispatchers.IO) {
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            input.readBounded(MAX_IMPORT_BYTES)
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppRoot(
                vm,
                onExport = { exportLauncher.launch("bento-backup.json") },
                onImport = { importLauncher.launch(IMPORT_MIME_TYPES) },
            )
        }
        requestNotificationPermission()
        // Play builds update through the store; only GitHub builds self-check.
        if (BuildConfig.SELF_UPDATER) vm.checkForUpdates()
        vm.requestCalendarPermission = { calendarPermission.launch(Manifest.permission.READ_CALENDAR) }
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

    override fun onResume() {
        super.onResume()
        vm.refreshNow()
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
