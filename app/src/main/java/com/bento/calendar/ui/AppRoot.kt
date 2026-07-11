package com.bento.calendar.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.bento.calendar.data.AppData
import com.bento.calendar.ui.calendar.CalendarScreen
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.components.tap
import com.bento.calendar.ui.notes.NoteEditorOverlay
import com.bento.calendar.ui.notes.NotesScreen
import com.bento.calendar.ui.notes.PinSheet
import com.bento.calendar.ui.search.SearchOverlay
import com.bento.calendar.ui.settings.SettingsOverlay
import com.bento.calendar.ui.sheets.CreateSheet
import com.bento.calendar.ui.sheets.EventEditorSheet
import com.bento.calendar.ui.sheets.TaskEditorSheet
import com.bento.calendar.ui.tasks.TasksScreen
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.BentoTheme
import com.bento.calendar.ui.theme.DarkColors
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.today.TodayScreen

@Composable
fun AppRoot(
    vm: AppViewModel,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
    onCalendarExport: () -> Unit = {},
    onCalendarImport: () -> Unit = {},
) {
    val data by vm.data.collectAsState()
    val now by vm.now.collectAsState()
    val d = data
    if (d == null) {
        Box(Modifier.fillMaxSize().background(DarkColors.bg))
        return
    }
    BentoTheme(d.prefs) {
        val c = LocalBento.current
        val view = LocalView.current
        LaunchedEffect(c.isLight) {
            val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = c.isLight
            controller.isAppearanceLightNavigationBars = c.isLight
        }
        // While locked, ONLY the lock screen composes. Hiding (rather than
        // covering) the app disposes every same-window sheet AND the
        // separate-window pieces a cover can't reach — time-picker Dialogs,
        // DropdownMenu popups, and the IME all float above overlays, so a
        // covered editor could still leak input. Drafts, the open tab, and
        // sheet state all live in the VM and restore untouched on unlock.
        if (vm.appLocked) {
            AppLockScreen(vm)
            return@BentoTheme
        }
        Box(Modifier.fillMaxSize().background(c.bg)) {
            Column(
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
            ) {
                Box(Modifier.weight(1f)) {
                    Crossfade(vm.tab, animationSpec = tween(220), label = "tab") { t ->
                        when (t) {
                            Tab.Today -> TodayScreen(vm, d, now)
                            Tab.Calendar -> CalendarScreen(vm, d, now)
                            Tab.Notes -> NotesScreen(vm, d, now)
                            Tab.Tasks -> TasksScreen(vm, d, now)
                        }
                    }
                }
                TabBar(vm)
            }
            // Undo pill for swipe actions, floating just above the tab bar.
            // key(undo) replays the entrance when the batch label changes so
            // each additional swipe is visibly acknowledged.
            vm.undoState?.let { undo ->
                androidx.compose.runtime.key(undo) {
                    com.bento.calendar.ui.components.UndoBanner(
                        label = undo.label,
                        onUndo = { vm.performUndo() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 88.dp),
                    )
                }
            }
            // Overlay stack, prototype z-order: full-page overlays, then search,
            // then bottom sheets on top.
            if (vm.openNoteId != null) NoteEditorOverlay(vm, d, now)
            if (vm.settingsOpen) SettingsOverlay(
                vm = vm,
                data = d,
                now = now,
                onExport = onExport,
                onImport = onImport,
                onCalendarExport = onCalendarExport,
                onCalendarImport = onCalendarImport,
            )
            if (vm.changelogOpen) com.bento.calendar.ui.settings.ChangelogOverlay(vm)
            if (vm.categoriesOpen) com.bento.calendar.ui.settings.CategoriesOverlay(vm, d)
            if (vm.trashOpen) com.bento.calendar.ui.settings.TrashOverlay(vm, d)
            if (vm.searchOpen) SearchOverlay(vm, d, now)
            if (vm.fabOpen) CreateSheet(vm)
            if (vm.evDraft != null) EventEditorSheet(vm, d, now)
            if (vm.tkDraft != null) TaskEditorSheet(vm, d, now)
            if (vm.pinCtx != null) PinSheet(vm)
            BackHandler(enabled = vm.hasOverlay()) { vm.backPress() }
        }
    }
}

/**
 * Full-screen gate while the app lock is armed: opaque background (nothing
 * behind may leak), app name, and an Unlock button that re-triggers the
 * system sheet (the automatic prompt can be dismissed).
 */
@Composable
private fun AppLockScreen(vm: AppViewModel) {
    val c = LocalBento.current
    Column(
        Modifier
            .fillMaxSize()
            .background(c.bg)
            // tap, not pressable: swallow input without the press-scale
            // animation shrinking the whole lock screen.
            .tap(onClick = {}),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(BentoIcons.Lock, null, tint = c.faint, modifier = Modifier.size(34.dp))
        Text(
            "Bento is locked",
            fontSize = 17.sp,
            fontWeight = FontWeight.W700,
            color = c.tx,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            "Unlock with fingerprint, face or your screen lock",
            fontSize = 12.sp,
            color = c.sub,
            modifier = Modifier.padding(top = 5.dp),
        )
        Box(
            Modifier
                .padding(top = 22.dp)
                .pressable(onClick = { vm.requestAppUnlock() })
                .background(c.acc, RoundedCornerShape(14.dp))
                .padding(horizontal = 26.dp, vertical = 12.dp),
        ) {
            Text("Unlock", fontSize = 13.5.sp, fontWeight = FontWeight.W700, color = Color.White)
        }
    }
}

@Composable
private fun TabBar(vm: AppViewModel) {
    val c = LocalBento.current
    Row(
        Modifier
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)
            .fillMaxWidth()
            .background(c.tile, RoundedCornerShape(22.dp))
            .border(1.dp, c.bd, RoundedCornerShape(22.dp))
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        TabItem(vm, Tab.Today, "Today", BentoIcons.TabToday, Modifier.weight(1f))
        TabItem(vm, Tab.Calendar, "Calendar", BentoIcons.TabCalendar, Modifier.weight(1f))
        TabItem(vm, Tab.Notes, "Notes", BentoIcons.TabNotes, Modifier.weight(1f))
        TabItem(vm, Tab.Tasks, "Tasks", BentoIcons.TabTasks, Modifier.weight(1f))
    }
}

@Composable
private fun TabItem(vm: AppViewModel, tab: Tab, label: String, icon: ImageVector, modifier: Modifier) {
    val c = LocalBento.current
    val active = vm.tab == tab
    Row(
        modifier
            .pressable { vm.setTab(tab) }
            .background(if (active) c.accTint(0.15f) else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(17.dp))
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = if (active) c.acc else c.sub, modifier = Modifier.size(19.dp))
        if (active) {
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.W600, color = c.acc)
        }
    }
}
