package com.bento.calendar

import com.bento.calendar.data.BlockState
import com.bento.calendar.data.BusyInterval
import com.bento.calendar.data.Priority
import com.bento.calendar.data.TaskBlock
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.WorkHours
import com.bento.calendar.data.planningCandidates
import com.bento.calendar.data.suggestDayPlan
import com.bento.calendar.data.suggestWeekPlan
import com.bento.calendar.data.summarizeDayPlan
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

    @Test fun `same day replanning never proposes a block before now`() {
        val task = TaskItem("t", "Task", estimateMin = 45)
        val result = suggestDayPlan(
            day, listOf(task), emptyList(), emptyList(), hours, notBeforeMin = 623,
        )
        assertEquals(630, result.suggestions.single().startMin)
    }

    @Test fun `expired planned blocks no longer consume a task estimate`() {
        val expired = TaskBlock("old", "t", date = day.minusDays(1).toString(), startMin = 540, durationMin = 60)
        val result = suggestDayPlan(
            day, listOf(TaskItem("t", "Task", estimateMin = 60)), listOf(expired), emptyList(), hours,
        )
        assertEquals(60, result.suggestions.sumOf { it.durationMin })
    }

    @Test fun `missed blocks earlier today do not consume a replan estimate`() {
        val missed = TaskBlock("old", "t", date = day.toString(), startMin = 540, durationMin = 30)
        val result = suggestDayPlan(
            day, listOf(TaskItem("t", "Task", estimateMin = 30)), listOf(missed), emptyList(), hours,
            notBeforeMin = 600,
        )
        assertEquals(600, result.suggestions.single().startMin)
    }

    @Test fun `future planned blocks still prevent over allocating an estimate`() {
        val future = TaskBlock("future", "t", date = day.plusDays(1).toString(), startMin = 540, durationMin = 45)
        val result = suggestDayPlan(
            day, listOf(TaskItem("t", "Task", estimateMin = 60)), listOf(future), emptyList(), hours,
        )
        assertEquals(15, result.suggestions.sumOf { it.durationMin })
    }

    @Test fun `another recurring cycle does not consume this cycles estimate`() {
        val task = TaskItem("t", "Weekly", due = day.toString(), recur = Recur.WEEKLY, estimateMin = 30)
        val nextCycle = TaskBlock(
            "next", "t", day.plusWeeks(1).toString(), day.plusWeeks(1).toString(), 540, 30,
        )
        val result = suggestDayPlan(day, listOf(task), listOf(nextCycle), emptyList(), hours)
        assertEquals(30, result.suggestions.sumOf { it.durationMin })
    }

    @Test fun `overload remains visible rather than spilling days`() {
        val task = TaskItem("t", "Large", estimateMin = 600)
        val result = suggestDayPlan(day, listOf(task), emptyList(), emptyList(), hours)
        assertEquals(480, result.suggestions.sumOf { it.durationMin })
        assertEquals(120, result.unscheduledMinutes["t"])
    }

    @Test fun `week capacity merges overlapping meetings instead of double counting`() {
        val summary = summarizeDayPlan(
            day,
            emptyList(),
            listOf(BusyInterval(600, 660), BusyInterval(630, 690)),
            hours,
        )
        assertEquals(390, summary.availableMinutes)
    }

    @Test fun `week summary counts only unresolved blocks as planned work`() {
        val blocks = listOf(
            TaskBlock("open", "t", date = day.toString(), startMin = 540, durationMin = 60),
            TaskBlock("done", "t", date = day.toString(), startMin = 600, durationMin = 30, state = BlockState.COMPLETED),
            TaskBlock("skip", "t", date = day.toString(), startMin = 630, durationMin = 30, state = BlockState.SKIPPED),
        )
        val summary = summarizeDayPlan(day, blocks, emptyList(), hours)
        assertEquals(60, summary.plannedMinutes)
        assertEquals(30, summary.completedMinutes)
        assertEquals(30, summary.skippedMinutes)
        assertEquals(1, summary.completedBlocks)
        assertEquals(1, summary.skippedBlocks)
    }

    @Test fun `day off summary has no capacity and exposes overload`() {
        val block = TaskBlock("open", "t", date = day.toString(), startMin = 540, durationMin = 30)
        val summary = summarizeDayPlan(day, listOf(block), emptyList(), hours.copy(enabled = false))
        assertEquals(0, summary.availableMinutes)
        assertEquals(30, summary.overloadMinutes)
    }

    @Test fun `week plan carries unfinished effort into the next workday`() {
        val shortDays = (1..7).map { WorkHours(it, it <= 5, "09:00", "10:00") }
        val result = suggestWeekPlan(
            day, day, listOf(TaskItem("t", "Deep work", estimateMin = 120)),
            emptyList(), emptyMap(), shortDays,
        )
        assertEquals(
            listOf(day to 60, day.plusDays(1) to 60),
            result.suggestions.map { it.date to it.durationMin },
        )
        assertTrue(result.unscheduledMinutes.isEmpty())
    }

    @Test fun `week plan respects calendar blocks and current time`() {
        val result = suggestWeekPlan(
            weekStart = day,
            fromDate = day,
            tasks = listOf(TaskItem("t", "Task", estimateMin = 30)),
            existingBlocks = emptyList(),
            calendarBusy = mapOf(day to listOf(BusyInterval(615, 660))),
            workHours = listOf(hours),
            notBeforeMin = 607,
        )
        assertEquals(660, result.suggestions.single().startMin)
    }

    @Test fun `dated work is never placed after its deadline`() {
        val shortDays = (1..7).map { WorkHours(it, it <= 5, "09:00", "10:00") }
        val urgent = TaskItem("urgent", "Urgent", due = day.toString(), estimateMin = 90)
        val later = TaskItem("later", "Later", estimateMin = 60)
        val result = suggestWeekPlan(day, day, listOf(later, urgent), emptyList(), emptyMap(), shortDays)
        assertEquals(listOf("urgent"), result.suggestions.filter { it.date == day }.map { it.taskId })
        assertEquals(30, result.unscheduledMinutes["urgent"])
        assertEquals(day.plusDays(1), result.suggestions.first { it.taskId == "later" }.date)
    }

    @Test fun `existing future allocation is subtracted before week planning`() {
        val existing = TaskBlock("b", "t", date = day.plusDays(2).toString(), startMin = 540, durationMin = 60)
        val result = suggestWeekPlan(
            day, day, listOf(TaskItem("t", "Task", estimateMin = 90)),
            listOf(existing), emptyMap(), listOf(hours),
        )
        assertEquals(30, result.suggestions.sumOf { it.durationMin })
    }

    @Test fun `future week preview counts work already planned before that week`() {
        val futureWeek = day.plusWeeks(1)
        val existing = TaskBlock("b", "t", date = day.plusDays(1).toString(), startMin = 540, durationMin = 60)
        val result = suggestWeekPlan(
            futureWeek, futureWeek, listOf(TaskItem("t", "Task", estimateMin = 90)),
            listOf(existing), emptyMap(), WorkHours.defaults(), allocationFromDate = day,
        )
        assertEquals(30, result.suggestions.sumOf { it.durationMin })
    }

    @Test fun `missed block earlier today does not reduce week estimate`() {
        val missed = TaskBlock("b", "t", date = day.toString(), startMin = 540, durationMin = 60)
        val result = suggestWeekPlan(
            day, day, listOf(TaskItem("t", "Task", estimateMin = 60)),
            listOf(missed), emptyMap(), WorkHours.defaults(),
            notBeforeMin = 660,
            allocationFromDate = day,
            allocationNotBeforeMin = 660,
        )
        assertEquals(60, result.suggestions.sumOf { it.durationMin })
        assertEquals(660, result.suggestions.first().startMin)
    }

    @Test fun `week plan never writes into days before its planning date`() {
        val result = suggestWeekPlan(
            day, day.plusDays(2), listOf(TaskItem("t", "Task", estimateMin = 30)),
            emptyList(), emptyMap(), WorkHours.defaults(),
        )
        assertEquals(day.plusDays(2), result.suggestions.single().date)
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
