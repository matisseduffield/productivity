package com.bento.calendar.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bento.calendar.data.Accents
import com.bento.calendar.data.AppData
import com.bento.calendar.data.AppGraph
import com.bento.calendar.data.Cats
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.NoteItem
import com.bento.calendar.data.Prefs
import com.bento.calendar.data.Recur
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.minsToHm
import com.bento.calendar.data.newId
import com.bento.calendar.data.toDate
import com.bento.calendar.data.toIso
import com.bento.calendar.data.toMins
import com.bento.calendar.reminders.ReminderScheduler
import com.bento.calendar.updates.UpdateManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.max
import kotlin.math.min

enum class Tab { Today, Calendar, Notes, Tasks }
enum class CalView { Month, Week, Day }

data class EventDraft(
    val id: String?,
    val title: String = "",
    val date: LocalDate,
    val start: String = "09:00",
    val end: String = "10:00",
    val cat: String = Cats.PERSONAL,
    val recur: String = Recur.NONE,
    val remind: Int? = 10,
    val loc: String = "",
)

data class TaskDraft(
    val id: String?,
    val title: String = "",
    val due: LocalDate? = null,
    val cat: String = "",
)

enum class PinMode { Set, Enter }

sealed interface PinThen {
    data class OpenNote(val id: String) : PinThen
    data class LockNote(val id: String) : PinThen
    data object None : PinThen
}

data class PinCtx(val mode: PinMode, val then: PinThen, val noteTitle: String)

/** Two-tap confirm keys ("Delete" -> "Sure?"). */
object Arm {
    const val NOTE = "note"
    const val EVENT = "ev"
    const val TASK = "tk"
    const val CLEAR = "clear"
    const val RESET = "reset"
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppGraph.repository(app)

    /** Null only for the first frames while the store loads. */
    val data: StateFlow<AppData?> = repo.data.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _now = MutableStateFlow(LocalDateTime.now())
    val now: StateFlow<LocalDateTime> = _now

    // ---- Transient UI state ----
    private var tabState by mutableStateOf(Tab.Today)
    val tab: Tab get() = tabState
    private var calViewState by mutableStateOf(CalView.Month)
    val calView: CalView get() = calViewState
    var selDate: LocalDate by mutableStateOf(LocalDate.now())
        private set
    var cursor: YearMonth by mutableStateOf(YearMonth.now())
        private set
    var searchOpen by mutableStateOf(false)
        private set
    private var queryState by mutableStateOf("")
    val query: String get() = queryState
    var settingsOpen by mutableStateOf(false)
        private set
    var fabOpen by mutableStateOf(false)
        private set
    var openNoteId by mutableStateOf<String?>(null)
        private set
    var unlocked by mutableStateOf(setOf<String>())
        private set
    var pinCtx by mutableStateOf<PinCtx?>(null)
        private set
    var pinBuf by mutableStateOf("")
        private set
    var pinErr by mutableStateOf(false)
        private set
    var evDraft by mutableStateOf<EventDraft?>(null)
        private set
    var tkDraft by mutableStateOf<TaskDraft?>(null)
        private set
    var doneOpen by mutableStateOf(false)
        private set
    var dismissed by mutableStateOf(setOf<String>())
        private set
    var armed by mutableStateOf(setOf<String>())
        private set

    private val armJobs = mutableMapOf<String, Job>()
    private var pinErrJob: Job? = null

    init {
        viewModelScope.launch {
            while (true) {
                _now.value = LocalDateTime.now()
                delay(15_000)
            }
        }
        viewModelScope.launch {
            val d = repo.data.first()
            ReminderScheduler.reschedule(getApplication(), d)
            lastScheduledEvents = d.events
        }
        // NOTE: the automatic update check is kicked off from MainActivity, not
        // here — the update state properties are declared below this init block
        // and would not be initialized yet.
    }

    /** Events snapshot the alarm chain was last armed for (main-thread only). */
    private var lastScheduledEvents: List<EventItem>? = null

    fun refreshNow() {
        _now.value = LocalDateTime.now()
    }

    private fun prefs(): Prefs = data.value?.prefs ?: Prefs()

    private fun today(): LocalDate = _now.value.toLocalDate()

