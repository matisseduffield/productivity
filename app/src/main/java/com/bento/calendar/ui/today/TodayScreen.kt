package com.bento.calendar.ui.today

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.DeviceEvent
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.NoteItem
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.data.toIso
import com.bento.calendar.data.toMins
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.Tab
import com.bento.calendar.ui.activeReminder
import com.bento.calendar.ui.components.BentoCheckbox
import com.bento.calendar.ui.components.BentoSheet
import com.bento.calendar.ui.components.Dot
import com.bento.calendar.ui.components.GBtn
import com.bento.calendar.ui.components.tap
import com.bento.calendar.ui.sortedOpenTasks
import com.bento.calendar.ui.startOfWeek
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.theme.color
import com.bento.calendar.ui.theme.hexColor
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * The Today landing tab: greeting header + bento grid (Up next, Tasks,
 * Pinned note, This week, Later today) with an optional reminder banner.
 */
@Composable
fun TodayScreen(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    // Read-only device-calendar overlay: tapping a device row opens a details
    // sheet (device events have no editor, delete or drag).
    var deviceSheet by remember { mutableStateOf<DeviceEvent?>(null) }
    Box(Modifier.fillMaxSize()) {
        TodayBody(vm, data, now, onDeviceTap = { deviceSheet = it })
        deviceSheet?.let { ev ->
            DeviceEventSheet(ev, data.prefs.use24h, onDismiss = { deviceSheet = null })
        }
    }
}

@Composable
private fun TodayBody(
    vm: AppViewModel,
    data: AppData,
    now: LocalDateTime,
    onDeviceTap: (DeviceEvent) -> Unit,
) {
    val c = LocalBento.current
    val today = now.toLocalDate()
    val nowMin = now.hour * 60 + now.minute
    val use24h = data.prefs.use24h

    // All-day events are excluded from the up-next pick and the later-today
    // rows (an all-day end of 23:59 would occupy "up next" all day); they get
    // their own pill row above the bento grid instead.
    val occT = occurrencesOn(data.events, today)
    val allDayT = occT.filter { it.allDay }
    val evT = occT.filter { !it.allDay }
    val nx = evT.firstOrNull { it.end.toMins() > nowMin }
    val openTasks = sortedOpenTasks(data.tasks, today)
    // Device events surface ONLY in the Later-today rows — they never drive
    // the up-next pick, the reminder banner or the This-week dots.
    val devT = vm.deviceEvents[today.toIso()].orEmpty()

    Column(Modifier.fillMaxSize()) {
        // ---- Header (.ahd) ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Tapping the date jumps to today's day view in the calendar.
            Column(Modifier.weight(1f).tap { vm.weekStripTap(today) }) {
                Text(
                    Fmt.greeting(now.hour),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.W500,
                    color = c.sub,
                )
                Text(
                    Fmt.todayTitle(today),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.01).em,
                    color = c.tx,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GBtn(onClick = { vm.openFab() }, primary = true) {
                    Icon(BentoIcons.Plus, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                GBtn(onClick = { vm.openSearch() }) {
                    Icon(BentoIcons.Search, null, tint = c.sub, modifier = Modifier.size(18.dp))
                }
                GBtn(onClick = { vm.openSettings() }) {
                    Icon(BentoIcons.Sliders, null, tint = c.sub, modifier = Modifier.size(19.dp))
                }
            }
        }

        // ---- Scrollable body (.abody) ----
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 14.dp),
        ) {
            // Reminder banner (.notif); an available app update borrows the
            // same slot and styling when no reminder is showing.
            val banner = activeReminder(data.events, today, nowMin, vm.dismissed)
            val update = vm.updateInfo
            if (banner != null) {
                ReminderBannerCard(
                    title = banner.event.title,
                    sub = (if (banner.minsUntil <= 0) "Starting now" else "In ${banner.minsUntil} min") +
                        (if (banner.event.loc.isNotEmpty()) " · ${banner.event.loc}" else ""),
                    onDismiss = { vm.dismissReminder(banner.key) },
                )
            } else if (update != null && !vm.updateDismissed) {
                val phase = vm.updatePhase
                ReminderBannerCard(
                    title = "Update ${update.versionName} available",
                    sub = when (phase) {
                        AppViewModel.UpdatePhase.Downloading ->
                            "Downloading… ${(vm.updateProgress * 100).toInt()}%"
                        AppViewModel.UpdatePhase.AwaitingConfirm ->
                            "Waiting for install confirmation…"
                        AppViewModel.UpdatePhase.Idle ->
                            vm.updateError ?: "Tap to download and install"
                    },
                    onDismiss = { vm.dismissUpdate() },
                    icon = BentoIcons.Download,
                    onTap = if (phase == AppViewModel.UpdatePhase.Idle) {
                        { vm.downloadAndInstallUpdate() }
                    } else {
                        null
                    },
                )
            }

            // All-day events: small accent pills between the banner slot and
            // the bento grid; tap opens the editor.
            if (allDayT.isNotEmpty()) {
                AllDayPillRow(vm, allDayT)
            }

            // Bento grid (.bgrid): 2 columns, 10dp gap, 14dp top margin.
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                UpNextTile(vm, data, evT, nx, nowMin, use24h, hasAllDay = allDayT.isNotEmpty())

                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    TasksTile(vm, openTasks, Modifier.weight(1f).fillMaxHeight())
                    PinnedNoteTile(vm, data, now, use24h, Modifier.weight(1f).fillMaxHeight())
                }

                ThisWeekTile(vm, data, today)

                LaterTodayTile(vm, data, evT, nx, use24h, devT, nowMin, onDeviceTap)
            }
        }
    }
}

