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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.Recur
import com.bento.calendar.data.minsToHm
import com.bento.calendar.data.toMins
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Arm
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.components.BentoDateField
import com.bento.calendar.ui.components.BentoSelectField
import com.bento.calendar.ui.components.BentoSheet
import com.bento.calendar.ui.components.BentoSwitch
import com.bento.calendar.ui.components.BentoTextField
import com.bento.calendar.ui.components.BentoTimeField
import com.bento.calendar.ui.components.CategoryPills
import com.bento.calendar.ui.components.DangerTextButton
import com.bento.calendar.ui.components.FieldLabel
import com.bento.calendar.ui.components.PrimaryButton
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.theme.LocalBento
import java.time.LocalDateTime

/** Duration quick-pick presets: label -> minutes. */
private val DurationPresets = listOf(
    "30 min" to 30,
    "45 min" to 45,
    "1 h" to 60,
    "1.5 h" to 90,
    "2 h" to 120,
)

/**
 * Event editor bottom sheet (prototype `evOpen` sheet, markup lines 257-267,
 * logic lines 674-684): Title / Date / Starts+Ends / Category / Repeat+Reminder
 * / Location / Save / two-tap Delete. Repeating-event note when editing a
 * recurring event.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EventEditorSheet(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    val c = LocalBento.current
    val d = vm.evDraft ?: return
    val use24h = data.prefs.use24h
    val titleFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        if (d.id == null) titleFocus.requestFocus()
    }
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
                modifier = Modifier.focusRequester(titleFocus),
                placeholder = "Event title",
                // Date and time below are tap-to-open pickers, not focusable
                // text fields, so there is nothing for "Next" to advance to.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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
        // All-day toggle; when on, the time-of-day controls below disappear
        // (saveEvent forces 00:00-23:59 regardless of the hidden fields).
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("All day", fontSize = 13.5.sp, fontWeight = FontWeight.W600, color = c.tx)
            Spacer(Modifier.weight(1f))
            BentoSwitch(on = d.allDay, onToggle = {
                val durDef = data.prefs.durDef
                vm.updateEventDraft {
                    val next = it.copy(allDay = !it.allDay)
                    // Toggling all-day OFF on a draft holding the all-day
                    // sentinels (00:00-23:59, e.g. opened from a persisted
                    // all-day event) would expose a nonsensical near-24h timed
                    // event — restore the newEvent defaults instead. A timed
                    // draft toggled on->off within one edit keeps its hidden
                    // field values, so it never trips the sentinel check.
                    if (it.allDay && it.start == "00:00" && it.end == "23:59") {
                        next.copy(
                            start = "09:00",
                            end = minsToHm((9 * 60 + durDef).coerceAtMost(1439)),
                        )
                    } else {
                        next
                    }
                }
            })
        }
        if (!d.allDay) {
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
                        onPick = { newStart ->
                            // Keep the current duration when the start moves.
                            // Zero-or-negative durations fall back to 60 min,
                            // matching saveEvent's coercion.
                            val raw = d.end.toMins() - d.start.toMins()
                            val dur = if (raw <= 0) 60 else raw
                            // Cap the start at 23:58 so the end always has at
                            // least one minute of headroom before 23:59.
                            val startM = newStart.toMins().coerceAtMost(1438)
                            val end = minsToHm((startM + dur).coerceIn(startM + 1, 1439))
                            vm.updateEventDraft { it.copy(start = minsToHm(startM), end = end) }
                        },
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
            val durMins = d.end.toMins() - d.start.toMins()
            if (durMins > 0) {
                Text(
                    Fmt.duration(d.start, d.end),
                    fontSize = 11.sp,
                    color = c.sub,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                )
            } else {
                // Mirrors saveEvent's coercion: end = min(start + 60, 23:59).
                val fixedEnd = (d.start.toMins() + 60).coerceAtMost(1439)
                Text(
                    if (fixedEnd == d.start.toMins()) {
                        // Start is 23:59 — no headroom left; avoid "will save as 0 min".
                        "Ends before it starts — end will be adjusted on save"
                    } else {
                        "Ends before it starts — will save as " +
                            Fmt.duration(d.start, minsToHm(fixedEnd))
                    },
                    fontSize = 11.sp,
                    color = c.dng,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                )
            }
            Column(Modifier.padding(top = 15.dp)) {
                FieldLabel("Duration")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    DurationPresets.forEach { (label, mins) ->
                        DurationPill(
                            label = label,
                            active = durMins > 0 && durMins == mins,
                            // A preset that would push the end past 23:59 cannot
                            // be honored — disable it instead of silently capping.
                            enabled = d.start.toMins() + mins <= 1439,
                            onClick = {
                                vm.updateEventDraft {
                                    it.copy(end = minsToHm((it.start.toMins() + mins).coerceAtMost(1439)))
                                }
                            },
                        )
                    }
                }
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
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
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

/** Duration preset pill — same visual language as the category pills. */
@Composable
private fun DurationPill(
    label: String,
    active: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalBento.current
    Box(
        Modifier
            .alpha(if (enabled) 1f else 0.4f)
            .pressable(enabled = enabled, onClick = onClick)
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
