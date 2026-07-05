package com.bento.calendar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.Trash
import com.bento.calendar.data.TrashEntry
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Arm
import com.bento.calendar.ui.components.FullOverlay
import com.bento.calendar.ui.components.TextLink
import com.bento.calendar.ui.components.hairlineBottom
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento

/**
 * Full-page trash shown from Settings → Data → "Trash". Soft-deleted events,
 * tasks and notes wait here for [Trash.RETENTION_DAYS] days; each row offers
 * Restore, and a two-tap "Empty trash" purges everything at once.
 */
@Composable
fun TrashOverlay(vm: AppViewModel, data: AppData) {
    val c = LocalBento.current
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
                        .pressable { vm.closeTrash() }
                        .background(c.tile, CircleShape)
                        .border(1.dp, c.bd, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BentoIcons.ChevronLeft, null, tint = c.sub, modifier = Modifier.size(17.dp))
                }
                Column {
                    Text(
                        "Trash",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.W700,
                        letterSpacing = (-0.01).em,
                        color = c.tx,
                    )
                    Text(
                        "Items stay for ${Trash.RETENTION_DAYS} days, then delete forever",
                        fontSize = 11.sp,
                        color = c.faint,
                    )
                }
            }
            if (data.trash.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing in the trash",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.W600,
                        color = c.faint,
                    )
                }
            } else {
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
                ) {
                    // List card (.stcard language), newest first.
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .background(c.tile, RoundedCornerShape(18.dp))
                            .border(1.dp, c.bd, RoundedCornerShape(18.dp))
                            .padding(horizontal = 14.dp, vertical = 2.dp),
                    ) {
                        data.trash.forEachIndexed { i, entry ->
                            TrashRow(vm, entry, last = i == data.trash.lastIndex)
                        }
                    }
                    // Two-tap purge, mirroring the Settings danger links.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TextLink(
                            if (vm.isArmed(Arm.TRASH)) "Tap again to confirm" else "Empty trash",
                            onClick = { vm.emptyTrash() },
                            color = c.dng,
                        )
                    }
                }
            }
        }
    }
}

/** One trashed item: title over "kind · deleted-when", with a Restore link. */
@Composable
private fun TrashRow(vm: AppViewModel, entry: TrashEntry, last: Boolean) {
    val c = LocalBento.current
    val kind = when {
        entry.event != null -> "Event"
        entry.task != null -> "Task"
        else -> "Note"
    }
    // Locked notes keep their titles private even in the trash.
    val title = if (entry.note?.locked == true) "Locked note" else entry.title
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (last) Modifier else Modifier.hairlineBottom(c.line))
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.W600,
                color = c.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "$kind · ${deletedWhen(entry.deletedAt)}",
                fontSize = 11.sp,
                color = c.faint,
            )
        }
        TextLink("Restore", onClick = { vm.restoreTrash(entry) })
    }
}

/** Relative deleted-when in whole days: "Today", "Yesterday", "5 days ago". */
private fun deletedWhen(deletedAt: Long): String {
    val days = ((System.currentTimeMillis() - deletedAt) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "Today"
        days == 1 -> "Yesterday"
        else -> "$days days ago"
    }
}
