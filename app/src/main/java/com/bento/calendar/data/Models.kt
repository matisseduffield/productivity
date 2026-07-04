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
)

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
)

@Serializable
data class NoteItem(
    val id: String,
    val title: String = "",
    val body: String = "",
    val pinned: Boolean = false,
    val locked: Boolean = false,
    /** Epoch millis of last edit. */
    val updated: Long,
)

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
)

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
