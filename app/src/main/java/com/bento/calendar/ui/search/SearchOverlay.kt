package com.bento.calendar.ui.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.NoteItem
import com.bento.calendar.data.Recur
import com.bento.calendar.data.toDate
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.Fmt
import com.bento.calendar.ui.components.EmptyText
import com.bento.calendar.ui.components.SectionLabel
import com.bento.calendar.ui.components.TextLink
import com.bento.calendar.ui.components.hairlineBottom
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.components.tap
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.theme.Sora
import java.time.LocalDateTime
import kotlinx.coroutines.launch

/** One row of the search dropdown (.srow). */
private data class SearchRow(
    val title: AnnotatedString,
    val sub: AnnotatedString,
    val meta: String,
    val icon: ImageVector,
    val onTap: () -> Unit,
)

private data class SearchGroup(val label: String, val rows: List<SearchRow>)

/**
 * Search overlay (.scatch + .sbar + .sdrop): dimmed scrim, floating search bar
 * near the top and a dropdown results panel. Not a full page.
 */
@Composable
fun SearchOverlay(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    val c = LocalBento.current
    val use24h = data.prefs.use24h
    val today = now.toLocalDate()

    // ---- Results (ported 1:1 from the prototype) ----
    val q = vm.query.trim().lowercase()
    val groups = buildList {
        if (q.isNotEmpty()) {
            val ev = data.events
                .filter { it.title.lowercase().contains(q) || it.loc.lowercase().contains(q) }
                .take(6)
                .map { e ->
                    SearchRow(
                        title = highlight(e.title, q, c.acc),
                        // The location is part of the match filter, so show it
                        // (highlighted) — otherwise a loc-only match renders no
                        // visible occurrence of the query and reads as a false
                        // positive. highlight() is a no-op when q isn't in loc.
                        sub = buildAnnotatedString {
                            append(
                                Fmt.dayShort(e.date.toDate()) + " · " +
                                    (if (e.allDay) "All day" else Fmt.time(e.start, use24h)) +
                                    if (e.recur.isNotEmpty() && e.recur != Recur.NONE) " · repeats ${e.recur}" else "",
                            )
                            if (e.loc.isNotEmpty()) {
                                append(" · ")
                                append(highlight(e.loc, q, c.acc))
                            }
                        },
                        meta = "Event",
                        icon = BentoIcons.TabCalendar,
                        onTap = {
                            vm.recordSearch(vm.query)
                            vm.openEvent(e)
                        },
                    )
                }
            val tk = data.tasks
                .filter { it.title.lowercase().contains(q) }
                .take(6)
                .map { t ->
                    SearchRow(
                        title = highlight(t.title, q, c.acc),
                        sub = AnnotatedString(
                            when {
                                t.done -> "Completed"
                                t.due != null -> "Due " + Fmt.dueLabel(t.due, today)
                                else -> "No due date"
                            },
                        ),
                        meta = "Task",
                        icon = BentoIcons.TabTasks,
                        onTap = {
                            vm.recordSearch(vm.query)
                            vm.openTask(t)
                        },
                    )
                }
            val nt = data.notes
                .filter { it.title.lowercase().contains(q) || (!it.locked && it.body.lowercase().contains(q)) }
                .take(6)
                .map { n ->
                    SearchRow(
                        title = highlight(n.title.ifEmpty { "Untitled" }, q, c.acc),
                        sub = if (n.locked) AnnotatedString("Locked note") else highlight(notePreview(n), q, c.acc),
                        meta = "Note",
                        icon = BentoIcons.Doc,
                        onTap = {
                            vm.recordSearch(vm.query)
                            vm.openNote(n.id)
                        },
                    )
                }
            if (ev.isNotEmpty()) add(SearchGroup("Events", ev))
            if (tk.isNotEmpty()) add(SearchGroup("Tasks", tk))
            if (nt.isNotEmpty()) add(SearchGroup("Notes", nt))
        }
    }

    // ---- Enter animations: scrim + bar fade 180ms, dropdown slide-in 220ms ----
    val fade = remember { Animatable(0f) }
    val slide = remember { Animatable(0f) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        launch { fade.animateTo(1f, tween(180)) }
        launch { slide.animateTo(1f, tween(220)) }
        focusRequester.requestFocus()
    }

    // imePadding shrinks maxHeight (and thus panelMax) to the space above the
    // keyboard, so the autofocused IME can never occlude the dropdown.
    BoxWithConstraints(Modifier.fillMaxSize().imePadding()) {
        val panelMax = maxHeight * 0.62f

        // Scrim (.scatch) — tap to close.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = fade.value }
                .background(c.scrim)
                .tap { vm.closeSearch() },
        )

        // Floating search bar (.sbar). The real status bar replaces the
        // prototype's 36px fake one, leaving the 8px gap below it.
        Row(
            Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth()
                .graphicsLayer { alpha = fade.value }
                .tap {}, // don't let gaps fall through to the scrim
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val style = TextStyle(fontFamily = Sora, fontSize = 13.5.sp, color = c.tx)
            // Cursor-aware wrapper around vm.query (the source of truth): the
            // String-overload field keeps an internal selection that stays at 0
            // when a recent search is recalled programmatically, putting the
            // caret BEFORE the recalled text. Reconcile any external query
            // change by moving the cursor to the end.
            var fieldState by remember { mutableStateOf(TextFieldValue(vm.query, TextRange(vm.query.length))) }
            val field =
                if (fieldState.text == vm.query) fieldState
                else TextFieldValue(vm.query, TextRange(vm.query.length))
            BasicTextField(
                value = field,
                onValueChange = {
                    fieldState = it
                    vm.setQuery(it.text)
                },
                modifier = Modifier
                    .weight(1f)
                    .background(c.inp, RoundedCornerShape(14.dp))
                    .border(1.dp, c.bd, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .focusRequester(focusRequester),
                textStyle = style,
                singleLine = true,
                cursorBrush = SolidColor(c.acc),
                decorationBox = { inner ->
                    Box {
                        if (vm.query.isEmpty()) {
                            Text("Search events, tasks, notes", style = style.copy(color = c.faint))
                        }
                        inner()
                    }
                },
            )
            TextLink("Cancel", onClick = { vm.closeSearch() })
        }

        // Dropdown results panel (.sdrop).
        val shape = RoundedCornerShape(18.dp)
        Column(
            Modifier
                .statusBarsPadding()
                .padding(top = 61.dp, start = 12.dp, end = 12.dp)
                .fillMaxWidth()
                .heightIn(max = panelMax)
                .graphicsLayer {
                    alpha = slide.value
                    translationY = (1f - slide.value) * 16.dp.toPx()
                }
                .shadow(18.dp, shape)
                .background(c.tile, shape)
                .border(1.dp, c.bd, shape)
                .tap {} // consume taps on the panel itself
                .verticalScroll(rememberScrollState())
                .padding(start = 14.dp, end = 14.dp, top = 2.dp, bottom = 8.dp),
        ) {
            groups.forEach { group ->
                SectionLabel(group.label)
                group.rows.forEach { row -> ResultRow(row) }
            }
            if (groups.isEmpty()) {
                val recents = data.prefs.recents
                if (q.isEmpty() && recents.isNotEmpty()) {
                    SectionLabel("Recent")
                    recents.take(5).forEach { r ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .tap { vm.setQuery(r) }
                                .padding(vertical = 9.dp, horizontal = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Icon(BentoIcons.Clock, null, tint = c.faint, modifier = Modifier.size(14.dp))
                            Text(
                                r,
                                fontSize = 13.sp,
                                color = c.sub,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Text(
                            "Clear",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.W700,
                            color = c.acc,
                            modifier = Modifier
                                .pressable(onClick = { vm.clearRecentSearches() })
                                .padding(horizontal = 2.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    EmptyText(
                        if (q.isEmpty()) "Search your events, tasks and notes"
                        else "No matches for “${vm.query}”",
                    )
                }
            }
        }
    }
}

/** Note preview: non-blank body lines joined with dots, capped at 90 chars. */
private fun notePreview(n: NoteItem): String {
    val joined = n.body.split("\n").filter { it.isNotBlank() }.joinToString(" · ").take(90)
    return joined.ifEmpty { "No content" }
}

/**
 * Accent + bold the first case-insensitive occurrence of [query] in [text];
 * plain text when the query is empty or doesn't occur.
 */
private fun highlight(text: String, query: String, accent: Color): AnnotatedString {
    val q = query.trim()
    if (q.isEmpty()) return AnnotatedString(text)
    val i = text.indexOf(q, ignoreCase = true)
    if (i < 0) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text.substring(0, i))
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.W700)) {
            append(text.substring(i, i + q.length))
        }
        append(text.substring(i + q.length))
    }
}

@Composable
private fun ResultRow(row: SearchRow) {
    val c = LocalBento.current
    Row(
        Modifier
            .fillMaxWidth()
            .tap(onClick = row.onTap)
            .hairlineBottom(c.line)
            .padding(vertical = 11.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(37.dp)
                .background(c.tile, RoundedCornerShape(12.dp))
                .border(1.dp, c.bd, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(row.icon, null, tint = c.sub, modifier = Modifier.size(15.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                row.title,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.W600,
                color = c.tx,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                row.sub,
                fontSize = 11.sp,
                color = c.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Text(row.meta, fontSize = 10.sp, color = c.faint)
    }
}
