package com.bento.calendar.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.BlockState
import com.bento.calendar.data.BusyInterval
import com.bento.calendar.data.DayPlanningSummary
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.data.summarizeDayPlan
import com.bento.calendar.data.toIso
import com.bento.calendar.data.toMins
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.components.FullOverlay
import com.bento.calendar.ui.components.tap
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private data class WeekDayPlan(
    val summary: DayPlanningSummary,
    val blocks: List<com.bento.calendar.data.TaskBlock>,
)

/** Seven-day capacity board and the entry point for planning future days. */
@Composable
fun WeekPlanOverlay(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    val c = LocalBento.current
    val anchor = vm.weekPlannerAnchor
    val days = remember(data, vm.deviceEvents, anchor) {
        (0L..6L).map { offset ->
            val date = anchor.plusDays(offset)
            val iso = date.toIso()
            val busy = occurrencesOn(data.events, date).filterNot { it.allDay }
                .map { BusyInterval(it.start.toMins(), it.end.toMins()) } +
                vm.deviceEvents[iso].orEmpty().filterNot { it.allDay }
                    .map { BusyInterval(it.start.toMins(), it.end.toMins()) }
            val hours = data.prefs.workHours.firstOrNull { it.day == date.dayOfWeek.value }
                ?: com.bento.calendar.data.WorkHours.defaults().first { it.day == date.dayOfWeek.value }
            WeekDayPlan(
                summary = summarizeDayPlan(date, data.taskBlocks, busy, hours),
                blocks = data.taskBlocks.filter { it.date == iso }
                    .sortedBy { it.startMin },
            )
        }
    }
    val totalCapacity = days.sumOf { it.summary.availableMinutes }
    val totalPlanned = days.sumOf { it.summary.plannedMinutes }
    val totalCompleted = days.sumOf { it.summary.completedMinutes }
    val overloaded = days.count { it.summary.overloadMinutes > 0 }
    val historical = anchor.plusDays(6).isBefore(now.toLocalDate())
    val rangeLabel = if (anchor.month == anchor.plusDays(6).month) {
        "${anchor.dayOfMonth}–${anchor.plusDays(6).dayOfMonth} ${anchor.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}"
    } else {
        "${anchor.format(DateTimeFormatter.ofPattern("d MMM"))} – ${anchor.plusDays(6).format(DateTimeFormatter.ofPattern("d MMM"))}"
    }

    FullOverlay {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                HeaderButton(BentoIcons.ChevronLeft) { vm.closeWeekPlanner() }
                Column(Modifier.weight(1f)) {
                    Text("Plan ahead", fontSize = 21.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.01).em, color = c.tx)
                    Text(rangeLabel, fontSize = 10.5.sp, color = c.faint)
                }
                HeaderButton(BentoIcons.ChevronLeft) { vm.shiftWeekPlanner(-1) }
                HeaderButton(BentoIcons.ChevronRight) { vm.shiftWeekPlanner(1) }
            }
            Column(
                Modifier.weight(1f)
                    .pointerInput(anchor) {
                        var total = 0f
                        val threshold = 56.dp.toPx()
                        detectHorizontalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                when {
                                    total <= -threshold -> vm.shiftWeekPlanner(1)
                                    total >= threshold -> vm.shiftWeekPlanner(-1)
                                }
                            },
                        ) { _, amount -> total += amount }
                    }
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                WeekSummary(
                    totalPlanned,
                    totalCompleted,
                    totalCapacity,
                    overloaded,
                    historical,
                    onCurrentWeek = { vm.resetWeekPlanner() },
                    onAutoPlan = if (historical) null else ({ vm.openWeekAutoPlan() }),
                )
                days.forEach { day ->
                    DayPlanCard(vm, data, day, now.toLocalDate())
                }
            }
        }
        if (vm.weekAutoPlanOpen) {
            WeekAutoPlanSheet(vm, data, anchor, now, onDismiss = { vm.closeWeekAutoPlan() })
        }
    }
}

@Composable
private fun HeaderButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    val c = LocalBento.current
    Box(
        Modifier.size(36.dp).tap(onClick = onClick).background(c.tile, CircleShape).border(1.dp, c.bd, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = c.sub, modifier = Modifier.size(17.dp)) }
}

