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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.bento.calendar.data.AppGraph
import com.bento.calendar.data.NoteItem
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.theme.BentoColors
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Pinned-note widget: the most recently edited pinned note (or the newest
 * note when nothing is pinned). Locked notes never leak content — the body
 * shows masking dots, exactly like in-app previews. Tapping opens the note
 * (through the PIN flow when locked).
 */
class NoteWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = AppGraph.repository(context)
        val initial = repo.data.first()
        provideContent {
            val data by repo.data.collectAsState(initial)
            val note = data.notes
                .sortedByDescending { it.updated }
                .let { sorted -> sorted.firstOrNull { it.pinned } ?: sorted.firstOrNull() }
            NoteBody(
                context = context,
                note = note,
                use24h = data.prefs.use24h,
                c = paletteOf(data),
                accent = accentOf(data),
            )
        }
    }
}

@Composable
private fun NoteBody(
    context: Context,
    note: NoteItem?,
    use24h: Boolean,
    c: BentoColors,
    accent: Color,
) {
    val open = if (note == null) {
        launchIntent(context)
    } else {
        launchIntent(context, WidgetActions.OPEN_NOTE)
            .putExtra(WidgetActions.EXTRA_NOTE_ID, note.id)
            .setData(android.net.Uri.parse("bento://note/${note.id}"))
    }
    // Body lines that fit under the header at ~15dp per preview line.
    val bodyLines = ((LocalSize.current.height.value.toInt() - 64) / 15).coerceIn(1, 12)
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(ColorProvider(c.tile))
            .cornerRadius(16.dp)
            .clickable(actionStartActivityIntent(open))
            .padding(14.dp),
    ) {
        Row {
            Text(
                if (note?.pinned == true) "PINNED NOTE" else "NOTE",
                style = TextStyle(
                    color = ColorProvider(accent),
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
        }
        Spacer(GlanceModifier.height(5.dp))
        if (note == null) {
            Text(
                "No notes yet — tap to write one",
                style = TextStyle(color = ColorProvider(c.faint), fontSize = 11.sp),
            )
        } else {
            Text(
                note.title.ifEmpty { "Untitled" },
                style = TextStyle(
                    color = ColorProvider(c.tx),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(3.dp))
            Text(
                if (note.locked) "•••• •••• ••••" else note.body.ifEmpty { "No content" },
                style = TextStyle(color = ColorProvider(c.sub), fontSize = 11.sp),
                maxLines = bodyLines,
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                Fmt.relEdit(note.updated, LocalDateTime.now(), use24h) +
                    if (note.locked) " · Locked" else "",
                style = TextStyle(color = ColorProvider(c.faint), fontSize = 9.5.sp),
                maxLines = 1,
            )
        }
    }
}

class NoteWidgetReceiver : BentoFamilyReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NoteWidget()
}
