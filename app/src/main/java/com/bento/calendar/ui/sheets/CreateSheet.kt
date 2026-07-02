package com.bento.calendar.ui.sheets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.components.BentoSheet
import com.bento.calendar.ui.components.tap
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento

/**
 * Create menu bottom sheet (prototype `fabOpen` sheet, markup lines 252-256):
 * "Create" title + three tappable rows — New event / New task / New note.
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
        CreateRow(BentoIcons.TabCalendar, "New event") { vm.newEvent() }
        CreateRow(BentoIcons.TabTasks, "New task") { vm.newTask() }
        CreateRow(BentoIcons.Doc, "New note") { vm.newNote() }
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
