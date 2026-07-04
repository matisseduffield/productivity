package com.bento.calendar.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.Cats
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.Recur
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.data.toMins
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.CalView
import com.bento.calendar.ui.Fmt
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import kotlin.math.floor

/**
 * Identity of the PERIOD being rendered (month start / week start / day), used
 * as the [AnimatedContent] target so the exiting content keeps drawing the OLD
 * period while it slides away. Deliberately excludes the day selection: tapping
 * a day pill or month cell must not re-animate the whole view.
 */
private data class CalTarget(val view: CalView, val periodStart: LocalDate)

@Composable
fun CalendarScreen(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Landing on today's Day view (or the current week's Week view) auto-
    // scrolls so the now-line sits comfortably in view; Month resets to top.
    // Keyed on the explicit nav tick, NOT raw selDate — selecting a day with
    // a header pill or month cell must never hijack the scroll position.
    LaunchedEffect(vm.calNavTick) {
        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        when (vm.calView) {
            CalView.Month -> scrollState.animateScrollTo(0)
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
                    val targetDp = ((nowMin - 360) / 60f) * 56f - 160f
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
                        CalView.Month -> MonthView(vm, data, now, YearMonth.from(target.periodStart), vm.selDate)
                        CalView.Week -> WeekView(vm, data, now, target.periodStart)
                        CalView.Day -> DayView(vm, data, now, target.periodStart)
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
    // Agenda for the selected day.
    val selEvs = occurrencesOn(data.events, selDate)
    SectionLabel(
        Fmt.dayShort(selDate),
        count = when {
            selEvs.isEmpty() -> "no events"
            selEvs.size == 1 -> "1 event"
            else -> "${selEvs.size} events"
        },
    )
    if (selEvs.isEmpty()) {
        EmptyText("Nothing scheduled — tap + to add an event.")
    } else {
        selEvs.forEach { e -> AgendaRow(vm, data, e) }
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
    val cats = occurrencesOn(data.events, date).map { Cats.of(it.cat) }.distinct().take(3)
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
            cats.forEach { Dot(it.color, size = 4.dp) }
        }
    }
}

@Composable
private fun AgendaRow(vm: AppViewModel, data: AppData, e: EventItem) {
    val c = LocalBento.current
    val meta = buildString {
        append(Fmt.duration(e.start, e.end))
        if (e.loc.isNotEmpty()) append(" · ").append(e.loc)
        if (e.recur != Recur.NONE) append(" · repeats")
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
            Fmt.time(e.start, data.prefs.use24h),
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
                .background(Cats.of(e.cat).color, CircleShape),
        )
        Column(Modifier.weight(1f)) {
            Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.W600, color = c.tx)
            Text(meta, fontSize = 11.5.sp, color = c.sub, modifier = Modifier.padding(top = 2.dp))
        }
        if (e.remind != null) {
            Icon(BentoIcons.Bell, null, tint = c.faint, modifier = Modifier.size(14.dp))
        }
    }
}

// ---- Week view ----

@Composable
private fun WeekView(vm: AppViewModel, data: AppData, now: LocalDateTime, weekStart: LocalDate) {
    val c = LocalBento.current
    val today = now.toLocalDate()
    val nowMin = now.hour * 60 + now.minute
    val use24h = data.prefs.use24h
    // The week identity comes in as a parameter (already normalized to the
    // week start); the SELECTED day is read live so pill taps don't animate.
    val ws = weekStart

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
                occurrencesOn(data.events, date).forEach { e ->
                    val s = maxOf(e.start.toMins(), 420)
                    val en = minOf(e.end.toMins(), 1320)
                    if (en <= 420 || s >= 1320) return@forEach
                    Box(
                        Modifier
                            .offset(x = colW * i + 2.dp, y = ((s - 420) / 60f * 44f).dp)
                            .width(colW - 4.dp)
                            .height(maxOf((en - s) / 60f * 44f - 2f, 14f).dp)
                            .clip(RoundedCornerShape(7.dp))
                            .background(Cats.of(e.cat).color)
                            .tap { vm.openEvent(e) }
                            .padding(horizontal = 5.dp, vertical = 3.dp),
                    ) {
                        Text(
                            e.title,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.W700,
                            color = OnCategory,
                            lineHeight = 1.25.em,
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
private fun DayView(vm: AppViewModel, data: AppData, now: LocalDateTime, selDate: LocalDate) {
    val c = LocalBento.current
    val today = now.toLocalDate()
    val nowMin = now.hour * 60 + now.minute
    val use24h = data.prefs.use24h

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
            occurrencesOn(data.events, selDate).forEach { e ->
                // Skip events entirely outside the 06:00-23:00 grid — clamping
                // alone would draw phantom min-height blocks at the edges.
                if (e.end.toMins() <= 360 || e.start.toMins() >= 1380) return@forEach
                val s = maxOf(e.start.toMins(), 360)
                val en = minOf(e.end.toMins(), 1380)
                Column(
                    Modifier
                        .offset(x = 4.dp, y = ((s - 360) / 60f * 56f).dp)
                        .width(blockW)
                        .height(maxOf((en - s) / 60f * 56f - 3f, 30f).dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(Cats.of(e.cat).color)
                        .tap { vm.openEvent(e) }
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
                        Fmt.time(e.start, use24h) + " – " + Fmt.time(e.end, use24h) +
                            (if (e.loc.isNotEmpty()) " · " + e.loc else ""),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.W600,
                        color = OnCategory.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 1.dp),
                    )
                }
            }
            if (selDate == today && nowMin in 360..1380) {
                NowLine(((nowMin - 360) / 60f * 56f).dp)
            }
        }
    }
}
