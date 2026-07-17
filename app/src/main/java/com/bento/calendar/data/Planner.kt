package com.bento.calendar.data

import java.time.LocalDate

const val PLAN_GRID_MIN = 15

data class BusyInterval(val startMin: Int, val endMin: Int)

data class PlanSuggestion(
    val taskId: String,
    val date: LocalDate,
    val startMin: Int,
    val durationMin: Int,
    val assumedEstimate: Boolean,
)

data class PlanResult(
    val suggestions: List<PlanSuggestion>,
    /** Minutes that could not fit, keyed by task id. */
    val unscheduledMinutes: Map<String, Int>,
    val availableMinutes: Int,
    val requestedMinutes: Int,
)

data class DayPlanningSummary(
    val date: LocalDate,
    /** Work time left after timed calendar commitments. */
    val availableMinutes: Int,
    val plannedMinutes: Int,
    val completedMinutes: Int,
    val skippedMinutes: Int,
    val completedBlocks: Int,
    val skippedBlocks: Int,
    val enabled: Boolean,
) {
    val overloadMinutes: Int get() = (plannedMinutes - availableMinutes).coerceAtLeast(0)
}

/** Shared capacity contract used by Today and the seven-day planning board. */
fun summarizeDayPlan(
    date: LocalDate,
    taskBlocks: List<TaskBlock>,
    calendarBusy: List<BusyInterval>,
    workHours: WorkHours,
): DayPlanningSummary {
    val blocks = taskBlocks.filter { it.date == date.toIso() }
    val capacity = suggestDayPlan(
        date = date,
        tasks = emptyList(),
        existingBlocks = emptyList(),
        calendarBusy = calendarBusy,
        workHours = workHours,
    ).availableMinutes
    return DayPlanningSummary(
        date = date,
        availableMinutes = capacity,
        plannedMinutes = blocks.filter { it.state == BlockState.PLANNED }.sumOf { it.durationMin },
        completedMinutes = blocks.filter { it.state == BlockState.COMPLETED }.sumOf { it.durationMin },
        skippedMinutes = blocks.filter { it.state == BlockState.SKIPPED }.sumOf { it.durationMin },
        completedBlocks = blocks.count { it.state == BlockState.COMPLETED },
        skippedBlocks = blocks.count { it.state == BlockState.SKIPPED },
        enabled = workHours.enabled,
    )
}

/**
 * Curated, stable shortlist for planning: overdue/due-soon first, then high
 * priority and unfinished work from a prior plan. It never mutates tasks.
 */
fun planningCandidates(
    tasks: List<TaskItem>,
    date: LocalDate,
    unfinishedTaskIds: Set<String> = emptySet(),
): List<TaskItem> {
    val soon = date.plusDays(3)
    return tasks.withIndex()
        .filter { (_, task) ->
            !task.done && (
                task.id in unfinishedTaskIds || task.priority == Priority.HIGH ||
                    task.due?.toDate()?.let { !it.isAfter(soon) } == true
                )
        }
        .sortedWith(
            compareBy<IndexedValue<TaskItem>>(
                { it.value.due?.toDate()?.isAfter(date) != false },
                { it.value.due?.toDate() ?: LocalDate.MAX },
                { -it.value.priority },
                { it.index },
            ),
        )
        .map { it.value }
}

/**
 * Pure, deterministic local scheduler. Existing blocks and calendar items are
 * immutable busy intervals. Work is split only at busy boundaries, aligned to
 * a 15-minute grid, and excess remains explicitly unscheduled.
 */
