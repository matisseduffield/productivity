package com.bento.calendar

import com.bento.calendar.data.AppData
import com.bento.calendar.data.BlockState
import com.bento.calendar.data.FocusDailyTotal
import com.bento.calendar.data.FocusOutcome
import com.bento.calendar.data.FocusSession
import com.bento.calendar.data.TaskBlock
import com.bento.calendar.data.productivityInsights
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class InsightsTest {
    private val today = LocalDate.of(2026, 7, 17)
    private fun epoch(date: LocalDate) = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun `insights combine detailed sessions and compacted totals`() {
        val yesterday = today.minusDays(1)
        val data = AppData(
            focusSessions = listOf(FocusSession(
                id = "recent", taskTitleSnapshot = "Write", categoryIdSnapshot = "work",
                startedAt = epoch(today), endedAt = epoch(today) + 1_800_000,
                activeSeconds = 1_800, runningSince = null, outcome = FocusOutcome.FINISHED,
            )),
            focusDailyTotals = listOf(FocusDailyTotal(yesterday.toString(), "work", 3_600)),
        )
        val result = productivityInsights(data, today, 7, ZoneOffset.UTC)
        assertEquals(90, result.totalFocusedMinutes)
        assertEquals(90, result.categoryFocus.single().focusedMinutes)
        assertEquals(2, result.focusStreakDays)
    }

    @Test fun `completion rate uses resolved blocks only`() {
        val data = AppData(taskBlocks = listOf(
            TaskBlock("done", "t", date = today.toString(), startMin = 540, durationMin = 30, state = BlockState.COMPLETED),
            TaskBlock("skip", "t", date = today.toString(), startMin = 600, durationMin = 30, state = BlockState.SKIPPED),
            TaskBlock("open", "t", date = today.toString(), startMin = 660, durationMin = 30),
        ))
        val result = productivityInsights(data, today, 7, ZoneOffset.UTC)
        assertEquals(50, result.completionPercent)
        assertEquals(90, result.totalPlannedMinutes)
    }

    @Test fun `a quiet current day does not break yesterdays active streak`() {
        val yesterday = today.minusDays(1)
        val data = AppData(focusDailyTotals = listOf(
            FocusDailyTotal(yesterday.toString(), "", 600),
            FocusDailyTotal(yesterday.minusDays(1).toString(), "", 600),
        ))
        assertEquals(2, productivityInsights(data, today, 7, ZoneOffset.UTC).focusStreakDays)
    }
}
