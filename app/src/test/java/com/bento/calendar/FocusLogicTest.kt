package com.bento.calendar

import com.bento.calendar.data.AppData
import com.bento.calendar.data.FocusOutcome
import com.bento.calendar.data.FocusSession
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.TaskBlock
import com.bento.calendar.data.BlockState
import com.bento.calendar.data.activeFocus
import com.bento.calendar.data.extendFocus
import com.bento.calendar.data.finishFocus
import com.bento.calendar.data.focusElapsedSeconds
import com.bento.calendar.data.pauseFocus
import com.bento.calendar.data.resumeFocus
import com.bento.calendar.data.startFocus
import com.bento.calendar.data.compactFocusHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class FocusLogicTest {
    private val base = AppData(tasks = listOf(TaskItem("t", "Write")))

    @Test fun `session survives pause and resume without counting paused time`() {
        val started = startFocus(base, "t", null, 1_000L, 1_800L)
        val paused = pauseFocus(started, 61_000L)
        assertEquals(60, activeFocus(paused)!!.activeSeconds)
        val resumed = resumeFocus(paused, 121_000L)
        assertEquals(90, focusElapsedSeconds(activeFocus(resumed)!!, 151_000L))
    }

    @Test fun `only one active session can exist`() {
        val once = startFocus(base, "t", null, 1_000L, 1_800L)
        val twice = startFocus(once, "t", null, 2_000L, 600L)
        assertEquals(1, twice.focusSessions.size)
    }

    @Test fun `finish records actual seconds and clears active focus`() {
        val started = startFocus(base, "t", null, 1_000L, 1_800L)
        val done = finishFocus(started, 31_000L)
        assertNull(activeFocus(done))
        assertEquals(FocusOutcome.FINISHED, done.focusSessions.single().outcome)
        assertEquals(30, done.focusSessions.single().activeSeconds)
    }

    @Test fun `finishing a full block records actual minutes and completes it`() {
        val block = TaskBlock("b", "t", date = "2026-07-13", startMin = 540, durationMin = 30)
        val started = startFocus(base.copy(taskBlocks = listOf(block)), "t", "b", 1_000L, 1_800L)
        val done = finishFocus(started, 1_801_000L)
        assertEquals(30, done.taskBlocks.single().actualMinutes)
        assertEquals(BlockState.COMPLETED, done.taskBlocks.single().state)
    }

    @Test fun `ending a partial block preserves it for another focus session`() {
        val block = TaskBlock("b", "t", date = "2026-07-13", startMin = 540, durationMin = 30)
        val started = startFocus(base.copy(taskBlocks = listOf(block)), "t", "b", 1_000L, 1_800L)
        val stopped = finishFocus(started, 601_000L)
        assertEquals(10, stopped.taskBlocks.single().actualMinutes)
        assertEquals(BlockState.PLANNED, stopped.taskBlocks.single().state)
    }

    @Test fun `extend adds fifteen minutes by default`() {
        val started = startFocus(base, "t", null, 1_000L, 1_800L)
        assertEquals(2_700L, activeFocus(extendFocus(started))!!.targetSeconds)
    }

    @Test fun `old details compact into daily category totals`() {
        val oldDay = LocalDate.of(2025, 1, 1)
        val old = FocusSession(
            id = "old",
            taskTitleSnapshot = "Write",
            categoryIdSnapshot = "work",
            startedAt = oldDay.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            endedAt = oldDay.atStartOfDay().plusMinutes(30).toInstant(ZoneOffset.UTC).toEpochMilli(),
            activeSeconds = 1_800,
            runningSince = null,
            targetSeconds = 1_800,
            outcome = FocusOutcome.FINISHED,
        )
        val compacted = compactFocusHistory(
            AppData(focusSessions = listOf(old)),
            LocalDate.of(2026, 7, 13),
            ZoneOffset.UTC,
        )
        assertEquals(0, compacted.focusSessions.size)
        assertEquals(1_800, compacted.focusDailyTotals.single().activeSeconds)
        assertEquals("work", compacted.focusDailyTotals.single().categoryId)
    }
}
