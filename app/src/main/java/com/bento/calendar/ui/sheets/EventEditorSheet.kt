package com.bento.calendar.ui.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.Recur
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Arm
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.components.BentoDateField
import com.bento.calendar.ui.components.BentoSelectField
import com.bento.calendar.ui.components.BentoSheet
import com.bento.calendar.ui.components.BentoTextField
import com.bento.calendar.ui.components.BentoTimeField
import com.bento.calendar.ui.components.CategoryPills
import com.bento.calendar.ui.components.DangerTextButton
import com.bento.calendar.ui.components.FieldLabel
import com.bento.calendar.ui.components.PrimaryButton
import com.bento.calendar.ui.theme.LocalBento
import java.time.LocalDateTime

/**
 * Event editor bottom sheet (prototype `evOpen` sheet, markup lines 257-267,
 * logic lines 674-684): Title / Date / Starts+Ends / Category / Repeat+Reminder
 * / Location / Save / two-tap Delete. Repeating-event note when editing a
 * recurring event.
 */
@Composable
fun EventEditorSheet(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    val c = LocalBento.current
    val d = vm.evDraft ?: return
    val use24h = data.prefs.use24h
    BentoSheet(onDismiss = vm::closeSheets) {
        Text(
            if (d.id != null) "Edit event" else "New event",
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            color = c.tx,
        )
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Title")
            BentoTextField(
                value = d.title,
                onValueChange = { v -> vm.updateEventDraft { it.copy(title = v) } },
                placeholder = "Event title",
            )
        }
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Date")
            BentoDateField(
                value = d.date,
                display = Fmt.dayShort(d.date),
                onPick = { v -> vm.updateEventDraft { it.copy(date = v) } },
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                FieldLabel("Starts")
                BentoTimeField(
                    valueHm = d.start,
                    display = Fmt.time(d.start, use24h),
                    use24h = use24h,
                    onPick = { v -> vm.updateEventDraft { it.copy(start = v) } },
                )
            }
            Column(Modifier.weight(1f)) {
                FieldLabel("Ends")
                BentoTimeField(
                    valueHm = d.end,
                    display = Fmt.time(d.end, use24h),
                    use24h = use24h,
                    onPick = { v -> vm.updateEventDraft { it.copy(end = v) } },
                )
            }
        }
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Category")
            CategoryPills(
                selected = d.cat,
                onSelect = { v -> vm.updateEventDraft { it.copy(cat = v) } },
                includeNone = false,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 15.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                FieldLabel("Repeat")
                BentoSelectField(
                    value = d.recur,
                    options = listOf(
                        "Doesn't repeat" to Recur.NONE,
                        "Daily" to Recur.DAILY,
                        "Weekly" to Recur.WEEKLY,
                        "Monthly" to Recur.MONTHLY,
                    ),
                    onSelect = { v -> vm.updateEventDraft { it.copy(recur = v) } },
                )
            }
            Column(Modifier.weight(1f)) {
                FieldLabel("Reminder")
                BentoSelectField(
                    value = d.remind,
                    options = listOf<Pair<String, Int?>>(
                        "None" to null,
                        "At start" to 0,
                        "10 min before" to 10,
                        "30 min before" to 30,
                        "1 hour before" to 60,
                        "1 day before" to 1440,
                    ),
                    onSelect = { v -> vm.updateEventDraft { it.copy(remind = v) } },
                )
            }
        }
        if (d.id != null && d.recur != Recur.NONE) {
            Text(
                "Repeats — edits apply to every occurrence",
                fontSize = 12.sp,
                color = c.faint,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 8.dp),
            )
        }
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Location")
            BentoTextField(
                value = d.loc,
                onValueChange = { v -> vm.updateEventDraft { it.copy(loc = v) } },
                placeholder = "Optional",
            )
        }
        PrimaryButton("Save event", onClick = vm::saveEvent)
        if (d.id != null) {
            DangerTextButton(
                if (vm.isArmed(Arm.EVENT)) "Tap again to delete" else "Delete event",
                onClick = vm::deleteEvent,
            )
        }
    }
}
