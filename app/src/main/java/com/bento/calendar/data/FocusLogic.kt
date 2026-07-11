package com.bento.calendar.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun activeFocus(data: AppData): FocusSession? = data.focusSessions
    .lastOrNull { it.outcome == FocusOutcome.ACTIVE || it.outcome == FocusOutcome.PAUSED }

fun focusElapsedSeconds(session: FocusSession, now: Long, elapsedNow: Long? = null): Long {
    val running = when {
        session.runningSinceElapsed != null && elapsedNow != null ->
            ((elapsedNow - session.runningSinceElapsed) / 1000L).coerceAtLeast(0)
        else -> session.runningSince?.let { ((now - it) / 1000L).coerceAtLeast(0) }.orZero()
    }
    return session.activeSeconds + running
}

fun startFocus(
    data: AppData,
    taskId: String,
    blockId: String?,
    now: Long,
    targetSeconds: Long,
    elapsedNow: Long = now,
): AppData {
    if (activeFocus(data) != null) return data
    val task = data.tasks.firstOrNull { it.id == taskId } ?: return data
    return data.copy(focusSessions = data.focusSessions + FocusSession(
        id = newId(),
        taskId = taskId,
        blockId = blockId,
        taskTitleSnapshot = task.title,
        categoryIdSnapshot = task.cat,
        startedAt = now,
        runningSince = now,
        runningSinceElapsed = elapsedNow,
        targetSeconds = targetSeconds.coerceAtLeast(60),
    ))
}

fun pauseFocus(data: AppData, now: Long, elapsedNow: Long = now): AppData = updateActiveFocus(data) { session ->
    if (session.outcome != FocusOutcome.ACTIVE) session else session.copy(
        activeSeconds = focusElapsedSeconds(session, now, elapsedNow),
        runningSince = null,
        runningSinceElapsed = null,
        outcome = FocusOutcome.PAUSED,
    )
}

fun resumeFocus(data: AppData, now: Long, elapsedNow: Long = now): AppData = updateActiveFocus(data) { session ->
    if (session.outcome != FocusOutcome.PAUSED) session else session.copy(
        runningSince = now,
        runningSinceElapsed = elapsedNow,
        outcome = FocusOutcome.ACTIVE,
    )
}

fun finishFocus(data: AppData, now: Long, elapsedNow: Long = now): AppData = updateActiveFocus(data) { session ->
    session.copy(
        activeSeconds = focusElapsedSeconds(session, now, elapsedNow),
        runningSince = null,
        runningSinceElapsed = null,
        endedAt = now,
        outcome = FocusOutcome.FINISHED,
    )
}

fun extendFocus(data: AppData, seconds: Long = 15 * 60L): AppData = updateActiveFocus(data) { session ->
    session.copy(targetSeconds = session.targetSeconds + seconds.coerceAtLeast(60))
}

/** Reboot cannot preserve the monotonic running interval; retain the record as interrupted. */
fun interruptFocus(data: AppData, now: Long): AppData = updateActiveFocus(data) { session ->
    session.copy(
        runningSince = null, runningSinceElapsed = null, endedAt = now,
        outcome = FocusOutcome.INTERRUPTED,
    )
}

private fun updateActiveFocus(data: AppData, transform: (FocusSession) -> FocusSession): AppData {
    val active = activeFocus(data) ?: return data
    return data.copy(focusSessions = data.focusSessions.map {
        if (it.id == active.id) transform(it) else it
    })
}

private fun Long?.orZero(): Long = this ?: 0L

/** Compact detailed sessions older than [retentionDays] into daily totals. */
fun compactFocusHistory(
    data: AppData,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
    retentionDays: Long = 365,
): AppData {
    val cutoff = today.minusDays(retentionDays)
    val (old, recent) = data.focusSessions.partition { session ->
        Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate().isBefore(cutoff)
    }
    if (old.isEmpty()) return data
    val accumulated = linkedMapOf<Pair<String, String>, Long>()
    data.focusDailyTotals.forEach { accumulated[it.date to it.categoryId] = it.activeSeconds }
    old.forEach { session ->
        val date = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate().toString()
        val key = date to session.categoryIdSnapshot
        accumulated[key] = accumulated.getOrDefault(key, 0L) + session.activeSeconds
    }
    return data.copy(
        focusSessions = recent,
        focusDailyTotals = accumulated.map { (key, seconds) ->
            FocusDailyTotal(key.first, key.second, seconds)
        },
    )
}
