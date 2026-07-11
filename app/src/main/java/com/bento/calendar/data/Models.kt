package com.bento.calendar.data

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.LocalTime

/**
 * Persisted store, mirroring the design prototype's single localStorage blob
 * (`bento.calendar.v1`). Dates are ISO strings ("2026-07-02"), times "HH:MM",
 * so the on-disk shape stays trivially inspectable and export-friendly.
 */
@Serializable
data class AppData(
    val events: List<EventItem> = emptyList(),
    val tasks: List<TaskItem> = emptyList(),
    val notes: List<NoteItem> = emptyList(),
    val prefs: Prefs = Prefs(),
    val pin: String? = null,
    /** User-editable categories; defaults to the classic four so pre-2.0
     *  stores and backups load unchanged. */
    val categories: List<Category> = Cats.DEFAULTS,
    /** Soft-deleted items, newest first; see [TrashEntry]. */
    val trash: List<TrashEntry> = emptyList(),
    /** Planned work sessions. Deadlines remain on [TaskItem.due]. */
    val taskBlocks: List<TaskBlock> = emptyList(),
    /** One finalized/reviewed planning record per local date. */
    val dayPlans: List<DayPlan> = emptyList(),
    /** Actual focused work; retained independently from task deletion. */
    val focusSessions: List<FocusSession> = emptyList(),
    /** Compacted focus history older than one year, grouped by day/category. */
    val focusDailyTotals: List<FocusDailyTotal> = emptyList(),
) {
    /** Category lookup with a stable fallback (first category) so orphaned
     *  ids from deleted categories never crash rendering. */
    fun categoryOf(id: String): Category =
        categories.firstOrNull { it.id == id }
            ?: categories.firstOrNull()
            ?: Cats.DEFAULTS[0]
}

@Serializable
data class EventItem(
    val id: String,
    val title: String,
    val date: String,
    val start: String,
    val end: String,
    val cat: String = Cats.WORK,
    val recur: String = Recur.NONE,
    /** Minutes before start (0 = at start), null = no reminder. */
    val remind: Int? = null,
    val loc: String = "",
    /** All-day events store start 00:00 / end 23:59; reminders fire off 00:00. */
    val allDay: Boolean = false,
    /**
     * Occurrence dates (ISO) excluded from a recurring series — created by
     * "delete this event only" or "edit this event only" (which excludes the
     * date and spawns a standalone event with the edits).
     */
    val exDates: List<String> = emptyList(),
    /**
     * Last day (ISO) of a multi-day event; null (or <= [date]) = single day.
     * [start] is the time on the first day, [end] the time on the last —
     * read-time expansion turns the days between into all-day segments.
     * Multi-day events never recur ([recur] is forced NONE on save).
     */
    val endDate: String? = null,
) {
    /** Last covered day when this is a valid multi-day span, else null. */
    fun spanEnd(): java.time.LocalDate? {
        if (recur != Recur.NONE) return null
        return endDate?.toDate()?.takeIf { it.isAfter(date.toDate()) }
    }
}

@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val due: String? = null,
    /** Category id or "" for none. */
    val cat: String = "",
    /**
     * Repeating tasks ([Recur] values): completing one advances its due date
     * to the next occurrence instead of marking it done.
     */
    val recur: String = Recur.NONE,
    /** [Priority] level; 0 = none. */
    val priority: Int = Priority.NONE,
    /** Checklist steps; repeating tasks reset them when the due date advances. */
    val subs: List<SubTask> = emptyList(),
    /**
     * Reminder time ("HH:MM") on the [due] date; null = no reminder.
     * Meaningless without a due date — save clears it when due is cleared.
     * Repeating tasks keep it as the due date advances.
     */
    val remindAt: String? = null,
    /** Remaining expected effort; null means the planner uses its visible default. */
    val estimateMin: Int? = null,
)

@Serializable
data class SubTask(val id: String, val title: String, val done: Boolean = false)

object Priority {
    const val NONE = 0
    const val LOW = 1
    const val MEDIUM = 2
    const val HIGH = 3
    val ALL = listOf(NONE, LOW, MEDIUM, HIGH)

    fun label(p: Int): String = when (p) {
        LOW -> "Low"
        MEDIUM -> "Medium"
        HIGH -> "High"
        else -> "None"
    }

    /** Flag tint, or null for [NONE] (no flag drawn). */
    fun colorHex(p: Int): String? = when (p) {
        LOW -> "#5BC8D9"
        MEDIUM -> "#F2C05A"
        HIGH -> "#EF6D6D"
        else -> null
    }
}

@Serializable
data class NoteItem(
    val id: String,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val locked: Boolean = false,
    /** Epoch millis of last edit. */
    val updated: Long,
    /** Tile tint from [Cats.PALETTE]; null = the plain tile color. */
    val colorHex: String? = null,
)

/**
 * A soft-deleted item awaiting restore or purge — exactly one of the three
 * is non-null. Entries older than [Trash.RETENTION_DAYS] purge on launch.
 */
@Serializable
data class TrashEntry(
    val deletedAt: Long,
    val event: EventItem? = null,
    val task: TaskItem? = null,
    val note: NoteItem? = null,
    /** Unfinished/future schedule restored with a deleted task. */
    val taskBlocks: List<TaskBlock> = emptyList(),
) {
    val title: String
        get() = event?.title ?: task?.title ?: note?.title?.ifEmpty { "Untitled" } ?: ""
}

object Trash {
    const val RETENTION_DAYS = 30L
    const val MAX_ENTRIES = 200
}

