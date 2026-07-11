package com.bento.calendar.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.bento.calendar.data.AppData
import com.bento.calendar.data.DeviceEvent
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.Priority
import com.bento.calendar.data.Recur
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.minsToHm
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.data.toIso
import com.bento.calendar.data.toMins
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.CalView
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.agendaDays
import com.bento.calendar.ui.components.BentoCheckbox
import com.bento.calendar.ui.components.BentoSheet
import com.bento.calendar.ui.components.Dot
import com.bento.calendar.ui.components.EmptyText
import com.bento.calendar.ui.components.GBtn
import com.bento.calendar.ui.components.SectionLabel
import com.bento.calendar.ui.components.hairlineBottom
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.components.tap
import com.bento.calendar.ui.startOfWeek
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.theme.OnCategory
import com.bento.calendar.ui.theme.color
import com.bento.calendar.ui.theme.hexColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Identity of the PERIOD being rendered (month/week/day/agenda start), used
 * as the [AnimatedContent] target so the exiting content keeps drawing the OLD
 * period while it slides away. Deliberately excludes the day selection: tapping
 * a day pill or month cell must not re-animate the whole view.
 */
private data class CalTarget(val view: CalView, val periodStart: LocalDate)

private enum class AgendaFilter { All, Events, Tasks }

@Composable
fun CalendarScreen(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    // Read-only device-calendar overlay: tapping a device event opens a
    // details sheet (device events have no editor, delete or drag).
    var deviceSheet by remember { mutableStateOf<DeviceEvent?>(null) }
    Box(Modifier.fillMaxSize()) {
        CalendarBody(vm, data, now, onDeviceTap = { deviceSheet = it })
        deviceSheet?.let { ev ->
            DeviceEventSheet(ev, data.prefs.use24h, onDismiss = { deviceSheet = null })
        }
    }
}

@Composable
private fun CalendarBody(
    vm: AppViewModel,
    data: AppData,
    now: LocalDateTime,
    onDeviceTap: (DeviceEvent) -> Unit,
) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    var agendaFilter by remember { mutableStateOf(AgendaFilter.All) }

    // Landing on today's Day view (or the current week's Week view) auto-
    // scrolls so the now-line sits comfortably in view; Month/Agenda reset.
    // Keyed on the explicit nav tick, NOT raw selDate — selecting a day with
    // a header pill or month cell must never hijack the scroll position.
    LaunchedEffect(vm.calNavTick) {
        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        when (vm.calView) {
            CalView.Month -> scrollState.animateScrollTo(0)
            CalView.Agenda -> scrollState.animateScrollTo(0)
            CalView.Week -> {
                val ws = startOfWeek(vm.selDate, data.prefs.monday)
                if (today >= ws && today <= ws.plusDays(6) && nowMin in 420..1320) {
                    // 07:00 grid at 44dp/hour, ~160dp headroom above the line.
                    val targetDp = ((nowMin - 420) / 60f) * 44f - 160f
                    val target = with(density) { targetDp.dp.toPx() }.toInt().coerceAtLeast(0)
                    scrollState.animateScrollTo(target)
                }
            }
            CalView.Day -> {
                if (vm.selDate == today && nowMin in 360..1380) {
                    // 06:00 grid at 56dp/hour, ~160dp headroom above the line.
                    // The due-task strip sits above the grid (~36dp/row), so
                    // its height joins the target or the now-line drifts a row
                    // lower per task.
                    val stripDp = dueTasksOn(data, vm.selDate).size * 36f
                    val targetDp = stripDp + ((nowMin - 360) / 60f) * 56f - 160f
                    val target = with(density) { targetDp.dp.toPx() }.toInt().coerceAtLeast(0)
                    scrollState.animateScrollTo(target)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        CalendarHeader(vm, data)
        Column(
            Modifier
                .weight(1f)
                // Swipe horizontally anywhere in the calendar body to move to
                // the previous/next month, week or day. Vertical scrolling and
                // grid taps are unaffected (horizontal touch slop only).
                .pointerInput(vm.calView) {
                    var total = 0f
                    val threshold = 56.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = {
                            when {
                                total <= -threshold -> vm.calNext()
                                total >= threshold -> vm.calPrev()
                            }
                        },
                    ) { _, dragAmount -> total += dragAmount }
                }
                .verticalScroll(scrollState)
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 14.dp),
        ) {
            SegmentedControl(vm)
            // Next/prev slides in the swipe direction; jumps (Today chip, date
            // picker, view switch) crossfade. calNavDir is set by the vm on
            // every navigation, so the spec just reads it.
            AnimatedContent(
                targetState = when (vm.calView) {
                    CalView.Month -> CalTarget(CalView.Month, vm.cursor.atDay(1))
                    CalView.Week -> CalTarget(CalView.Week, startOfWeek(vm.selDate, data.prefs.monday))
                    CalView.Day -> CalTarget(CalView.Day, vm.selDate)
                    CalView.Agenda -> CalTarget(CalView.Agenda, vm.selDate)
                },
                transitionSpec = {
                    val dir = vm.calNavDir
                    if (dir == 0) {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(120))
                    } else {
                        (slideInHorizontally(tween(260)) { full -> full * dir } + fadeIn(tween(200))) togetherWith
                            (slideOutHorizontally(tween(260)) { full -> -full * dir } + fadeOut(tween(160)))
                    }
                },
                label = "calPeriod",
            ) { target ->
                // AnimatedContent stacks bare children like a Box; the views
                // emit sibling rows, so restore the vertical flow explicitly.
                // Day selection reads live vm state — same target, no animation.
                Column(Modifier.fillMaxWidth()) {
                    when (target.view) {
                        CalView.Month -> MonthView(vm, data, now, YearMonth.from(target.periodStart), vm.selDate, onDeviceTap)
                        CalView.Week -> WeekView(vm, data, now, target.periodStart, onDeviceTap)
                        CalView.Day -> DayView(vm, data, now, target.periodStart, onDeviceTap)
                        CalView.Agenda -> AgendaView(
                            vm = vm,
                            data = data,
                            now = now,
                            start = target.periodStart,
                            filter = agendaFilter,
                            onFilter = { agendaFilter = it },
                            onDeviceTap = onDeviceTap,
                        )
                    }
                }
            }
        }
    }
}

// ---- Header ----

