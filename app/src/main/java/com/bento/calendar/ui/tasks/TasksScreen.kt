package com.bento.calendar.ui.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.Priority
import com.bento.calendar.data.Recur
import com.bento.calendar.data.TaskItem
import com.bento.calendar.data.toIso
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.components.BentoCheckbox
import com.bento.calendar.ui.components.Dot
import com.bento.calendar.ui.components.EmptyText
import com.bento.calendar.ui.components.GBtn
import com.bento.calendar.ui.components.SectionLabel
import com.bento.calendar.ui.components.SwipeAction
import com.bento.calendar.ui.components.SwipeActionRow
import com.bento.calendar.ui.components.hairlineBottom
import com.bento.calendar.ui.components.tap
import com.bento.calendar.ui.taskSections
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.theme.color
import com.bento.calendar.ui.theme.hexColor
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun TasksScreen(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    val c = LocalBento.current
    val today = now.toLocalDate()
    val todayIso = today.toIso()
    val openCount = data.tasks.count { !it.done }
    val sections = taskSections(data.tasks, today)
    val doneTasks = data.tasks.filter { it.done }

    Column(Modifier.fillMaxSize()) {
        // ---- Header (.ahd) ----
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 10.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Tasks", fontSize = 12.sp, fontWeight = FontWeight.W500, color = c.sub)
                Text(
                    "$openCount open",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.01).em,
                    color = c.tx,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GBtn(onClick = { vm.openSearch() }) {
                    Icon(BentoIcons.Search, null, tint = c.sub, modifier = Modifier.size(18.dp))
                }
                GBtn(onClick = { vm.newTask() }, primary = true) {
                    Icon(BentoIcons.PlusLight, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ---- Body (.abody) ----
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 14.dp),
        ) {
            sections.forEach { section ->
                val n = section.tasks.size
                SectionLabel(section.label, count = "$n " + if (n == 1) "task" else "tasks")
                // Priority descending within the section; sortedByDescending
                // is stable, so equal priorities keep the section's due-date
                // order from taskSections.
                section.tasks.sortedByDescending { it.priority }.forEach { t ->
                    // key() so swipe state stays with the task, not the slot —
                    // otherwise the next row inherits a mid-settle offset.
                    key(t.id) {
                        TaskRow(
                            task = t,
                            categoryColor = if (t.cat.isNotEmpty()) data.categoryOf(t.cat).color else null,
                            todayIso = todayIso,
                            today = today,
                            showExtras = true,
                            onToggle = { vm.toggleTask(t.id) },
                            onOpen = { vm.openTask(t) },
                            onDelete = { vm.deleteTaskBySwipe(t) },
                            onToggleSub = { subId -> vm.toggleSub(t.id, subId) },
                        )
                    }
                }
            }

            if (openCount == 0) {
                EmptyText("Nothing to do — tap + to add a task.")
            }

            if (doneTasks.isNotEmpty()) {
                // ---- Completed collapsible header (.dnhd) ----
                val chevron by animateFloatAsState(
                    targetValue = if (vm.doneOpen) 180f else 0f,
                    animationSpec = tween(200),
                    label = "doneChevron",
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 2.dp, end = 2.dp, top = 20.dp, bottom = 6.dp)
                        .tap { vm.toggleDoneOpen() },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Completed · ${doneTasks.size}".uppercase(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = 0.12.em,
                        color = c.sub,
                    )
                    Icon(
                        BentoIcons.ChevronDown,
                        null,
                        tint = c.sub,
                        modifier = Modifier.size(13.dp).rotate(chevron),
                    )
                }
                if (vm.doneOpen) {
                    doneTasks.forEach { t ->
                        key(t.id) {
                            TaskRow(
                                task = t,
                                categoryColor = if (t.cat.isNotEmpty()) data.categoryOf(t.cat).color else null,
                                todayIso = todayIso,
                                today = today,
                                showExtras = false,
                                onToggle = { vm.toggleTask(t.id) },
                                onOpen = { vm.openTask(t) },
                                onDelete = { vm.deleteTaskBySwipe(t) },
                                onToggleSub = { subId -> vm.toggleSub(t.id, subId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Task list row (.trow): checkbox, title (with a priority dot when set), then
 * (open rows only) repeat badge, category dot, reminder bell and due chip. Tasks with a
 * checklist get a compact progress line under the title ("2/5" + thin bar +
 * chevron) that expands the steps inline. Done rows are struck through and
 * faint. Wrapped in a [SwipeActionRow]: swipe right to toggle done (repeating
 * tasks reschedule instead — the VM routes through completeTask), swipe left
 * to delete (with undo via the AppRoot banner). The expanded checklist lives
 * OUTSIDE the swipe wrapper so drags only ever move the main row.
 */
@Composable
private fun TaskRow(
    task: TaskItem,
    categoryColor: Color?,
    todayIso: String,
    today: LocalDate,
    showExtras: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onToggleSub: (String) -> Unit,
) {
    val c = LocalBento.current
    val subs = task.subs
    val doneSubs = subs.count { it.done }
    var expanded by remember { mutableStateOf(false) }
    val showSubs = expanded && showExtras && subs.isNotEmpty()
    val prColor = Priority.colorHex(task.priority)?.let { hexColor(it) }
    Column(Modifier.fillMaxWidth()) {
        SwipeActionRow(
            right = SwipeAction(BentoIcons.Check, tint = c.acc, onTrigger = onToggle),
            left = SwipeAction(BentoIcons.Trash, tint = c.dng, onTrigger = onDelete),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    // The hairline moves to the checklist block while expanded
                    // so the steps still read as part of this row.
                    .then(if (showSubs) Modifier else Modifier.hairlineBottom(c.line))
                    .padding(horizontal = 2.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BentoCheckbox(checked = task.done, onToggle = onToggle, size = 22.dp, corner = 8.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .tap(onClick = onOpen),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (showExtras && prColor != null) {
                            Dot(prColor, size = 6.dp)
                        }
                        Text(
                            task.title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.W500,
                            color = if (task.done) c.faint else c.tx,
                            textDecoration = if (task.done) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (showExtras && subs.isNotEmpty()) {
                        // Progress line doubles as the expand toggle; its own
                        // tap target sits inside the title's onOpen tap, and
                        // the inner clickable wins, so expanding never opens
                        // the editor.
                        val chevron by animateFloatAsState(
                            targetValue = if (expanded) 180f else 0f,
                            animationSpec = tween(200),
                            label = "subsChevron",
                        )
                        Row(
                            Modifier
                                .tap { expanded = !expanded }
                                .padding(top = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                        ) {
                            Text(
                                "$doneSubs/${subs.size}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.W600,
                                color = c.faint,
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                            )
                            Box(
                                Modifier
                                    .width(56.dp)
                                    .height(3.dp)
                                    .background(c.inp, CircleShape),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(doneSubs / subs.size.toFloat())
                                        .background(prColor ?: c.acc, CircleShape),
                                )
                            }
                            Icon(
                                BentoIcons.ChevronDown,
                                null,
                                tint = c.faint,
                                modifier = Modifier.size(11.dp).rotate(chevron),
                            )
                        }
                    }
                }
                if (showExtras) {
                    if (task.recur != Recur.NONE) {
                        // Repeating-task badge: completing it reschedules instead
                        // of marking done.
                        Icon(
                            BentoIcons.Repeat,
                            null,
                            tint = c.faint,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    if (categoryColor != null) {
                        Dot(categoryColor)
                    }
                    if (task.remindAt != null && !task.done) {
                        // Reminder badge: a time is set on the due date.
                        Icon(
                            BentoIcons.Bell,
                            null,
                            tint = c.faint,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    val due = task.due
                    if (due != null && !task.done) {
                        val overdue = due < todayIso
                        Box(
                            Modifier.background(
                                if (overdue) c.dng else c.inp,
                                RoundedCornerShape(8.dp),
                            ),
                        ) {
                            Text(
                                Fmt.dueLabel(due, today),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.W700,
                                color = if (overdue) Color.White else c.sub,
                                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
        if (showSubs) {
            // Inline checklist: indented past the checkbox column, each step
            // toggles via vm.toggleSub.
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(c.bg)
                    .hairlineBottom(c.line)
                    .padding(start = 36.dp, end = 2.dp, bottom = 9.dp),
            ) {
                subs.forEach { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .tap { onToggleSub(s.id) }
                            .padding(vertical = 4.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        BentoCheckbox(
                            checked = s.done,
                            onToggle = { onToggleSub(s.id) },
                            size = 16.dp,
                            corner = 6.dp,
                        )
                        Text(
                            s.title,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.W500,
                            color = if (s.done) c.faint else c.tx,
                            textDecoration = if (s.done) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}
