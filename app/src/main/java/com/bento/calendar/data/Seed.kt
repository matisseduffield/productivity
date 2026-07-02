package com.bento.calendar.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

fun newId(): String = "x" + UUID.randomUUID().toString().replace("-", "").take(12)

/** First-run / reset content, a faithful port of the prototype's seed(). */
fun seedData(today: LocalDate = LocalDate.now(), nowMs: Long = System.currentTimeMillis()): AppData {
    val ti = today.toIso()
    fun a(n: Long) = today.plusDays(n).toIso()
    val nextMon = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)).toIso()
    val day = 86_400_000L
    val hour = 3_600_000L

    return AppData(
        events = listOf(
            EventItem("e1", "Gym — push day", ti, "07:30", "08:15", Cats.FITNESS, Recur.WEEKLY, null, ""),
            EventItem("e2", "Design review", ti, "11:30", "12:15", Cats.WORK, Recur.NONE, 10, "Google Meet"),
            EventItem("e3", "Dentist check-up", ti, "14:00", "14:30", Cats.PERSONAL, Recur.NONE, 60, "Smile Clinic"),
            EventItem("e4", "Dinner with Maya", ti, "19:00", "21:00", Cats.SOCIAL, Recur.NONE, null, "Bar Nico"),
            EventItem("e5", "Coffee with Sam", a(1), "10:00", "11:00", Cats.SOCIAL, Recur.NONE, 10, "Fern & Co"),
            EventItem("e6", "Sprint planning", nextMon, "09:30", "10:30", Cats.WORK, Recur.WEEKLY, 10, "Google Meet"),
        ),
        tasks = listOf(
            TaskItem("t1", "Confirm dentist appointment", done = true, due = ti, cat = Cats.PERSONAL),
            TaskItem("t2", "Renew car insurance", done = false, due = ti, cat = Cats.PERSONAL),
            TaskItem("t3", "Pick up parcel — locker 14", done = false, due = a(1), cat = ""),
            TaskItem("t4", "Book August flights", done = false, due = a(6), cat = Cats.SOCIAL),
            TaskItem("t5", "Reply to landlord", done = false, due = null, cat = ""),
        ),
        notes = listOf(
            NoteItem(
                "n1", "Recovery codes",
                "GitHub\n8f3k-29dm-11qz-7x2p\n\nEmail backup\nkq92-mm41-08va-3c7d\n\nBank one-time codes\n442198 · 771035 · 660912",
                pinned = true, locked = true, updated = nowMs - 3 * day,
            ),
            NoteItem(
                "n2", "Workout plan",
                "Push — bench 4×8, incline DB 3×10, OHP 3×10, dips\nPull — deadlift 3×5, rows 4×8, curls 3×12\nLegs — squat 4×6, RDL 3×8, calves 4×15",
                pinned = true, locked = false, updated = nowMs - day,
            ),
            NoteItem(
                "n3", "Groceries",
                "Chicken thighs\nGreek yoghurt\nBasmati rice\nOlive oil\nBananas\nCoffee beans",
                pinned = false, locked = false, updated = nowMs - 5 * hour,
            ),
            NoteItem(
                "n4", "Gift ideas",
                "Mum — linen apron\nDev — vinyl voucher\nMaya — ceramics class",
                pinned = false, locked = false, updated = nowMs - 8 * day,
            ),
        ),
        prefs = Prefs(),
        pin = null,
    )
}