    private fun mut(transform: (AppData) -> AppData) {
        viewModelScope.launch {
            val d = repo.update(transform)
            // Alarms only depend on events; note/task/pref edits (e.g. every
            // editor keystroke) must not trigger a 60-day alarm rescan.
            if (d.events != lastScheduledEvents) {
                ReminderScheduler.reschedule(getApplication(), d)
                lastScheduledEvents = d.events
            }
        }
    }

    // ---- Two-tap confirms ----
    fun isArmed(key: String) = key in armed

    private fun disarm(key: String) {
        armJobs.remove(key)?.cancel()
        armed = armed - key
    }

    private fun twoTap(key: String, action: () -> Unit) {
        if (key in armed) {
            disarm(key)
            action()
        } else {
            armJobs.remove(key)?.cancel()
            armed = armed + key
            armJobs[key] = viewModelScope.launch {
                delay(2500)
                armed = armed - key
            }
        }
    }

    // ---- Tabs & navigation ----
    fun setTab(t: Tab) {
        tabState = t
        searchOpen = false
    }

    fun openSettings() {
        settingsOpen = true
    }

    fun closeSheets() {
        settingsOpen = false
        fabOpen = false
        evDraft = null
        tkDraft = null
        pinCtx = null
        disarm(Arm.EVENT)
        disarm(Arm.TASK)
    }

    /** System back: dismiss the topmost layer, prototype z-order. */
    fun backPress() {
        when {
            pinCtx != null -> pinCancel()
            evDraft != null -> {
                evDraft = null
                disarm(Arm.EVENT)
            }
            tkDraft != null -> {
                tkDraft = null
                disarm(Arm.TASK)
            }
            fabOpen -> fabOpen = false
            searchOpen -> closeSearch()
            openNoteId != null -> closeNote()
            settingsOpen -> settingsOpen = false
        }
    }

    fun hasOverlay(): Boolean =
        pinCtx != null || evDraft != null || tkDraft != null || fabOpen ||
            searchOpen || openNoteId != null || settingsOpen

    // ---- Calendar ----

    /**
     * Direction of the last period navigation for the slide animation:
     * +1 = forward (next), -1 = backward (prev), 0 = jump (no slide, fade).
     */
    var calNavDir by mutableStateOf(0)
        private set

    /**
     * Increments on every real period/view navigation (not on day-selection
     * taps) — the scroll-to-now effect keys on this so selecting a day never
     * hijacks the scroll position.
     */
    var calNavTick by mutableStateOf(0)
        private set

    fun setCalView(v: CalView) {
        calNavDir = 0
        calNavTick++
        calViewState = v
    }

    fun calPrev() {
        calNavDir = -1
        calNavTick++
        when (calView) {
            CalView.Month -> cursor = cursor.minusMonths(1)
            CalView.Week -> selDate = selDate.minusDays(7)
            CalView.Day -> selDate = selDate.minusDays(1)
        }
    }

    fun calNext() {
        calNavDir = 1
        calNavTick++
        when (calView) {
            CalView.Month -> cursor = cursor.plusMonths(1)
            CalView.Week -> selDate = selDate.plusDays(7)
            CalView.Day -> selDate = selDate.plusDays(1)
        }
    }

    fun goToday() {
        calNavDir = 0
        calNavTick++
        selDate = today()
        cursor = YearMonth.from(today())
    }

    /** Month cell: tap selects, tapping the selected day opens Day view. */
    fun tapMonthCell(date: LocalDate) {
        calNavDir = 0
        if (selDate == date) {
            // Second tap opens Day view — that's a navigation.
            calNavTick++
            calViewState = CalView.Day
        } else {
            selDate = date
            cursor = YearMonth.from(date)
        }
    }

    /** Week header day tap. */
    fun selectDate(date: LocalDate) {
        calNavDir = 0
        selDate = date
    }

    /** Header-title date picker: jump straight to a date in the current view. */
    fun jumpToDate(date: LocalDate) {
        calNavDir = 0
        calNavTick++
        selDate = date
        cursor = YearMonth.from(date)
    }

    /** Today-tab week strip: jump to Day view of that date. */
    fun weekStripTap(date: LocalDate) {
        calNavDir = 0
        calNavTick++
        tabState = Tab.Calendar
        calViewState = CalView.Day
        selDate = date
        cursor = YearMonth.from(date)
    }

    // ---- Create menu ----
    fun openFab() {
        fabOpen = true
    }

