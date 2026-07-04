package com.bento.calendar.ui.tasks

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import com.bento.calendar.data.Cats
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
                section.tasks.forEach { t ->
                    // key() so swipe state stays with the task, not the slot —
                    // otherwise the next row inherits a mid-settle offset.
                    key(t.id) {
                        TaskRow(
                            task = t,
                            todayIso = todayIso,
                            today = today,
                            showExtras = true,
                            onToggle = { vm.toggleTask(t.id) },
                            onOpen = { vm.openTask(t) },
                            onDelete = { vm.deleteTaskBySwipe(t) },
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
                                todayIso = todayIso,
                                today = today,
                                showExtras = false,
                                onToggle = { vm.toggleTask(t.id) },
                                onOpen = { vm.openTask(t) },
                                onDelete = { vm.deleteTaskBySwipe(t) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Task list row (.trow): checkbox, title, then (open rows only) category dot
 * and due chip. Done rows are struck through and faint. Wrapped in a
 * [SwipeActionRow]: swipe right to toggle done, swipe left to delete (with
 * undo via the AppRoot banner).
 */
@Composable
private fun TaskRow(
    task: TaskItem,
    todayIso: String,
    today: LocalDate,
    showExtras: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = LocalBento.current
    SwipeActionRow(
        right = SwipeAction(BentoIcons.Check, tint = c.acc, onTrigger = onToggle),
        left = SwipeAction(BentoIcons.Trash, tint = c.dng, onTrigger = onDelete),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(c.bg)
                .hairlineBottom(c.line)
                .padding(horizontal = 2.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BentoCheckbox(checked = task.done, onToggle = onToggle, size = 22.dp, corner = 8.dp)
            Text(
                task.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.W500,
                color = if (task.done) c.faint else c.tx,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .tap(onClick = onOpen),
            )
            if (showExtras) {
                if (task.cat.isNotEmpty()) {
                    Dot(Cats.of(task.cat).color)
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
}
