package com.bento.calendar.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.BuildConfig
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.components.FullOverlay
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento

/**
 * In-app changelog shown from Settings → "What's new".
 * Newest first. ADD AN ENTRY HERE WITH EVERY RELEASE (see README release steps).
 */
data class ChangelogEntry(val version: String, val date: String, val changes: List<String>)

val CHANGELOG = listOf(
    ChangelogEntry(
        "2.7.0", "July 2026",
        listOf(
            "New Agenda view: see the next 30 days of Bento events, device calendars and due tasks in one list",
            "Filter Agenda to everything, events only or tasks only — counts update instantly",
            "Swipe or use the arrows to move through 30-day windows; tap any date to open its Day view",
            "Calendar now remembers whether you last used Month, Week, Day or Agenda",
        ),
    ),
    ChangelogEntry(
        "2.6.0", "July 2026",
        listOf(
            "Quick Add understands ISO dates and offsets like “2026-07-15” and “in 2 days”",
            "Create repeating events or tasks in one line with “every day”, “every week” or “every month”",
            "Add categories while typing — #work, #fitness or your own category name",
            "Imported Google/Outlook series now handle moved and cancelled occurrences without duplicates",
        ),
    ),
    ChangelogEntry(
        "2.5.0", "July 2026",
        listOf(
            "Calendar files: export your Bento events as .ics for Google Calendar, Outlook and more",
            "Import .ics calendars without replacing anything — repeat imports automatically skip duplicates",
            "Recurrence exceptions, all-day and multi-day events, locations, categories and reminders survive a Bento calendar round-trip",
        ),
    ),
    ChangelogEntry(
        "2.4.0", "July 2026",
        listOf(
            "Trash: deleted events, tasks and notes stay restorable for 30 days (Settings → Data)",
            "Note colors — tint your notes from the palette in the note editor",
            "Tasks with due dates now show in the Month and Day views, with a checkbox right there",
        ),
    ),
    ChangelogEntry(
        "2.3.1", "July 2026",
        listOf(
            "Changing or removing your notes PIN now asks for the current PIN (or your fingerprint) first",
        ),
    ),
    ChangelogEntry(
        "2.3.0", "July 2026",
        listOf(
            "Unlock private notes with your fingerprint or face — the PIN stays as backup",
            "Optional app lock: require fingerprint, face or your screen lock when Bento opens",
            "Both live in Settings → Notes & privacy",
        ),
    ),
    ChangelogEntry(
        "2.2.0", "July 2026",
        listOf(
            "Multi-day events — give an event an end date and it spans the days between",
            "Task reminders: get notified at a time you pick on the due date, with a Done button",
            "Quick add understands time ranges (\"standup 9-9:30am\") and priorities (\"pay rent !high fri\")",
            "Your device calendars now show on the Today and Up-next widgets too",
            "Removed device calendars no longer linger in Settings",
        ),
    ),
    ChangelogEntry(
        "2.1.0", "July 2026",
        listOf(
            "Quick add: type \"Dentist tue 3pm\" and it becomes an event — plain text becomes a task",
            "Checklists inside tasks, with progress shown in the list",
            "Task priorities: flag tasks low, medium or high and they sort first",
            "Match wallpaper colours — the accent can follow your system palette (Android 12+)",
            "Themed app icon for Material You home screens",
        ),
    ),
    ChangelogEntry(
        "2.0.0", "July 2026",
        listOf(
            "Custom categories — create, rename and recolor your own",
            "Recurring events: edit or delete a single occurrence without touching the series",
            "See your Google/device calendars alongside Bento events (read-only, off by default)",
            "Repeating tasks — complete them and they come back on schedule",
            "Widgets: real previews in the picker and friendlier empty states",
            "Play Store-ready builds",
        ),
    ),
    ChangelogEntry(
        "1.7.0", "July 2026",
        listOf(
            "Five new home-screen widgets: Up next, Tasks, Month, Quick add, and Pinned note",
            "Complete tasks straight from the Tasks widget",
            "Tap a day on the Month widget to open it in the app",
            "All widgets follow your theme and accent, and come in multiple sizes",
        ),
    ),
    ChangelogEntry(
        "1.6.0", "July 2026",
        listOf(
            "Home-screen widget: today's events at a glance with quick add",
            "All-day events — toggle in the event editor, shown as chips above the timeline",
            "Search: matching text is highlighted and your recent searches appear",
            "This changelog",
        ),
    ),
    ChangelogEntry(
        "1.5.0", "July 2026",
        listOf(
            "Long-press and drag events in Day view to reschedule them",
            "Quick due-date chips in the task editor: Today, Tomorrow, Next week",
            "Snooze reminders for 10 minutes right from the notification",
            "Long-press the app icon for New event / task / note shortcuts",
            "Back up everything to a file and restore it in Settings",
        ),
    ),
    ChangelogEntry(
        "1.4.0", "July 2026",
        listOf(
            "Events at the same time now sit side by side instead of overlapping",
            "Event editor: duration chips, live validation, smarter time handling",
            "Add a task straight from the keyboard with Done",
        ),
    ),
    ChangelogEntry(
        "1.3.0", "July 2026",
        listOf(
            "Swipe tasks right to complete, left to delete — with Undo",
            "Swipe notes to pin or delete",
            "Calendar slides between months, weeks and days",
            "Week and Day views open at the current time",
            "Long-press a day in Month view to create an event",
            "Haptic feedback throughout",
        ),
    ),
    ChangelogEntry(
        "1.2.0", "July 2026",
        listOf(
            "Swipe left and right to move between calendar periods",
            "Tap the calendar title to jump to any date",
            "Drag bottom sheets down to close them",
            "Tap the date on Today to open it in the calendar",
        ),
    ),
    ChangelogEntry(
        "1.1.0", "July 2026",
        listOf(
            "The app now updates itself — new versions appear as a banner",
            "Fresh installs start empty",
            "\"Start fresh\" keeps your theme and PIN",
        ),
    ),
    ChangelogEntry(
        "1.0.0", "July 2026",
        listOf(
            "First release: calendar, notes and tasks in one bento-style app",
            "Month, week and day views with recurring events and reminders",
            "PIN-locked private notes",
            "Dark and light themes with four accent colours",
        ),
    ),
)

