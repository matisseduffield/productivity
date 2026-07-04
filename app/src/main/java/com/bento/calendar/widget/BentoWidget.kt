package com.bento.calendar.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity as actionStartActivityIntent
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bento.calendar.MainActivity
import com.bento.calendar.data.AppGraph
import com.bento.calendar.data.Cats
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.theme.BentoColors
import com.bento.calendar.ui.theme.DarkColors
import com.bento.calendar.ui.theme.LightColors
import com.bento.calendar.ui.theme.hexColor
import kotlinx.coroutines.flow.first
import java.time.LocalDate

// Row budget (dp) for the reported widget size: fixed overhead is the card
// padding (14 top + 14 bottom), the header row, the spacer under it and the
// open-tasks line; each event row is ~23dp. Estimates track WidgetBody below.
private const val OVERHEAD_DP = 28 + 28 + 8 + 20
private const val ROW_DP = 23

/**
 * Home-screen widget: today's events plus quick-add chips, on a rounded card
 * matching the app's bento tiles (theme and accent follow the in-app
 * preferences). The composition collects the repository flow, so pushes to an
 * already-running Glance session (WidgetSync.push) recompose with fresh data;
 * the system re-updates every 30 min (updatePeriodMillis) and
 * [MidnightTickReceiver] rolls the date over at local midnight.
 */
class BentoWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 180.dp),
            DpSize(250.dp, 250.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = AppGraph.repository(context)
        // Seed with a snapshot so the first frame isn't an empty flash, then
        // collect the flow inside the composition: updates delivered to a
        // live session only recompose (provideGlance is not re-run), so the
        // collected value — not a captured local — must be the source.
        val initial = repo.data.first()
        provideContent {
            val data by repo.data.collectAsState(initial)
            val today = LocalDate.now() // recomputed per recomposition, not frozen per session
            val c = if (data.prefs.theme == "light") LightColors else DarkColors
            WidgetBody(
                context = context,
                today = today,
                events = occurrencesOn(data.events, today),
                openTasks = data.tasks.count { !it.done },
                use24h = data.prefs.use24h,
                c = c,
                accent = hexColor(data.prefs.accent),
            )
        }
    }
}

@Composable
private fun WidgetBody(
    context: Context,
    today: LocalDate,
    events: List<EventItem>,
    openTasks: Int,
    use24h: Boolean,
    c: BentoColors,
    accent: Color,
) {
    // Fit rows to the reported size, always reserving space for the task line
    // and — when events overflow — one row slot for the "+N more" indicator,
    // so neither is ever clipped off the bottom of a 4x2 placement.
    val capacity =
        ((LocalSize.current.height.value.toInt() - OVERHEAD_DP) / ROW_DP).coerceAtLeast(1)
    val shown = if (events.size > capacity) (capacity - 1).coerceAtLeast(1) else events.size
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(c.tile))
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Today",
                style = TextStyle(color = ColorProvider(c.sub), fontSize = 11.sp),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                Fmt.dayShort(today),
                style = TextStyle(
                    color = ColorProvider(c.tx),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            WidgetChip("+ Event", launchIntent(context, WidgetActions.NEW_EVENT), c, accent)
            Spacer(GlanceModifier.width(6.dp))
            WidgetChip("+ Task", launchIntent(context, WidgetActions.NEW_TASK), c, accent)
        }

        Spacer(GlanceModifier.height(8.dp))

        if (events.isEmpty()) {
            Text(
                "Nothing scheduled",
                style = TextStyle(color = ColorProvider(c.faint), fontSize = 11.sp),
            )
        } else {
            events.take(shown).forEach { EventRow(it, use24h, c) }
            if (events.size > shown) {
                Text(
                    "+${events.size - shown} more",
                    style = TextStyle(color = ColorProvider(c.faint), fontSize = 10.sp),
                    modifier = GlanceModifier.padding(top = 2.dp),
                )
            }
        }

        if (openTasks > 0) {
            Spacer(GlanceModifier.height(6.dp))
            Text(
                if (openTasks == 1) "1 open task" else "$openTasks open tasks",
                style = TextStyle(color = ColorProvider(c.sub), fontSize = 10.5.sp),
            )
        }
    }
}

@Composable
private fun EventRow(e: EventItem, use24h: Boolean, c: BentoColors) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (e.allDay) "All day" else Fmt.time(e.start, use24h),
            style = TextStyle(color = ColorProvider(c.sub), fontSize = 11.sp),
            modifier = GlanceModifier.width(56.dp),
            maxLines = 1,
        )
        Box(
            modifier = GlanceModifier
                .size(6.dp)
                .cornerRadius(3.dp)
                .background(ColorProvider(catColor(e.cat))),
        ) {}
        Spacer(GlanceModifier.width(6.dp))
        Text(
            e.title,
            style = TextStyle(color = ColorProvider(c.tx), fontSize = 12.sp),
            maxLines = 1,
        )
    }
}