@Composable
private fun WeekSummary(
    planned: Int,
    completed: Int,
    capacity: Int,
    overloaded: Int,
    historical: Boolean,
    onCurrentWeek: () -> Unit,
    onAutoPlan: (() -> Unit)?,
) {
    val c = LocalBento.current
    Row(
        Modifier.fillMaxWidth().background(c.accTint(0.1f), RoundedCornerShape(17.dp))
            .border(1.dp, c.accTint(0.28f), RoundedCornerShape(17.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("WEEK CAPACITY", fontSize = 9.sp, fontWeight = FontWeight.W700, letterSpacing = 0.1.em, color = c.acc)
            Text(
                if (historical) "${duration(completed)} completed of ${duration(capacity)} capacity"
                else "${duration(planned)} planned of ${duration(capacity)} available",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.W700,
                color = c.tx,
                modifier = Modifier.padding(top = 4.dp),
            )
            when {
                historical && completed > 0 -> Text("Review of finished focus blocks", fontSize = 10.5.sp, color = c.faint)
                overloaded > 0 -> Text("$overloaded overloaded ${if (overloaded == 1) "day" else "days"}", fontSize = 10.5.sp, color = c.dng)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (onAutoPlan != null) {
                Text(
                    "Auto-plan",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.W700,
                    color = Color.White,
                    modifier = Modifier.tap(onClick = onAutoPlan).background(c.acc, CircleShape).padding(horizontal = 11.dp, vertical = 7.dp),
                )
            }
            Text(
                "This week",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.W700,
                color = c.acc,
                modifier = Modifier.tap(onClick = onCurrentWeek).background(c.tile, CircleShape).padding(horizontal = 10.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun DayPlanCard(vm: AppViewModel, data: AppData, day: WeekDayPlan, today: LocalDate) {
    val c = LocalBento.current
    val summary = day.summary
    val date = summary.date
    val tasks = data.tasks.associateBy { it.id }
    val isToday = date == today
    val isPast = date.isBefore(today)
    val displayMinutes = if (isPast) summary.completedMinutes else summary.plannedMinutes
    val ratio = if (summary.availableMinutes > 0) {
        (displayMinutes.toFloat() / summary.availableMinutes).coerceIn(0f, 1f)
    } else 0f
    Column(
        Modifier.fillMaxWidth().background(c.tile, RoundedCornerShape(18.dp))
            .border(1.dp, if (isToday) c.accTint(0.4f) else c.bd, RoundedCornerShape(18.dp)).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).tap {
                vm.closeWeekPlanner()
                vm.weekStripTap(date)
            }) {
                Text(
                    if (isToday) "TODAY" else date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.1.em,
                    color = if (isToday) c.acc else c.faint,
                )
                Text(Fmt.dayShort(date), fontSize = 14.sp, fontWeight = FontWeight.W700, color = c.tx, modifier = Modifier.padding(top = 2.dp))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    when {
                        !summary.enabled -> "Day off"
                        isPast -> "${duration(summary.completedMinutes)} done / ${duration(summary.availableMinutes)}"
                        else -> "${duration(summary.plannedMinutes)} / ${duration(summary.availableMinutes)}"
                    },
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.W700,
                    color = if (summary.overloadMinutes > 0) c.dng else c.sub,
                )
                if (summary.overloadMinutes > 0) {
                    Text("${summary.overloadMinutes}m over", fontSize = 9.5.sp, color = c.dng)
                } else if (summary.completedBlocks > 0 || summary.skippedBlocks > 0) {
                    Text("${summary.completedBlocks} done · ${summary.skippedBlocks} skipped", fontSize = 9.5.sp, color = c.faint)
                }
            }
        }
        Box(Modifier.fillMaxWidth().padding(top = 9.dp).height(5.dp).background(c.inp, CircleShape)) {
            if (ratio > 0f) Box(
                Modifier.fillMaxWidth(ratio).fillMaxHeight()
                    .background(if (!isPast && summary.overloadMinutes > 0) c.dng else c.acc, CircleShape),
            )
        }
        if (day.blocks.isNotEmpty()) {
            Column(Modifier.padding(top = 7.dp)) {
                day.blocks.take(3).forEach { block ->
                    val task = tasks[block.taskId]
                    Row(
                        Modifier.fillMaxWidth().tap { if (task != null) vm.openTask(task, switchTab = false) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(Fmt.time(com.bento.calendar.data.minsToHm(block.startMin), data.prefs.use24h), fontSize = 10.5.sp, color = c.faint, modifier = Modifier.width(58.dp))
                        Text(task?.title ?: "Deleted task", fontSize = 11.5.sp, color = c.tx, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(
                            when (block.state) {
                                BlockState.COMPLETED -> "Done"
                                BlockState.SKIPPED -> "Skipped"
                                else -> "${block.durationMin}m"
                            },
                            fontSize = 9.5.sp,
                            color = when (block.state) {
                                BlockState.COMPLETED -> c.acc
                                BlockState.SKIPPED -> c.faint
                                else -> c.sub
                            },
                        )
                    }
                }
                if (day.blocks.size > 3) Text("+${day.blocks.size - 3} more", fontSize = 9.5.sp, color = c.faint)
            }
        } else {
            Text(
                if (summary.enabled) "Nothing planned yet" else "Working hours are disabled",
                fontSize = 11.sp,
                color = c.faint,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Row(Modifier.fillMaxWidth().padding(top = 9.dp), horizontalArrangement = Arrangement.End) {
            Text(
                if (isPast) "View day" else if (summary.plannedMinutes == 0) "Plan day" else "Adjust day",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.W700,
                color = c.acc,
                modifier = Modifier.tap {
                    if (isPast) {
                        vm.closeWeekPlanner()
                        vm.weekStripTap(date)
                    } else vm.planFromWeek(date)
                }
                    .background(c.accTint(0.12f), CircleShape).padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
    }
}

private fun duration(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
