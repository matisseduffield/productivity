package com.bento.calendar.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.Priority
import com.bento.calendar.data.parseQuickAdd
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.components.BentoSheet
import com.bento.calendar.ui.components.BentoTextField
import com.bento.calendar.ui.components.Dot
import com.bento.calendar.ui.components.GBtn
import com.bento.calendar.ui.components.tap
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.theme.hexColor
import java.time.LocalDate

/**
 * Create menu bottom sheet (prototype `fabOpen` sheet, markup lines 252-256):
 * "Create" title + natural-language quick add + three tappable rows —
 * New event / New task / New note.
 */
@Composable
fun CreateSheet(vm: AppViewModel) {
    val c = LocalBento.current
    BentoSheet(onDismiss = vm::closeSheets) {
        Text(
            "Create",
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            color = c.tx,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        QuickAdd(vm)
        CreateRow(BentoIcons.TabCalendar, "New event") { vm.newEvent() }
        CreateRow(BentoIcons.TabTasks, "New task") { vm.newTask() }
        CreateRow(BentoIcons.Doc, "New note") { vm.newNote() }
    }
}

/**
 * Natural-language quick add: "Dentist tue 3pm" → event, "Buy milk fri" →
 * dated task, bare text → undated task. Chips live-preview what
 * [parseQuickAdd] will create; Done on the keyboard or the blue (+) commits
 * via [AppViewModel.commitQuickAdd] (which closes the sheet).
 */
@Composable
private fun QuickAdd(vm: AppViewModel) {
    val c = LocalBento.current
    val data by vm.data.collectAsState()
    val now by vm.now.collectAsState()
    val today = now.toLocalDate()
    var text by remember { mutableStateOf("") }
    val fieldFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { fieldFocus.requestFocus() }
    // Read the live field text at commit time; clearing is belt-and-braces —
    // a successful commit closes the whole sheet (fabOpen = false).
    val commit = {
        if (vm.commitQuickAdd(text)) text = ""
    }
    Column(Modifier.padding(bottom = 6.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BentoTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f).focusRequester(fieldFocus),
                placeholder = "Quick add — try “Dentist tue 3pm”",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
            )
            GBtn(onClick = commit, primary = true) {
                Icon(BentoIcons.Plus, "Add", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
        val parsed = parseQuickAdd(text, today, data?.prefs?.durDef ?: 60)
        if (parsed != null) {
            val use24h = data?.prefs?.use24h ?: true
            Row(
                Modifier.padding(top = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                PreviewChip(if (parsed.isEvent) "Event" else "Task", accent = true)
                // A time without a date parses with date = null; the commit
                // defaults such events to today, so preview that too. Undated
                // tasks land in the Tasks screen's "Someday" section.
                val date = parsed.date ?: if (parsed.isEvent) today else null
                PreviewChip(date?.let { dateChipLabel(it, today) } ?: "Someday")
                if (parsed.isEvent) {
                    PreviewChip("${Fmt.time(parsed.start!!, use24h)}–${Fmt.time(parsed.end!!, use24h)}")
                }
                // Priority only persists on tasks (events have no priority
                // field) — previewing it on an event would promise a flag
                // that commitQuickAdd drops.
                if (!parsed.isEvent && parsed.priority > 0) {
                    PreviewChip(
                        Priority.label(parsed.priority),
                        dot = Priority.colorHex(parsed.priority)?.let { hexColor(it) },
                    )
                }
            }
        }
    }
}

/** "Today" / "Tomorrow" / "Sat 11 Jul" — same language as the task due chips. */
private fun dateChipLabel(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> Fmt.dayShort(d)
}

/**
 * Small readout pill under the quick-add field (same pill language as
 * CategoryPills). An optional [dot] color prefixes the label — the priority
 * chip uses it with the flag tint from [Priority.colorHex].
 */
@Composable
private fun PreviewChip(label: String, accent: Boolean = false, dot: Color? = null) {
    val c = LocalBento.current
    Row(
        Modifier
            .background(if (accent) c.accTint(0.12f) else c.inp, CircleShape)
            .border(1.dp, if (accent) c.acc else c.bd, CircleShape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (dot != null) Dot(dot, size = 5.dp)
        Text(
            label,
            fontSize = 11.sp,
            fontWeight = FontWeight.W600,
            color = if (accent) c.tx else c.sub,
        )
    }
}

/** One `.fmrow`: 37dp icon square (tile bg, 1dp border, 12dp radius, accent 16dp icon) + 14sp/600 label. */
@Composable
private fun CreateRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val c = LocalBento.current
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(37.dp)
                .background(c.tile, RoundedCornerShape(12.dp))
                .border(1.dp, c.bd, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = c.acc, modifier = Modifier.size(16.dp))
        }
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.W600, color = c.tx)
    }
}
