package com.bento.calendar.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bento.calendar.data.Accents
import com.bento.calendar.data.AppData
import com.bento.calendar.data.AppGraph
import com.bento.calendar.data.BlockSource
import com.bento.calendar.data.BlockState
import com.bento.calendar.data.BusyInterval
import com.bento.calendar.data.Category
import com.bento.calendar.data.Cats
import com.bento.calendar.data.DeviceCal
import com.bento.calendar.data.DeviceCalendars
import com.bento.calendar.data.DeviceEvent
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.FocusSession
import com.bento.calendar.data.activeFocus
import com.bento.calendar.data.focusElapsedSeconds
import com.bento.calendar.data.compactFocusHistory
import com.bento.calendar.data.IcsImportResult
import com.bento.calendar.data.completeTaskWithBlocks
import com.bento.calendar.data.exportEventsToIcs
import com.bento.calendar.data.importEventsFromIcs
import com.bento.calendar.data.NoteItem
import com.bento.calendar.data.Prefs
import com.bento.calendar.data.Priority
import com.bento.calendar.data.PlanResult
import com.bento.calendar.data.Recur
import com.bento.calendar.data.SubTask
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.TaskBlock
import com.bento.calendar.data.DayPlan
import com.bento.calendar.data.Trash
import com.bento.calendar.data.TrashEntry
import com.bento.calendar.data.parseQuickAdd
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.data.planningCandidates
import com.bento.calendar.data.suggestDayPlan
import com.bento.calendar.data.minsToHm
import com.bento.calendar.data.newId
import com.bento.calendar.data.toDate
import com.bento.calendar.data.toIso
import com.bento.calendar.data.toMins
import com.bento.calendar.reminders.ReminderScheduler
import com.bento.calendar.focus.FocusTimer
import com.bento.calendar.updates.UpdateManager
import com.bento.calendar.widget.WidgetSync
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
enum class CalView { Month, Week, Day, Agenda }

private fun CalView.prefValue(): String = name.lowercase()
private fun calViewFromPref(value: String): CalView = when (value) {
    "week" -> CalView.Week
    "day" -> CalView.Day
    "agenda" -> CalView.Agenda
    else -> CalView.Month
}

/** Scope of an edit/delete on a recurring event. */
enum class EditScope { Single, Series }

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
    val allDay: Boolean = false,
    /** Last day of a multi-day event; null = single day. Excludes recurrence. */
    val endDate: LocalDate? = null,
    /** The tapped instance date when editing a recurring event. */
    val occurrenceDate: LocalDate? = null,
    /** "This event only" vs "whole series" for recurring edits/deletes. */
    val scope: EditScope = EditScope.Single,
)

data class TaskDraft(
    val id: String?,
    val title: String = "",
    val due: LocalDate? = null,
    val cat: String = "",
    val recur: String = Recur.NONE,
    val priority: Int = Priority.NONE,
    val subs: List<SubTask> = emptyList(),
    /** Reminder time ("HH:MM") on the due date; needs [due] to be set. */
    val remindAt: String? = null,
    val estimateMin: Int? = null,
)

enum class PinMode { Set, Enter }

sealed interface PinThen {
    data class OpenNote(val id: String) : PinThen
    data class LockNote(val id: String) : PinThen

    /** Verified Enter → roll straight into a Set sheet for the new PIN. */
    data object ChangePin : PinThen

