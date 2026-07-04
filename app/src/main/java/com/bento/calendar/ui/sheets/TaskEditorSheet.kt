package com.bento.calendar.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.Priority
import com.bento.calendar.data.Recur
import com.bento.calendar.data.SubTask
import com.bento.calendar.data.newId
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Arm
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.components.BentoCheckbox
import com.bento.calendar.ui.components.BentoDateField
import com.bento.calendar.ui.components.BentoSelectField
import com.bento.calendar.ui.components.BentoSheet
import com.bento.calendar.ui.components.BentoTextField
import com.bento.calendar.ui.components.BentoTimeField
import com.bento.calendar.ui.components.CategoryPills
import com.bento.calendar.ui.components.DangerTextButton
import com.bento.calendar.ui.components.Dot
import com.bento.calendar.ui.components.FieldLabel
import com.bento.calendar.ui.components.GBtn
import com.bento.calendar.ui.components.PrimaryButton
import com.bento.calendar.ui.components.TextLink
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.theme.hexColor
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Task editor bottom sheet (prototype `tkOpen` sheet, markup lines 268-274,
 * logic lines 685-692): Task / optional Due date / Category (incl. None) /
 * Save / two-tap Delete. A "Clear" link beside the due-date label replaces the
 * native date input's clear affordance.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskEditorSheet(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    val c = LocalBento.current
    val d = vm.tkDraft ?: return
    val titleFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        if (d.id == null) titleFocus.requestFocus()
    }
    // Add-step input state lives up here so every save path can flush a
    // half-typed step into the draft first — Save must never silently drop
    // text sitting in the field. updateTaskDraft mutates synchronously, so
    // flush-then-save within one handler is safe.
    var newStep by remember { mutableStateOf("") }
    val addStep = {
        val t = newStep.trim()
        if (t.isNotEmpty()) {
            vm.updateTaskDraft { it.copy(subs = it.subs + SubTask(newId(), t)) }
            newStep = ""
        }
    }
    val saveWithPendingStep = {
        addStep()
        vm.saveTask()
    }
    BentoSheet(onDismiss = vm::closeSheets) {
        Text(
            if (d.id != null) "Edit task" else "New task",
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            color = c.tx,
        )
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Task")
            BentoTextField(
                value = d.title,
                onValueChange = { v -> vm.updateTaskDraft { it.copy(title = v) } },
                modifier = Modifier.focusRequester(titleFocus),
                placeholder = "What needs doing?",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                // Quick-add flow: type the task, hit Done, saved. Read the
                // live draft (not the composed snapshot) so a same-frame edit
                // can't slip a stale title past the guard; on a blank title,
                // dismiss the keyboard instead of doing nothing.
                keyboardActions = KeyboardActions(onDone = {
                    if (vm.tkDraft?.title?.isNotBlank() == true) {
                        saveWithPendingStep()
                    } else {
                        focusManager.clearFocus()
                    }
                }),
            )
        }
        Column(Modifier.padding(top = 15.dp)) {
            Row(Modifier.fillMaxWidth()) {
                FieldLabel("Due date · optional")
                Spacer(Modifier.weight(1f))
                if (d.due != null) {
                    TextLink("Clear", onClick = { vm.updateTaskDraft { it.copy(due = null) } })
                }
            }
            BentoDateField(
                value = d.due,
                display = d.due?.let { Fmt.dayShort(it) } ?: "No due date",
                onPick = { v -> vm.updateTaskDraft { it.copy(due = v) } },
            )
            // Quick-pick chips: relative dates off today, mirroring the pill
            // language of CategoryPills below.
            val today = LocalDate.now()
            Row(
                Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                DueChip("Today", active = d.due == today) {
                    vm.updateTaskDraft { it.copy(due = LocalDate.now()) }
                }
                DueChip("Tomorrow", active = d.due == today.plusDays(1)) {
                    vm.updateTaskDraft { it.copy(due = LocalDate.now().plusDays(1)) }
                }
                DueChip("Next week", active = d.due == today.plusDays(7)) {
                    vm.updateTaskDraft { it.copy(due = LocalDate.now().plusDays(7)) }
                }
            }
        }
        // Reminder time on the due date — only meaningful with one (saveTask
        // nulls remindAt whenever due is cleared).
        if (d.due != null) {
            Column(Modifier.padding(top = 15.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    FieldLabel("Remind · optional")
                    Spacer(Modifier.weight(1f))
                    if (d.remindAt != null) {
                        TextLink("Clear", onClick = { vm.updateTaskDraft { it.copy(remindAt = null) } })
                    }
                }
                BentoTimeField(
                    // Empty field opens the picker on a 09:00 suggestion.
                    valueHm = d.remindAt ?: "09:00",
                    display = d.remindAt?.let { Fmt.time(it, data.prefs.use24h) } ?: "No reminder",
                    use24h = data.prefs.use24h,
                    onPick = { v -> vm.updateTaskDraft { it.copy(remindAt = v) } },
                )
            }
        }
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Category")
            CategoryPills(
                categories = data.categories,
                selected = d.cat,
                onSelect = { v -> vm.updateTaskDraft { it.copy(cat = v) } },
                includeNone = true,
            )
        }
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Priority")
            // Same pill language as CategoryPills / the due-date quick chips;
            // colored levels carry their Priority tint as a leading dot.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Priority.ALL.forEach { p ->
                    PriorityPill(
                        label = Priority.label(p),
                        dot = Priority.colorHex(p)?.let { hexColor(it) },
                        active = d.priority == p,
                        onClick = { vm.updateTaskDraft { it.copy(priority = p) } },
                    )
                }
            }
        }
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Repeat")
            BentoSelectField(
                value = d.recur,
                options = listOf(
                    "Doesn't repeat" to Recur.NONE,
                    "Daily" to Recur.DAILY,
                    "Weekly" to Recur.WEEKLY,
                    "Monthly" to Recur.MONTHLY,
                ),
                onSelect = { v -> vm.updateTaskDraft { it.copy(recur = v) } },
            )
            if (d.recur != Recur.NONE && d.due == null) {
                Text(
                    "Repeats from today when completed",
                    fontSize = 11.sp,
                    color = c.faint,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 7.dp),
                )
            }
        }
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Checklist · optional")
            d.subs.forEach { sub ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    BentoCheckbox(
                        checked = sub.done,
                        onToggle = {
                            vm.updateTaskDraft { dr ->
                                dr.copy(subs = dr.subs.map { s ->
                                    if (s.id == sub.id) s.copy(done = !s.done) else s
                                })
                            }
                        },
                        size = 18.dp,
                        corner = 6.dp,
                    )
                    Text(
                        sub.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W500,
                        color = if (sub.done) c.faint else c.tx,
                        textDecoration = if (sub.done) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    // Small remove (×): padded past the glyph for a usable target.
                    Box(
                        Modifier
                            .pressable(onClick = {
                                vm.updateTaskDraft { dr ->
                                    dr.copy(subs = dr.subs.filterNot { it.id == sub.id })
                                }
                            })
                            .padding(6.dp),
                    ) {
                        Icon(BentoIcons.Close, null, tint = c.faint, modifier = Modifier.size(13.dp))
                    }
                }
            }
            // Add-step input: appends to the draft only — like every other
            // field here, nothing persists until Save.
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (d.subs.isEmpty()) 0.dp else 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BentoTextField(
                    value = newStep,
                    onValueChange = { newStep = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "Add step",
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addStep() }),
                )
                GBtn(onClick = addStep) {
                    Icon(BentoIcons.PlusLight, null, tint = c.sub, modifier = Modifier.size(16.dp))
                }
            }
        }
        PrimaryButton("Save task", onClick = saveWithPendingStep)
        if (d.id != null) {
            DangerTextButton(
                if (vm.isArmed(Arm.TASK)) "Tap again to delete" else "Delete task",
                onClick = vm::deleteTask,
            )
        }
    }
}

/**
 * Priority level pill — the CategoryPills language with the category dot
 * swapped for the level's [Priority.colorHex] tint (none for NONE).
 */
@Composable
private fun PriorityPill(label: String, dot: Color?, active: Boolean, onClick: () -> Unit) {
    val c = LocalBento.current
    Row(
        Modifier
            .pressable(onClick = onClick)
            .background(if (active) c.accTint(0.12f) else c.inp, CircleShape)
            .border(1.dp, if (active) c.acc else c.bd, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Dot(dot, size = 6.dp)
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.W600,
            color = if (active) c.tx else c.sub,
        )
    }
}

/** Due-date quick chip — same pill language as CategoryPills (Common.kt). */
@Composable
private fun DueChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalBento.current
    Box(
        Modifier
            .pressable(onClick = onClick)
            .background(if (active) c.accTint(0.12f) else c.inp, CircleShape)
            .border(1.dp, if (active) c.acc else c.bd, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.W600,
            color = if (active) c.tx else c.sub,
        )
    }
}
