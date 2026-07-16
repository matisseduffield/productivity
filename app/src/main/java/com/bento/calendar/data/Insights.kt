package com.bento.calendar.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class ProductivityDay(
    val date: LocalDate,
    val plannedMinutes: Int,
    val focusedMinutes: Int,
    val completedBlocks: Int,
    val skippedBlocks: Int,
)

data class CategoryFocus(
    val categoryId: String,
    val focusedMinutes: Int,
)

data class ProductivityInsights(
    val days: List<ProductivityDay>,
    val totalPlannedMinutes: Int,
    val totalFocusedMinutes: Int,
    val completedBlocks: Int,
    val skippedBlocks: Int,
    val completionPercent: Int,
    val focusStreakDays: Int,
    val categoryFocus: List<CategoryFocus>,
)

/**
 * Builds one deterministic review window from detailed focus sessions plus the
 * compacted daily totals retained after old session details are pruned.
 */
fun productivityInsights(
    data: AppData,
    through: LocalDate,
    rangeDays: Int,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
    elapsedNow: Long? = null,
): ProductivityInsights {
    val count = rangeDays.coerceIn(1, 365)
    val start = through.minusDays(count.toLong() - 1)
    val dates = generateSequence(start) { it.plusDays(1) }.take(count).toList()
    val focusByDate = linkedMapOf<LocalDate, Long>()
    val focusByCategory = linkedMapOf<String, Long>()

    data.focusDailyTotals.forEach { total ->
        val date = runCatching { total.date.toDate() }.getOrNull() ?: return@forEach
        if (date !in start..through) return@forEach
        focusByDate[date] = focusByDate.getOrDefault(date, 0L) + total.activeSeconds
        focusByCategory[total.categoryId] = focusByCategory.getOrDefault(total.categoryId, 0L) + total.activeSeconds
    }
    data.focusSessions.forEach { session ->
        val date = Instant.ofEpochMilli(session.startedAt).atZone(zone).toLocalDate()
        if (date !in start..through) return@forEach
        val seconds = focusElapsedSeconds(session, nowMillis, elapsedNow)
        focusByDate[date] = focusByDate.getOrDefault(date, 0L) + seconds
        focusByCategory[session.categoryIdSnapshot] =
            focusByCategory.getOrDefault(session.categoryIdSnapshot, 0L) + seconds
    }

    val blocksByDate = data.taskBlocks.groupBy { it.date }
    val days = dates.map { date ->
        val blocks = blocksByDate[date.toIso()].orEmpty()
        ProductivityDay(
            date = date,
            plannedMinutes = blocks.sumOf { it.durationMin },
            focusedMinutes = (focusByDate[date].orZero() / 60L).toInt(),
            completedBlocks = blocks.count { it.state == BlockState.COMPLETED },
            skippedBlocks = blocks.count { it.state == BlockState.SKIPPED },
        )
    }
    val completed = days.sumOf { it.completedBlocks }
    val skipped = days.sumOf { it.skippedBlocks }
    val resolved = completed + skipped
    val streakAnchor = if (days.lastOrNull()?.focusedMinutes.orZero() > 0) through else through.minusDays(1)
    val streak = generateSequence(streakAnchor) { it.minusDays(1) }
        .takeWhile { focusByDate[it].orZero() > 0L }
        .count()

    return ProductivityInsights(
        days = days,
        totalPlannedMinutes = days.sumOf { it.plannedMinutes },
        totalFocusedMinutes = days.sumOf { it.focusedMinutes },
        completedBlocks = completed,
        skippedBlocks = skipped,
        completionPercent = if (resolved == 0) 0 else completed * 100 / resolved,
        focusStreakDays = streak,
        categoryFocus = focusByCategory.entries
            .map { CategoryFocus(it.key, (it.value / 60L).toInt()) }
            .filter { it.focusedMinutes > 0 }
            .sortedByDescending { it.focusedMinutes },
    )
}

private fun Long?.orZero(): Long = this ?: 0L
private fun Int?.orZero(): Int = this ?: 0
