package com.bento.calendar.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.Accents
import com.bento.calendar.BuildConfig
import com.bento.calendar.data.AppData
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Arm
import com.bento.calendar.ui.components.BentoSelectField
import com.bento.calendar.ui.components.BentoSwitch
import com.bento.calendar.ui.components.FullOverlay
import com.bento.calendar.ui.components.SectionLabel
import com.bento.calendar.ui.components.TextLink
import com.bento.calendar.ui.components.hairlineBottom
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.theme.hexColor
import kotlinx.coroutines.delay
import java.time.LocalDateTime

/** Full-page Settings overlay (prototype markup 226-251, logic 654-673). */
@Composable
fun SettingsOverlay(
    vm: AppViewModel,
    data: AppData,
    now: LocalDateTime,
    onExport: () -> Unit = {},
    onImport: () -> Unit = {},
) {
    val c = LocalBento.current
    val prefs = data.prefs
    FullOverlay {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top bar (.ne-hd override: flex-start, gap 12, padding 12px 16px 6px)
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
                        .pressable { vm.closeSheets() }
                        .background(c.tile, CircleShape)
                        .border(1.dp, c.bd, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BentoIcons.ChevronLeft, null, tint = c.sub, modifier = Modifier.size(17.dp))
                }
                Text(
                    "Settings",
                    fontSize = 21.sp,
                    fontWeight = FontWeight.W700,
                    letterSpacing = (-0.01).em,
                    color = c.tx,
                )
            }

            // Scrollable body (.abody override: padding 6px 16px 20px)
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 20.dp),
            ) {
                // ---- Appearance ----
                FirstSectionLabel("Appearance")
                SettingsCard {
                    SettingsRow(
                        icon = BentoIcons.Moon,
                        title = "Theme",
                        sub = if (prefs.theme == "dark") "Easy on the eyes" else "Bright and clean",
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            ThemePill("Dark", active = prefs.theme == "dark") { vm.setTheme("dark") }
                            ThemePill("Light", active = prefs.theme != "dark") { vm.setTheme("light") }
                        }
                    }
                    // Material You needs the Android 12 dynamic palette APIs.
                    val dynamicAvailable = Build.VERSION.SDK_INT >= 31
                    val dynamicOn = dynamicAvailable && prefs.dynamicColor
                    if (dynamicAvailable) {
                        SettingsRow(
                            icon = BentoIcons.Droplet,
                            title = "Match wallpaper colours",
                            sub = "Material You — uses your system palette",
                        ) {
                            BentoSwitch(
                                on = prefs.dynamicColor,
                                onToggle = { vm.toggleDynamicColor() },
                            )
                        }
                    }
                    SettingsRow(
                        icon = BentoIcons.Droplet,
                        title = "Accent colour",
                        sub = if (dynamicOn) "Following your wallpaper · widgets keep this" else Accents.nameOf(prefs.accent),
                        last = true,
                    ) {
                        // Manual swatches step back while dynamic colour drives
                        // the accent: dimmed and inert, same as other disabled
                        // controls, so the choice is preserved for when it's
                        // switched off.
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.alpha(if (dynamicOn) 0.4f else 1f),
                        ) {
                            Accents.ALL.forEach { option ->
                                AccentSwatch(
                                    hex = option.hex,
                                    selected = prefs.accent == option.hex,
                                    enabled = !dynamicOn,
                                ) { vm.setAccent(option.hex) }
                            }
                        }
                    }
                }

                // ---- Calendar ----
                SectionLabel("Calendar")
                SettingsCard {
                    SettingsRow(
                        icon = BentoIcons.Clock,
                        title = "24-hour time",
                        sub = "14:00 instead of 2:00 pm",
                    ) {
                        BentoSwitch(on = prefs.use24h, onToggle = { vm.toggle24h() })
                    }
                    SettingsRow(
                        icon = BentoIcons.SettingsCalendar,
                        title = "Week starts Monday",
                        sub = "All calendar views",
                    ) {
                        BentoSwitch(on = prefs.monday, onToggle = { vm.toggleMonday() })
                    }
                    SettingsRow(
                        icon = BentoIcons.Bell,
                        title = "Default reminder",
                        sub = "For new events",
                    ) {
                        BentoSelectField(
                            value = prefs.remindDef,
                            options = listOf<Pair<String, Int?>>(
                                "None" to null,
                                "At start" to 0,
                                "10 min" to 10,
                                "30 min" to 30,
                                "1 hour" to 60,
                            ),
                            onSelect = { vm.setRemindDef(it) },
                            compact = true,
                        )
                    }
                    SettingsRow(
                        icon = BentoIcons.Timer,
                        title = "Event length",
                        sub = "For new events",
                    ) {
                        BentoSelectField(
                            value = prefs.durDef,
                            options = listOf(
                                "30 min" to 30,
                                "45 min" to 45,
                                "1 hour" to 60,
                                "1.5 hours" to 90,
                                "2 hours" to 120,
                            ),
                            onSelect = { vm.setDurDef(it) },
                            compact = true,
                        )
                    }
                    SettingsRow(
                        icon = BentoIcons.Droplet,
                        title = "Categories",
                        sub = "Add, rename, recolor",
                        last = true,
                    ) {
                        TextLink("Edit", onClick = { vm.openCategories() })
                    }
                }

                // ---- Device calendars ----
                SectionLabel("Device calendars")
                SettingsCard {
                    val devEnabled = prefs.deviceCalsEnabled
                    SettingsRow(
                        icon = BentoIcons.SettingsCalendar,
                        title = "Show device calendars",
                        sub = "Read-only overlay from your Google/Samsung calendars",
                        last = !devEnabled,
                    ) {
                        BentoSwitch(on = devEnabled, onToggle = { vm.setDeviceCalsEnabled(!devEnabled) })
                    }
                    if (devEnabled) {
                        val cals = vm.deviceCals
                        if (cals.isEmpty()) {
                            Text(
                                "No calendars found",
                                fontSize = 11.sp,
                                color = c.faint,
                                modifier = Modifier.padding(vertical = 14.dp),
                            )
                        } else {
                            cals.forEachIndexed { i, cal ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .then(
                                            if (i == cals.lastIndex) Modifier
                                            else Modifier.hairlineBottom(c.line),
                                        )
                                        .padding(vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Box(
                                        Modifier
                                            .size(10.dp)
                                            .background(hexColor(cal.colorHex), CircleShape),
                                    )
                                    Text(
                                        cal.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.W600,
                                        color = c.tx,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    val checked =
                                        prefs.deviceCalIds.isEmpty() || cal.id in prefs.deviceCalIds
                                    BentoSwitch(on = checked, onToggle = { vm.toggleDeviceCal(cal.id) })
                                }
                            }
                        }
                    }
                }

                // ---- Tasks ----
                val doneCount = data.tasks.count { it.done }
                SectionLabel("Tasks")
                SettingsCard {
                    SettingsRow(
                        icon = BentoIcons.SettingsChecklist,
                        title = "Clear completed",
                        sub = if (doneCount > 0) {
                            "$doneCount completed task" + (if (doneCount > 1) "s" else "")
                        } else {
                            "Nothing completed yet"
                        },
                        last = true,
                    ) {
                        TextLink(
                            if (vm.isArmed(Arm.CLEAR)) "Sure?" else "Clear",
                            onClick = { vm.clearCompleted() },
                        )
                    }
                }

                // ---- Notes & privacy ----
                val hasPin = data.pin != null
                SectionLabel("Notes & privacy")
                SettingsCard {
                    SettingsRow(
                        icon = BentoIcons.LockLight,
                        title = "Notes PIN",
                        sub = if (hasPin) "PIN is set" else "No PIN yet",
                        last = !vm.bioAvailable && !vm.credentialAvailable,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TextLink(
                                if (hasPin) "Change" else "Set PIN",
                                onClick = { vm.startSetPin() },
                            )
                            if (hasPin) {
                                TextLink("Remove", onClick = { vm.removePin() }, color = c.dng)
                            }
                        }
                    }
                    // Biometric rows only make sense with something enrolled;
                    // availability is refreshed by the activity on every start.
                    if (vm.bioAvailable) {
                        SettingsRow(
                            icon = BentoIcons.LockLight,
                            title = "Biometric note unlock",
                            sub = "Fingerprint or face instead of the PIN",
                            last = !vm.credentialAvailable,
                        ) {
                            BentoSwitch(on = prefs.bioNotes, onToggle = { vm.toggleBioNotes() })
                        }
                    }
                    if (vm.credentialAvailable) {
                        SettingsRow(
                            icon = BentoIcons.LockLight,
                            title = "Lock the app",
                            sub = "Ask for fingerprint, face or screen lock on open",
                            last = true,
                        ) {
                            BentoSwitch(on = prefs.appLock, onToggle = { vm.toggleAppLock() })
                        }
                    }
                }

                // ---- Data ----
                SectionLabel("Data")
                SettingsCard {
                    SettingsRow(
                        icon = BentoIcons.Download,
                        title = "Export data",
                        sub = "Everything, incl. locked notes, as a file",
                    ) {
                        TextLink("Export", onClick = onExport)
                    }
                    // Import replaces the whole store, so it gets the app's
                    // two-tap confirm — kept local (remember + timeout) to
                    // match the VM's twoTap feel without touching Arm keys.
                    var importArmed by remember { mutableStateOf(false) }
                    LaunchedEffect(importArmed) {
                        if (importArmed) {
                            delay(2500)
                            importArmed = false
                        }
                    }
                    SettingsRow(
                        icon = BentoIcons.Doc,
                        title = "Import data",
                        sub = "Replace everything with a backup",
                    ) {
                        TextLink(
                            if (importArmed) "Sure?" else "Import",
                            onClick = {
                                if (importArmed) {
                                    importArmed = false
                                    onImport()
                                } else {
                                    importArmed = true
                                }
                            },
                        )
                    }
                    SettingsRow(
                        icon = BentoIcons.Trash,
                        title = "Start fresh",
                        sub = "Erase all events, tasks and notes",
                        last = true,
                        danger = true,
                    ) {
                        TextLink(
                            if (vm.isArmed(Arm.RESET)) "Sure?" else "Reset",
                            onClick = { vm.resetApp() },
                            color = c.dng,
                        )
                    }
                }

                // ---- App ----
                SectionLabel("App")
                SettingsCard {
                    // Sideloaded (GitHub) builds self-update in-app; Play
                    // builds update through the store, so the row disappears.
                    if (BuildConfig.SELF_UPDATER) {
                        val update = vm.updateInfo
                        val phase = vm.updatePhase
                        SettingsRow(
                            icon = BentoIcons.Download,
                            title = "App updates",
                            sub = when {
                                phase == AppViewModel.UpdatePhase.Downloading ->
                                    "Downloading… ${(vm.updateProgress * 100).toInt()}%"
                                phase == AppViewModel.UpdatePhase.AwaitingConfirm ->
                                    "Waiting for install confirmation…"
                                vm.updateError != null -> vm.updateError!!
                                update != null -> "Version ${update.versionName} available"
                                vm.updateChecking -> "Checking…"
                                vm.updateUpToDate -> "You're on the latest version"
                                else -> "Version ${BuildConfig.VERSION_NAME}"
                            },
                        ) {
                            if (update != null && phase == AppViewModel.UpdatePhase.Idle) {
                                TextLink("Update", onClick = { vm.downloadAndInstallUpdate() })
                            } else if (phase == AppViewModel.UpdatePhase.Idle) {
                                TextLink(
                                    if (vm.updateChecking) "…" else "Check",
                                    onClick = { vm.checkForUpdates(manual = true) },
                                )
                            }
                        }
                    }
                    SettingsRow(
                        icon = BentoIcons.Doc,
                        title = "What's new",
                        sub = "Version history",
                        last = true,
                    ) {
                        TextLink("View", onClick = { vm.openChangelog() })
                    }
                }
            }
        }
    }
}

