package com.bento.calendar.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bento.calendar.data.AppGraph
import com.bento.calendar.data.EventItem
import com.bento.calendar.data.occursOn
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.startOfWeek
import com.bento.calendar.ui.theme.BentoColors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.YearMonth

/**
 * Mini month widget: the current month's grid with today highlighted and a
 * dot under days that have events. Tapping a day opens the app on that day's
 * Day view; the header opens the app's calendar.
 */
class MonthWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = AppGraph.repository(context)
        val initial = repo.data.first()
        provideContent {
            val data by repo.data.collectAsState(initial)
            val today = LocalDate.now()
            MonthBody(
                context = context,
                today = today,
                month = YearMonth.from(today),
                events = data.events,
                monday = data.prefs.monday,
                c = paletteOf(data),
                accent = accentOf(data),
            )
        }
    }
}

@Composable
private fun MonthBody(
    context: Context,
    today: LocalDate,
    month: YearMonth,
    events: List<EventItem>,
    monday: Boolean,
    c: BentoColors,
    accent: Color,
) {
    val gridStart = startOfWeek(month.atDay(1), monday)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(c.tile))
            .cornerRadius(16.dp)
            .clickable(actionStartActivityIntent(launchIntent(context)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            Fmt.monthTitle(month),
            style = TextStyle(
                color = ColorProvider(c.tx),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(6.dp))

        // Weekday letters, respecting the week-start preference.
        Row(GlanceModifier.fillMaxWidth()) {
            val order = if (monday) listOf(1, 2, 3, 4, 5, 6, 0) else (0..6).toList()
            order.forEach { dowIdx ->
                Text(
                    Fmt.WS[dowIdx].first().toString(),
                    style = TextStyle(
                        color = ColorProvider(c.faint),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
        Spacer(GlanceModifier.height(2.dp))

        for (week in 0 until 6) {
            Row(
                GlanceModifier.fillMaxWidth().defaultWeight(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (dow in 0 until 7) {
                    val date = gridStart.plusDays((week * 7 + dow).toLong())
                    DayCell(
                        context = context,
                        date = date,
                        inMonth = YearMonth.from(date) == month,
                        isToday = date == today,
                        hasEvents = events.any { it.occursOn(date) },
                        c = c,
                        accent = accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.DayCell(
    context: Context,
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    c: BentoColors,
    accent: Color,
) {
    val open = launchIntent(context, WidgetActions.OPEN_DAY)
        .putExtra(WidgetActions.EXTRA_DATE, date.toString())
        // Distinct data URI so each day's PendingIntent stays unique.
        .setData(android.net.Uri.parse("bento://day/$date"))
    Column(
        modifier = GlanceModifier
            .defaultWeight()
            .clickable(actionStartActivityIntent(open)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier
                .size(20.dp)
                .cornerRadius(10.dp)
                .background(ColorProvider(if (isToday) accent else Color.Transparent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                date.dayOfMonth.toString(),
                style = TextStyle(
                    color = ColorProvider(
                        when {
                            isToday -> Color.White
                            inMonth -> c.tx
                            else -> c.faint
                        },
                    ),
                    fontSize = 10.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
        Box(
            modifier = GlanceModifier
                .size(3.dp)
                .cornerRadius(2.dp)
                .background(
                    ColorProvider(if (hasEvents && inMonth) accent else Color.Transparent),
                ),
        ) {}
    }
}

class MonthWidgetReceiver : BentoFamilyReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()
}