    // ---- Events ----
    fun openEvent(occurrence: EventItem) {
        val base = data.value?.events?.firstOrNull { it.id == occurrence.id } ?: occurrence
        evDraft = EventDraft(
            id = base.id,
            title = base.title,
            date = base.date.toDate(),
            start = base.start,
            end = base.end,
            cat = base.cat,
            recur = base.recur,
            remind = base.remind,
            loc = base.loc,
        )
        disarm(Arm.EVENT)
        searchOpen = false
        fabOpen = false
    }

    fun newEvent() {
        val pf = prefs()
        val date = if (tab == Tab.Calendar) selDate else today()
        val endM = min(9 * 60 + pf.durDef, 1439)
        evDraft = EventDraft(
            id = null,
            date = date,
            start = "09:00",
            end = minsToHm(endM),
            cat = Cats.PERSONAL,
            recur = Recur.NONE,
            remind = pf.remindDef,
        )
        disarm(Arm.EVENT)
        fabOpen = false
    }

    /** Tap on an empty week/day grid slot. */
    fun newEventAt(date: LocalDate, startMin: Int) {
        val pf = prefs()
        val s = max(0, min(startMin, 1380))
        val e = min(s + pf.durDef, 1439)
        evDraft = EventDraft(
            id = null,
            date = date,
            start = minsToHm(s),
            end = minsToHm(e),
            cat = Cats.PERSONAL,
            recur = Recur.NONE,
            remind = pf.remindDef,
        )
        disarm(Arm.EVENT)
        fabOpen = false
    }

    fun updateEventDraft(transform: (EventDraft) -> EventDraft) {
        evDraft = evDraft?.let(transform)
    }

    fun saveEvent() {
        val d = evDraft ?: return
        // A start of 23:59 leaves no room for any duration — clamp to 23:58 so
        // the persisted event can never be zero-length.
        val startM = d.start.toMins().coerceAtMost(1438)
        var endM = d.end.toMins()
        if (endM <= startM) {
            endM = min(startM + 60, 1439)
        }
        val rec = EventItem(
            id = d.id ?: newId(),
            title = d.title.trim().ifEmpty { "Untitled" },
            date = d.date.toIso(),
            start = minsToHm(startM),
            end = minsToHm(endM),
            cat = d.cat,
            recur = d.recur,
            remind = d.remind,
            loc = d.loc.trim(),
        )
        mut { x ->
            val i = x.events.indexOfFirst { it.id == rec.id }
            if (i >= 0) {
                x.copy(events = x.events.toMutableList().apply { set(i, rec) })
            } else {
                x.copy(events = x.events + rec)
            }
        }
        evDraft = null
    }

    fun deleteEvent() {
        val id = evDraft?.id ?: return
        twoTap(Arm.EVENT) {
            mut { x -> x.copy(events = x.events.filter { it.id != id }) }
            evDraft = null
        }
    }

    /**
     * Day-view drag-to-reschedule: shift an event to a new start, preserving
     * its duration. Recurring events shift the whole series' time, consistent
     * with the app's edit-the-series semantics.
     */
    fun moveEvent(id: String, newStartMin: Int) {
        val e = data.value?.events?.firstOrNull { it.id == id } ?: return
        val dur = (e.end.toMins() - e.start.toMins()).coerceAtLeast(1)
        val s = newStartMin.coerceIn(0, 1439 - dur)
        if (s == e.start.toMins()) return
        mut { x ->
            x.copy(events = x.events.map {
                if (it.id == id) it.copy(start = minsToHm(s), end = minsToHm(s + dur)) else it
            })
        }
    }

    // ---- Tasks ----
    fun toggleTask(id: String) {
        mut { x ->
            x.copy(tasks = x.tasks.map { if (it.id == id) it.copy(done = !it.done) else it })
        }
    }

    fun openTask(t: TaskItem) {
        tkDraft = TaskDraft(t.id, t.title, t.due?.toDate(), t.cat)
        disarm(Arm.TASK)
        searchOpen = false
        fabOpen = false
        tabState = Tab.Tasks
    }

    fun newTask() {
        tkDraft = TaskDraft(id = null)
        disarm(Arm.TASK)
        fabOpen = false
    }

    fun updateTaskDraft(transform: (TaskDraft) -> TaskDraft) {
        tkDraft = tkDraft?.let(transform)
    }