/** Full-page "What's new" overlay: version cards in the settings language. */
@Composable
fun ChangelogOverlay(vm: AppViewModel) {
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
                        .pressable { vm.closeChangelog() }
                        .background(c.tile, CircleShape)
                        .border(1.dp, c.bd, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BentoIcons.ChevronLeft, null, tint = c.sub, modifier = Modifier.size(17.dp))
                }
                Text(
                    "What's new",
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
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CHANGELOG.forEach { entry ->
                    VersionCard(entry, current = entry.version == BuildConfig.VERSION_NAME)
                }
            }
        }
    }
}

@Composable
private fun VersionCard(entry: ChangelogEntry, current: Boolean) {
    val c = LocalBento.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(c.tile, RoundedCornerShape(18.dp))
            .border(1.dp, if (current) c.accTint(0.4f) else c.bd, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Version ${entry.version}",
                fontSize = 13.5.sp,
                fontWeight = FontWeight.W700,
                color = if (current) c.acc else c.tx,
            )
            if (current) {
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .background(c.accTint(0.13f), CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text("Current", fontSize = 9.5.sp, fontWeight = FontWeight.W700, color = c.acc)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(entry.date, fontSize = 11.sp, fontWeight = FontWeight.W500, color = c.faint)
        }
        Column(
            Modifier.padding(top = 9.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            entry.changes.forEach { change ->
                Row {
                    Text(
                        "•",
                        fontSize = 12.5.sp,
                        color = c.acc,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(
                        change,
                        fontSize = 12.5.sp,
                        color = c.sub,
                        lineHeight = 1.5.em,
                    )
                }
            }
        }
    }
}
