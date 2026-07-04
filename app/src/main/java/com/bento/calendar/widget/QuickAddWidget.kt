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
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bento.calendar.data.AppGraph
import com.bento.calendar.ui.theme.BentoColors
import kotlinx.coroutines.flow.first

/**
 * Quick-add bar: four one-tap actions — new event, new task, new note and
 * search — for the top or bottom of a home screen.
 */
class QuickAddWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = AppGraph.repository(context)
        val initial = repo.data.first()
        provideContent {
            val data by repo.data.collectAsState(initial)
            QuickAddBody(context, paletteOf(data), accentOf(data))
        }
    }
}

@Composable
private fun QuickAddBody(context: Context, c: BentoColors, accent: Color) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(c.tile))
            .cornerRadius(16.dp)
            .padding(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionCell(context, "＋ Event", WidgetActions.NEW_EVENT, c, accent)
        Spacer(GlanceModifier.width(6.dp))
        ActionCell(context, "＋ Task", WidgetActions.NEW_TASK, c, accent)
        Spacer(GlanceModifier.width(6.dp))
        ActionCell(context, "＋ Note", WidgetActions.NEW_NOTE, c, accent)
        Spacer(GlanceModifier.width(6.dp))
        ActionCell(context, "Search", WidgetActions.OPEN_SEARCH, c, accent)
    }
}

@Composable
private fun androidx.glance.layout.RowScope.ActionCell(
    context: Context,
    label: String,
    action: String,
    c: BentoColors,
    accent: Color,
) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .cornerRadius(12.dp)
            .background(ColorProvider(c.bd))
            .clickable(actionStartActivityIntent(launchIntent(context, action))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(
                color = ColorProvider(accent),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}

class QuickAddWidgetReceiver : BentoFamilyReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}
