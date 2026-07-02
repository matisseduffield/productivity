package com.bento.calendar.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.NoteItem
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.components.EmptyText
import com.bento.calendar.ui.components.GBtn
import com.bento.calendar.ui.components.SectionLabel
import com.bento.calendar.ui.components.hairlineBottom
import com.bento.calendar.ui.components.tap
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import java.time.LocalDateTime

/**
 * Notes tab: header, pinned 2-column grid, "All notes" rows, empty state.
 * Ported 1:1 from the prototype's notes screen (markup lines 202-209).
 */
@Composable
fun NotesScreen(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    val c = LocalBento.current
    val today = now.toLocalDate()

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
                Text("Notes", fontSize = 12.sp, fontWeight = FontWeight.W500, color = c.sub)
                Text(
                    if (data.notes.size == 1) "1 note" else "${data.notes.size} notes",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.01).em,
                    color = c.tx,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                GBtn(onClick = vm::openSearch) {
                    Icon(BentoIcons.Search, null, tint = c.sub, modifier = Modifier.size(18.dp))
                }
                GBtn(onClick = vm::newNote, primary = true) {
                    Icon(BentoIcons.Plus, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ---- Body (.abody) ----
        val byUpd = data.notes.sortedByDescending { it.updated }
        val pinned = byUpd.filter { it.pinned }
        val others = byUpd.filter { !it.pinned }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 2.dp, bottom = 14.dp),
        ) {
            if (pinned.isNotEmpty()) {
                SectionLabel("Pinned")
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pinned.chunked(2).forEach { rowNotes ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            rowNotes.forEach { n ->
                                PinnedCard(
                                    note = n,
                                    onTap = { vm.openNote(n.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (rowNotes.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            SectionLabel(
                "All notes",
                count = if (others.isEmpty()) "—" else others.size.toString(),
            )
            others.forEach { n ->
                NoteRow(
                    note = n,
                    stamp = Fmt.editStamp(n.updated, today, data.prefs.use24h),
                    onTap = { vm.openNote(n.id) },
                )
            }

            if (data.notes.isEmpty()) {
                EmptyText("No notes yet — tap + to write one.")
            }
        }
    }
}

/** Locked notes never leak content into previews. */
private fun preview(n: NoteItem): String {
    if (n.locked) return "•••• •••• ••••"
    val joined = n.body
        .split("\n")
        .filter { it.isNotBlank() }
        .joinToString(" · ")
        .take(90)
    return joined.ifEmpty { "No content" }
}

/** Pinned note card (.ncard). */
@Composable
private fun PinnedCard(note: NoteItem, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    Column(
        modifier
            .heightIn(min = 88.dp)
            .tap(onClick = onTap)
            .background(c.tile, RoundedCornerShape(16.dp))
            .border(1.dp, c.bd, RoundedCornerShape(16.dp))
            .padding(13.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (note.locked) {
                Icon(BentoIcons.Lock, null, tint = c.faint, modifier = Modifier.size(12.dp))
            }
            Text(
                note.title.ifEmpty { "Untitled" },
                fontSize = 13.sp,
                fontWeight = FontWeight.W600,
                color = c.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            preview(note),
            fontSize = 11.sp,
            color = c.sub,
            lineHeight = 1.5.em,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** "All notes" list row (.nrow). */
@Composable
private fun NoteRow(note: NoteItem, stamp: String, onTap: () -> Unit) {
    val c = LocalBento.current
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick = onTap)
            .hairlineBottom(c.line)
            .padding(horizontal = 2.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(37.dp)
                .background(c.tile, RoundedCornerShape(12.dp))
                .border(1.dp, c.bd, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (note.locked) BentoIcons.Lock else BentoIcons.Doc,
                null,
                tint = c.sub,
                modifier = Modifier.size(15.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                note.title.ifEmpty { "Untitled" },
                fontSize = 13.5.sp,
                fontWeight = FontWeight.W600,
                color = c.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                preview(note),
                fontSize = 11.sp,
                color = c.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(
            stamp,
            fontSize = 10.sp,
            color = c.faint,
            style = LocalTextStyle.current.copy(fontFeatureSettings = "tnum"),
        )
    }
}
