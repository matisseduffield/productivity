package com.bento.calendar

import com.bento.calendar.data.BlockState
import com.bento.calendar.data.BusyInterval
import com.bento.calendar.data.Priority
import com.bento.calendar.data.TaskBlock
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.WorkHours
import com.bento.calendar.data.planningCandidates
import com.bento.calendar.data.suggestDayPlan
import com.bento.calendar.data.completeTaskWithBlocks
import com.bento.calendar.data.AppData
import com.bento.calendar.data.Recur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PlannerTest {
    private val day = LocalDate.of(2026, 7, 13)
    private val hours = WorkHours(1, true, "09:00", "17:00")

    @Test fun `suggestions avoid events and split at their boundary`() {
        val task = TaskItem("t", "Write", estimateMin = 120)
        val result = suggestDayPlan(
            day, listOf(task), emptyList(), listOf(BusyInterval(600, 660)), hours,
        )
        assertEquals(listOf(540 to 60, 660 to 60), result.suggestions.map { it.startMin to it.durationMin })
    }

    @Test fun `manual blocks are immutable busy time`() {
        val block = TaskBlock("b", "old", date = day.toString(), startMin = 540, durationMin = 60)
        val result = suggestDayPlan(day, listOf(TaskItem("t", "New", estimateMin = 30)), listOf(block), emptyList(), hours)
        assertEquals(600, result.suggestions.single().startMin)
    }

    @Test fun `non-grid meetings reserve their whole touched quarter hours`() {
        val task = TaskItem("t", "Task", estimateMin = 30)
        val result = suggestDayPlan(day, listOf(task), emptyList(), listOf(BusyInterval(547, 607)), hours)
        assertEquals(615, result.suggestions.single().startMin)
    }

    @Test fun `overload remains visible rather than spilling days`() {
        val task = TaskItem("t", "Large", estimateMin = 600)
        val result = suggestDayPlan(day, listOf(task), emptyList(), emptyList(), hours)
        assertEquals(480, result.suggestions.sumOf { it.durationMin })
        assertEquals(120, result.unscheduledMinutes["t"])
    }

    @Test fun `disabled workday schedules nothing`() {
        val result = suggestDayPlan(day, listOf(TaskItem("t", "Task", estimateMin = 30)), emptyList(), emptyList(), hours.copy(enabled = false))
        assertTrue(result.suggestions.isEmpty())
        assertEquals(30, result.unscheduledMinutes["t"])
    }

    @Test fun `candidate order favors overdue then due soon then high priority`() {
        val tasks = listOf(
            TaskItem("high", "High", priority = Priority.HIGH),
            TaskItem("soon", "Soon", due = day.plusDays(2).toString()),
            TaskItem("late", "Late", due = day.minusDays(1).toString()),
            TaskItem("none", "None"),
        )
        assertEquals(listOf("late", "soon", "high"), planningCandidates(tasks, day).map { it.id })
    }

    @Test fun `completed or skipped blocks do not consume future capacity`() {
        val done = TaskBlock("b", "old", date = day.toString(), startMin = 540, durationMin = 60, state = BlockState.COMPLETED)
        val result = suggestDayPlan(day, listOf(TaskItem("t", "Task", estimateMin = 30)), listOf(done), emptyList(), hours)
        assertEquals(540, result.suggestions.single().startMin)
    }

    @Test fun `completing a task closes current blocks and skips future ones`() {
        val current = TaskBlock("now", "t", date = day.toString(), startMin = 540, durationMin = 30)
        val future = TaskBlock("later", "t", date = day.plusDays(1).toString(), startMin = 540, durationMin = 30)
        val result = completeTaskWithBlocks(
            AppData(tasks = listOf(TaskItem("t", "Task")), taskBlocks = listOf(current, future)),
            "t", day, now = 99,
        )
        assertEquals(BlockState.COMPLETED, result.taskBlocks[0].state)
        assertEquals(BlockState.SKIPPED, result.taskBlocks[1].state)
    }

    @Test fun `recurring completion only closes the current due cycle`() {
        val task = TaskItem("t", "Weekly", due = day.toString(), recur = Recur.WEEKLY)
        val current = TaskBlock("a", "t", day.toString(), day.toString(), 540, 30)
        val next = TaskBlock("b", "t", day.plusWeeks(1).toString(), day.plusWeeks(1).toString(), 540, 30)
        val result = completeTaskWithBlocks(AppData(tasks = listOf(task), taskBlocks = listOf(current, next)), "t", day)
        assertEquals(BlockState.COMPLETED, result.taskBlocks[0].state)
        assertEquals(BlockState.PLANNED, result.taskBlocks[1].state)
    }
}