@Composable
private fun CalendarHeader(vm: AppViewModel, data: AppData) {
    val c = LocalBento.current
    val title = when (vm.calView) {
        CalView.Month -> Fmt.monthTitle(vm.cursor)
        CalView.Week -> Fmt.weekTitle(startOfWeek(vm.selDate, data.prefs.monday))
        CalView.Day -> Fmt.dayTitle(vm.selDate)
        CalView.Agenda -> Fmt.agendaTitle(vm.selDate)
    }
    var pickerOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, start = 20.dp, end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Tapping the period title opens a date picker to jump anywhere.
        Column(Modifier.weight(1f).tap { pickerOpen = true }) {
            Text("Calendar", fontSize = 12.sp, fontWeight = FontWeight.W500, color = c.sub)
            Text(
                title,
                fontSize = 21.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = (-0.01).em,
                color = c.tx,
                maxLines = 1,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .height(38.dp)
                    .pressable { vm.goToday() }
                    .background(c.accTint(0.13f), CircleShape)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Today", fontSize = 11.sp, fontWeight = FontWeight.W700, color = c.acc)
            }
            GBtn(onClick = { vm.calPrev() }) {
                Icon(BentoIcons.ChevronLeft, null, tint = c.sub, modifier = Modifier.size(17.dp))
            }
            GBtn(onClick = { vm.calNext() }) {
                Icon(BentoIcons.ChevronRight, null, tint = c.sub, modifier = Modifier.size(17.dp))
            }
            GBtn(onClick = { vm.newEvent() }, primary = true) {
                Icon(BentoIcons.Plus, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
    if (pickerOpen) {
        DatePickerDialogModal(
            initial = if (vm.calView == CalView.Month) vm.cursor.atDay(1) else vm.selDate,
            onDismiss = { pickerOpen = false },
            onPick = {
                vm.jumpToDate(it)
                pickerOpen = false
            },
        )
    }
}

/** Material date picker in a dialog, themed to match the app. */
@Composable
private fun DatePickerDialogModal(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(java.time.ZoneOffset.UTC)
            .toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    onPick(
                        java.time.Instant.ofEpochMilli(it)
                            .atZone(java.time.ZoneOffset.UTC).toLocalDate(),
                    )
                }
            }) { Text("Jump") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

// ---- Segmented control ----

@Composable
private fun SegmentedControl(vm: AppViewModel) {
    val c = LocalBento.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(c.tile, RoundedCornerShape(14.dp))
            .border(1.dp, c.bd, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SegButton("Month", vm.calView == CalView.Month, Modifier.weight(1f)) { vm.setCalView(CalView.Month) }
        SegButton("Week", vm.calView == CalView.Week, Modifier.weight(1f)) { vm.setCalView(CalView.Week) }
        SegButton("Day", vm.calView == CalView.Day, Modifier.weight(1f)) { vm.setCalView(CalView.Day) }
        SegButton("Agenda", vm.calView == CalView.Agenda, Modifier.weight(1f)) { vm.setCalView(CalView.Agenda) }
    }
}

@Composable
private fun SegButton(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalBento.current
    Box(
        modifier
            .pressable(onClick = onClick)
            .background(if (active) c.accTint(0.15f) else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.W600,
            color = if (active) c.acc else c.sub,
        )
    }
}

// ---- Shared pieces ----

/**
 * One row/block in a merged Bento + device-calendar list: exactly one of
 * [bento] / [device] is set. [startMin]/[endMin] carry the minutes the layout
 * works with — clamped to the visible grid in Week/Day view, raw in the
 * Month agenda (where they only drive the interleave order).
 */
private data class GridBlock(
    val bento: EventItem?,
    val device: DeviceEvent?,
    val startMin: Int,
    val endMin: Int,
)

/**
 * The read-only device-event fill: the calendar color at 28% over the tile —
 * clearly tinted next to the solid Bento category blocks. Pairs with a solid
 * 3dp leading rail in the full color and c.tx text.
 */
@Composable
private fun deviceTint(color: Color): Color =
    color.copy(alpha = 0.28f).compositeOver(LocalBento.current.tile)

/**
 * OPEN tasks due on [date], priority-high-first (stable, like the Tasks tab
 * sections) — empty when the tasks-on-calendar pref is off, so every task
 * surface in this file disappears with the toggle. Done tasks never render
 * on the calendar; completing one repeats through [AppViewModel.toggleTask]
 * (repeating tasks advance their due date) and the row drops out on the
 * store update.
 */
private fun dueTasksOn(data: AppData, date: LocalDate): List<TaskItem> =
    if (!data.prefs.tasksOnCalendar) emptyList()
    else data.tasks
        .filter { !it.done && it.due == date.toIso() }
        .sortedByDescending { it.priority }

/**
 * One calendar task row (Month agenda + Day strip): mini checkbox — the
 * giveaway that this is a task, not an event — then priority dot when set,
 * title, and the compact checklist count. The checkbox completes the task
 * (with [BentoCheckbox]'s usual haptic); tapping the row body opens the
 * task editor. Expansion and swipe actions stay on the Tasks tab.
 */
@Composable
private fun CalendarTaskRow(vm: AppViewModel, t: TaskItem) {
    val c = LocalBento.current
    Row(
        Modifier
            .fillMaxWidth()
            .tap { vm.openTask(t, switchTab = false) }
            .hairlineBottom(c.line)
            .padding(horizontal = 2.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        BentoCheckbox(
            checked = t.done,
            onToggle = { vm.toggleTask(t.id) },
            size = 17.dp,
            corner = 6.dp,
        )
        Priority.colorHex(t.priority)?.let { Dot(hexColor(it), size = 5.dp) }
        Text(
            t.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.W500,
            color = c.tx,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (t.subs.isNotEmpty()) {
            Text(
                "${t.subs.count { it.done }}/${t.subs.size}",
                fontSize = 10.sp,
                fontWeight = FontWeight.W600,
                color = c.faint,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            )
        }
    }
}

/** Day-number pill: today = accent/white, selected (week header) = tile bg + 1dp inset border. */
@Composable
private fun DayNumberPill(day: Int, isToday: Boolean, isSelected: Boolean) {
    val c = LocalBento.current
    val shape = RoundedCornerShape(8.dp)
    Text(
        day.toString(),
        fontSize = 13.sp,
        fontWeight = FontWeight.W600,
        color = if (isToday) Color.White else c.tx,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .widthIn(min = 24.dp)
            .background(
                when {
                    isToday -> c.acc
                    isSelected -> c.tile
                    else -> Color.Transparent
                },
                shape,
            )
            .then(if (isSelected && !isToday) Modifier.border(1.dp, c.bd, shape) else Modifier)
            .padding(vertical = 1.dp),
    )
}

/** Hour gutter label row (right-aligned, lifted so it sits on the grid line). */
@Composable
private fun HourGutterLabel(text: String, rowHeight: Dp, fontSize: TextUnit, endPad: Dp, lift: Dp) {
    val c = LocalBento.current
    Box(Modifier.fillMaxWidth().height(rowHeight)) {
        Text(
            text,
            fontSize = fontSize,
            fontWeight = FontWeight.W600,
            color = c.faint,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = endPad)
                .offset(y = -lift),
        )
    }
}

/** Accent "now" line with the 8dp dot at its left edge. Not hit-testable. */
@Composable
private fun NowLine(y: Dp) {
    val c = LocalBento.current
    Box(
        Modifier
            .offset(y = y)
            .fillMaxWidth()
            .height(2.dp)
            .background(c.acc)
            // 8dp dot straddling the line (prototype .nowln-d: left:-2px, top:-3px);
            // drawn here because a child Box would be clamped to the 2dp height.
            .drawBehind {
                drawCircle(c.acc, radius = 4.dp.toPx(), center = Offset(2.dp.toPx(), 1.dp.toPx()))
            },
    )
}

// ---- Month view ----

@Composable
private fun MonthView(
    vm: AppViewModel,
    data: AppData,
    now: LocalDateTime,
    cursor: YearMonth,
    selDate: LocalDate,
    onDeviceTap: (DeviceEvent) -> Unit,
) {
    val c = LocalBento.current
    val today = now.toLocalDate()
    val monday = data.prefs.monday
    // Weekday header (order respects week-start pref).
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp)) {
        val order = if (monday) listOf(1, 2, 3, 4, 5, 6, 0) else (0..6).toList()
        order.forEach { i ->
            Text(
                Fmt.WS[i].uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = 0.08.em,
                color = c.faint,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    // 6x7 grid of 42 cells starting at the week containing the 1st.
    val gs = startOfWeek(cursor.atDay(1), monday)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        for (r in 0..5) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val date = gs.plusDays((r * 7 + col).toLong())
                    MonthCell(vm, data, date, today, cursor, selDate, Modifier.weight(1f))
                }
            }
        }
    }
    // Agenda for the selected day: Bento occurrences + read-only device events
    // interleaved by start time. Both source lists are start-sorted and
    // sortedBy is stable, so Bento rows keep their existing relative order
    // (and the list is untouched when the device overlay is off).
    val selEvs = occurrencesOn(data.events, selDate)
    val selDev = vm.deviceEvents[selDate.toIso()].orEmpty()
    val agenda = (selEvs.map { GridBlock(it, null, it.start.toMins(), it.end.toMins()) } +
        selDev.map { GridBlock(null, it, it.start.toMins(), it.end.toMins()) })
        .sortedBy { it.startMin }
    // Open tasks due the selected day trail the timed rows (tasks have no
    // time-of-day, so they can't interleave meaningfully). Computed inline
    // like the agenda above — no memo to go stale — and empty (zero UI, label
    // untouched) when none are due or the pref is off.
    val dueTasks = dueTasksOn(data, selDate)
    val evCount = when {
        agenda.isEmpty() -> "no events"
        agenda.size == 1 -> "1 event"
        else -> "${agenda.size} events"
    }
    val taskCount = "${dueTasks.size} " + if (dueTasks.size == 1) "task" else "tasks"
    SectionLabel(
        Fmt.dayShort(selDate),
        count = when {
            dueTasks.isEmpty() -> evCount
            agenda.isEmpty() -> taskCount
            else -> "$evCount · $taskCount"
        },
    )
    if (agenda.isEmpty() && dueTasks.isEmpty()) {
        EmptyText("Nothing scheduled — tap + to add an event.")
    } else {
        agenda.forEach { b ->
            val e = b.bento
            if (e != null) {
                AgendaRow(vm, data, e)
            } else {
                val dev = b.device!!
                DeviceAgendaRow(data, dev, onTap = { onDeviceTap(dev) })
            }
        }
        dueTasks.forEach { t -> CalendarTaskRow(vm, t) }
    }
}

