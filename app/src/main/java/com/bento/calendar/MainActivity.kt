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
        vm.checkForUpdates()
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
        if (action != ACTION_NEW_EVENT && action != ACTION_NEW_TASK && action != ACTION_NEW_NOTE) return
        // On a cold start the DataStore read hasn't landed yet (vm.data is null
        // for the first frames); building the draft then would fall back to
        // Prefs() defaults instead of the user's saved duration/reminder prefs.
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
