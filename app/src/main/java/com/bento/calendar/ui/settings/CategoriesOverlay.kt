package com.bento.calendar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.Category
import com.bento.calendar.data.Cats
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.components.BentoSheet
import com.bento.calendar.ui.components.BentoTextField
import com.bento.calendar.ui.components.DangerTextButton
import com.bento.calendar.ui.components.Dot
import com.bento.calendar.ui.components.FieldLabel
import com.bento.calendar.ui.components.FullOverlay
import com.bento.calendar.ui.components.PrimaryButton
import com.bento.calendar.ui.components.TextLink
import com.bento.calendar.ui.components.hairlineBottom
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.theme.color
import com.bento.calendar.ui.theme.hexColor
import kotlinx.coroutines.delay

/**
 * Full-page category manager shown from Settings → Calendar → "Categories".
 * List card in the settings language; create/edit share a bottom-sheet editor.
 */
@Composable
fun CategoriesOverlay(vm: AppViewModel, data: AppData) {
    val c = LocalBento.current
    // Editor sheet state: closed until a row's Edit or "+ New category" opens
    // it; [editing] is the category being edited, or null for a new one.
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Category?>(null) }
    FullOverlay {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(36.dp)
                        .pressable { vm.closeCategories() }
                        .background(c.tile, CircleShape)
                        .border(1.dp, c.bd, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BentoIcons.ChevronLeft, null, tint = c.sub, modifier = Modifier.size(17.dp))
                }
                Text(
                    "Categories",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.01).em,
                    color = c.tx,
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
            ) {
                // List card (.stcard language).
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(c.tile, RoundedCornerShape(18.dp))
                        .border(1.dp, c.bd, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 2.dp),
                ) {
                    data.categories.forEachIndexed { i, cat ->
                        val last = i == data.categories.lastIndex
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .then(if (last) Modifier else Modifier.hairlineBottom(c.line))
                                .padding(vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Dot(cat.color, size = 14.dp)
                            Text(
                                cat.label,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.W600,
                                color = c.tx,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            TextLink("Edit", onClick = {
                                editing = cat
                                sheetOpen = true
                            })
                        }
                    }
                }
                // Full-width create row below the card.
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .pressable {
                            editing = null
                            sheetOpen = true
                        }
                        .background(c.tile, RoundedCornerShape(14.dp))
                        .border(1.dp, c.bd, RoundedCornerShape(14.dp))
                        .padding(vertical = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+ New category",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.W700,
                        color = c.acc,
                    )
                }
                Text(
                    "Deleting a category moves its events to the first remaining one.",
                    fontSize = 11.sp,
                    color = c.faint,
                    lineHeight = 1.5.em,
                    modifier = Modifier.padding(start = 2.dp, end = 2.dp, top = 10.dp),
                )
            }
        }
    }
    if (sheetOpen) {
        CategoryEditorSheet(
            existing = editing,
            // The VM refuses to delete the last category; hide the button too.
            showDelete = editing != null && data.categories.size > 1,
            onSave = { label, hex ->
                val e = editing
                if (e == null) vm.addCategory(label, hex) else vm.updateCategory(e.id, label, hex)
                sheetOpen = false
            },
            onDelete = {
                editing?.let { vm.deleteCategory(it.id) }
                sheetOpen = false
            },
            onDismiss = { sheetOpen = false },
        )
    }
}

/** Shared create/edit sheet: name field, palette swatches, Save, two-tap delete. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorSheet(
    existing: Category?,
    showDelete: Boolean,
    onSave: (String, String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = LocalBento.current
    var name by remember(existing) { mutableStateOf(existing?.label ?: "") }
    var colorHex by remember(existing) { mutableStateOf(existing?.colorHex ?: Cats.PALETTE[0]) }
    // Local two-tap confirm, matching the VM's twoTap feel (2500ms disarm).
    var deleteArmed by remember(existing) { mutableStateOf(false) }
    LaunchedEffect(deleteArmed) {
        if (deleteArmed) {
            delay(2500)
            deleteArmed = false
        }
    }
    val nameFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) {
        if (existing == null) nameFocus.requestFocus()
    }
    BentoSheet(onDismiss = onDismiss) {
        Text(
            if (existing != null) "Edit category" else "New category",
            fontSize = 16.sp,
            fontWeight = FontWeight.W700,
            color = c.tx,
        )
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Name")
            BentoTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.focusRequester(nameFocus),
                placeholder = "Category name",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            )
        }
        Column(Modifier.padding(top = 15.dp)) {
            FieldLabel("Colour")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Cats.PALETTE.forEach { hex ->
                    PaletteSwatch(hex = hex, selected = colorHex == hex) { colorHex = hex }
                }
            }
        }
        PrimaryButton("Save", onClick = { onSave(name, colorHex) })
        if (showDelete) {
            DangerTextButton(
                if (deleteArmed) "Tap again to delete" else "Delete category",
                onClick = {
                    if (deleteArmed) {
                        deleteArmed = false
                        onDelete()
                    } else {
                        deleteArmed = true
                    }
                },
            )
        }
    }
}

/**
 * 24dp palette swatch; selected draws the AccentSwatch-style double ring
 * outside its bounds (sheet-bg gap ring, then text-colour ring) so layout
 * never shifts.
 */
@Composable
private fun PaletteSwatch(hex: String, selected: Boolean, onClick: () -> Unit) {
    val c = LocalBento.current
    Box(
        Modifier
            .size(24.dp)
            .pressable(onClick = onClick)
            .drawBehind {
                if (selected) {
                    val r = size.minDimension / 2f
                    drawCircle(c.bg, radius = r + 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                    drawCircle(c.tx, radius = r + 3.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
            }
            .background(hexColor(hex), CircleShape),
    )
}