@Composable
private fun MonthCell(
    vm: AppViewModel,
    data: AppData,
    date: LocalDate,
    today: LocalDate,
    cursor: YearMonth,
    selDate: LocalDate,
    modifier: Modifier,
) {
    val c = LocalBento.current
    val haptics = LocalHapticFeedback.current
    val inMonth = YearMonth.from(date) == cursor
    val selected = date == selDate
    val shape = RoundedCornerShape(13.dp)
    // Up-to-3 dots: Bento category colors first, then device-calendar colors
    // that don't duplicate one already shown. Identical to the pre-overlay
    // dots whenever deviceEvents has nothing for this date.
    val catColors = occurrencesOn(data.events, date).map { data.categoryOf(it.cat) }.distinct().map { it.color }
    val devColors = vm.deviceEvents[date.toIso()].orEmpty()
        .map { hexColor(it.colorHex) }.distinct().filterNot { it in catColors }
    val dots = (catColors + devColors).take(3)
    // One hollow ring after the event dots marks a day with open due tasks —
    // the outline (vs the solid event dots) is the task tell, echoing the
    // unchecked checkbox in the agenda. Gated on the pref; task-free days
    // add nothing.
    val hasDueTasks = data.prefs.tasksOnCalendar &&
        data.tasks.any { !it.done && it.due == date.toIso() }
    Column(
        modifier
            .aspectRatio(0.92f)
            .alpha(if (inMonth) 1f else 0.32f)
            .then(
                if (selected) Modifier.background(c.tile, shape).border(1.dp, c.bd, shape)
                else Modifier
            )
            // Tap selects (tap again for Day view); long-press quick-creates
            // an event that day at 09:00 with the user's default duration.
            .pointerInput(date) {
                detectTapGestures(
                    onTap = { vm.tapMonthCell(date) },
                    onLongPress = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.newEventAt(date, 540)
                    },
                )
            }
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        DayNumberPill(date.dayOfMonth, isToday = date == today, isSelected = false)
        Row(
            Modifier.height(5.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            dots.forEach { Dot(it, size = 4.dp) }
            if (hasDueTasks) {
                Box(Modifier.size(5.dp).border(1.dp, c.faint, CircleShape))
            }
        }
    }
}

