package com.bento.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bento.calendar.MainActivity
import com.bento.calendar.data.AppData
import com.bento.calendar.data.DeviceCalendars
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.toIso
import com.bento.calendar.ui.theme.BentoColors
import com.bento.calendar.ui.theme.DarkColors
import com.bento.calendar.ui.theme.LightColors
import com.bento.calendar.ui.theme.hexColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Shared plumbing for the widget family: intent actions MainActivity handles,
 * theme resolution from the store, common visual atoms, and the base receiver
 * that keeps the midnight-rollover alarm armed while any widget is placed.
 */
object WidgetActions {
    const val NEW_EVENT = "com.bento.calendar.NEW_EVENT"
    const val NEW_TASK = "com.bento.calendar.NEW_TASK"
    const val NEW_NOTE = "com.bento.calendar.NEW_NOTE"
    const val OPEN_SEARCH = "com.bento.calendar.OPEN_SEARCH"
    const val OPEN_TASKS = "com.bento.calendar.OPEN_TASKS"
    const val OPEN_DAY = "com.bento.calendar.OPEN_DAY"
    const val OPEN_NOTE = "com.bento.calendar.OPEN_NOTE"
    const val EXTRA_DATE = "date"
    const val EXTRA_NOTE_ID = "noteId"
}

/** Every widget class in the family — WidgetSync and midnight updates hit all. */
internal fun familyWidgets(): List<GlanceAppWidget> = listOf(
    BentoWidget(),
    UpNextWidget(),
    TasksWidget(),
    MonthWidget(),
    QuickAddWidget(),
    NoteWidget(),
)

internal val familyReceivers = listOf(
    BentoWidgetReceiver::class.java,
    UpNextWidgetReceiver::class.java,
    TasksWidgetReceiver::class.java,
    MonthWidgetReceiver::class.java,
    QuickAddWidgetReceiver::class.java,
    NoteWidgetReceiver::class.java,
)

/**
 * Base receiver: arms the midnight alarm on placement/update (onUpdate also
 * covers reboot via the boot-time APPWIDGET_UPDATE) and cancels it only when
 * NO widget of ANY family type remains.
 */
abstract class BentoFamilyReceiver : GlanceAppWidgetReceiver() {
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightTickReceiver.arm(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        MidnightTickReceiver.arm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        val app = context.applicationContext
        // goAsync keeps the receiver alive until the check completes — without
        // it the process can die first and the midnight alarm stays armed
        // forever after the last widget is removed.
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                runCatching {
                    val awm = AppWidgetManager.getInstance(app)
                    val anyLeft = familyReceivers.any { receiver ->
                        awm.getAppWidgetIds(
                            android.content.ComponentName(app, receiver),
                        ).isNotEmpty()
                    }
                    if (!anyLeft) MidnightTickReceiver.cancel(app)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

internal fun paletteOf(data: AppData): BentoColors =
    if (data.prefs.theme == "light") LightColors else DarkColors

internal fun accentOf(data: AppData): Color = hexColor(data.prefs.accent)

internal fun catColor(data: AppData, cat: String): Color = hexColor(data.categoryOf(cat).colorHex)

/**
 * Today's device-calendar overlay as widget rows: (synthetic event, tint)
 * pairs ready to merge with the bento occurrences. Empty when the overlay is
 * off or READ_CALENDAR is missing.
 *
 * BLOCKING resolver query — and Glance 1.1.1's SessionWorker runs
 * provideGlance on Dispatchers.Main, so callers MUST hop to Dispatchers.IO
 * (`withContext`) or every widget refresh janks the app's UI thread.
 * WidgetSync pushes re-run provideGlance, so the snapshot refreshes with
 * every store change, midnight tick, and app-resume sync.
 */
internal fun deviceRowsFor(
    context: Context,
    data: AppData,
    day: java.time.LocalDate,
): List<Pair<EventItem, Color>> {
    val pf = data.prefs
    if (!pf.deviceCalsEnabled || !DeviceCalendars.hasPermission(context)) return emptyList()
    return DeviceCalendars.eventsBetween(context, day, day, pf.deviceCalIds)[day.toIso()]
        .orEmpty()
        .map { de ->
            EventItem(
                id = "device:${de.id}",
                title = de.title,
                date = de.date,
                start = de.start,
                end = de.end,
                allDay = de.allDay,
                cat = "",
                remind = null,
            ) to hexColor(de.colorHex)
        }
}

internal fun launchIntent(context: Context, action: String? = null): Intent =
    Intent(context, MainActivity::class.java)
        .apply { if (action != null) setAction(action) }
        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * Friendly empty state: a soft glyph above (or beside, when [compact]) calm
 * copy, so an empty widget reads as "all done" rather than broken.
 */
@Composable
internal fun WidgetEmptyState(
    iconRes: Int,
    line: String,
    hint: String? = null,
    c: BentoColors,
    compact: Boolean = false,
) {
    if (compact) {
        androidx.glance.layout.Row(
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.glance.Image(
                provider = androidx.glance.ImageProvider(iconRes),
                contentDescription = null,
                colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(c.faint)),
                modifier = GlanceModifier.size(16.dp),
            )
            androidx.glance.layout.Spacer(GlanceModifier.size(7.dp))
            Text(
                line,
                style = TextStyle(color = ColorProvider(c.sub), fontSize = 11.5.sp),
                maxLines = 1,
            )
        }
    } else {
        androidx.glance.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            androidx.glance.Image(
                provider = androidx.glance.ImageProvider(iconRes),
                contentDescription = null,
                colorFilter = androidx.glance.ColorFilter.tint(ColorProvider(c.faint)),
                modifier = GlanceModifier.size(26.dp),
            )
            androidx.glance.layout.Spacer(GlanceModifier.size(6.dp))
            Text(
                line,
                style = TextStyle(
                    color = ColorProvider(c.sub),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
            if (hint != null) {
                Text(
                    hint,
                    style = TextStyle(color = ColorProvider(c.faint), fontSize = 10.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

/** Small rounded action chip (quick add, headers). */
@Composable
internal fun WidgetChip(label: String, intent: Intent, c: BentoColors, accent: Color) {
    Box(
        modifier = GlanceModifier
            .height(28.dp)
            .cornerRadius(14.dp)
            .background(ColorProvider(c.bd))
            .clickable(actionStartActivityIntent(intent)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.padding(horizontal = 10.dp),
            maxLines = 1,
        )
    }
}
