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
)

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
)

@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val done: Boolean = false,
    val due: String? = null,
    /** Category id or "" for none. */
    val cat: String = "",
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
)

object Recur {
    const val NONE = "none"
    const val DAILY = "daily"
    const val WEEKLY = "weekly"
    const val MONTHLY = "monthly"
}

data class Category(val id: String, val label: String, val colorHex: String)

object Cats {
    const val WORK = "work"
    const val FITNESS = "fitness"
    const val PERSONAL = "personal"
    const val SOCIAL = "social"

    val ALL = listOf(
        Category(WORK, "Work", "#7BA7F7"),
        Category(FITNESS, "Fitness", "#5BD9A5"),
        Category(PERSONAL, "Personal", "#F2C05A"),
        Category(SOCIAL, "Social", "#F08FAE"),
    )

    /** Unknown/blank ids fall back to Work, matching the prototype. */
    fun of(id: String): Category = ALL.firstOrNull { it.id == id } ?: ALL[0]
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
