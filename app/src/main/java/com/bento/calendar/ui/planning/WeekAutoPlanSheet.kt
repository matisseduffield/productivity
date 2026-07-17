package com.bento.calendar.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.BusyInterval
import com.bento.calendar.data.Priority
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.data.suggestWeekPlan
import com.bento.calendar.data.toDate
import com.bento.calendar.data.toIso
import com.bento.calendar.data.toMins
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.components.BentoCheckbox
import com.bento.calendar.ui.components.BentoSheet
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.components.tap
import com.bento.calendar.ui.theme.LocalBento
import java.time.LocalDate
import java.time.LocalDateTime

/** Review-before-write multi-day planner. Selection and preview stay transient. */
@Composable
fun WeekAutoPlanSheet(
    vm: AppViewModel,
    data: AppData,
    weekStart: LocalDate,
    now: LocalDateTime,
    onDismiss: () -> Unit,
) {
    val c = LocalBento.current
    val today = now.toLocalDate()
    val weekEnd = weekStart.plusDays(6)
    val candidates = remember(data.tasks, weekStart) {
        data.tasks.withIndex().filter { !it.value.done }.sortedWith(
            compareBy<IndexedValue<TaskItem>>(
                { it.value.due?.toDate() ?: LocalDate.MAX },
                { -it.value.priority },
                { it.index },
            ),
        ).map { it.value }
    }
    val initial = remember(candidates, weekEnd) {
        candidates.filter { task ->
            task.priority == Priority.HIGH || task.due?.toDate()?.let { !it.isAfter(weekEnd) } == true
        }.ifEmpty { candidates.take(3) }.mapTo(linkedSetOf()) { it.id }
    }
    var selected by remember(weekStart, candidates) { mutableStateOf<Set<String>>(initial) }
    val busy = remember(data.events, vm.deviceEvents, weekStart) {
        (0L..6L).associate { offset ->
            val date = weekStart.plusDays(offset)
            val intervals = occurrencesOn(data.events, date).filterNot { it.allDay }
                .map { BusyInterval(it.start.toMins(), it.end.toMins()) } +
                vm.deviceEvents[date.toIso()].orEmpty().filterNot { it.allDay }
                    .map { BusyInterval(it.start.toMins(), it.end.toMins()) }
            date to intervals
        }
    }
    val notBefore = if (today in weekStart..weekEnd) {
        ((now.hour * 60 + now.minute + 14) / 15 * 15).coerceAtMost(1439)
    } else null
    val result = remember(selected, data.taskBlocks, data.prefs, busy, weekStart, today, notBefore) {
        suggestWeekPlan(
            weekStart = weekStart,
            fromDate = maxOf(weekStart, today),
            tasks = candidates.filter { it.id in selected },
            existingBlocks = data.taskBlocks,
            calendarBusy = busy,
            workHours = data.prefs.workHours,
            defaultEstimateMin = data.prefs.defaultTaskEstimateMin,
            notBeforeMin = notBefore,
            allocationFromDate = today,
            allocationNotBeforeMin = notBefore,
        )
    }
    val byId = candidates.associateBy { it.id }
    val scheduledMinutes = result.suggestions.sumOf { it.durationMin }
    val overflowMinutes = result.unscheduledMinutes.values.sum()

    BentoSheet(onDismiss = onDismiss) {
        Text("Auto-plan this week", fontSize = 19.sp, fontWeight = FontWeight.W700, color = c.tx)
        Text(
            "Choose work, review every proposed block, then add it around your existing plans. Nothing already scheduled is moved.",
            fontSize = 11.5.sp,
            color = c.sub,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (candidates.isEmpty()) {
            Text("No open tasks to plan.", fontSize = 12.sp, color = c.faint, modifier = Modifier.padding(vertical = 24.dp))
        } else {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "CHOOSE WORK",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.12.em,
                    color = c.sub,
                    modifier = Modifier.weight(1f),
                )
                Text("Recommended", fontSize = 10.sp, fontWeight = FontWeight.W700, color = c.acc, modifier = Modifier.tap { selected = initial }.padding(5.dp))
                Text("Clear", fontSize = 10.sp, fontWeight = FontWeight.W700, color = c.faint, modifier = Modifier.tap { selected = emptySet() }.padding(5.dp))
            }
            candidates.forEach { task ->
                val checked = task.id in selected
                Row(
                    Modifier.fillMaxWidth().tap {
                        selected = if (checked) selected - task.id else selected + task.id
                    }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BentoCheckbox(
                        checked,
                        onToggle = { selected = if (checked) selected - task.id else selected + task.id },
                        size = 18.dp,
                        corner = 6.dp,
                    )
                    Column(Modifier.padding(start = 9.dp).weight(1f)) {
                        Text(task.title, fontSize = 12.5.sp, color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val due = task.due?.toDate()
                        if (due != null || task.priority > Priority.NONE) {
                            Text(
                                listOfNotNull(
                                    due?.let { "Due ${Fmt.dayShort(it)}" },
                                    task.priority.takeIf { it > 0 }?.let { Priority.label(it) },
                                ).joinToString(" · "),
                                fontSize = 9.5.sp,
                                color = c.faint,
                            )
                        }
                    }
                    Text(
                        "${task.estimateMin ?: data.prefs.defaultTaskEstimateMin}m" + if (task.estimateMin == null) "*" else "",
                        fontSize = 10.5.sp,
                        color = c.faint,
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp)
                    .background(c.accTint(0.1f), RoundedCornerShape(14.dp))
                    .border(1.dp, c.accTint(0.25f), RoundedCornerShape(14.dp)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${durationLabel(scheduledMinutes)} scheduled", fontSize = 12.5.sp, fontWeight = FontWeight.W700, color = c.tx)
                    Text("${result.suggestions.size} new ${if (result.suggestions.size == 1) "block" else "blocks"}", fontSize = 10.sp, color = c.faint)
                }
                if (overflowMinutes > 0) Text("${durationLabel(overflowMinutes)} won’t fit", fontSize = 10.5.sp, fontWeight = FontWeight.W700, color = c.dng)
            }
            if (result.unscheduledMinutes.isNotEmpty()) {
                Column(Modifier.fillMaxWidth().padding(top = 7.dp)) {
                    result.unscheduledMinutes.entries.take(5).forEach { (taskId, minutes) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                byId[taskId]?.title ?: "Task",
                                fontSize = 10.5.sp,
                                color = c.sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${durationLabel(minutes)} unscheduled", fontSize = 10.sp, color = c.dng)
                        }
                    }
                    if (result.unscheduledMinutes.size > 5) {
                        Text("+${result.unscheduledMinutes.size - 5} more tasks", fontSize = 9.5.sp, color = c.faint)
                    }
                }
            }

            if (result.suggestions.isNotEmpty()) {
                Text(
                    "PROPOSED WEEK",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.12.em,
                    color = c.sub,
                    modifier = Modifier.padding(top = 15.dp),
                )
                result.suggestions.groupBy { it.date }.forEach { (date, suggestions) ->
                    Text(Fmt.dayShort(date), fontSize = 11.sp, fontWeight = FontWeight.W700, color = c.acc, modifier = Modifier.padding(top = 9.dp))
                    suggestions.forEach { suggestion ->
                        Row(Modifier.fillMaxWidth().padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                Fmt.time(com.bento.calendar.data.minsToHm(suggestion.startMin), data.prefs.use24h),
                                fontSize = 10.5.sp,
                                color = c.faint,
                                modifier = Modifier.width(57.dp),
                            )
                            Text(
                                byId[suggestion.taskId]?.title ?: "Task",
                                fontSize = 11.5.sp,
                                color = c.tx,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Text("${suggestion.durationMin}m", fontSize = 9.5.sp, color = c.sub)
                        }
                    }
                }
            } else if (selected.isNotEmpty()) {
                Text(
                    "No additional time is available before these tasks’ deadlines, or their estimates are already fully planned.",
                    fontSize = 11.sp,
                    color = c.faint,
                    modifier = Modifier.padding(top = 13.dp),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.weight(1f).tap(onClick = onDismiss).background(c.inp, RoundedCornerShape(12.dp)).padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) { Text("Cancel", fontSize = 12.sp, fontWeight = FontWeight.W700, color = c.sub) }
            Box(
                Modifier.weight(1f).pressable(enabled = result.suggestions.isNotEmpty()) {
                    vm.confirmWeekPlan(result)
                    onDismiss()
                }.background(if (result.suggestions.isNotEmpty()) c.acc else c.inp, RoundedCornerShape(12.dp)).padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (result.suggestions.isEmpty()) "Nothing to add" else "Add ${result.suggestions.size} blocks",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W700,
                    color = if (result.suggestions.isNotEmpty()) Color.White else c.faint,
                )
            }
        }
        if (candidates.any { it.estimateMin == null }) {
            Text("* Uses your default task estimate", fontSize = 9.5.sp, color = c.faint, modifier = Modifier.padding(top = 8.dp))
        }
        Spacer(Modifier.padding(bottom = 2.dp))
    }
}

private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
