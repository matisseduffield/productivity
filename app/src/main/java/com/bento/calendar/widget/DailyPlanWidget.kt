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
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
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
import com.bento.calendar.data.AppData
import com.bento.calendar.data.AppGraph
import com.bento.calendar.data.BlockState
import com.bento.calendar.data.FocusOutcome
import com.bento.calendar.data.activeFocus
import com.bento.calendar.data.focusElapsedSeconds
import com.bento.calendar.data.minsToHm
import com.bento.calendar.data.startFocus
import com.bento.calendar.focus.FocusTimer
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.theme.BentoColors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import android.os.SystemClock

class DailyPlanWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(250.dp, 110.dp), DpSize(250.dp, 180.dp), DpSize(250.dp, 250.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = AppGraph.repository(context)
        val initial = repo.data.first()
        provideContent {
            val data by repo.data.collectAsState(initial)
            DailyPlanBody(context, data, paletteOf(data), accentOf(data))
        }
    }
}

class StartFocusAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TASK_ID] ?: return
        val blockId = parameters[BLOCK_ID] ?: return
        val now = System.currentTimeMillis()
        val updated = AppGraph.repository(context).update { data ->
            val block = data.taskBlocks.firstOrNull { it.id == blockId } ?: return@update data
            startFocus(data, taskId, blockId, now, block.durationMin * 60L, SystemClock.elapsedRealtime())
        }
        FocusTimer.sync(context, updated, now)
        WidgetSync.pushNow(context)
    }

    companion object {
        val TASK_ID = ActionParameters.Key<String>("focusTaskId")
        val BLOCK_ID = ActionParameters.Key<String>("focusBlockId")
    }
}

@Composable
private fun DailyPlanBody(context: Context, data: AppData, c: BentoColors, accent: Color) {
    val today = LocalDate.now().toString()
    val blocks = data.taskBlocks.filter { it.date == today && it.state == BlockState.PLANNED }.sortedBy { it.startMin }
    val tasks = data.tasks.associateBy { it.id }
    val active = activeFocus(data)
    val capacity = ((LocalSize.current.height.value.toInt() - 50) / 27).coerceAtLeast(1)
    Column(
        GlanceModifier.fillMaxSize().background(ColorProvider(c.tile)).cornerRadius(16.dp)
            .clickable(actionStartActivityIntent(launchIntent(context))).padding(14.dp),
    ) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Daily plan", style = TextStyle(ColorProvider(c.tx), 14.sp, FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text("${blocks.sumOf { it.durationMin }} min", style = TextStyle(ColorProvider(c.sub), 10.5.sp))
        }
        Spacer(GlanceModifier.height(7.dp))
        if (active != null) {
            val elapsed = focusElapsedSeconds(active, System.currentTimeMillis(), SystemClock.elapsedRealtime())
            Row(
                GlanceModifier.fillMaxWidth().background(ColorProvider(c.bd)).cornerRadius(10.dp).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(GlanceModifier.size(7.dp).cornerRadius(4.dp).background(ColorProvider(accent))) {}
                Spacer(GlanceModifier.width(7.dp))
                Text(active.taskTitleSnapshot, style = TextStyle(ColorProvider(c.tx), 11.5.sp, FontWeight.Bold), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                Text(if (active.outcome == FocusOutcome.PAUSED) "Paused" else "${elapsed / 60}m", style = TextStyle(ColorProvider(accent), 10.sp))
            }
            Spacer(GlanceModifier.height(5.dp))
        }
        if (blocks.isEmpty()) {
            Box(GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                WidgetEmptyState(com.bento.calendar.R.drawable.widget_empty_sun, "No plan yet", "Open Bento to plan your day", c)
            }
        } else {
            blocks.take(capacity).forEach { block ->
                val task = tasks[block.taskId] ?: return@forEach
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Fmt.time(minsToHm(block.startMin), data.prefs.use24h), style = TextStyle(ColorProvider(c.faint), 10.sp), modifier = GlanceModifier.width(54.dp))
                    Text(task.title, style = TextStyle(ColorProvider(c.tx), 11.5.sp), maxLines = 1, modifier = GlanceModifier.defaultWeight())
                    Box(
                        GlanceModifier.size(24.dp).cornerRadius(12.dp).background(ColorProvider(c.bd)).clickable(
                            actionRunCallback<StartFocusAction>(
                                actionParametersOf(StartFocusAction.TASK_ID to task.id, StartFocusAction.BLOCK_ID to block.id),
                            ),
                        ),
                        contentAlignment = Alignment.Center,
                    ) { Text("▶", style = TextStyle(ColorProvider(accent), 10.sp)) }
                }
            }
        }
    }
}

class DailyPlanWidgetReceiver : BentoFamilyReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DailyPlanWidget()
}