    /** Verified Enter → drop the PIN from the store. */
    data object RemovePin : PinThen
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
    const val TRASH = "trash"
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
    var changelogOpen by mutableStateOf(false)
        private set
    var insightsOpen by mutableStateOf(false)
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
    var planningOpen by mutableStateOf(false)
        private set
    var planningDate by mutableStateOf(LocalDate.now())
        private set
    var planningTaskIds by mutableStateOf(setOf<String>())
        private set
    var planningResult by mutableStateOf<PlanResult?>(null)
        private set
    private var planningReplaceBlockIds: Set<String> = emptySet()
    val planningReplacesSuggestions: Boolean get() = planningReplaceBlockIds.isNotEmpty()

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
            // Calendar mode is lightweight UI state, but restoring it makes
            // Agenda (and the existing Day/Week modes) a real home workflow
            // rather than snapping back to Month after every process restart.
            calViewState = calViewFromPref(d.prefs.lastCalView)
            ReminderScheduler.reschedule(getApplication(), d)
            lastScheduledEvents = d.events
            lastScheduledTasks = d.tasks
            lastScheduledTaskBlocks = d.taskBlocks
            lastBlockReminder = d.prefs.blockReminderMin
            // Seed the widget gate too: the widget already reflects the store
            // at launch, so the first mutation only pushes if it changed
            // something widget-relevant.
            lastPushedWidget = widgetSnapshot(d)
            lastFocusSessions = d.focusSessions
            FocusTimer.sync(getApplication(), d)
            // Trash retention: purge entries past the window once per launch.
            val cutoff = System.currentTimeMillis() - Trash.RETENTION_DAYS * 86_400_000L
            if (d.trash.any { it.deletedAt < cutoff }) {
                mut { x -> x.copy(trash = x.trash.filter { it.deletedAt >= cutoff }) }
            }
            if (compactFocusHistory(d, today()) != d) {
                mut { x -> compactFocusHistory(x, today()) }
            }
        }
        // NOTE: the automatic update check is kicked off from MainActivity, not
        // here — the update state properties are declared below this init block
        // and would not be initialized yet.
    }

    /** Events snapshot the alarm chain was last armed for (main-thread only). */
    private var lastScheduledEvents: List<EventItem>? = null

    /** Tasks snapshot ditto — task reminders arm the same chain. */
    private var lastScheduledTasks: List<TaskItem>? = null
    private var lastScheduledTaskBlocks: List<TaskBlock>? = null
    private var lastBlockReminder: Int? = null
    private var lastFocusSessions: List<FocusSession>? = null

    /**
     * The projection of the store the widget actually renders. [mut] only
     * pushes a widget update when this changes, so note-editor keystrokes and
     * unrelated pref edits don't trigger a full Glance re-render per write.
     */
    private data class WidgetSnapshot(
        val events: List<EventItem>,
        // Full list, not a count: the Tasks widget renders titles, priorities
        // and checklist progress, so any task change must push.
        val tasks: List<TaskItem>,
        val taskBlocks: List<TaskBlock>,
        val use24h: Boolean,
        val theme: String,
        val accent: String,
    )

    /** Snapshot the widget was last pushed for (main-thread only). */
    private var lastPushedWidget: WidgetSnapshot? = null

    private fun widgetSnapshot(d: AppData) = WidgetSnapshot(
        events = d.events,
        tasks = d.tasks,
        taskBlocks = d.taskBlocks,
        use24h = d.prefs.use24h,
        theme = d.prefs.theme,
        accent = d.prefs.accent,
    )

    fun refreshNow() {
        _now.value = LocalDateTime.now()
        // Resume also re-syncs the device-calendar overlay (edits made in the
        // Calendar app while Bento was backgrounded).
        if (data.value?.prefs?.deviceCalsEnabled == true) refreshDeviceCalendarData()
    }

    private fun prefs(): Prefs = data.value?.prefs ?: Prefs()

    private fun today(): LocalDate = _now.value.toLocalDate()

    private fun mut(transform: (AppData) -> AppData) {
        viewModelScope.launch {
            val d = repo.update(transform)
            // Alarms depend on events and tasks (task reminders); note/pref
            // edits (e.g. every editor keystroke) must not trigger a 60-day
            // alarm rescan.
            if (
                d.events != lastScheduledEvents || d.tasks != lastScheduledTasks ||
                d.taskBlocks != lastScheduledTaskBlocks || d.prefs.blockReminderMin != lastBlockReminder
            ) {
                ReminderScheduler.reschedule(getApplication(), d)
                lastScheduledEvents = d.events
                lastScheduledTasks = d.tasks
                lastScheduledTaskBlocks = d.taskBlocks
                lastBlockReminder = d.prefs.blockReminderMin
            }
            // Same idea for the widget: it only shows events, tasks and a
            // few prefs, so gate the push on that projection.
            val snap = widgetSnapshot(d)
            if (snap != lastPushedWidget) {
                WidgetSync.push(getApplication())
                lastPushedWidget = snap
            }
            if (d.focusSessions != lastFocusSessions) {
                FocusTimer.sync(getApplication(), d)
                lastFocusSessions = d.focusSessions
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

    fun openChangelog() {
        changelogOpen = true
    }

    fun closeChangelog() {
        changelogOpen = false
    }

    fun openInsights() {
        insightsOpen = true
    }

    fun closeInsights() {
        insightsOpen = false
    }

    fun closeSheets() {
        settingsOpen = false
        changelogOpen = false
        insightsOpen = false
        categoriesOpen = false
        fabOpen = false
        evDraft = null
        tkDraft = null
        pinCtx = null
        planningOpen = false
        planningResult = null
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
            trashOpen -> trashOpen = false
            categoriesOpen -> categoriesOpen = false
            changelogOpen -> changelogOpen = false
            insightsOpen -> insightsOpen = false
            settingsOpen -> settingsOpen = false
            planningOpen -> closePlanner()
        }
    }

    fun hasOverlay(): Boolean =
        pinCtx != null || evDraft != null || tkDraft != null || fabOpen ||
            searchOpen || openNoteId != null || settingsOpen || changelogOpen ||
            insightsOpen || categoriesOpen || trashOpen

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
        if (calViewState == v) return
        calNavDir = 0
        calNavTick++
        calViewState = v
        mutPrefs { it.copy(lastCalView = v.prefValue()) }
    }

    fun calPrev() {
        calNavDir = -1
        calNavTick++
        when (calView) {
            CalView.Month -> cursor = cursor.minusMonths(1)
            CalView.Week -> selDate = selDate.minusDays(7)
            CalView.Day -> selDate = selDate.minusDays(1)
            CalView.Agenda -> {
                selDate = selDate.minusDays(30)
                cursor = YearMonth.from(selDate)
            }
        }
        maybeRefreshDeviceWindow()
    }

    fun calNext() {
        calNavDir = 1
        calNavTick++
        when (calView) {
            CalView.Month -> cursor = cursor.plusMonths(1)
            CalView.Week -> selDate = selDate.plusDays(7)
            CalView.Day -> selDate = selDate.plusDays(1)
            CalView.Agenda -> {
                selDate = selDate.plusDays(30)
                cursor = YearMonth.from(selDate)
            }
        }
        maybeRefreshDeviceWindow()
    }

    fun goToday() {
        calNavDir = 0
        calNavTick++
        selDate = today()
        cursor = YearMonth.from(today())
        maybeRefreshDeviceWindow()
    }

    /** Month cell: tap selects, tapping the selected day opens Day view. */
    fun tapMonthCell(date: LocalDate) {
        calNavDir = 0
        if (selDate == date) {
            // Second tap opens Day view — that's a navigation.
            calNavTick++
            calViewState = CalView.Day
            mutPrefs { it.copy(lastCalView = CalView.Day.prefValue()) }
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
        maybeRefreshDeviceWindow()
    }

    /** Today-tab week strip: jump to Day view of that date. */
    fun weekStripTap(date: LocalDate) {
        calNavDir = 0
        calNavTick++
        tabState = Tab.Calendar
        calViewState = CalView.Day
        mutPrefs { it.copy(lastCalView = CalView.Day.prefValue()) }
        selDate = date
        cursor = YearMonth.from(date)
        maybeRefreshDeviceWindow()
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
            allDay = base.allDay,
            endDate = base.spanEnd(),
            // For recurring events remember which instance was tapped so
            // "this event only" edits/deletes know their target date.
            occurrenceDate = if (base.recur != Recur.NONE) occurrence.date.toDate() else null,
            scope = if (base.recur != Recur.NONE) EditScope.Single else EditScope.Series,
        )
        disarm(Arm.EVENT)
        searchOpen = false
        fabOpen = false
    }

    fun newEvent() {
        newEventDraft(if (tab == Tab.Calendar) selDate else today())
    }

    /** Blank draft with the prefs defaults, dated [date]. */
    private fun newEventDraft(date: LocalDate) {
        val pf = prefs()
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

    /**
     * Widget/shortcut quick-add. Unlike [newEvent] it (a) never clobbers a
     * draft the user has already put content into — the widget chip lands in
     * onNewIntent when the app is warm, possibly mid-edit — and (b) always
     * dates the draft today, bypassing the Calendar-tab selDate heuristic,
     * which can hold a stale selection from days ago.
     */
    fun quickAddEvent() {
        val d = evDraft
        if (d != null && (d.id != null || d.title.isNotBlank() || d.loc.isNotBlank())) return
        newEventDraft(today())
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
        // Multi-day: a later end day makes the times independent (start on
        // the first day, end on the last) — the end-after-start rule only
        // applies within a single day. Multi-day excludes recurrence.
        val spanEnd = d.endDate?.takeIf { it.isAfter(d.date) }
        val multiDay = spanEnd != null
        // A start of 23:59 leaves no room for any duration — clamp to 23:58 so
        // the persisted event can never be zero-length. All-day events span
        // the whole day regardless of whatever the time fields held.
        val startM = if (d.allDay) 0 else d.start.toMins().coerceAtMost(1438)
        var endM = if (d.allDay) 1439 else d.end.toMins()
        if (!multiDay && endM <= startM) {
            endM = min(startM + 60, 1439)
        }
        // The last-day segment renders 00:00→end; keep it non-zero-length.
        if (multiDay && endM < 1) endM = 1
        // "This event only" on a recurring series: carve the tapped date out
        // of the series and spawn a standalone event carrying the edits.
        val singleOcc = d.id != null && d.scope == EditScope.Single && d.occurrenceDate != null
        if (singleOcc) {
            val standalone = EventItem(
                id = newId(),
                title = d.title.trim().ifEmpty { "Untitled" },
                date = d.date.toIso(),
                start = minsToHm(startM),
                end = minsToHm(endM),
                allDay = d.allDay,
                cat = d.cat,
                recur = Recur.NONE,
                remind = d.remind,
                loc = d.loc.trim(),
            )
            val exDate = d.occurrenceDate!!.toIso()
            mut { x ->
                x.copy(
                    events = x.events.map {
                        if (it.id == d.id) it.copy(exDates = it.exDates + exDate) else it
                    } + standalone,
                )
            }
            evDraft = null
            return
        }
        // Belt and braces: the editor disables recurrence for multi-day
        // drafts, but never persist both — expansion assumes they exclude.
        val recur = if (multiDay) Recur.NONE else d.recur
        val rec = EventItem(
            id = d.id ?: newId(),
            title = d.title.trim().ifEmpty { "Untitled" },
            date = d.date.toIso(),
            start = minsToHm(startM),
            end = minsToHm(endM),
            allDay = d.allDay,
            cat = d.cat,
            recur = recur,
            remind = d.remind,
            loc = d.loc.trim(),
            endDate = spanEnd?.toIso(),
            // Series edits keep the carved-out dates unless recurrence is
            // switched off, which turns it back into a plain one-off.
            exDates = if (recur == Recur.NONE) {
                emptyList()
            } else {
                data.value?.events?.firstOrNull { it.id == d.id }?.exDates ?: emptyList()
            },
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
        val d = evDraft ?: return
        val id = d.id ?: return
        twoTap(Arm.EVENT) {
            if (d.scope == EditScope.Single && d.occurrenceDate != null) {
                // Skip just this occurrence; the series lives on. Not a real
                // deletion, so nothing goes to the trash.
                val exDate = d.occurrenceDate.toIso()
                mut { x ->
                    x.copy(events = x.events.map {
                        if (it.id == id) it.copy(exDates = it.exDates + exDate) else it
                    })
                }
            } else {
                mut { x ->
                    val victim = x.events.firstOrNull { it.id == id }
                    val next = x.copy(events = x.events.filter { it.id != id })
                    if (victim != null) next.withTrashed(trashEntry(e = victim)) else next
                }
            }
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
        // Multi-day: start and end live on different days, so "shift by
        // duration" is meaningless — reschedule through the editor instead.
        if (e.spanEnd() != null) return
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
    /** Plain tasks toggle done; repeating tasks advance to the next due date. */
    fun toggleTask(id: String) {
        mut { completeTaskWithBlocks(it, id, today()) }
    }

    /**
     * [switchTab] navigates to the Tasks tab under the sheet — right for
     * search results ("take me to it"), wrong for the calendar's task rows,
     * where dismissing the editor should land back on the calendar.
     */
    fun openTask(t: TaskItem, switchTab: Boolean = true) {
        tkDraft = TaskDraft(
            t.id, t.title, t.due?.toDate(), t.cat, t.recur, t.priority,
            t.subs, t.remindAt, t.estimateMin,
        )
        disarm(Arm.TASK)
        searchOpen = false
        fabOpen = false
        if (switchTab) tabState = Tab.Tasks
    }

    fun newTask() {
        tkDraft = TaskDraft(id = null)
        disarm(Arm.TASK)
        fabOpen = false
    }

    /** Widget/shortcut quick-add: like [quickAddEvent], never clobbers a task
     *  draft the user has already put content into. */
    fun quickAddTask() {
        val d = tkDraft
        if (d != null && (
                d.id != null || d.title.isNotBlank() || d.due != null ||
                    d.cat.isNotBlank() || d.priority != Priority.NONE || d.subs.isNotEmpty() ||
                    d.estimateMin != null
                )
        ) {
            return
        }
        newTask()
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
                recur = d.recur,
                priority = d.priority,
                subs = d.subs.map { it.copy(title = it.title.trim()) }.filter { it.title.isNotEmpty() },
                // A reminder is a time on the due date — meaningless without one.
                remindAt = if (d.due != null) d.remindAt else null,
                estimateMin = d.estimateMin?.coerceIn(15, 24 * 60),
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
            mut { x ->
                val victim = x.tasks.firstOrNull { it.id == id }
                val blocks = x.taskBlocks.filter { it.taskId == id && it.state == BlockState.PLANNED }
                val next = x.copy(
                    tasks = x.tasks.filter { it.id != id },
                    taskBlocks = x.taskBlocks.filterNot { it.taskId == id && it.state == BlockState.PLANNED },
                )
                if (victim != null) next.withTrashed(trashEntry(t = victim, blocks = blocks)) else next
            }
            tkDraft = null
        }
    }

    /** Check a checklist step off directly from a task row (not the editor). */
    fun toggleSub(taskId: String, subId: String) {
        mut { x ->
            x.copy(tasks = x.tasks.map { t ->
                if (t.id != taskId) t
                else t.copy(subs = t.subs.map { s ->
                    if (s.id == subId) s.copy(done = !s.done) else s
                })
            })
        }
    }

    // ---- Daily planning & internal task blocks ----

    fun openPlanner(date: LocalDate = today()) {
        val d = data.value ?: return
        planningDate = date
        val unfinished = d.taskBlocks
            .filter {
                it.state == BlockState.PLANNED && (
                    it.date < date.toIso() ||
                        (it.date == date.toIso() && it.source == BlockSource.SUGGESTED)
                    )
            }
            .mapTo(mutableSetOf()) { it.taskId }
        planningTaskIds = planningCandidates(d.tasks, date, unfinished).mapTo(linkedSetOf()) { it.id }
        planningOpen = true
        rebuildPlanPreview()
    }

    fun closePlanner() {
        planningOpen = false
        planningResult = null
        planningReplaceBlockIds = emptySet()
    }

    fun togglePlanningTask(id: String) {
        planningTaskIds = if (id in planningTaskIds) planningTaskIds - id else planningTaskIds + id
        rebuildPlanPreview()
    }

    fun rebuildPlanPreview() {
        val d = data.value ?: return
        val date = planningDate
        val nowDateTime = _now.value
        val notBefore = if (date == nowDateTime.toLocalDate()) {
            ((nowDateTime.hour * 60 + nowDateTime.minute + 14) / 15 * 15).coerceAtMost(1439)
        } else null
        planningReplaceBlockIds = d.taskBlocks
            .filter { block ->
                block.date == date.toIso() && block.state == BlockState.PLANNED &&
                    block.source == BlockSource.SUGGESTED &&
                    (notBefore == null || block.startMin >= notBefore)
            }
            .mapTo(mutableSetOf()) { it.id }
        val tasks = d.tasks.filter { it.id in planningTaskIds && !it.done }
        val timedEvents = occurrencesOn(d.events, date)
            .filterNot { it.allDay }
            .map { BusyInterval(it.start.toMins(), it.end.toMins()) }
        val deviceBusy = deviceEvents[date.toIso()].orEmpty()
            .filterNot { it.allDay }
            .map { BusyInterval(it.start.toMins(), it.end.toMins()) }
        val hours = d.prefs.workHours.firstOrNull { it.day == date.dayOfWeek.value }
            ?: com.bento.calendar.data.WorkHours.defaults().first { it.day == date.dayOfWeek.value }
        planningResult = suggestDayPlan(
            date = date,
            tasks = tasks,
            existingBlocks = d.taskBlocks.filterNot { it.id in planningReplaceBlockIds },
            calendarBusy = timedEvents + deviceBusy,
            workHours = hours,
            defaultEstimateMin = d.prefs.defaultTaskEstimateMin,
            notBeforeMin = notBefore,
        )
    }

    fun confirmPlan() {
        val result = planningResult ?: return
        val date = planningDate
        val now = System.currentTimeMillis()
        mut { d ->
            val tasksById = d.tasks.associateBy { it.id }
            val blocks = result.suggestions.map { suggestion ->
                val task = tasksById[suggestion.taskId]
                TaskBlock(
                    id = newId(),
                    taskId = suggestion.taskId,
                    occurrenceKey = task?.takeIf { it.recur != Recur.NONE }?.due,
                    date = suggestion.date.toIso(),
                    startMin = suggestion.startMin,
                    durationMin = suggestion.durationMin,
                    source = BlockSource.SUGGESTED,
                    createdAt = now,
                    updatedAt = now,
                )
            }
            val hours = d.prefs.workHours.firstOrNull { it.day == date.dayOfWeek.value }
                ?: com.bento.calendar.data.WorkHours.defaults().first { it.day == date.dayOfWeek.value }
            val plan = DayPlan(
                date = date.toIso(),
                workStartMin = hours.start.toMins(),
                workEndMin = hours.end.toMins(),
                plannedAt = now,
            )
            d.copy(
                taskBlocks = d.taskBlocks.filterNot { existing ->
                    existing.id in planningReplaceBlockIds &&
                        existing.date == date.toIso() &&
                        existing.state == BlockState.PLANNED &&
                        existing.source == BlockSource.SUGGESTED
                } + blocks,
                dayPlans = d.dayPlans.filterNot { it.date == plan.date } + plan,
            )
        }
        closePlanner()
    }

    fun scheduleTaskBlock(taskId: String, date: LocalDate, startMin: Int, durationMin: Int) {
        val now = System.currentTimeMillis()
        mut { d ->
            val task = d.tasks.firstOrNull { it.id == taskId } ?: return@mut d
            val start = (startMin / 15 * 15).coerceIn(0, 1425)
            val duration = (durationMin / 15 * 15).coerceIn(15, 24 * 60)
                .coerceAtMost(1440 - start)
            d.copy(taskBlocks = d.taskBlocks + TaskBlock(
                id = newId(),
                taskId = taskId,
                occurrenceKey = task.takeIf { it.recur != Recur.NONE }?.due,
                date = date.toIso(),
                startMin = start,
                durationMin = duration,
                createdAt = now,
                updatedAt = now,
            ))
        }
    }

    fun moveTaskBlock(id: String, date: LocalDate, startMin: Int) {
        mut { d -> d.copy(taskBlocks = d.taskBlocks.map { block ->
            if (block.id != id) block else block.copy(
                date = date.toIso(),
                startMin = (startMin / 15 * 15).coerceIn(0, 1440 - block.durationMin),
                source = BlockSource.MANUAL,
                updatedAt = System.currentTimeMillis(),
            )
        }) }
    }

    fun resizeTaskBlock(id: String, durationMin: Int) {
        mut { d -> d.copy(taskBlocks = d.taskBlocks.map { block ->
            if (block.id != id) block else block.copy(
                durationMin = (durationMin / 15 * 15).coerceIn(15, 1440 - block.startMin),
                source = BlockSource.MANUAL,
                updatedAt = System.currentTimeMillis(),
            )
        }) }
    }

    fun removeTaskBlock(id: String) = mut { d ->
        d.copy(taskBlocks = d.taskBlocks.filterNot { it.id == id })
    }

    fun reviewExpiredBlock(id: String, action: String) {
        val block = data.value?.taskBlocks?.firstOrNull { it.id == id } ?: return
        when (action) {
            "later" -> {
                val nowDateTime = _now.value
                val nextSlot = ((nowDateTime.hour * 60 + nowDateTime.minute + 14) / 15 * 15)
                if (nextSlot + block.durationMin <= 1440) moveTaskBlock(id, today(), nextSlot)
                else moveTaskBlock(id, today().plusDays(1), block.startMin)
            }
            "tomorrow" -> moveTaskBlock(id, today().plusDays(1), block.startMin)
            "complete" -> {
                val task = data.value?.tasks?.firstOrNull { it.id == block.taskId }
                if (task != null && !task.done) toggleTask(block.taskId)
                else mut { d -> d.copy(taskBlocks = d.taskBlocks.map {
                    if (it.id == id) it.copy(state = BlockState.COMPLETED, updatedAt = System.currentTimeMillis()) else it
                }) }
            }
            "skip" -> mut { d -> d.copy(taskBlocks = d.taskBlocks.map {
                if (it.id == id) it.copy(state = BlockState.SKIPPED, updatedAt = System.currentTimeMillis()) else it
            }) }
            else -> removeTaskBlock(id)
        }
    }

    // ---- Focus sessions ----

    fun activeFocusSession(): FocusSession? = data.value?.let(::activeFocus)

    fun activeFocusElapsedSeconds(): Long = activeFocusSession()?.let {
        focusElapsedSeconds(it, System.currentTimeMillis(), SystemClock.elapsedRealtime())
    } ?: 0L

    fun startFocus(taskId: String, blockId: String? = null) {
        val snapshot = data.value ?: return
        if (activeFocus(snapshot) != null) return
        val task = snapshot.tasks.firstOrNull { it.id == taskId } ?: return
        val block = blockId?.let { id -> snapshot.taskBlocks.firstOrNull { it.id == id } }
        val targetSeconds = (
            block?.let { (it.durationMin - (it.actualMinutes ?: 0)).coerceAtLeast(1) }
                ?: task.estimateMin ?: snapshot.prefs.defaultTaskEstimateMin
            ) * 60L
        mut {
            com.bento.calendar.data.startFocus(
                it, taskId, blockId, System.currentTimeMillis(), targetSeconds,
                SystemClock.elapsedRealtime(),
            )
        }
    }

    fun pauseFocus() = mut {
        com.bento.calendar.data.pauseFocus(it, System.currentTimeMillis(), SystemClock.elapsedRealtime())
    }
    fun resumeFocus() = mut {
        com.bento.calendar.data.resumeFocus(it, System.currentTimeMillis(), SystemClock.elapsedRealtime())
    }
    fun extendFocus() = mut { com.bento.calendar.data.extendFocus(it) }

    fun finishFocus(complete: Boolean = false) {
        val session = activeFocusSession() ?: return
        mut { current ->
            val elapsedSeconds = focusElapsedSeconds(session, System.currentTimeMillis(), SystemClock.elapsedRealtime())
            var finished = com.bento.calendar.data.finishFocus(
                current, System.currentTimeMillis(), SystemClock.elapsedRealtime(),
            )
            if (!complete && session.taskId != null) {
                val focusedMin = (elapsedSeconds / 60).toInt()
                finished = finished.copy(tasks = finished.tasks.map { task ->
                    if (task.id != session.taskId || task.estimateMin == null) task else task.copy(
                        estimateMin = (task.estimateMin - focusedMin).coerceAtLeast(15),
                    )
                })
            }
            val taskId = session.taskId
            if (complete && taskId != null && finished.tasks.any { it.id == taskId && !it.done }) {
                completeTaskWithBlocks(finished, taskId, today())
            } else {
                finished
            }
        }
    }

    // ---- Natural-language quick add ----
    /**
     * "Dentist tue 3pm" → event; "Gym in 2 days #fitness" → categorized
     * task; "Standup every week mon 9am" → recurring event. Bare text becomes
     * an undated task. The live-preview chips call [parseQuickAdd] directly;
     * this is the matching commit path.
     */
    fun commitQuickAdd(raw: String): Boolean {
        val pf = prefs()
        val snapshot = data.value
        val p = parseQuickAdd(
            raw = raw,
            today = today(),
            defaultDurMin = pf.durDef,
            categories = snapshot?.categories ?: Cats.DEFAULTS,
        ) ?: return false
        if (p.start != null) {
            val ev = EventItem(
                id = newId(),
                title = p.title,
                date = (p.date ?: today()).toIso(),
                start = p.start,
                end = p.end!!,
                // categoryOf: "personal" may have been deleted from the
                // user's categories — never mint a dangling id.
                cat = p.categoryId
                    ?: snapshot?.categoryOf(Cats.PERSONAL)?.id
                    ?: Cats.PERSONAL,
                recur = p.recur,
                remind = pf.remindDef,
            )
            mut { x -> x.copy(events = x.events + ev) }
        } else {
            val tk = TaskItem(
                id = newId(),
                title = p.title,
                due = p.date?.toIso(),
                cat = p.categoryId.orEmpty(),
                recur = p.recur,
                priority = p.priority,
                estimateMin = p.estimateMin,
            )
            mut { x -> x.copy(tasks = x.tasks + tk) }
        }
        fabOpen = false
        return true
    }

    fun toggleDoneOpen() {
        doneOpen = !doneOpen
    }

    fun clearCompleted() {
        if (data.value?.tasks?.none { it.done } != false) return
        twoTap(Arm.CLEAR) {
            dismissUndo()
            mut { x ->
                val done = x.tasks.filter { it.done }
                val doneIds = done.mapTo(mutableSetOf()) { it.id }
                x.copy(
                    tasks = x.tasks.filter { !it.done },
                    taskBlocks = x.taskBlocks.filterNot {
                        it.taskId in doneIds && it.state == BlockState.PLANNED
                    },
                ).withTrashed(*done.map { task ->
                    trashEntry(t = task, blocks = x.taskBlocks.filter {
                        it.taskId == task.id && it.state == BlockState.PLANNED
                    })
                }.toTypedArray())
            }
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
            // Lead with the biometric sheet when it's usable; the PIN pad
            // stays underneath as the fallback.
            requestNoteBio()
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
            mut { x ->
                val victim = x.notes.firstOrNull { it.id == id }
                val next = x.copy(notes = x.notes.filter { it.id != id })
                if (victim != null) next.withTrashed(trashEntry(n = victim)) else next
            }
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
        when (val t = ctx.then) {
            is PinThen.OpenNote -> {
                unlocked = unlocked + t.id
                openNoteId = t.id
            }
            is PinThen.LockNote -> unlocked = unlocked + t.id
            PinThen.ChangePin -> {
                // Verified — hand over to a fresh Set sheet for the new PIN.
                pinBuf = ""
                pinErr = false
                pinCtx = PinCtx(PinMode.Set, PinThen.None, "")
                return
            }
            PinThen.RemovePin -> {
                mut { it.copy(pin = null) }
                unlocked = emptySet()
            }
            PinThen.None -> {}
        }
        pinBuf = ""
        pinCtx = null
    }

    fun pinCancel() {
        pinCtx = null
        pinBuf = ""
        pinErr = false
    }

    // ---- Biometrics ----
    /**
     * Set by MainActivity (BiometricPrompt needs the FragmentActivity):
     * (title, subtitle, allowDeviceCredential, onSuccess). Null while no
     * activity is attached — callers no-op and the PIN pad remains.
     */
    var bioPrompt: ((String, String?, Boolean, () -> Unit) -> Unit)? = null

    /** Hardware states, refreshed by MainActivity onStart (enrollment can
     *  change in system settings while we're backgrounded). */
    var bioAvailable by mutableStateOf(false)
    var credentialAvailable by mutableStateOf(false)

    /** The PIN sheet's fingerprint affordance (and the auto-prompt) gate. */
    val canNoteBio: Boolean
        get() = bioAvailable && data.value?.prefs?.bioNotes == true

    /** Whether the app-lock overlay is up (set on launch/return, not here). */
    var appLocked by mutableStateOf(false)
        private set

    fun lockApp() {
        appLocked = true
    }

    /**
     * Prompt to clear the app lock. Falls back to the device PIN/pattern —
     * the app's own note PIN is deliberately NOT a fallback here: the app
     * lock guards everything, notes included, so it shouldn't be weaker than
     * the lock screen.
     */
    fun requestAppUnlock() {
        if (!credentialAvailable) {
            // No biometrics and no device lock: nothing can ever satisfy the
            // prompt, so fail open rather than brick the app.
            appLocked = false
            return
        }
        bioPrompt?.invoke("Unlock Bento", null, true) { appLocked = false }
    }

    /**
     * Prompt to unlock the note behind the current Enter-mode PIN sheet.
     * Success behaves exactly like a correct PIN; dismissal leaves the pad.
     */
    fun requestNoteBio() {
        val ctx = pinCtx ?: return
        if (ctx.mode != PinMode.Enter || !canNoteBio) return
        // One prompt at a time: constructing a second BiometricPrompt while
        // the app-lock sheet is up swaps the in-flight callback (both share
        // the activity-scoped BiometricViewModel) and a successful auth would
        // unlock the NOTE while the lock overlay stays. The PIN sheet's link
        // re-offers biometrics once the app lock clears.
        if (appLocked) return
        val title = when (ctx.then) {
            PinThen.ChangePin, PinThen.RemovePin -> "Verify it's you"
            else -> "Unlock note"
        }
        bioPrompt?.invoke(title, ctx.noteTitle.ifEmpty { null }, false) {
            // Re-read: the sheet may have been dismissed while the system
            // prompt was up; finishing a stale ctx would unlock the wrong UI.
            val current = pinCtx ?: return@invoke
            if (current.mode == PinMode.Enter) finishPin(current)
        }
    }

    /** Settings: first-time PIN setup (no existing PIN to prove). */
    fun startSetPin() {
        pinCtx = PinCtx(PinMode.Set, PinThen.None, "")
        pinBuf = ""
        pinErr = false
    }

    /**
     * Settings: change the PIN — proves knowledge of the current one first
     * (or a biometric, same trust as note unlock), then rolls into Set.
     * Without this gate anyone holding the unlocked phone could swap the PIN
     * and read every locked note through the new one.
     */
    fun startChangePin() {
        if (data.value?.pin == null) {
            startSetPin()
            return
        }
        pinCtx = PinCtx(PinMode.Enter, PinThen.ChangePin, "")
        pinBuf = ""
        pinErr = false
        requestNoteBio()
    }

    /** Settings: remove the PIN — verification-gated like [startChangePin]. */
    fun startRemovePin() {
        if (data.value?.pin == null) return
        pinCtx = PinCtx(PinMode.Enter, PinThen.RemovePin, "")
        pinBuf = ""
        pinErr = false
        requestNoteBio()
    }

    // ---- Categories ----
    var categoriesOpen by mutableStateOf(false)
        private set

    fun openCategories() {
        categoriesOpen = true
    }

    fun closeCategories() {
        categoriesOpen = false
    }

    fun addCategory(label: String, colorHex: String) {
        val name = label.trim()
        if (name.isEmpty()) return
        mut { x ->
            x.copy(categories = x.categories + Category(newId(), name, colorHex))
        }
    }

    fun updateCategory(id: String, label: String, colorHex: String) {
        val name = label.trim()
        if (name.isEmpty()) return
        mut { x ->
            x.copy(categories = x.categories.map {
                if (it.id == id) it.copy(label = name, colorHex = colorHex) else it
            })
        }
    }

    /**
     * Deleting a category reassigns its events to the first remaining
     * category and clears it from tasks. The last category can't be deleted.
     */
    fun deleteCategory(id: String) {
        mut { x ->
            if (x.categories.size <= 1) return@mut x
            val remaining = x.categories.filter { it.id != id }
            val fallback = remaining.first().id
            x.copy(
                categories = remaining,
                events = x.events.map { if (it.cat == id) it.copy(cat = fallback) else it },
                tasks = x.tasks.map { if (it.cat == id) it.copy(cat = "") else it },
            )
        }
    }

    // ---- Device calendar overlay ----
    var deviceCals by mutableStateOf<List<DeviceCal>>(emptyList())
        private set

    /** Occurrence-date ISO -> device events, for the loaded window. */
    var deviceEvents by mutableStateOf<Map<String, List<DeviceEvent>>>(emptyMap())
        private set

    /** Set by MainActivity; invoked when enabling the overlay needs the
     *  READ_CALENDAR runtime permission. */
    var requestCalendarPermission: (() -> Unit)? = null

    fun hasCalendarOverlayEnabled(): Boolean = data.value?.prefs?.deviceCalsEnabled == true

    fun setDeviceCalsEnabled(on: Boolean) {
        if (on && !DeviceCalendars.hasPermission(getApplication())) {
            requestCalendarPermission?.invoke()
            return
        }
        mutPrefs { it.copy(deviceCalsEnabled = on) }
        if (on) refreshDeviceCalendarData() else deviceEvents = emptyMap()
    }

    /** Called by MainActivity with the permission result. */
    fun onCalendarPermissionResult(granted: Boolean) {
        if (granted) {
            mutPrefs { it.copy(deviceCalsEnabled = true) }
            refreshDeviceCalendarData()
        }
    }

    fun toggleDeviceCal(id: Long) {
        val all = deviceCals.map { it.id }
        mutPrefs { p ->
            // Purge ids for calendars that no longer exist on the device —
            // they'd otherwise pin deviceCalIds non-empty ("some selected")
            // forever, with no visible toggle to clear them.
            val current = (if (p.deviceCalIds.isEmpty()) all else p.deviceCalIds)
                .filter { it in all }
            val next = if (id in current) current - id else current + id
            p.copy(deviceCalIds = if (next.toSet() == all.toSet()) emptyList() else next)
        }
        refreshDeviceCalendarData()
    }

    /** The date range the current [deviceEvents] map covers (main-thread). */
    private var deviceWindow: Pair<LocalDate, LocalDate>? = null

    /**
     * (Re)load the device calendar list and a generous instance window around
     * the visible range. Called on enable, permission grant, calendar
     * navigation past the loaded window, and resume; cheap IO queries.
     */
    fun refreshDeviceCalendarData() {
        val app = getApplication<Application>()
        // Snapshot nav state on the caller's (main) thread: the IO coroutine
        // must not observe positions mutated after this call.
        val anchorFrom = minOf(selDate, cursor.atDay(1))
        val anchorTo = maxOf(selDate, cursor.atEndOfMonth())
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val pf = data.first { it != null }!!.prefs
            if (!pf.deviceCalsEnabled || !DeviceCalendars.hasPermission(app)) {
                // Disabled — or the permission was revoked in system settings:
                // stale device events must not keep rendering.
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    deviceEvents = emptyMap()
                    deviceWindow = null
                }
                // Widgets query the overlay themselves in provideGlance —
                // push so they drop (or pick up) device rows promptly.
                WidgetSync.push(app)
                return@launch
            }
            val cals = DeviceCalendars.calendars(app)
            val from = anchorFrom.minusMonths(1)
            val to = anchorTo.plusMonths(2)
            val events = DeviceCalendars.eventsBetween(app, from, to, pf.deviceCalIds)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                deviceCals = cals
                deviceEvents = events
                deviceWindow = from to to
            }
            WidgetSync.push(app)
        }
    }

    /**
     * Calendar-navigation hook: refetch when the visible range drifts within a
     * week of the loaded window's edge (or nothing is loaded yet).
     */
    private fun maybeRefreshDeviceWindow() {
        if (data.value?.prefs?.deviceCalsEnabled != true) return
        val w = deviceWindow ?: run {
            refreshDeviceCalendarData()
            return
        }
        val lo = minOf(selDate, cursor.atDay(1))
        val hi = maxOf(selDate, cursor.atEndOfMonth())
        if (lo < w.first.plusDays(7) || hi > w.second.minusDays(7)) {
            refreshDeviceCalendarData()
        }
    }

    // ---- Preferences ----
    private fun mutPrefs(transform: (Prefs) -> Prefs) {
        mut { it.copy(prefs = transform(it.prefs)) }
    }

    fun setTheme(theme: String) = mutPrefs { it.copy(theme = theme) }
    fun setAccent(hex: String) = mutPrefs { it.copy(accent = hex) }
    fun toggleDynamicColor() = mutPrefs { it.copy(dynamicColor = !it.dynamicColor) }
    fun toggleBioNotes() = mutPrefs { it.copy(bioNotes = !it.bioNotes) }
    fun toggleAppLock() = mutPrefs { it.copy(appLock = !it.appLock) }
    fun toggleTasksOnCalendar() = mutPrefs { it.copy(tasksOnCalendar = !it.tasksOnCalendar) }

    /** Note tile tint; null returns to the plain tile. */
    fun setNoteColor(hex: String?) {
        val id = openNoteId ?: return
        mut { x ->
            x.copy(notes = x.notes.map { if (it.id == id) it.copy(colorHex = hex) else it })
        }
    }
    fun toggle24h() = mutPrefs { it.copy(use24h = !it.use24h) }
    fun toggleMonday() = mutPrefs { it.copy(monday = !it.monday) }
    fun setRemindDef(v: Int?) = mutPrefs { it.copy(remindDef = v) }
    fun setDurDef(v: Int) = mutPrefs { it.copy(durDef = v) }
    fun setDefaultTaskEstimate(v: Int) = mutPrefs {
        it.copy(defaultTaskEstimateMin = v.coerceIn(15, 240))
    }
    fun setBlockReminder(v: Int?) = mutPrefs {
        it.copy(blockReminderMin = v?.coerceIn(0, 1440))
    }
    fun setWorkHours(day: Int, enabled: Boolean? = null, start: String? = null, end: String? = null) =
        mutPrefs { prefs ->
            val current = prefs.workHours.ifEmpty { com.bento.calendar.data.WorkHours.defaults() }
            prefs.copy(workHours = current.map { hours ->
                if (hours.day != day) hours else {
                    var nextStart = start ?: hours.start
                    var nextEnd = end ?: hours.end
                    if (nextStart.toMins() >= nextEnd.toMins()) {
                        if (start != null) {
                            val startMin = nextStart.toMins().coerceAtMost(1438)
                            nextStart = minsToHm(startMin)
                            nextEnd = minsToHm((startMin + 60).coerceAtMost(1439))
                        } else {
                            val endMin = nextEnd.toMins().coerceAtLeast(1)
                            nextEnd = minsToHm(endMin)
                            nextStart = minsToHm((endMin - 60).coerceAtLeast(0))
                        }
                    }
                    hours.copy(
                        enabled = enabled ?: hours.enabled,
                        start = nextStart,
                        end = nextEnd,
                    )
                }
            })
        }

    // ---- Data ----
    fun resetApp() {
        twoTap(Arm.RESET) {
            // "Erase all events, tasks and notes" — keep theme, prefs and PIN,
            // which the label doesn't claim to touch. The trash goes too:
            // it holds exactly the content the label claims to erase, and a
            // privacy wipe that leaves restorable copies isn't a wipe.
            dismissUndo()
            mut {
                it.copy(
                    events = emptyList(),
                    tasks = emptyList(),
                    notes = emptyList(),
                    trash = emptyList(),
                    taskBlocks = emptyList(),
                    dayPlans = emptyList(),
                    focusSessions = emptyList(),
                    focusDailyTotals = emptyList(),
                )
            }
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

    /** Called when a search result is opened: remember the query (max 5). */
    fun recordSearch(q: String) {
        val query = q.trim()
        if (query.isEmpty()) return
        mutPrefs { p ->
            p.copy(recents = (listOf(query) + p.recents.filter { !it.equals(query, ignoreCase = true) }).take(5))
        }
    }

    fun clearRecentSearches() = mutPrefs { it.copy(recents = emptyList()) }

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
        val blocks = data.value?.taskBlocks?.filter {
            it.taskId == t.id && it.state == BlockState.PLANNED
        }.orEmpty()
        mut { x -> x.copy(
            tasks = x.tasks.filter { it.id != t.id },
            taskBlocks = x.taskBlocks.filterNot {
                it.taskId == t.id && it.state == BlockState.PLANNED
            },
        ).withTrashed(trashEntry(t = t, blocks = blocks)) }
        offerUndo("task") { x ->
            if (x.tasks.any { it.id == t.id }) {
                x
            } else {
                x.copy(
                    // Undo pulls the item back OUT of the trash too.
                    trash = x.trash.filterNot { it.task?.id == t.id },
                    tasks = if (idx < 0) {
                        x.tasks + t
                    } else {
                        x.tasks.toMutableList().apply { add(idx.coerceAtMost(size), t) }
                    },
                    taskBlocks = (x.taskBlocks + blocks).distinctBy { it.id },
                )
            }
        }
    }

    /** Swipe-left on an (unlocked) note row: delete with undo. */
    fun deleteNoteBySwipe(n: NoteItem) {
        val idx = data.value?.notes?.indexOfFirst { it.id == n.id } ?: -1
        mut { x -> x.copy(notes = x.notes.filter { it.id != n.id }).withTrashed(trashEntry(n = n)) }
        offerUndo("note") { x ->
            if (x.notes.any { it.id == n.id }) {
                x
            } else {
                x.copy(
                    trash = x.trash.filterNot { it.note?.id == n.id },
                    notes = if (idx < 0) {
                        x.notes + n
                    } else {
                        x.notes.toMutableList().apply { add(idx.coerceAtMost(size), n) }
                    },
                )
            }
        }
    }

    // ---- Trash ----
    var trashOpen by mutableStateOf(false)
        private set

    fun openTrash() {
        trashOpen = true
        disarm(Arm.TRASH)
    }

    fun closeTrash() {
        trashOpen = false
    }

    /** Prepend entries (newest first), capped so the store can't balloon. */
    private fun AppData.withTrashed(vararg entries: TrashEntry): AppData =
        copy(trash = (entries.toList() + trash).take(Trash.MAX_ENTRIES))

    private fun trashEntry(
        e: EventItem? = null,
        t: TaskItem? = null,
        n: NoteItem? = null,
        blocks: List<TaskBlock> = emptyList(),
    ) = TrashEntry(
        deletedAt = System.currentTimeMillis(), event = e, task = t, note = n,
        taskBlocks = blocks,
    )

    /** Put a trashed item back; ids are UUIDs so collisions mean "already back". */
    fun restoreTrash(entry: TrashEntry) {
        mut { x ->
            var d = x.copy(trash = x.trash - entry)
            entry.event?.let { e -> if (d.events.none { it.id == e.id }) d = d.copy(events = d.events + e) }
            entry.task?.let { t ->
                if (d.tasks.none { it.id == t.id }) d = d.copy(
                    tasks = d.tasks + t,
                    taskBlocks = (d.taskBlocks + entry.taskBlocks).distinctBy { it.id },
                )
            }
            entry.note?.let { n -> if (d.notes.none { it.id == n.id }) d = d.copy(notes = d.notes + n) }
            d
        }
    }

    fun emptyTrash() {
        if (data.value?.trash?.isEmpty() != false) return
        twoTap(Arm.TRASH) {
            // A live undo banner could resurrect an item the user just
            // deleted forever — same discipline as clearCompleted/resetApp.
            dismissUndo()
            mut { it.copy(trash = emptyList()) }
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

    // ---- Calendar files (.ics) ----

    /** Standards-based event export for Google Calendar, Outlook, etc. */
    fun exportCalendarIcs(): String? = data.value?.let {
        exportEventsToIcs(it.events, it.categories)
    }

    /**
     * Merge a calendar file into the live event list. The decoder's stable
     * UID-derived ids make repeat imports idempotent; unlike backup restore,
     * this never replaces tasks, notes, preferences, categories, or trash.
     */
    suspend fun importCalendarIcs(text: String): IcsImportResult {
        val snapshot = data.value ?: return IcsImportResult(
            events = emptyList(), duplicates = 0, skipped = 0,
            sourceEvents = 0, validCalendar = false,
        )
        // Calendar exports can be large; parsing, line unfolding and hashing
        // stay off the UI thread. mut() below returns to the VM's Main scope.
        val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            importEventsFromIcs(
                text = text,
                categories = snapshot.categories,
                existingIds = snapshot.events.mapTo(mutableSetOf()) { it.id },
            )
        }
        if (result.validCalendar && result.events.isNotEmpty()) {
            mut { current ->
                val ids = current.events.mapTo(mutableSetOf()) { it.id }
                current.copy(events = current.events + result.events.filter { ids.add(it.id) })
            }
        }
        return result
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
            val knownKeys = listOf(
                "events", "tasks", "notes", "prefs", "pin", "taskBlocks", "dayPlans", "focusSessions",
            )
            if (!hasMarker && knownKeys.none { obj.containsKey(it) }) return false
            val decoded = backupJson.decodeFromJsonElement(AppData.serializer(), obj)
            // Semantic validation: date/start/end/due are plain strings, and a
            // value like "2026-7-2" or "9:00" would persist fine but then
            // crash every render (occursOn -> LocalDate.parse) with no
            // recovery short of clearing app data.
            decoded.events.forEach { ev ->
                ev.date.toDate()
                ev.endDate?.toDate()
                // Multi-day spans put start and end on different days, so
                // end-after-start only holds for single-day events. Both
                // toMins calls still validate the "HH:MM" shape.
                require(ev.end.toMins() > ev.start.toMins() || ev.spanEnd() != null)
                ev.exDates.forEach { it.toDate() }
            }
            decoded.tasks.forEach { it.due?.toDate() }
            // A bad accent crashes BentoTheme at the root; normalize instead
            // of rejecting. Clamp durDef/remindDef so newEvent/saveEvent can't
            // generate unparseable draft times.
            val accent = decoded.prefs.accent
                .takeIf { it.removePrefix("#").toLongOrNull(16) != null }
                ?: Accents.DEFAULT
            // Categories: blank/invalid entries fall back to the defaults so
            // the app can always render; unknown task recur values normalize.
            val validCats = decoded.categories.filter {
                it.id.isNotBlank() && it.label.isNotBlank() &&
                    it.colorHex.removePrefix("#").toLongOrNull(16) != null
            }.ifEmpty { Cats.DEFAULTS }
            val knownRecur = setOf(Recur.NONE, Recur.DAILY, Recur.WEEKLY, Recur.MONTHLY)
            decoded.copy(
                categories = validCats,
                events = decoded.events.map {
                    val recur = if (it.recur in knownRecur) it.recur else Recur.NONE
                    // spanEnd() is the read-time truth; normalizing to it here
                    // drops endDate from recurring or backwards spans.
                    it.copy(recur = recur, endDate = it.copy(recur = recur).spanEnd()?.toIso())
                },
                tasks = decoded.tasks.map {
                    it.copy(
                        recur = if (it.recur in knownRecur) it.recur else Recur.NONE,
                        priority = it.priority.coerceIn(Priority.NONE, Priority.HIGH),
                        // A reminder needs a due date and a parseable time.
                        remindAt = it.remindAt?.takeIf { r ->
                            it.due != null && runCatching { r.toMins() }.isSuccess
                        },
                        estimateMin = it.estimateMin?.coerceIn(15, 24 * 60),
                    )
                },
                notes = decoded.notes.map { n ->
                    n.copy(colorHex = n.colorHex?.takeIf { it.removePrefix("#").toLongOrNull(16) != null })
                },
                // Trash entries must hold exactly one restorable item that
                // passes the SAME validation as the live lists — restore
                // feeds them straight back in, where a bad "9:00" start or
                // garbage remindAt crash-loops every render/reschedule with
                // no recovery. Bad entries drop (it's trash).
                trash = decoded.trash.filter { entry ->
                    listOfNotNull(entry.event, entry.task, entry.note).size == 1 &&
                        runCatching {
                            entry.event?.let { ev ->
                                ev.date.toDate()
                                ev.endDate?.toDate()
                                require(ev.end.toMins() > ev.start.toMins() || ev.spanEnd() != null)
                                ev.exDates.forEach { it.toDate() }
                            }
                            entry.task?.let { t ->
                                t.due?.toDate()
                                t.remindAt?.toMins()
                                entry.taskBlocks.forEach { block ->
                                    require(block.taskId == t.id)
                                    block.date.toDate()
                                    require(block.startMin in 0..1425)
                                    require(block.durationMin in 15..1440)
                                    require(block.startMin % 15 == 0 && block.durationMin % 15 == 0)
                                    require(block.startMin + block.durationMin <= 1440)
                                    require(block.state in BlockState.ALL)
                                    require(block.actualMinutes == null || block.actualMinutes >= 0)
                                }
                            }
                        }.isSuccess
                }.take(Trash.MAX_ENTRIES),
                taskBlocks = decoded.taskBlocks.mapNotNull { block ->
                    block.takeIf {
                        block.id.isNotBlank() && block.taskId.isNotBlank() &&
                        decoded.tasks.any { it.id == block.taskId } &&
                        runCatching { block.date.toDate() }.isSuccess &&
                        block.startMin in 0..1425 && block.durationMin in 15..1440 &&
                        block.startMin % 15 == 0 && block.durationMin % 15 == 0 &&
                        block.startMin + block.durationMin <= 1440 && block.state in BlockState.ALL
                    }?.copy(
                        source = block.source.takeIf { it in BlockSource.ALL } ?: BlockSource.MANUAL,
                        actualMinutes = block.actualMinutes?.coerceAtLeast(0),
                    )
                },
                dayPlans = decoded.dayPlans.mapNotNull { plan ->
                    runCatching {
                        plan.date.toDate()
                        require(plan.workStartMin in 0..1439 && plan.workEndMin in 1..1440)
                        require(plan.workEndMin > plan.workStartMin)
                        plan
                    }.getOrNull()
                }.distinctBy { it.date },
                focusSessions = decoded.focusSessions.mapNotNull { session ->
                    session.takeIf {
                        session.id.isNotBlank() && session.taskTitleSnapshot.isNotBlank() &&
                        session.startedAt > 0 && session.targetSeconds >= 60 &&
                        session.activeSeconds >= 0 && session.outcome in com.bento.calendar.data.FocusOutcome.ALL
                    }?.let { valid ->
                        if (valid.outcome == com.bento.calendar.data.FocusOutcome.ACTIVE ||
                            valid.outcome == com.bento.calendar.data.FocusOutcome.PAUSED
                        ) valid.copy(
                            runningSince = null,
                            runningSinceElapsed = null,
                            endedAt = System.currentTimeMillis(),
                            outcome = com.bento.calendar.data.FocusOutcome.INTERRUPTED,
                        ) else valid
                    }
                }.takeLast(10_000),
                focusDailyTotals = decoded.focusDailyTotals.filter { total ->
                    total.activeSeconds >= 0 && runCatching { total.date.toDate() }.isSuccess
                }.distinctBy { it.date to it.categoryId },
                prefs = decoded.prefs.copy(
                    accent = accent,
                    durDef = decoded.prefs.durDef.coerceAtLeast(1),
                    remindDef = decoded.prefs.remindDef?.coerceAtLeast(0),
                    lastCalView = decoded.prefs.lastCalView
                        .takeIf { it in setOf("month", "week", "day", "agenda") }
                        ?: "month",
                    defaultTaskEstimateMin = decoded.prefs.defaultTaskEstimateMin.coerceIn(15, 240),
                    blockReminderMin = decoded.prefs.blockReminderMin?.coerceIn(0, 24 * 60),
                    workHours = decoded.prefs.workHours.mapNotNull { hours ->
                        runCatching {
                            val start = hours.start.toMins()
                            val end = hours.end.toMins()
                            require(hours.day in 1..7 && end > start)
                            hours
                        }.getOrNull()
                    }.takeIf { it.map { h -> h.day }.toSet().size == 7 }
                        ?: com.bento.calendar.data.WorkHours.defaults(),
                ),
            )
        } catch (_: Exception) {
            return false
        }
        dismissUndo()
        mut { imported }
        calViewState = calViewFromPref(imported.prefs.lastCalView)
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