fun suggestDayPlan(
    date: LocalDate,
    tasks: List<TaskItem>,
    existingBlocks: List<TaskBlock>,
    calendarBusy: List<BusyInterval>,
    workHours: WorkHours,
    defaultEstimateMin: Int = 30,
    notBeforeMin: Int? = null,
): PlanResult {
    if (!workHours.enabled) return PlanResult(emptyList(), tasks.associate { it.id to estimateOf(it, defaultEstimateMin) }, 0, tasks.sumOf { estimateOf(it, defaultEstimateMin) })
    val configuredStart = workHours.start.toMins().coerceIn(0, 1439)
    val workStart = alignUp(maxOf(configuredStart, notBeforeMin ?: configuredStart).coerceIn(0, 1439))
    val workEnd = alignDown(workHours.end.toMins().coerceIn(1, 1440))
    if (workEnd <= workStart) return PlanResult(emptyList(), tasks.associate { it.id to estimateOf(it, defaultEstimateMin) }, 0, tasks.sumOf { estimateOf(it, defaultEstimateMin) })

    val dateIso = date.toIso()
    val occupied = (
        calendarBusy + existingBlocks
            .filter { it.date == dateIso && it.state == BlockState.PLANNED }
            .map { BusyInterval(it.startMin, it.startMin + it.durationMin) }
        )
        .mapNotNull { interval ->
            // Reserve the full touched grid cells so a 10:07 meeting can
            // never produce a supposedly valid 10:07 task block.
            val start = maxOf(workStart, alignDown(interval.startMin))
            val end = minOf(workEnd, alignUp(interval.endMin))
            if (end > start) BusyInterval(start, end) else null
        }
        .sortedBy { it.startMin }
        .merge()

    val free = mutableListOf<BusyInterval>()
    var cursor = workStart
    occupied.forEach { busy ->
        if (busy.startMin > cursor) free += BusyInterval(cursor, busy.startMin)
        cursor = maxOf(cursor, busy.endMin)
    }
    if (cursor < workEnd) free += BusyInterval(cursor, workEnd)

    val suggestions = mutableListOf<PlanSuggestion>()
    val unscheduled = linkedMapOf<String, Int>()
    var freeIndex = 0
    var freeCursor = free.firstOrNull()?.startMin ?: workEnd
    var requested = 0

    tasks.forEach { task ->
        val estimate = estimateOf(task, defaultEstimateMin)
        val alreadyAllocated = existingBlocks
            .filter { block ->
                block.taskId == task.id && block.state == BlockState.PLANNED &&
                    (block.date > dateIso || (
                        block.date == dateIso &&
                            (notBeforeMin == null || block.startMin + block.durationMin > notBeforeMin)
                        )) &&
                    (task.recur == Recur.NONE || block.occurrenceKey == task.due)
            }
            .sumOf { it.durationMin }
        var remaining = (estimate - alreadyAllocated).coerceAtLeast(0)
        requested += remaining

        while (remaining >= PLAN_GRID_MIN && freeIndex < free.size) {
            val slot = free[freeIndex]
            freeCursor = maxOf(freeCursor, slot.startMin)
            val available = alignDown(slot.endMin - freeCursor)
            if (available < PLAN_GRID_MIN) {
                freeIndex++
                freeCursor = free.getOrNull(freeIndex)?.startMin ?: workEnd
                continue
            }
            val duration = minOf(alignUp(remaining), available)
            suggestions += PlanSuggestion(
                taskId = task.id,
                date = date,
                startMin = freeCursor,
                durationMin = duration,
                assumedEstimate = task.estimateMin == null,
            )
            remaining = (remaining - duration).coerceAtLeast(0)
            freeCursor += duration
            if (freeCursor >= slot.endMin) {
                freeIndex++
                freeCursor = free.getOrNull(freeIndex)?.startMin ?: workEnd
            }
        }
        if (remaining > 0) unscheduled[task.id] = remaining
    }

    return PlanResult(
        suggestions = suggestions,
        unscheduledMinutes = unscheduled,
        availableMinutes = free.sumOf { it.endMin - it.startMin },
        requestedMinutes = requested,
    )
}

private fun estimateOf(task: TaskItem, fallback: Int): Int =
    (task.estimateMin ?: fallback).coerceIn(PLAN_GRID_MIN, 24 * 60)

private fun alignUp(value: Int): Int = ((value + PLAN_GRID_MIN - 1) / PLAN_GRID_MIN) * PLAN_GRID_MIN
private fun alignDown(value: Int): Int = (value / PLAN_GRID_MIN) * PLAN_GRID_MIN

private fun List<BusyInterval>.merge(): List<BusyInterval> {
    if (isEmpty()) return emptyList()
    val result = mutableListOf(first())
    drop(1).forEach { next ->
        val last = result.last()
        if (next.startMin <= last.endMin) {
            result[result.lastIndex] = BusyInterval(last.startMin, maxOf(last.endMin, next.endMin))
        } else {
            result += next
        }
    }
    return result
}