@Composable
private fun AgendaRow(vm: AppViewModel, data: AppData, e: EventItem) {
    val c = LocalBento.current
    // Multi-day span segments (occurrencesOn keeps endDate on every segment):
    // first/middle days still have spanEnd() ahead; the last day is re-dated
    // onto its endDate, so endDate == date marks it.
    val span = e.spanEnd()
    val spanLast = e.endDate != null && e.endDate == e.date
    val meta = buildString {
        // "23 h 59" for an all-day event is noise — the time column already
        // says "All day". A span segment's within-day duration ("15 h") is
        // equally noise: say where the span is going instead.
        when {
            span != null -> append("Until ${Fmt.dayShort(span)}")
            spanLast && !e.allDay -> append("Ends ${Fmt.time(e.end, data.prefs.use24h)}")
            spanLast -> append("Last day")
            !e.allDay -> append(Fmt.duration(e.start, e.end))
        }
        if (e.loc.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(e.loc)
        }
        if (e.recur != Recur.NONE) {
            if (isNotEmpty()) append(" · ")
            append("repeats")
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .tap { vm.openEvent(e) }
            .hairlineBottom(c.line)
            .padding(horizontal = 2.dp, vertical = 10.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (e.allDay) "All day" else Fmt.time(e.start, data.prefs.use24h),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.W600,
            color = c.sub,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.width(44.dp),
        )
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(data.categoryOf(e.cat).color, CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.W600, color = c.tx)
            if (meta.isNotEmpty()) {
                Text(meta, fontSize = 11.5.sp, color = c.sub, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (e.remind != null) {
            Icon(BentoIcons.Bell, null, tint = c.faint, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * Month-agenda row for a read-only device event: same layout as [AgendaRow]
 * (time column, 3dp rail, title + meta) with the tinted device treatment and
 * the calendar name in the meta line. Tap opens the details sheet — there is
 * no editor for device events.
 */
@Composable
private fun DeviceAgendaRow(data: AppData, e: DeviceEvent, onTap: () -> Unit) {
    val c = LocalBento.current
    val devColor = hexColor(e.colorHex)
    val meta = buildString {
        if (!e.allDay && e.end.toMins() > e.start.toMins()) append(Fmt.duration(e.start, e.end))
        if (isNotEmpty()) append(" · ")
        append(e.calName)
        if (e.loc.isNotEmpty()) {
            append(" · ")
            append(e.loc)
        }
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(deviceTint(devColor))
            .tap(onClick = onTap)
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (e.allDay) "All day" else Fmt.time(e.start, data.prefs.use24h),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.W600,
            color = c.sub,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.width(44.dp),
        )
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(devColor, CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.W600, color = c.tx)
            Text(meta, fontSize = 11.5.sp, color = c.sub, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ---- Agenda view ----

/**
 * Rolling 30-day list that brings Bento events, read-only device events and
 * due tasks into one chronological surface. It deliberately reuses the Month
 * agenda rows so event editing, task completion and device detail behavior are
 * identical across views.
 */
@Composable
private fun AgendaView(
    vm: AppViewModel,
    data: AppData,
    now: LocalDateTime,
    start: LocalDate,
    filter: AgendaFilter,
    onFilter: (AgendaFilter) -> Unit,
    onDeviceTap: (DeviceEvent) -> Unit,
) {
    val c = LocalBento.current
    val days = remember(
        data.events,
        data.tasks,
        data.prefs.tasksOnCalendar,
        vm.deviceEvents,
        start,
    ) {
        agendaDays(data, vm.deviceEvents, start)
    }
    val eventCount = days.sumOf { it.events.size + it.deviceEvents.size }
    val taskCount = days.sumOf { it.tasks.size }
    val visibleDays = days.filter { day ->
        when (filter) {
            AgendaFilter.All -> true
            AgendaFilter.Events -> day.events.isNotEmpty() || day.deviceEvents.isNotEmpty()
            AgendaFilter.Tasks -> day.tasks.isNotEmpty()
        }
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(c.tile, RoundedCornerShape(14.dp))
            .border(1.dp, c.bd, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        AgendaFilterButton(
            label = "All",
            count = eventCount + taskCount,
            active = filter == AgendaFilter.All,
            modifier = Modifier.weight(1f),
        ) { onFilter(AgendaFilter.All) }
        AgendaFilterButton(
            label = "Events",
            count = eventCount,
            active = filter == AgendaFilter.Events,
            modifier = Modifier.weight(1f),
        ) { onFilter(AgendaFilter.Events) }
        AgendaFilterButton(
            label = "Tasks",
            count = taskCount,
            active = filter == AgendaFilter.Tasks,
            modifier = Modifier.weight(1f),
        ) { onFilter(AgendaFilter.Tasks) }
    }

    if (visibleDays.isEmpty()) {
        EmptyText(
            when (filter) {
                AgendaFilter.All -> "Nothing scheduled in these 30 days."
                AgendaFilter.Events -> "No events in these 30 days."
                AgendaFilter.Tasks -> "No due tasks in these 30 days."
            },
        )
        return
    }

    visibleDays.forEach { day ->
        val showEvents = filter != AgendaFilter.Tasks
        val showTasks = filter != AgendaFilter.Events
        AgendaDayCard(
            vm = vm,
            data = data,
            day = day,
            today = now.toLocalDate(),
            showEvents = showEvents,
            showTasks = showTasks,
            onDeviceTap = onDeviceTap,
        )
    }
}

@Composable
private fun AgendaFilterButton(
    label: String,
    count: Int,
    active: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val c = LocalBento.current
    Box(
        modifier
            .pressable(onClick = onClick)
            .background(if (active) c.accTint(0.15f) else Color.Transparent, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$label  $count",
            fontSize = 11.5.sp,
            fontWeight = FontWeight.W600,
            color = if (active) c.acc else c.sub,
        )
    }
}

@Composable
private fun AgendaDayCard(
    vm: AppViewModel,
    data: AppData,
    day: com.bento.calendar.ui.AgendaDay,
    today: LocalDate,
    showEvents: Boolean,
    showTasks: Boolean,
    onDeviceTap: (DeviceEvent) -> Unit,
) {
    val c = LocalBento.current
    val isToday = day.date == today
    val eventTotal = if (showEvents) day.events.size + day.deviceEvents.size else 0
    val taskTotal = if (showTasks) day.tasks.size else 0
    val count = buildString {
        if (eventTotal > 0) append("$eventTotal ${if (eventTotal == 1) "event" else "events"}")
        if (taskTotal > 0) {
            if (isNotEmpty()) append(" · ")
            append("$taskTotal ${if (taskTotal == 1) "task" else "tasks"}")
        }
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(shape)
            .background(c.tile)
            .border(1.dp, if (isToday) c.acc.copy(alpha = 0.65f) else c.bd, shape)
            .padding(start = 10.dp, end = 10.dp, bottom = 5.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .tap { vm.weekStripTap(day.date) }
                .padding(horizontal = 2.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    when (day.date) {
                        today -> "Today"
                        today.plusDays(1) -> "Tomorrow"
                        else -> Fmt.WD[Fmt.dow(day.date)]
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.W700,
                    color = if (isToday) c.acc else c.tx,
                )
                Text(
                    "${day.date.dayOfMonth} ${Fmt.MN[day.date.monthValue - 1]} ${day.date.year}",
                    fontSize = 10.5.sp,
                    color = c.faint,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Text(count, fontSize = 10.5.sp, fontWeight = FontWeight.W600, color = c.sub)
            Icon(
                BentoIcons.ChevronRight,
                "Open day",
                tint = c.faint,
                modifier = Modifier.padding(start = 6.dp).size(14.dp),
            )
        }

        if (showEvents) {
            val events = (
                day.events.map { GridBlock(it, null, it.start.toMins(), it.end.toMins()) } +
                    day.deviceEvents.map { GridBlock(null, it, it.start.toMins(), it.end.toMins()) }
                ).sortedBy { it.startMin }
            events.forEach { block ->
                block.bento?.let { AgendaRow(vm, data, it) }
                    ?: block.device?.let { device ->
                        DeviceAgendaRow(data, device, onTap = { onDeviceTap(device) })
                    }
            }
        }
        if (showTasks) day.tasks.forEach { CalendarTaskRow(vm, it) }
    }
}

// ---- Week view ----

/** One folded "+N" overflow pill: [count] hidden events; [bottomMin] is the cluster's visual end in grid minutes. */
private data class WeekOverflow(val count: Int, val bottomMin: Int)

/** Pre-computed layout for one Week-view day column: rendered blocks, overflow pills and all-day events. */
private data class WeekDayLayout(
    val blocks: List<Positioned<GridBlock>>,
    val overflows: List<WeekOverflow>,
    val allDay: List<EventItem>,
    val allDayDevice: List<DeviceEvent>,
)

@Composable
private fun WeekView(
    vm: AppViewModel,
    data: AppData,
    now: LocalDateTime,
    weekStart: LocalDate,
    onDeviceTap: (DeviceEvent) -> Unit,
) {
    val c = LocalBento.current
    val today = now.toLocalDate()
    val nowMin = now.hour * 60 + now.minute
    val use24h = data.prefs.use24h
    // The week identity comes in as a parameter (already normalized to the
    // week start); the SELECTED day is read live so pill taps don't animate.
    val ws = weekStart

    // Per-day occurrences + overlap layout, computed once per data/week change
    // — NOT on every 15s clock tick (nothing here depends on `now`). Events
    // shorter than the 14dp min block height get a visually-effective end of
    // start+22 min (14dp block + 2dp gap at 44dp/hour) so blocks that will
    // RENDER overlapping are laid out side by side; clusters wider than two
    // columns fold the rest into a per-cluster "+N" pill (Day view shows all).
    val perDay = remember(data.events, weekStart, vm.deviceEvents) {
        (0..6).map { i ->
            val date = ws.plusDays(i.toLong())
            // All-day events would render as full-day columns in the timed
            // grid — split them out here (same memo) for the strip above it.
            val occ = occurrencesOn(data.events, date)
            val allDay = occ.filter { it.allDay }
            // Read-only device events share the overlap columns with Bento
            // blocks so concurrent items render side by side.
            val dev = vm.deviceEvents[date.toIso()].orEmpty()
            val allDayDevice = dev.filter { it.allDay }
            val visible = occ.filter { !it.allDay }.mapNotNull { e ->
                val s = maxOf(e.start.toMins(), 420)
                val en = minOf(e.end.toMins(), 1320)
                if (en <= 420 || s >= 1320) null else GridBlock(e, null, s, en)
            } + dev.filter { !it.allDay }.mapNotNull { e ->
                val s = maxOf(e.start.toMins(), 420)
                val en = minOf(e.end.toMins(), 1320)
                // en <= s also drops multi-day fan-out days whose HH:MM range
                // wraps midnight — they'd otherwise draw phantom blocks.
                if (en <= s || en <= 420 || s >= 1320) null else GridBlock(null, e, s, en)
            }
            val effEnd = { t: GridBlock -> maxOf(t.endMin, t.startMin + 22) }
            val positioned = layoutOverlaps(visible, startOf = { it.startMin }, endOf = effEnd)
            // Fold columns >= 2 into per-cluster "+N" pills. The positioned
            // list is in cluster order, so boundaries are re-detected with the
            // same rule layoutOverlaps uses (start >= running max effective end).
            val blocks = ArrayList<Positioned<GridBlock>>()
            val overflows = ArrayList<WeekOverflow>()
            var clusterMaxEnd = Int.MIN_VALUE
            var folded = 0
            fun flush() {
                if (folded > 0) overflows.add(WeekOverflow(folded, clusterMaxEnd))
                folded = 0
            }
            for (p in positioned) {
                if (clusterMaxEnd != Int.MIN_VALUE && p.item.startMin >= clusterMaxEnd) {
                    flush()
                    clusterMaxEnd = Int.MIN_VALUE
                }
                clusterMaxEnd = maxOf(clusterMaxEnd, effEnd(p.item))
                if (p.col >= 2) folded++ else blocks.add(p)
            }
            flush()
            WeekDayLayout(blocks, overflows, allDay, allDayDevice)
        }
    }

    // Day header row (offset by the 34dp hour gutter).
    Row(Modifier.fillMaxWidth().padding(top = 12.dp, start = 34.dp)) {
        for (i in 0..6) {
            val date = ws.plusDays(i.toLong())
            Column(
                Modifier
                    .weight(1f)
                    .tap { vm.selectDate(date) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    Fmt.WS[Fmt.dow(date)].uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = 0.04.em,
                    color = c.faint,
                )
                DayNumberPill(date.dayOfMonth, isToday = date == today, isSelected = date == vm.selDate)
            }
        }
    }

    // All-day strip: a thin category-colored bar per all-day event (max 2)
    // above each day column — the columns are too narrow for text; tapping a
    // day's bars opens that date's Day view where the full pills live. Device
    // all-day events join in their calendar color, after the Bento bars.
    if (perDay.any { it.allDay.isNotEmpty() || it.allDayDevice.isNotEmpty() }) {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp, start = 34.dp)) {
            for (i in 0..6) {
                val date = ws.plusDays(i.toLong())
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .tap {
                            vm.selectDate(date)
                            vm.setCalView(CalView.Day)
                        },
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    val bars = (perDay[i].allDay.map { data.categoryOf(it.cat).color } +
                        perDay[i].allDayDevice.map { hexColor(it.colorHex) }).take(2)
                    bars.forEach { barColor ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .background(barColor, RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }
    }

    // Grid: 34dp gutter + 07:00-22:00 columns area (15h x 44dp = 660dp).
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.width(34.dp)) {
            for (h in 7..21) {
                HourGutterLabel(Fmt.hourLabel(h, use24h), 44.dp, 8.5.sp, endPad = 6.dp, lift = 4.dp)
            }
        }
        val lineColor = c.line
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(660.dp)
                .drawBehind {
                    val lw = 1.dp.toPx()
                    val hourPx = 44.dp.toPx()
                    for (k in 0..14) {
                        drawRect(lineColor, topLeft = Offset(0f, k * hourPx), size = Size(size.width, lw))
                    }
                    val colW = size.width / 7f
                    for (i in 1..7) {
                        drawRect(lineColor, topLeft = Offset(i * colW - lw, 0f), size = Size(lw, size.height))
                    }
                },
        ) {
            val colW = maxWidth / 7
            // Empty-slot taps land here (event blocks sit on top and consume theirs).
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(weekStart) {
                        detectTapGestures { off ->
                            val day = floor(off.x / size.width * 7f).toInt().coerceIn(0, 6)
                            val hour = floor(off.y / 44.dp.toPx()).toInt().coerceIn(0, 14)
                            vm.newEventAt(ws.plusDays(day.toLong()), 420 + hour * 60)
                        }
                    },
            )
            for (i in 0..6) {
                val date = ws.plusDays(i.toLong())
                val dayLayout = perDay[i]
                val dayX = colW * i
                val inner = colW - 4.dp
                dayLayout.blocks.forEach { p ->
                    val s = p.item.startMin
                    val en = p.item.endMin
                    val cols = minOf(p.cols, 2)
                    val slice = inner / cols
                    // Height first, then clamp y so min-height blocks near the
                    // grid end stay inside the 660dp timeline.
                    val h = maxOf((en - s) / 60f * 44f - 2f, 14f)
                    val y = minOf((s - 420) / 60f * 44f, 660f - h)
                    val blockX = dayX + 2.dp + slice * p.col
                    val blockW = (slice - (if (p.col < cols - 1) 2.dp else 0.dp)).coerceAtLeast(6.dp)
                    val e = p.item.bento
                    if (e != null) {
                        Box(
                            Modifier
                                .offset(x = blockX, y = y.dp)
                                .width(blockW)
                                .height(h.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(data.categoryOf(e.cat).color)
                                .tap { vm.openEvent(e) }
                                .padding(horizontal = if (slice < 20.dp) 2.dp else 5.dp, vertical = 3.dp),
                        ) {
                            Text(
                                e.title,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.W700,
                                color = OnCategory,
                                lineHeight = 1.25.em,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    } else {
                        // Read-only device block: tinted fill + solid leading
                        // rail, c.tx text; tap shows the details sheet.
                        val dev = p.item.device!!
                        val devColor = hexColor(dev.colorHex)
                        Row(
                            Modifier
                                .offset(x = blockX, y = y.dp)
                                .width(blockW)
                                .height(h.dp)
                                .clip(RoundedCornerShape(7.dp))
                                .background(deviceTint(devColor))
                                .tap { onDeviceTap(dev) },
                        ) {
                            Box(Modifier.width(3.dp).fillMaxHeight().background(devColor))
                            Text(
                                dev.title,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.W700,
                                color = c.tx,
                                lineHeight = 1.25.em,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(
                                        horizontal = if (slice < 20.dp) 1.dp else 4.dp,
                                        vertical = 3.dp,
                                    ),
                            )
                        }
                    }
                }
                dayLayout.overflows.forEach { o ->
                    // "+N" chip at the bottom-right of the cluster's time range;
                    // tapping jumps to Day view, where every event is visible.
                    val bottom = minOf((o.bottomMin - 420) / 60f * 44f, 660f)
                    Box(
                        Modifier
                            .offset(x = dayX + 2.dp, y = (bottom - 16f).coerceAtLeast(0f).dp)
                            .width(inner)
                            .height(16.dp),
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        Text(
                            "+${o.count}",
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.W700,
                            color = c.sub,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(c.inp)
                                .border(1.dp, c.bd, RoundedCornerShape(6.dp))
                                .tap {
                                    vm.selectDate(date)
                                    vm.setCalView(CalView.Day)
                                }
                                .padding(horizontal = 4.dp, vertical = 1.dp),
                        )
                    }
                }
            }
            if (today >= ws && today <= ws.plusDays(6) && nowMin in 420..1320) {
                NowLine(((nowMin - 420) / 60f * 44f).dp)
            }
        }
    }
}

// ---- Day view ----

@Composable
private fun DayView(
    vm: AppViewModel,
    data: AppData,
    now: LocalDateTime,
    selDate: LocalDate,
    onDeviceTap: (DeviceEvent) -> Unit,
) {
    val c = LocalBento.current
    val today = now.toLocalDate()
    val nowMin = now.hour * 60 + now.minute
    val use24h = data.prefs.use24h

    // Occurrences + overlap layout memoized so the 15s clock tick does not
    // recompute them (nothing here depends on `now`). All-day events are split
    // out in the SAME memo: they never enter the timed grid or the overlap
    // layout (they'd render as full-day columns) and feed the pill strip
    // instead. Events shorter than the 30dp min block height get a visually-
    // effective end of start+36 min (30dp block + 3dp gap at 56dp/hour) so
    // blocks that will RENDER overlapping are laid out side by side. Read-only
    // device events join the same overlap layout so they share columns.
    val (allDayEvents, allDayDevice, positioned) = remember(data.events, selDate, vm.deviceEvents) {
        val occ = occurrencesOn(data.events, selDate)
        val dev = vm.deviceEvents[selDate.toIso()].orEmpty()
        val visible = occ.filter { !it.allDay }.mapNotNull { e ->
            // Skip events entirely outside the 06:00-23:00 grid — clamping
            // alone would draw phantom min-height blocks at the edges.
            if (e.end.toMins() <= 360 || e.start.toMins() >= 1380) null
            else GridBlock(e, null, maxOf(e.start.toMins(), 360), minOf(e.end.toMins(), 1380))
        } + dev.filter { !it.allDay }.mapNotNull { e ->
            val s = maxOf(e.start.toMins(), 360)
            val en = minOf(e.end.toMins(), 1380)
            // en <= s also drops multi-day fan-out days whose HH:MM range
            // wraps midnight — they'd otherwise draw phantom blocks.
            if (en <= s || e.end.toMins() <= 360 || e.start.toMins() >= 1380) null
            else GridBlock(null, e, s, en)
        }
        Triple(
            occ.filter { it.allDay },
            dev.filter { it.allDay },
            layoutOverlaps(visible, startOf = { it.startMin }, endOf = { maxOf(it.endMin, it.startMin + 36) }),
        )
    }

    // All-day pill strip above the timed grid: full-width pills, up to 3
    // collapsed. Tapping "+N more" expands the strip in place so every all-day
    // event stays reachable from Day view (the Week strip routes here on the
    // promise it "shows all"); tapping again collapses. The strip lives in the
    // scrollable column, so the expanded height is fine. State resets per day.
    val allDayPills = allDayEvents.map { GridBlock(it, null, 0, 0) } +
        allDayDevice.map { GridBlock(null, it, 0, 0) }
    if (allDayPills.isNotEmpty()) {
        var allDayExpanded by remember(selDate) { mutableStateOf(false) }
        Column(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val shown = if (allDayExpanded) allDayPills else allDayPills.take(3)
            shown.forEach { b ->
                val e = b.bento
                if (e != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(data.categoryOf(e.cat).color)
                            .tap { vm.openEvent(e) }
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            e.title,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.W700,
                            color = OnCategory,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    // Read-only device pill: tinted fill + leading rail.
                    val dev = b.device!!
                    val devColor = hexColor(dev.colorHex)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(22.dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(deviceTint(devColor))
                            .tap { onDeviceTap(dev) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(devColor))
                        Text(
                            dev.title,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.W700,
                            color = c.tx,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 5.dp),
                        )
                    }
                }
            }
            if (allDayPills.size > 3) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(22.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(c.tile, RoundedCornerShape(7.dp))
                        .border(1.dp, c.bd, RoundedCornerShape(7.dp))
                        .tap { allDayExpanded = !allDayExpanded }
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        if (allDayExpanded) "Show less" else "+${allDayPills.size - 3} more",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.W700,
                        color = c.sub,
                        maxLines = 1,
                    )
                }
            }
        }
    }

    // Open tasks due this day: a compact strip between the all-day pills and
    // the timed grid — tasks have no time-of-day, so they never enter the
    // grid or its overlap layout. Memoized SEPARATELY from the events memo
    // above (own keys: tasks + pref) so a toggle or pref flip re-filters
    // immediately while the 15s clock tick still skips the work.
    val dueTasks = remember(data.tasks, selDate, data.prefs.tasksOnCalendar) {
        dueTasksOn(data, selDate)
    }
    if (dueTasks.isNotEmpty()) {
        Column(Modifier.fillMaxWidth().padding(top = 10.dp)) {
            dueTasks.forEach { t -> CalendarTaskRow(vm, t) }
        }
    }

    // Grid: 44dp gutter + 06:00-23:00 column (17h x 56dp = 952dp).
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Column(Modifier.width(44.dp)) {
            for (h in 6..22) {
                HourGutterLabel(Fmt.hourLabel(h, use24h), 56.dp, 9.5.sp, endPad = 8.dp, lift = 5.dp)
            }
        }
        val lineColor = c.line
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .height(952.dp)
                .drawBehind {
                    val lw = 1.dp.toPx()
                    val hourPx = 56.dp.toPx()
                    for (k in 0..16) {
                        drawRect(lineColor, topLeft = Offset(0f, k * hourPx), size = Size(size.width, lw))
                    }
                },
        ) {
            val blockW = maxWidth - 8.dp
            // Empty-slot taps land here (event blocks sit on top and consume theirs).
            Box(
                Modifier
                    .matchParentSize()
                    .pointerInput(selDate) {
                        detectTapGestures { off ->
                            val hour = floor(off.y / 56.dp.toPx()).toInt().coerceIn(0, 16)
                            vm.newEventAt(selDate, 360 + hour * 60)
                        }
                    },
            )
            positioned.forEach { p ->
                val s = p.item.startMin
                val en = p.item.endMin
                val slice = blockW / p.cols
                // Height first, then clamp y so min-height blocks near the grid
                // end stay inside the 952dp timeline.
                val h = maxOf((en - s) / 60f * 56f - 3f, 30f)
                val y = minOf((s - 360) / 60f * 56f, 952f - h)
                val x = 4.dp + slice * p.col
                val w = slice - (if (p.col < p.cols - 1) 3.dp else 0.dp)
                val e = p.item.bento
                if (e != null) {
                    DayEventBlock(
                        vm = vm,
                        e = e,
                        blockColor = data.categoryOf(e.cat).color,
                        x = x,
                        y = y,
                        w = w,
                        h = h,
                        use24h = use24h,
                    )
                } else {
                    val dev = p.item.device!!
                    DayDeviceBlock(
                        e = dev,
                        x = x,
                        y = y,
                        w = w,
                        h = h,
                        use24h = use24h,
                        onTap = { onDeviceTap(dev) },
                    )
                }
            }
            if (selDate == today && nowMin in 360..1380) {
                NowLine(((nowMin - 360) / 60f * 56f).dp)
            }
        }
    }
}

/**
 * One Day-view event block. Tap opens the editor; long-press then vertical
 * drag reschedules: the block follows the finger snapped to 15-minute steps
 * (14dp per 15 min at 56dp/hour), the prospective new start is appended to
 * the meta line, and release commits via [AppViewModel.moveEvent]. Ghost,
 * label and commit all derive from ONE [dragTargetStart] value relative to
 * the event's TRUE start, so a zero-step jitter after the long-press is a
 * strict no-op (edge-clamped or off-grid events are never silently re-timed)
 * and the committed time is exactly what was previewed. After release the
 * ghost holds at the target until the async store update re-renders the
 * block, so it never flashes back to the old slot. A cancelled drag animates
 * the block back to its slot.
 *
 * [y] and [h] are dp values from the grid top / block height as computed by
 * the day layout; the drag state deliberately lives HERE, outside the
 * remember(data.events, selDate) layout memo, keyed per event id.
 */
@Composable
private fun DayEventBlock(
    vm: AppViewModel,
    e: EventItem,
    blockColor: Color,
    x: Dp,
    y: Float,
    w: Dp,
    h: Float,
    use24h: Boolean,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // Multi-day span segment: occurrencesOn keeps endDate on every segment,
    // so any non-null endDate marks one (first day still has spanEnd() ahead;
    // the last day is re-dated onto its endDate). Drag-to-reschedule is
    // disabled for them — moveEvent no-ops on spans, so an optimistic ghost
    // would only snap back confusingly.
    val spanSeg = e.endDate != null
    val spanFirst = e.spanEnd() != null
    val spanLast = spanSeg && e.endDate == e.date

    // Raw drag offset in px; rendering snaps it to the 15-min grid. Keyed on
    // the event id so a recycled slot never inherits another block's offset.
    var dragOffsetY by remember(e.id) { mutableFloatStateOf(0f) }
    var dragging by remember(e.id) { mutableStateOf(false) }
    val settle = remember(e.id) { Animatable(0f) }
    var settleJob by remember(e.id) { mutableStateOf<Job?>(null) }
    // Committed-but-not-yet-rendered start: keeps the ghost at the released
    // target until the async store write lands and re-renders the block, so
    // it never flashes back to the old slot. Stores the CLAMPED value that
    // moveEvent will commit, so the held ghost equals the post-commit y.
    var pendingStartMin by remember(e.id) { mutableStateOf<Int?>(null) }
    // The gesture is keyed on e.id only; read the event and geometry through
    // these so the handlers never see stale values after a store update
    // re-lays the day out (or an edit changes the event's times).
    val curE by rememberUpdatedState(e)
    val curY by rememberUpdatedState(y)
    val curH by rememberUpdatedState(h)
    // Clear the hold once (or if) the committed start arrives — this runs
    // AFTER the frame that applied the new y, so the handoff is seamless; it
    // also fires if the vm clamped to a different value than requested.
    LaunchedEffect(e.id, e.start) { pendingStartMin = null }

    // Prospective TRUE-start-relative target while dragging — the single
    // source of truth for the meta-line label, the ghost and the commit.
    val durMin = e.end.toMins() - e.start.toMins()
    val visDur = visDurMin(h)
    val newStartMin = dragTargetStart(e.start.toMins(), durMin, visDur, dragOffsetY / density.density)

    Column(
        Modifier
            .offset(x = x, y = y.dp)
            // Live drag offset layered on top of the static position: snapped
            // to 15-min steps while dragging, raw during the cancel settle.
            .offset {
                val pend = pendingStartMin
                val extra = if (dragging) {
                    val ns = dragTargetStart(e.start.toMins(), durMin, visDur, dragOffsetY.toDp().value)
                    (snappedGhostY(ns, h) - y).dp.roundToPx()
                } else if (pend != null) {
                    // Hold at the committed target until the store update lands.
                    (snappedGhostY(pend, h) - y).dp.roundToPx()
                } else {
                    dragOffsetY.roundToInt()
                }
                IntOffset(0, extra)
            }
            .zIndex(if (dragging || pendingStartMin != null) 1f else 0f)
            .graphicsLayer {
                if (dragging) {
                    alpha = 0.92f
                    scaleX = 1.02f
                    scaleY = 1.02f
                }
            }
            .width(w)
            .height(h.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(blockColor)
            .tap { vm.openEvent(e) }
            .then(if (spanSeg) Modifier else Modifier.pointerInput(e.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        settleJob?.cancel()
                        pendingStartMin = null
                        dragOffsetY = 0f
                        dragging = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDragEnd = {
                        dragging = false
                        // Commit only on a real move (>= 1 snapped step): a
                        // post-long-press jitter can never re-time the event.
                        val steps = dragSteps(dragOffsetY.toDp().value)
                        if (steps != 0) {
                            val startMin = curE.start.toMins()
                            val dur = curE.end.toMins() - startMin
                            val ns = dragTargetStart(startMin, dur, visDurMin(curH), dragOffsetY.toDp().value)
                            // Mirror moveEvent's clamp/no-op so the held ghost
                            // equals what will actually be committed, and skip
                            // when nothing changes (moveEvent early-returns
                            // with no emission — pending would never clear).
                            val s = ns.coerceIn(0, 1439 - dur.coerceAtLeast(1))
                            if (s != startMin) {
                                pendingStartMin = s
                                vm.moveEvent(e.id, ns)
                            }
                        }
                        dragOffsetY = 0f
                    },
                    onDragCancel = {
                        dragging = false
                        // Settle back from the snapped ghost position (what is
                        // on screen right now) so there is no jump at release.
                        val startMin = curE.start.toMins()
                        val dur = curE.end.toMins() - startMin
                        val ns = dragTargetStart(startMin, dur, visDurMin(curH), dragOffsetY.toDp().value)
                        val from = ((snappedGhostY(ns, curH) - curY).dp).toPx()
                        settleJob = scope.launch {
                            settle.snapTo(from)
                            settle.animateTo(0f, tween(220)) { dragOffsetY = value }
                        }
                    },
                ) { change, amt ->
                    change.consume()
                    dragOffsetY += amt.y
                }
            })
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            e.title,
            fontSize = 12.sp,
            fontWeight = FontWeight.W700,
            color = OnCategory,
            lineHeight = 1.3.em,
        )
        Text(
            // Span segments: "14:00 →" runs on past today; "→ 17:00" ran in
            // from an earlier day. "– 23:59" / "00:00 –" would be noise.
            when {
                spanFirst -> Fmt.time(e.start, use24h) + " →"
                spanLast -> "→ " + Fmt.time(e.end, use24h)
                else -> Fmt.time(e.start, use24h) + " – " + Fmt.time(e.end, use24h)
            } +
                (if (e.loc.isNotEmpty()) " · " + e.loc else "") +
                (if (dragging) " → " + Fmt.time(minsToHm(newStartMin), use24h) else ""),
            fontSize = 9.5.sp,
            fontWeight = FontWeight.W600,
            color = OnCategory.copy(alpha = 0.8f),
            modifier = Modifier.padding(top = 1.dp),
        )
    }
}

/**
 * Read-only device-calendar block in the Day grid. Same geometry as
 * [DayEventBlock] but deliberately a separate, simple composable: device
 * events have no editor and no long-press drag-to-reschedule. Tinted fill
 * over the tile with a solid 3dp leading rail; tap opens the details sheet.
 */
@Composable
private fun DayDeviceBlock(
    e: DeviceEvent,
    x: Dp,
    y: Float,
    w: Dp,
    h: Float,
    use24h: Boolean,
    onTap: () -> Unit,
) {
    val c = LocalBento.current
    val devColor = hexColor(e.colorHex)
    Row(
        Modifier
            .offset(x = x, y = y.dp)
            .width(w)
            .height(h.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(deviceTint(devColor))
            .tap(onClick = onTap),
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(devColor))
        Column(Modifier.weight(1f).padding(start = 7.dp, end = 10.dp, top = 7.dp, bottom = 7.dp)) {
            Text(
                e.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.W700,
                color = c.tx,
                lineHeight = 1.3.em,
            )
            Text(
                Fmt.time(e.start, use24h) + " – " + Fmt.time(e.end, use24h) + " · " + e.calName,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.W600,
                color = c.tx.copy(alpha = 0.8f),
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

/**
 * Details sheet for a read-only device-calendar event: title, time, source
 * calendar (with its color dot), location — and no actions. Device events are
 * an overlay; editing happens in the device's Calendar app.
 */
@Composable
private fun DeviceEventSheet(e: DeviceEvent, use24h: Boolean, onDismiss: () -> Unit) {
    val c = LocalBento.current
    BentoSheet(onDismiss = onDismiss) {
        Text(e.title, fontSize = 16.sp, fontWeight = FontWeight.W700, color = c.tx)
        Text(
            if (e.allDay) "All day" else Fmt.time(e.start, use24h) + " – " + Fmt.time(e.end, use24h),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.W500,
            color = c.sub,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            Modifier.padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Dot(hexColor(e.colorHex), size = 8.dp)
            Text(e.calName, fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = c.tx)
        }
        if (e.loc.isNotEmpty()) {
            Text(e.loc, fontSize = 12.5.sp, color = c.sub, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            "From your device calendar — edit it in the Calendar app",
            fontSize = 11.sp,
            color = c.faint,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}

/** Whole 15-minute steps for a raw vertical drag ([dragDp] in dp; 14dp per 15 min at 56dp/hour). */
private fun dragSteps(dragDp: Float): Int = (dragDp / 14f).roundToInt()

/** Visible duration (grid minutes) a rendered block of height [h] dp stands for (inverse of h = dur/60*56 - 3, floored at 30). */
private fun visDurMin(h: Float): Int = ((h + 3f) / 56f * 60f).roundToInt()

/**
 * TRUE-start-relative, grid-clamped drag target — the single source of truth
 * for the drag ghost, the meta-line label AND the committed move. Steps from
 * the event's actual start in 15-minute increments (so off-grid events keep
 * their minute offset, and a zero-step jitter is a strict no-op), clamped so
 * the rendered block stays on the 06:00-23:00 grid ([visDurMin] keeps
 * bottom-clipped events at their pin instead of yanking them up) and so the
 * full [durMin] still fits before midnight (moveEvent's own cap, mirrored so
 * the previewed time IS the committed time). coerceAtMost BEFORE
 * coerceAtLeast: an event longer than the 17h grid pins to 06:00 rather than
 * throwing from an empty range.
 */
private fun dragTargetStart(startMin: Int, durMin: Int, visDurMin: Int, dragDp: Float): Int =
    (startMin + dragSteps(dragDp) * 15)
        .coerceAtMost(minOf(1380 - visDurMin, 1439 - durMin.coerceAtLeast(1)))
        .coerceAtLeast(360)

/** Absolute ghost y (dp from grid top) for a snapped start, kept on the 952dp grid. */
private fun snappedGhostY(startMin: Int, h: Float): Float =
    ((startMin - 360) / 15f * 14f).coerceIn(0f, 952f - h)