@Serializable
data class Prefs(
    val theme: String = "dark",
    val use24h: Boolean = true,
    val monday: Boolean = true,
    val accent: String = Accents.DEFAULT,
    /** Default reminder for new events, minutes before; null = none. */
    val remindDef: Int? = 10,
    /** Default event length in minutes. */
    val durDef: Int = 60,
    /** Last few search queries, most recent first. */
    val recents: List<String> = emptyList(),
    /** Read-only device (Google) calendar overlay. */
    val deviceCalsEnabled: Boolean = false,
    /** Device calendar ids the user opted in; empty = all visible calendars. */
    val deviceCalIds: List<Long> = emptyList(),
    /** Material You: derive the accent from the system wallpaper (Android 12+). */
    val dynamicColor: Boolean = false,
    /** Fingerprint/face unlocks PIN-locked notes (when hardware allows). */
    val bioNotes: Boolean = true,
    /** Require biometric/device unlock when the app opens. */
    val appLock: Boolean = false,
    /** Show due tasks in the calendar views alongside events. */
    val tasksOnCalendar: Boolean = true,
    /** Last Calendar mode, restored on the next launch. */
    val lastCalView: String = "month",
    /** Local auto-plan boundaries. No breaks or buffers are imposed. */
    val workHours: List<WorkHours> = WorkHours.defaults(),
    val defaultTaskEstimateMin: Int = 30,
    /** Minutes before an internal task block starts; null disables it. */
    val blockReminderMin: Int? = 0,
)

@Serializable
data class WorkHours(
    /** java.time.DayOfWeek value: Monday=1 through Sunday=7. */
    val day: Int,
    val enabled: Boolean,
    val start: String = "09:00",
    val end: String = "17:00",
) {
    companion object {
        fun defaults(): List<WorkHours> = (1..7).map { day ->
            WorkHours(day = day, enabled = day <= 5)
        }
    }
}

@Serializable
data class TaskBlock(
    val id: String,
    val taskId: String,
    /** Recurring-task cycle key, normally the due date at scheduling time. */
    val occurrenceKey: String? = null,
    val date: String,
    val startMin: Int,
    val durationMin: Int,
    val source: String = BlockSource.MANUAL,
    val state: String = BlockState.PLANNED,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
)

object BlockSource {
    const val MANUAL = "manual"
    const val SUGGESTED = "suggested"
    val ALL = setOf(MANUAL, SUGGESTED)
}

object BlockState {
    const val PLANNED = "planned"
    const val COMPLETED = "completed"
    const val SKIPPED = "skipped"
    val ALL = setOf(PLANNED, COMPLETED, SKIPPED)
}

@Serializable
data class DayPlan(
    val date: String,
    val workStartMin: Int,
    val workEndMin: Int,
    val plannedAt: Long,
    val reviewedAt: Long? = null,
)

@Serializable
data class FocusSession(
    val id: String,
    val taskId: String? = null,
    val blockId: String? = null,
    val taskTitleSnapshot: String,
    val categoryIdSnapshot: String = "",
    val startedAt: Long,
    val endedAt: Long? = null,
    val activeSeconds: Long = 0,
    /** Wall-clock anchor for the currently running interval; null while paused. */
    val runningSince: Long? = startedAt,
    /** Monotonic anchor used while the device remains on; reboot interrupts. */
    val runningSinceElapsed: Long? = null,
    val targetSeconds: Long = 30 * 60L,
    val outcome: String = FocusOutcome.ACTIVE,
)

@Serializable
data class FocusDailyTotal(
    val date: String,
    val categoryId: String = "",
    val activeSeconds: Long,
)

object FocusOutcome {
    const val ACTIVE = "active"
    const val PAUSED = "paused"
    const val FINISHED = "finished"
    const val INTERRUPTED = "interrupted"
    val ALL = setOf(ACTIVE, PAUSED, FINISHED, INTERRUPTED)
}

object Recur {
    const val NONE = "none"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
}

@Serializable
data class Category(val id: String, val label: String, val colorHex: String)

object Cats {
    const val WORK = "work"
    const val FITNESS = "fitness"
    const val PERSONAL = "personal"
    const val SOCIAL = "social"

    /** The classic four — the default set and the pre-2.0 fallback. */
    val DEFAULTS = listOf(
        Category(WORK, "Work", "#7BA7F7"),
        Category(FITNESS, "Fitness", "#5BD9A5"),
        Category(PERSONAL, "Personal", "#F2C05A"),
        Category(SOCIAL, "Social", "#F08FAE"),
    )

    /** Swatches offered when creating/recoloring a category. */
    val PALETTE = listOf(
        "#7BA7F7", "#5BD9A5", "#F2C05A", "#F08FAE",
        "#8B6FE8", "#5B8DEF", "#34B98C", "#E08B4C",
        "#EF6D6D", "#5BC8D9", "#B79BF0", "#9BB068",
    )
}

data class AccentOption(val name: String, val hex: String)

object Accents {
    const val DEFAULT = "#8B6FE8"
    val ALL = listOf(
        AccentOption("Violet", "#8B6FE8"),
        AccentOption("Mint", "#34B98C"),
        AccentOption("Apricot", "#E08B4C"),
        AccentOption("Blue", "#5B8DEF"),
    )

    fun nameOf(hex: String): String = ALL.firstOrNull { it.hex == hex }?.name ?: "Custom"
}

// ---- ISO string <-> java.time bridges ----

fun String.toDate(): LocalDate = LocalDate.parse(this)
fun LocalDate.toIso(): String = toString()
fun String.toTime(): LocalTime = LocalTime.parse(this)
fun LocalTime.toHm(): String = "%02d:%02d".format(hour, minute)
fun String.toMins(): Int = toTime().let { it.hour * 60 + it.minute }
fun minsToHm(m: Int): String = "%02d:%02d".format(m / 60, m % 60)
