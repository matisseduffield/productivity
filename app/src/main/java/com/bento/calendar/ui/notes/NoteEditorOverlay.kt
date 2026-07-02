package com.bento.calendar.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Arm
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.components.FullOverlay
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.theme.Sora
import java.time.LocalDateTime

/**
 * Full-page note editor (.ovl / .ne-*): back button, pin/lock/delete actions,
 * title input, meta line, flexible body. Autosaves through the view model on
 * every change (prototype markup line 225, logic 646-653).
 */
@Composable
fun NoteEditorOverlay(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    val note = data.notes.firstOrNull { it.id == vm.openNoteId } ?: return
    val c = LocalBento.current

    // Local buffers are the source of truth while typing: the store is written
    // through on every change (autosave, README line 92) but never fed back
    // into the fields, so a slow DataStore round-trip can't drop keystrokes or
    // reset the IME composing region. remember(note.id) reseeds on note switch.
    var title by remember(note.id) { mutableStateOf(note.title) }
    var body by remember(note.id) { mutableStateOf(note.body) }

    FullOverlay {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // ---- Top bar (.ne-hd) ----
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NabButton(active = false, onClick = vm::closeNote) {
                    Icon(BentoIcons.ChevronLeft, null, tint = c.sub, modifier = Modifier.size(17.dp))
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    NabButton(active = note.pinned, onClick = vm::toggleNotePin) {
                        Icon(
                            BentoIcons.PinTack,
                            null,
                            tint = if (note.pinned) c.acc else c.sub,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    NabButton(active = note.locked, onClick = vm::toggleNoteLock) {
                        Icon(
                            BentoIcons.LockLight,
                            null,
                            tint = if (note.locked) c.acc else c.sub,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    NabButton(active = false, onClick = vm::deleteNote) {
                        if (vm.isArmed(Arm.NOTE)) {
                            Text("Sure?", fontSize = 9.sp, fontWeight = FontWeight.W700, color = c.dng)
                        } else {
                            Icon(BentoIcons.Trash, null, tint = c.dng, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // ---- Title input (.ne-title) ----
            val titleStyle = TextStyle(
                fontFamily = Sora,
                fontSize = 20.sp,
                fontWeight = FontWeight.W700,
                letterSpacing = (-0.01).em,
                color = c.tx,
            )
            BasicTextField(
                value = title,
                onValueChange = { v ->
                    title = v
                    vm.setNoteTitle(v)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 22.dp, top = 8.dp),
                textStyle = titleStyle,
                singleLine = true,
                cursorBrush = SolidColor(c.acc),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) {
                            Text("Title", style = titleStyle.copy(color = c.faint))
                        }
                        inner()
                    }
                },
            )

            // ---- Meta line (.ne-meta) ----
            val meta = buildString {
                append(Fmt.relEdit(note.updated, now, data.prefs.use24h))
                if (note.locked) append(" · Locked")
                if (note.pinned) append(" · Pinned")
            }
            Text(
                meta,
                fontSize = 10.5.sp,
                color = c.faint,
                style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 4.dp),
            )

            // ---- Body (.ne-body) ----
            val bodyStyle = TextStyle(
                fontFamily = Sora,
                fontSize = 13.5.sp,
                lineHeight = 1.7.em,
                color = c.tx,
            )
            BasicTextField(
                value = body,
                onValueChange = { v ->
                    body = v
                    vm.setNoteBody(v)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 24.dp),
                textStyle = bodyStyle,
                cursorBrush = SolidColor(c.acc),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize()) {
                        if (body.isEmpty()) {
                            Text("Start writing…", style = bodyStyle.copy(color = c.faint))
                        }
                        inner()
                    }
                },
            )
        }
    }
}

/**
 * 36dp circular editor action button (.nab / .naon). Active = accent icon on
 * accent-tinted tile with an accent-mixed border.
 */
@Composable
private fun NabButton(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalBento.current
    val bg = if (active) c.accTint(0.12f).compositeOver(c.tile) else c.tile
    val border = if (active) lerp(c.bd, c.acc, 0.4f) else c.bd
    Box(
        Modifier
            .size(36.dp)
            .pressable(onClick = onClick)
            .background(bg, CircleShape)
            .border(1.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