    fun saveTask() {
        val d = tkDraft ?: return
        mut { x ->
            val i = x.tasks.indexOfFirst { it.id == d.id }
            val rec = TaskItem(
                id = d.id ?: newId(),
                title = d.title.trim().ifEmpty { "Untitled task" },
                done = if (i >= 0) x.tasks[i].done else false,
                due = d.due?.toIso(),
                cat = d.cat,
            )
            if (i >= 0) {
                x.copy(tasks = x.tasks.toMutableList().apply { set(i, rec) })
            } else {
                x.copy(tasks = x.tasks + rec)
            }
        }
        tkDraft = null
    }

    fun deleteTask() {
        val id = tkDraft?.id ?: return
        twoTap(Arm.TASK) {
            mut { x -> x.copy(tasks = x.tasks.filter { it.id != id }) }
            tkDraft = null
        }
    }

    fun toggleDoneOpen() {
        doneOpen = !doneOpen
    }

    fun clearCompleted() {
        if (data.value?.tasks?.none { it.done } != false) return
        twoTap(Arm.CLEAR) {
            dismissUndo()
            mut { x -> x.copy(tasks = x.tasks.filter { !it.done }) }
        }
    }

    // ---- Notes ----
    fun openNote(id: String) {
        val n = data.value?.notes?.firstOrNull { it.id == id } ?: return
        if (n.locked && id !in unlocked) {
            pinCtx = PinCtx(
                mode = if (data.value?.pin != null) PinMode.Enter else PinMode.Set,
                then = PinThen.OpenNote(id),
                noteTitle = n.title.ifEmpty { "Untitled" },
            )
            pinBuf = ""
            pinErr = false
            searchOpen = false
        } else {
            openNoteId = id
            disarm(Arm.NOTE)
            searchOpen = false
        }
    }

    private var creatingNote = false

    fun newNote() {
        if (creatingNote) return
        creatingNote = true
        val id = newId()
        viewModelScope.launch {
            try {
                repo.update { x ->
                    x.copy(notes = x.notes + NoteItem(id = id, updated = System.currentTimeMillis()))
                }
                // Open only once the note exists in the store, otherwise the
                // editor overlay finds nothing and silently closes.
                openNoteId = id
                tabState = Tab.Notes
                fabOpen = false
                disarm(Arm.NOTE)
            } finally {
                creatingNote = false
            }
        }
    }

    fun setNoteTitle(v: String) = editNote { it.copy(title = v) }

    fun setNoteBody(v: String) = editNote { it.copy(body = v) }

    private fun editNote(transform: (NoteItem) -> NoteItem) {
        val id = openNoteId ?: return
        mut { x ->
            x.copy(notes = x.notes.map {
                if (it.id == id) transform(it).copy(updated = System.currentTimeMillis()) else it
            })
        }
    }

