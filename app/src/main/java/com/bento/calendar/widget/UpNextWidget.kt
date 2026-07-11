package com.bento.calendar.widget

import android.content.Context
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
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bento.calendar.data.AppGraph
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.BlockState
import com.bento.calendar.data.minsToHm
import com.bento.calendar.data.occurrencesOn
import com.bento.calendar.data.toMins
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.theme.BentoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime

/**
 * Small "up next" widget: the next event still to come today (timed events by
 * end time, like the Today tile), or today's all-day event, or an all-clear
 * line. Taller placements add the location line.
 */
class UpNextWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 40.dp), DpSize(110.dp, 110.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = AppGraph.repository(context)
        val initial = repo.data.first()
        // Device overlay merges into "up next" too — snapshot per session,
        // keyed to its own day (see BentoWidget for the rollover rationale).
        // IO hop: Glance 1.1.1 runs provideGlance on Main.
        val deviceDay = LocalDate.now()
        val deviceRows = withContext(Dispatchers.IO) {
            deviceRowsFor(context, initial, deviceDay)
        }
        provideContent {
            val data by repo.data.collectAsState(initial)
            val today = LocalDate.now()
            val nowMin = LocalTime.now().let { it.hour * 60 + it.minute }
            val eventItems = (
                occurrencesOn(data.events, today).map { it to catColor(data, it.cat) } +
                    if (today == deviceDay) deviceRows else emptyList()
                ).map { (event, color) ->
                    UpNextItem(event.title, event.start, event.end, event.allDay, event.loc, color, event.endDate != null)
                }
            val taskItems = data.taskBlocks
                .filter { it.date == today.toString() && it.state == BlockState.PLANNED }
                .mapNotNull { block ->
                    val task = data.tasks.firstOrNull { it.id == block.taskId && !it.done } ?: return@mapNotNull null
                    UpNextItem(
                        task.title, minsToHm(block.startMin), minsToHm(block.startMin + block.durationMin),
                        false, "Focused task", accentOf(data), false,
                    )
                }
            val occ = (eventItems + taskItems).sortedBy { it.start }
            val nextPair = occ.firstOrNull { !it.allDay && it.end.toMins() > nowMin }
                ?: occ.firstOrNull { it.allDay }
            UpNextBody(
                context = context,
                next = nextPair,
                // Dot only renders when next != null; Transparent is inert.
                dotColor = nextPair?.color ?: Color.Transparent,
                nowMin = nowMin,
                use24h = data.prefs.use24h,
                c = paletteOf(data),
                accent = accentOf(data),
            )
        }
    }
}

private data class UpNextItem(
    val title: String,
    val start: String,
    val end: String,
    val allDay: Boolean,
    val loc: String,
    val color: Color,
    val spanSegment: Boolean,
)

@Composable
private fun UpNextBody(
    context: Context,
    next: UpNextItem?,
    dotColor: Color,
    nowMin: Int,
    use24h: Boolean,
    c: BentoColors,
    accent: Color,
) {
    val tall = LocalSize.current.height >= 90.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(c.tile))
            .cornerRadius(16.dp)
            .clickable(actionStartActivityIntent(launchIntent(context)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (tall) {
            Text(
                if (next != null && !next.allDay && next.start.toMins() <= nowMin) "HAPPENING NOW" else "UP NEXT",
                style = TextStyle(
                    color = ColorProvider(accent),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.height(4.dp))
        }
        if (next == null) {
            WidgetEmptyState(
                iconRes = com.bento.calendar.R.drawable.widget_empty_sun,
                line = "All clear today",
                c = c,
                compact = !tall,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = GlanceModifier
                        .size(7.dp)
                        .cornerRadius(4.dp)
                        .background(ColorProvider(dotColor)),
                ) {}
                Spacer(GlanceModifier.width(7.dp))
                Text(
                    next.title,
                    style = TextStyle(
                        color = ColorProvider(c.tx),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.height(2.dp))
            // Span segments: show the real edge with an arrow instead of the
            // 00:00/23:59 sentinels (same language as the in-app agenda).
            val spanSeg = next.spanSegment && !next.allDay
            Text(
                when {
                    next.allDay -> "All day"
                    spanSeg && next.start == "00:00" -> "Until ${Fmt.time(next.end, use24h)}"
                    spanSeg && next.end == "23:59" -> "${Fmt.time(next.start, use24h)} →"
                    else -> "${Fmt.time(next.start, use24h)} – ${Fmt.time(next.end, use24h)}"
                },
                style = TextStyle(color = ColorProvider(c.sub), fontSize = 10.5.sp),
                maxLines = 1,
            )
            if (tall && next.loc.isNotEmpty()) {
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    next.loc,
                    style = TextStyle(color = ColorProvider(c.faint), fontSize = 10.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

class UpNextWidgetReceiver : BentoFamilyReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UpNextWidget()
}