/** Bento tile chrome (.bt): tile bg, 1dp border, 19dp radius, 15dp padding. */
@Composable
private fun Tile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = LocalBento.current
    Column(
        modifier
            .then(if (onClick != null) Modifier.tap(onClick = onClick) else Modifier)
            .background(c.tile, RoundedCornerShape(19.dp))
            .border(1.dp, c.bd, RoundedCornerShape(19.dp))
            .padding(15.dp),
        content = content,
    )
}

/** Tile eyebrow (.bt-eb): 9.5sp/700, 0.14em tracking, optional leading lock icon. */
@Composable
private fun Eyebrow(text: String, color: Color, lockIcon: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (lockIcon) {
            Icon(BentoIcons.Lock, null, tint = color, modifier = Modifier.size(11.dp))
        }
        Text(
            text,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.14.em,
            color = color,
        )
    }
}

/** Wrap row of accent-tinted pills for today's all-day events (location-chip style at tile level). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AllDayPillRow(vm: AppViewModel, allDay: List<EventItem>) {
    val c = LocalBento.current
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        allDay.forEach { e ->
            Text(
                e.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.W700,
                color = c.acc,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .tap { vm.openEvent(e) }
                    .background(c.accTint(0.12f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Accent-tinted reminder banner (.notif) above the grid; slides in from 16dp below + fade, 300ms. */
@Composable
private fun ReminderBannerCard(
    title: String,
    sub: String,
    onDismiss: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector = BentoIcons.Bell,
    onTap: (() -> Unit)? = null,
) {
    val c = LocalBento.current
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(300)) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 16.dp.toPx()
            }
            .then(if (onTap != null) Modifier.tap(onClick = onTap) else Modifier)
            .background(c.accTint(0.14f).compositeOver(c.tile), RoundedCornerShape(16.dp))
            .border(1.dp, c.accTint(0.35f).compositeOver(c.bd), RoundedCornerShape(16.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Icon(icon, null, tint = c.acc, modifier = Modifier.size(17.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 12.5.sp, fontWeight = FontWeight.W600, color = c.tx)
            Text(sub, fontSize = 11.sp, color = c.sub, modifier = Modifier.padding(top = 1.dp))
        }
        Box(
            Modifier
                .tap(onClick = onDismiss)
                .padding(4.dp),
        ) {
            Icon(BentoIcons.Close, null, tint = c.faint, modifier = Modifier.size(14.dp))
        }
    }
}