    fun toggleNotePin() {
        val id = openNoteId ?: return
        mut { x ->
            x.copy(notes = x.notes.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
        }
    }

    fun toggleNoteLock() {
        val id = openNoteId ?: return
        val n = data.value?.notes?.firstOrNull { it.id == id } ?: return
        if (!n.locked && data.value?.pin == null) {
            pinCtx = PinCtx(PinMode.Set, PinThen.LockNote(id), n.title.ifEmpty { "Untitled" })
            pinBuf = ""
            pinErr = false
        } else {
            mut { x ->
                x.copy(notes = x.notes.map { if (it.id == id) it.copy(locked = !it.locked) else it })
            }
        }
    }

    fun deleteNote() {
        val id = openNoteId ?: return
        twoTap(Arm.NOTE) {
            mut { x -> x.copy(notes = x.notes.filter { it.id != id }) }
            openNoteId = null
        }
    }

    /** Closing an empty note discards it. */
    fun closeNote() {
        val id = openNoteId
        val n = data.value?.notes?.firstOrNull { it.id == id }
        if (n != null && n.title.isBlank() && n.body.isBlank()) {
            mut { x -> x.copy(notes = x.notes.filter { it.id != n.id }) }
        }
        openNoteId = null
        disarm(Arm.NOTE)
    }

    // ---- PIN ----
    fun pinPress(key: String) {
        val ctx = pinCtx ?: return
        if (key == "back") {
            pinBuf = pinBuf.dropLast(1)
            return
        }
        if (pinBuf.length >= 4) return
        val buf = pinBuf + key
        if (buf.length < 4) {
            pinBuf = buf
            pinErr = false
            return
        }
        when {
            ctx.mode == PinMode.Set -> {
                mut { x ->
                    var d = x.copy(pin = buf)
                    val lockId = (ctx.then as? PinThen.LockNote)?.id
                    if (lockId != null) {
                        d = d.copy(notes = d.notes.map { if (it.id == lockId) it.copy(locked = true) else it })
                    }
                    d
                }
                finishPin(ctx)
            }
            buf == data.value?.pin -> finishPin(ctx)
            else -> {
                pinBuf = ""
                pinErr = true
                pinErrJob?.cancel()
                pinErrJob = viewModelScope.launch {
                    delay(600)
                    pinErr = false
                }
            }
        }
    }

    private fun finishPin(ctx: PinCtx) {
        val noteId = when (val t = ctx.then) {
            is PinThen.OpenNote -> t.id
            is PinThen.LockNote -> t.id
            PinThen.None -> null
        }
        if (noteId != null) unlocked = unlocked + noteId
        if (ctx.then is PinThen.OpenNote) openNoteId = noteId
        pinBuf = ""
        pinCtx = null
    }

    fun pinCancel() {
        pinCtx = null
        pinBuf = ""
        pinErr = false
    }

    /** Settings: set or change the PIN. */
    fun startSetPin() {
        pinCtx = PinCtx(PinMode.Set, PinThen.None, "")
        pinBuf = ""
        pinErr = false
    }

    fun removePin() {
        mut { it.copy(pin = null) }
        unlocked = emptySet()
    }

    // ---- Preferences ----
    private fun mutPrefs(transform: (Prefs) -> Prefs) {
        mut { it.copy(prefs = transform(it.prefs)) }
    }

    fun setTheme(theme: String) = mutPrefs { it.copy(theme = theme) }
    fun setAccent(hex: String) = mutPrefs { it.copy(accent = hex) }
    fun toggle24h() = mutPrefs { it.copy(use24h = !it.use24h) }
    fun toggleMonday() = mutPrefs { it.copy(monday = !it.monday) }
    fun setRemindDef(v: Int?) = mutPrefs { it.copy(remindDef = v) }
    fun setDurDef(v: Int) = mutPrefs { it.copy(durDef = v) }

    // ---- Data ----
    fun resetApp() {
        twoTap(Arm.RESET) {
            // "Erase all events, tasks and notes" — keep theme, prefs and PIN,
            // which the label doesn't claim to touch.
            dismissUndo()
            mut { it.copy(events = emptyList(), tasks = emptyList(), notes = emptyList()) }
            unlocked = emptySet()
            openNoteId = null
            doneOpen = false
            dismissed = emptySet()
        }
    }

    // ---- Search ----
    fun openSearch() {
        searchOpen = true
        queryState = ""
    }

    fun closeSearch() {
        searchOpen = false
    }

    fun setQuery(q: String) {
        queryState = q
    }

    // ---- Reminder banner ----
    fun dismissReminder(key: String) {
        dismissed = dismissed + key
    }

    // ---- Swipe actions & undo ----

    data class UndoState(val label: String)

    /** Non-null while undoable swipe actions can be reverted (~4s window). */
    var undoState by mutableStateOf<UndoState?>(null)
        private set
    private var undoJob: Job? = null

    /** Successive swipes within the window batch into one undo. */
    private val pendingRestores = mutableListOf<(AppData) -> AppData>()
    private var undoTaskCount = 0
    private var undoNoteCount = 0
    private var overlayWatcherStarted = false

    /**
     * Pause the undo countdown while any overlay/sheet covers the banner, so
     * the window can't expire unreachable behind a scrim. Started lazily from
     * offerUndo — by then every state property is initialized.
     */
    private fun ensureOverlayWatcher() {
        if (overlayWatcherStarted) return
        overlayWatcherStarted = true
        viewModelScope.launch {
            snapshotFlow { hasOverlay() }.collect { covered ->
                if (covered) {
                    undoJob?.cancel()
                } else if (undoState != null) {
                    undoJob?.cancel()
                    undoJob = viewModelScope.launch {
                        delay(4000)
                        clearUndo()
                    }
                }
            }
        }
    }

    private fun offerUndo(kind: String, restore: (AppData) -> AppData) {
        ensureOverlayWatcher()
        pendingRestores += restore
        if (kind == "task") undoTaskCount++ else undoNoteCount++
        val label = when {
            undoTaskCount > 0 && undoNoteCount > 0 -> "${undoTaskCount + undoNoteCount} items deleted"
            undoTaskCount > 1 -> "$undoTaskCount tasks deleted"
            undoNoteCount > 1 -> "$undoNoteCount notes deleted"
            undoTaskCount == 1 -> "Task deleted"
            else -> "Note deleted"
        }
        undoState = UndoState(label)
        undoJob?.cancel()
        undoJob = viewModelScope.launch {
            delay(4000)
            clearUndo()
        }
    }

    private fun clearUndo() {
        undoState = null
        pendingRestores.clear()
        undoTaskCount = 0
        undoNoteCount = 0
    }

    fun performUndo() {
        if (undoState == null) return
        val restores = pendingRestores.toList()
        undoJob?.cancel()
        clearUndo()
        // Reverse order: each restore's captured index is valid in the list
        // state that existed just after the LATER deletions were captured.
        mut { d -> restores.foldRight(d) { r, acc -> r(acc) } }
    }

    fun dismissUndo() {
        undoJob?.cancel()
        clearUndo()
    }

    /** Swipe-left on a task row: delete with undo, restored at its old spot. */
    fun deleteTaskBySwipe(t: TaskItem) {
        val idx = data.value?.tasks?.indexOfFirst { it.id == t.id } ?: -1
        mut { x -> x.copy(tasks = x.tasks.filter { it.id != t.id }) }
        offerUndo("task") { x ->
            if (x.tasks.any { it.id == t.id }) {
                x
            } else {
                x.copy(
                    tasks = if (idx < 0) {
                        x.tasks + t
                    } else {
                        x.tasks.toMutableList().apply { add(idx.coerceAtMost(size), t) }
                    },
                )
            }
        }
    }

    /** Swipe-left on an (unlocked) note row: delete with undo. */
    fun deleteNoteBySwipe(n: NoteItem) {
        val idx = data.value?.notes?.indexOfFirst { it.id == n.id } ?: -1
        mut { x -> x.copy(notes = x.notes.filter { it.id != n.id }) }
        offerUndo("note") { x ->
            if (x.notes.any { it.id == n.id }) {
                x
            } else {
                x.copy(
                    notes = if (idx < 0) {
                        x.notes + n
                    } else {
                        x.notes.toMutableList().apply { add(idx.coerceAtMost(size), n) }
                    },
                )
            }
        }
    }

    /** Swipe-right on a note row: pin/unpin without opening it. */
    fun toggleNotePinById(id: String) {
        mut { x ->
            x.copy(notes = x.notes.map { if (it.id == id) it.copy(pinned = !it.pinned) else it })
        }
    }

    // ---- App updates ----
    var updateInfo by mutableStateOf<UpdateManager.UpdateInfo?>(null)
        private set

    enum class UpdatePhase { Idle, Downloading, AwaitingConfirm }

    /** Download/install lifecycle beyond "an update exists". */
    var updatePhase by mutableStateOf(UpdatePhase.Idle)
        private set

    /** 0..1 while [UpdatePhase.Downloading]. */
    var updateProgress by mutableStateOf(0f)
        private set
    var updateDismissed by mutableStateOf(false)
        private set
    var updateChecking by mutableStateOf(false)
        private set

    /** True once a manual check finished and found the app up to date. */
    var updateUpToDate by mutableStateOf(false)
        private set

    /** Last check/install error message, or null. Shown in the App settings row. */
    var updateError by mutableStateOf<String?>(null)
        private set

    /** Guards the automatic launch check so a dismissed banner stays dismissed
     *  across activity recreation (rotation, theme change). */
    private var autoCheckDone = false

    init {
        viewModelScope.launch {
            UpdateManager.installError.collect { msg ->
                if (msg != null) {
                    updateError = msg
                    updatePhase = UpdatePhase.Idle
                }
            }
        }
    }

    fun checkForUpdates(manual: Boolean = false) {
        if (!manual && autoCheckDone) return
        if (updateChecking) return
        updateChecking = true
        if (manual) {
            updateUpToDate = false
            updateError = null
        }
        viewModelScope.launch {
            try {
                when (val result = UpdateManager.check()) {
                    is UpdateManager.CheckResult.Available -> {
                        updateInfo = result.info
                        if (manual) updateDismissed = false
                    }
                    is UpdateManager.CheckResult.UpToDate -> {
                        updateInfo = null
                        if (manual) updateUpToDate = true
                    }
                    is UpdateManager.CheckResult.Failed -> {
                        if (manual) updateError = result.message
                    }
                }
            } finally {
                updateChecking = false
                if (!manual) autoCheckDone = true
            }
        }
    }

    fun downloadAndInstallUpdate() {
        val info = updateInfo ?: return
        if (updatePhase != UpdatePhase.Idle) return
        updateError = null
        updateProgress = 0f
        updatePhase = UpdatePhase.Downloading
        viewModelScope.launch {
            try {
                val apk = UpdateManager.download(getApplication(), info) { p ->
                    updateProgress = p
                }
                // Session committed; the system confirm dialog / notification
                // takes over from here. Stay in AwaitingConfirm so the banner
                // doesn't invite a duplicate download.
                updatePhase = UpdatePhase.AwaitingConfirm
                UpdateManager.install(getApplication(), apk)
            } catch (e: Exception) {
                updateError = e.message ?: "Update failed"
                updatePhase = UpdatePhase.Idle
            }
        }
    }

    fun dismissUpdate() {
        updateDismissed = true
    }

    // ---- Backup: export / import ----

    private val backupJson = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** Full store as pretty JSON, or null while still loading. */
    fun exportJson(): String? = data.value?.let {
        // Strip the PIN so the plaintext backup never leaks it; import
        // tolerates a null pin (locked notes fall back to the Set-PIN flow).
        val obj = backupJson
            .encodeToJsonElement(AppData.serializer(), it.copy(pin = null)) as JsonObject
        // The format marker is added on the raw object rather than as a
        // defaulted @Serializable field: a defaulted field would be silently
        // filled in when decoding foreign JSON (defeating the import check),
        // while a non-defaulted one would reject all existing legacy backups.
        val marked = JsonObject(obj + (BACKUP_FORMAT_KEY to JsonPrimitive(BACKUP_FORMAT)))
        backupJson.encodeToString(JsonObject.serializer(), marked)
    }

    /**
     * Replace the entire store with a backup file's content. Returns false if
     * the text isn't a valid backup. Session state is reset like a fresh open.
     */
    fun importFromJson(text: String): Boolean {
        val imported = try {
            // Reject arbitrary JSON: AppData's fields all have defaults and
            // backupJson ignores unknown keys, so "{}" or any unrelated file
            // (package.json...) would decode to an empty store and wipe
            // everything. Require the format marker (new exports) or, for
            // legacy backups — which always contain every top-level key
            // because exportJson uses encodeDefaults — at least one known key.
            val obj = backupJson.parseToJsonElement(text) as? JsonObject ?: return false
            val hasMarker =
                (obj[BACKUP_FORMAT_KEY] as? JsonPrimitive)?.content == BACKUP_FORMAT
            val knownKeys = listOf("events", "tasks", "notes", "prefs", "pin")
            if (!hasMarker && knownKeys.none { obj.containsKey(it) }) return false
            val decoded = backupJson.decodeFromJsonElement(AppData.serializer(), obj)
            // Semantic validation: date/start/end/due are plain strings, and a
            // value like "2026-7-2" or "9:00" would persist fine but then
            // crash every render (occursOn -> LocalDate.parse) with no
            // recovery short of clearing app data.
            decoded.events.forEach { ev ->
                ev.date.toDate()
                require(ev.end.toMins() > ev.start.toMins())
            }
            decoded.tasks.forEach { it.due?.toDate() }
            // A bad accent crashes BentoTheme at the root; normalize instead
            // of rejecting. Clamp durDef/remindDef so newEvent/saveEvent can't
            // generate unparseable draft times.
            val accent = decoded.prefs.accent
                .takeIf { it.removePrefix("#").toLongOrNull(16) != null }
                ?: Accents.DEFAULT
            decoded.copy(
                prefs = decoded.prefs.copy(
                    accent = accent,
                    durDef = decoded.prefs.durDef.coerceAtLeast(1),
                    remindDef = decoded.prefs.remindDef?.coerceAtLeast(0),
                ),
            )
        } catch (_: Exception) {
            return false
        }
        dismissUndo()
        mut { imported }
        unlocked = emptySet()
        openNoteId = null
        doneOpen = false
        dismissed = emptySet()
        closeSheets()
        return true
    }
}

/** Marker written into exports and accepted (or legacy keys) on import. */
private const val BACKUP_FORMAT_KEY = "format"
private const val BACKUP_FORMAT = "bento.calendar.v1"
