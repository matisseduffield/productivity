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
import com.bento.calendar.data.AppGraph
import com.bento.calendar.data.TaskItem
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.sortedOpenTasks
import com.bento.calendar.ui.theme.BentoColors
import kotlinx.coroutines.flow.first
import java.time.LocalDate

// Row budget: card padding + header row + spacer; ~26dp per task row.
private const val OVERHEAD_DP = 28 + 28 + 6
private const val ROW_DP = 26

/**
 * Open-tasks widget with checkboxes that complete tasks directly from the
 * home screen (via [ToggleTaskAction] — no app launch). Tapping a row's text
 * or the card opens the app on the Tasks tab.
 */
class TasksWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 180.dp),
            DpSize(250.dp, 250.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = AppGraph.repository(context)
        val initial = repo.data.first()
        provideContent {
            val data by repo.data.collectAsState(initial)
            val today = LocalDate.now()
            TasksBody(
                context = context,
                tasks = sortedOpenTasks(data.tasks, today),
                today = today,
                c = paletteOf(data),
                accent = accentOf(data),
            )
        }
    }
}

/** Toggles a task's done state straight from the widget. */
class ToggleTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[TASK_ID] ?: return
        runCatching {
            AppGraph.repository(context).update { x ->
                x.copy(tasks = x.tasks.map { if (it.id == taskId) it.copy(done = !it.done) else it })
            }
            WidgetSync.pushNow(context)
        }
    }

    companion object {
        val TASK_ID = ActionParameters.Key<String>("taskId")
    }
}

@Composable
private fun TasksBody(
    context: Context,
    tasks: List<TaskItem>,
    today: LocalDate,
    c: BentoColors,
    accent: Color,
) {
    val size = LocalSize.current
    val narrow = size.width < 200.dp
    val capacity = ((size.height.value.toInt() - OVERHEAD_DP) / ROW_DP).coerceAtLeast(1)
    val shown = if (tasks.size > capacity) (capacity - 1).coerceAtLeast(1) else tasks.size
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(c.tile))
            .cornerRadius(16.dp)
            .clickable(actionStartActivityIntent(launchIntent(context, WidgetActions.OPEN_TASKS)))
            .padding(14.dp),
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Tasks",
                style = TextStyle(
                    color = ColorProvider(c.tx),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                "${tasks.size} open",
                style = TextStyle(color = ColorProvider(c.sub), fontSize = 11.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.defaultWeight())
            WidgetChip(
                if (narrow) "+" else "+ Task",
                launchIntent(context, WidgetActions.NEW_TASK),
                c,
                accent,
            )
        }

        Spacer(GlanceModifier.height(6.dp))

        if (tasks.isEmpty()) {
            Text(
                "All clear",
                style = TextStyle(color = ColorProvider(c.faint), fontSize = 11.sp),
            )
        } else {
            tasks.take(shown).forEach { TaskRow(it, today, narrow, c, accent) }
            if (tasks.size > shown) {
                Text(
                    "+${tasks.size - shown} more",
                    style = TextStyle(color = ColorProvider(c.faint), fontSize = 10.sp),
                    modifier = GlanceModifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun TaskRow(t: TaskItem, today: LocalDate, narrow: Boolean, c: BentoColors, accent: Color) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Tappable checkbox: completes the task without opening the app.
        Box(
            modifier = GlanceModifier
                .size(18.dp)
                .cornerRadius(6.dp)
                .background(ColorProvider(c.bd))
                .clickable(
                    actionRunCallback<ToggleTaskAction>(
                        actionParametersOf(ToggleTaskAction.TASK_ID to t.id),
                    ),
                ),
        ) {}
        Spacer(GlanceModifier.width(9.dp))
        Text(
            t.title,
            style = TextStyle(color = ColorProvider(c.tx), fontSize = 12.sp),
            maxLines = 1,
            modifier = GlanceModifier.defaultWeight(),
        )
        val due = t.due
        if (!narrow && due != null) {
            Spacer(GlanceModifier.width(6.dp))
            val overdue = due < today.toString()
            Text(
                Fmt.dueLabel(due, today),
                style = TextStyle(
                    color = ColorProvider(if (overdue) c.dng else c.faint),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
            )
        }
    }
}

class TasksWidgetReceiver : BentoFamilyReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TasksWidget()
}