/**
 * First section label: prototype markup line 227 overrides the default .slab
 * top margin with an inline `margin-top:10px`, so this mirrors the shared
 * SectionLabel styling (10.5sp/W700/0.12em/sub, 2dp sides, 8dp bottom) with a
 * 10dp top padding instead of the component's 18dp default.
 */
@Composable
private fun FirstSectionLabel(text: String) {
    val c = LocalBento.current
    Text(
        text.uppercase(),
        fontSize = 10.5.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.12.em,
        color = c.sub,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 10.dp, bottom = 8.dp),
    )
}

/** Grouped card (.stcard): tile bg, 1dp border, 18dp radius, 14dp/2dp padding. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    val c = LocalBento.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 2.dp)
            .background(c.tile, RoundedCornerShape(18.dp))
            .border(1.dp, c.bd, RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 2.dp),
        content = content,
    )
}

/**
 * Settings row (.strow): 32dp tinted icon square, title 13.5/600 + sub 11
 * (ellipsized), control at the right; hairline below unless [last].
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    sub: String,
    last: Boolean = false,
    danger: Boolean = false,
    control: @Composable () -> Unit,
) {
    val c = LocalBento.current
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (last) Modifier else Modifier.hairlineBottom(c.line))
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .background(
                    if (danger) c.dng.copy(alpha = 0.12f) else c.accTint(0.12f),
                    RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = if (danger) c.dng else c.acc, modifier = Modifier.size(15.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.W600,
                color = c.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sub,
                fontSize = 11.sp,
                color = c.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        control()
    }
}

/** Theme chip (.cpill / .pon): active = accent border + 12% accent tint. */
@Composable
private fun ThemePill(label: String, active: Boolean, onClick: () -> Unit) {
    val c = LocalBento.current
    Box(
        Modifier
            .pressable(onClick = onClick)
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

/**
 * 25dp accent swatch (.swt); selected (.swon) draws the double ring outside
 * its bounds — 2dp tile ring then 2dp text-color ring — like the prototype's
 * box-shadow, so layout never shifts.
 */
@Composable
private fun AccentSwatch(
    hex: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val c = LocalBento.current
    Box(
        Modifier
            .size(25.dp)
            .pressable(enabled = enabled, onClick = onClick)
            .drawBehind {
                if (selected) {
                    val r = size.minDimension / 2f
                    drawCircle(c.tile, radius = r + 1.dp.toPx(), style = Stroke(2.dp.toPx()))
                    drawCircle(c.tx, radius = r + 3.dp.toPx(), style = Stroke(2.dp.toPx()))
                }
            }
            .background(hexColor(hex), CircleShape),
    )
}