/** Up next tile (span 2). */
@Composable
private fun UpNextTile(
    vm: AppViewModel,
    data: AppData,
    evT: List<EventItem>,
    nx: EventItem?,
    nowMin: Int,
    use24h: Boolean,
    hasAllDay: Boolean = false,
) {
    val c = LocalBento.current
    val eyebrow = when {
        nx != null && nx.start.toMins() <= nowMin -> "HAPPENING NOW"
        nx != null -> "UP NEXT · ${Fmt.time(nx.start, use24h)}"
        evT.isNotEmpty() -> "ALL DONE FOR TODAY"
        else -> "TODAY"
    }
    val title = nx?.title ?: when {
        evT.isNotEmpty() -> "Nothing else scheduled"
        hasAllDay -> "All-day events only"
        else -> "No events today"
    }
    val meta = if (nx != null) {
        "${Fmt.time(nx.start, use24h)} – ${Fmt.time(nx.end, use24h)} · ${data.categoryOf(nx.cat).label}" +
            if (nx.start.toMins() > nowMin) " · ${Fmt.countdown(nx.start.toMins() - nowMin)}" else ""
    } else {
        "Tap to add an event"
    }
    Tile(
        Modifier.fillMaxWidth(),
        onClick = { if (nx != null) vm.openEvent(nx) else vm.newEvent() },
    ) {
        Eyebrow(eyebrow, c.acc)
        Text(
            title,
            fontSize = 19.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = (-0.01).em,
            color = c.tx,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(meta, fontSize = 12.sp, color = c.sub, modifier = Modifier.padding(top = 4.dp))
        if (nx != null && nx.loc.isNotEmpty()) {
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .background(c.acc, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Icon(BentoIcons.LocationPin, null, tint = Color.White, modifier = Modifier.size(12.dp))
                Text(nx.loc, fontSize = 12.sp, fontWeight = FontWeight.W700, color = Color.White)
            }
        }
    }
}

/** Tasks tile: top 3 open tasks with mini checkboxes. */
@Composable
private fun TasksTile(
    vm: AppViewModel,
    openTasks: List<TaskItem>,
    modifier: Modifier = Modifier,
) {
    val c = LocalBento.current
    Tile(modifier) {
        Eyebrow("TASKS · ${openTasks.size} OPEN", c.sub)
        Column(Modifier.padding(top = 8.dp)) {
            openTasks.take(3).forEach { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .tap { vm.toggleTask(t.id) }
                        .padding(vertical = 5.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    BentoCheckbox(
                        checked = t.done,
                        onToggle = { vm.toggleTask(t.id) },
                        size = 17.dp,
                        corner = 6.dp,
                    )
                    Text(
                        t.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.W500,
                        color = c.tx,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (openTasks.size > 3) {
                Text(
                    "+${openTasks.size - 3} more",
                    fontSize = 12.sp,
                    color = c.sub,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .tap { vm.setTab(Tab.Tasks) },
                )
            }
            if (openTasks.isEmpty()) {
                Text(
                    "All clear",
                    fontSize = 12.sp,
                    color = c.sub,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/** Pinned note tile: most recently updated pinned note (or empty copy). */
@Composable
private fun PinnedNoteTile(
    vm: AppViewModel,
    data: AppData,
    now: LocalDateTime,
    use24h: Boolean,
    modifier: Modifier = Modifier,
) {
    val c = LocalBento.current
    val pn: NoteItem? = data.notes.filter { it.pinned }.maxByOrNull { it.updated }
    val title = if (pn != null) pn.title.ifEmpty { "Untitled" } else "No pinned note"
    val preview = when {
        pn == null -> "Pin a note to keep it handy"
        pn.locked -> "•••• •••• ••••"
        else -> pn.body.lineSequence().firstOrNull { it.isNotBlank() } ?: "No content"
    }
    val sub = when {
        pn == null -> ""
        pn.locked -> "Locked · tap to unlock"
        else -> Fmt.relEdit(pn.updated, now, use24h)
    }
    Tile(
        modifier,
        onClick = { if (pn != null) vm.openNote(pn.id) else vm.setTab(Tab.Notes) },
    ) {
        Eyebrow("PINNED NOTE", c.sub, lockIcon = pn?.locked == true)
        Text(
            title,
            fontSize = 14.sp,
            fontWeight = FontWeight.W600,
            color = c.tx,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            preview,
            fontSize = 12.sp,
            letterSpacing = 0.14.em,
            color = c.faint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        if (sub.isNotEmpty()) {
            Text(sub, fontSize = 12.sp, color = c.sub, modifier = Modifier.padding(top = 7.dp))
        } else {
            Spacer(Modifier.height(7.dp))
        }
    }
}

/** This week tile (span 2): 7 day columns with dots. */
@Composable
private fun ThisWeekTile(vm: AppViewModel, data: AppData, today: LocalDate) {
    val c = LocalBento.current
    val wkStart = startOfWeek(today, data.prefs.monday)
    Tile(Modifier.fillMaxWidth()) {
        Eyebrow("THIS WEEK", c.sub)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        ) {
            for (i in 0..6) {
                val d = wkStart.plusDays(i.toLong())
                val isToday = d == today
                val catIds = LinkedHashSet<String>()
                for (e in occurrencesOn(data.events, d)) {
                    catIds.add(data.categoryOf(e.cat).id)
                }
                val dots = catIds.take(3).map { data.categoryOf(it).color }
                Column(
                    Modifier
                        .weight(1f)
                        .tap { vm.weekStripTap(d) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        "SMTWTFS"[Fmt.dow(d)].toString(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.W600,
                        letterSpacing = 0.04.em,
                        color = c.faint,
                    )
                    Box(
                        Modifier
                            .widthIn(min = 24.dp)
                            .then(
                                if (isToday) {
                                    Modifier.background(c.acc, RoundedCornerShape(8.dp))
                                } else {
                                    Modifier
                                }
                            )
                            .padding(vertical = 1.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "${d.dayOfMonth}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.W600,
                            color = if (isToday) Color.White else c.tx,
                        )
                    }
                    Row(
                        Modifier
                            .padding(top = 2.dp)
                            .height(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                    ) {
                        dots.forEach { Dot(it, size = 4.dp) }
                    }
                }
            }
        }
    }
}

/** Later today tile (span 2): remaining events after the "up next" one. */
@Composable
private fun LaterTodayTile(
    vm: AppViewModel,
    data: AppData,
    evT: List<EventItem>,
    nx: EventItem?,
    use24h: Boolean,
    deviceEvents: List<DeviceEvent>,
    nowMin: Int,
    onDeviceTap: (DeviceEvent) -> Unit,
) {
    val c = LocalBento.current
    val later = if (nx != null) evT.filter { it.start.toMins() > nx.start.toMins() } else emptyList()
    // Read-only device events join the rows on the same "later than the
    // current pick" rule (later than now when there is no Bento pick) — they
    // never become the pick itself. Both inputs are start-sorted and sortedBy
    // is stable, so Bento rows are untouched when the overlay is off.
    val cutoff = nx?.start?.toMins() ?: nowMin
    val laterDev = deviceEvents.filter { !it.allDay && it.start.toMins() > cutoff }
    val rows = (later.map { Pair<EventItem?, DeviceEvent?>(it, null) } +
        laterDev.map { Pair<EventItem?, DeviceEvent?>(null, it) })
        .sortedBy { (e, d) -> e?.start?.toMins() ?: d!!.start.toMins() }
    Tile(Modifier.fillMaxWidth()) {
        Eyebrow("LATER TODAY", c.sub)
        if (rows.isNotEmpty()) {
            Column(Modifier.padding(top = 7.dp)) {
                rows.forEach { (e, d) ->
                    if (e != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .tap { vm.openEvent(e) }
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                Fmt.time(e.start, use24h),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.W600,
                                color = c.sub,
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                modifier = Modifier.width(48.dp),
                            )
                            Text(
                                e.title,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.W500,
                                color = c.tx,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Dot(data.categoryOf(e.cat).color)
                        }
                    } else {
                        val dev = d!!
                        DeviceLaterRow(dev, use24h, onTap = { onDeviceTap(dev) })
                    }
                }
            }
        } else {
            Text(
                if (evT.isNotEmpty()) "That was the last one — evening is yours" else "Nothing scheduled",
                fontSize = 12.sp,
                color = c.faint,
                modifier = Modifier.padding(top = 10.dp, bottom = 2.dp),
            )
        }
    }
}

/**
 * Later-today row for a read-only device event: time + title with a faint
 * calendar-name suffix, and a 6dp dot in the device-calendar color. Tap opens
 * the details sheet — device events are not editable in Bento.
 */
@Composable
private fun DeviceLaterRow(e: DeviceEvent, use24h: Boolean, onTap: () -> Unit) {
    val c = LocalBento.current
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick = onTap)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            Fmt.time(e.start, use24h),
            fontSize = 12.sp,
            fontWeight = FontWeight.W600,
            color = c.sub,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
            modifier = Modifier.width(48.dp),
        )
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                e.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.W500,
                color = c.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                e.calName,
                fontSize = 10.sp,
                color = c.faint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Dot(hexColor(e.colorHex), size = 6.dp)
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
