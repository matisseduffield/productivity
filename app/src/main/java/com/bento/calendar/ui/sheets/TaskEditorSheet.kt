package com.bento.calendar.ui.sheets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Arm
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.components.BentoDateField
import com.bento.calendar.ui.components.BentoSheet
import com.bento.calendar.ui.components.BentoTextField
import com.bento.calendar.ui.components.CategoryPills
import com.bento.calendar.ui.components.DangerTextButton
import com.bento.calendar.ui.components.FieldLabel
import com.bento.calendar.ui.components.PrimaryButton
import com.bento.calendar.ui.components.TextLink
import com.bento.calendar.ui.theme.LocalBento
import java.time.LocalDateTime

/**
 * Task editor bottom sheet (prototype `tkOpen` sheet, markup lines 268-274,
 * logic lines 685-692): Task / optional Due date / Category (incl. None) /
 * Save / two-tap Delete. A "Clear" link beside the due-date label replaces the
 * native date input's clear affordance.
 */
@Composable
fun TaskEditorSheet(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    val c = LocalBento.current
    val d = vm.tkDraft ?: return
    val titleFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        if (d.id == null) titleFocus.requestFocus()
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
                        vm.saveTask()
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
        }
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Category")
            CategoryPills(
                selected = d.cat,
                onSelect = { v -> vm.updateTaskDraft { it.copy(cat = v) } },
                includeNone = true,
            )
        }
        PrimaryButton("Save task", onClick = vm::saveTask)
        if (d.id != null) {
            DangerTextButton(
                if (vm.isArmed(Arm.TASK)) "Tap again to delete" else "Delete task",
                onClick = vm::deleteTask,
            )
        }
    }
}
