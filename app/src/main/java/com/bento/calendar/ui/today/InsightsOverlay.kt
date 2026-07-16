package com.bento.calendar.ui.today

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.AppData
import com.bento.calendar.data.ProductivityDay
import com.bento.calendar.data.ProductivityInsights
import com.bento.calendar.data.productivityInsights
import com.bento.calendar.ui.AppViewModel
import com.bento.calendar.ui.components.FullOverlay
import com.bento.calendar.ui.components.pressable
import com.bento.calendar.ui.theme.BentoIcons
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.theme.hexColor
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/** Private, on-device review of plans and focused work. */
@Composable
fun InsightsOverlay(vm: AppViewModel, data: AppData, now: LocalDateTime) {
    val c = LocalBento.current
    var rangeDays by remember { mutableIntStateOf(7) }
    val insights = remember(data, now, rangeDays) {
        productivityInsights(
            data = data,
            through = now.toLocalDate(),
            rangeDays = rangeDays,
            nowMillis = System.currentTimeMillis(),
            elapsedNow = SystemClock.elapsedRealtime(),
        )
    }
    FullOverlay {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(36.dp).pressable { vm.closeInsights() }
                        .background(c.tile, CircleShape).border(1.dp, c.bd, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(BentoIcons.ChevronLeft, null, tint = c.sub, modifier = Modifier.size(17.dp))
                }
                Column {
                    Text("Insights", fontSize = 21.sp, fontWeight = FontWeight.W700, letterSpacing = (-0.01).em, color = c.tx)
                    Text("Your plans and focused time stay on this device", fontSize = 10.5.sp, color = c.faint)
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RangeSwitch(rangeDays) { rangeDays = it }
                MetricRow(insights)
                TrendCard(insights, rangeDays)
                CategoryCard(data, insights)
                RecentDaysCard(insights)
            }
        }
    }
}

@Composable
private fun RangeSwitch(selected: Int, onSelect: (Int) -> Unit) {
    val c = LocalBento.current
    Row(
        Modifier.fillMaxWidth().background(c.inp, CircleShape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        listOf(7 to "7 days", 30 to "30 days").forEach { (days, label) ->
            Box(
                Modifier.weight(1f).pressable { onSelect(days) }
                    .background(if (selected == days) c.tile else Color.Transparent, CircleShape)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, fontSize = 11.5.sp, fontWeight = FontWeight.W700, color = if (selected == days) c.tx else c.faint)
            }
        }
    }
}

@Composable
private fun MetricRow(insights: ProductivityInsights) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Metric("Focused", durationLabel(insights.totalFocusedMinutes), "actual time", Modifier.weight(1f))
        Metric("Follow-through", "${insights.completionPercent}%", "resolved blocks", Modifier.weight(1f))
        Metric("Streak", "${insights.focusStreakDays}", if (insights.focusStreakDays == 1) "focus day" else "focus days", Modifier.weight(1f))
    }
}

@Composable
private fun Metric(label: String, value: String, sub: String, modifier: Modifier) {
    val c = LocalBento.current
    Column(
        modifier.background(c.tile, RoundedCornerShape(16.dp)).border(1.dp, c.bd, RoundedCornerShape(16.dp)).padding(12.dp),
    ) {
        Text(label.uppercase(), fontSize = 8.5.sp, fontWeight = FontWeight.W700, letterSpacing = 0.08.em, color = c.faint)
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.W700, color = c.tx, modifier = Modifier.padding(top = 5.dp))
        Text(sub, fontSize = 9.5.sp, color = c.sub, maxLines = 1)
    }
}

@Composable
private fun TrendCard(insights: ProductivityInsights, rangeDays: Int) {
    val c = LocalBento.current
    val maxMinutes = insights.days.maxOfOrNull { maxOf(it.plannedMinutes, it.focusedMinutes) }?.coerceAtLeast(30) ?: 30
    Column(
        Modifier.fillMaxWidth().background(c.tile, RoundedCornerShape(18.dp))
            .border(1.dp, c.bd, RoundedCornerShape(18.dp)).padding(15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Plan vs focus", fontSize = 13.5.sp, fontWeight = FontWeight.W700, color = c.tx)
            Spacer(Modifier.weight(1f))
            Legend(c.faint, "Planned")
            Spacer(Modifier.width(10.dp))
            Legend(c.acc, "Focused")
        }
        Row(
            Modifier.fillMaxWidth().height(112.dp).padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(if (rangeDays == 7) 7.dp else 2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            insights.days.forEach { day ->
                Row(
                    Modifier.weight(1f).height(100.dp),
                    horizontalArrangement = Arrangement.spacedBy(1.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    TrendBar(day.plannedMinutes, maxMinutes, c.faint.copy(alpha = 0.42f), Modifier.weight(1f))
                    TrendBar(day.focusedMinutes, maxMinutes, c.acc, Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Text(insights.days.first().date.dayOfMonth.toString(), fontSize = 9.sp, color = c.faint)
            Spacer(Modifier.weight(1f))
            Text("Today", fontSize = 9.sp, color = c.faint)
        }
    }
}

@Composable
private fun TrendBar(minutes: Int, maxMinutes: Int, color: Color, modifier: Modifier) {
    val height = if (minutes == 0) 2 else (minutes.toFloat() / maxMinutes * 100f).roundToInt().coerceIn(3, 100)
    Box(modifier.height(height.dp).background(color, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
}

@Composable
private fun Legend(color: Color, label: String) {
    val c = LocalBento.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(color, CircleShape))
        Text(label, fontSize = 9.sp, color = c.faint, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun CategoryCard(data: AppData, insights: ProductivityInsights) {
    val c = LocalBento.current
    val max = insights.categoryFocus.firstOrNull()?.focusedMinutes?.coerceAtLeast(1) ?: 1
    Column(
        Modifier.fillMaxWidth().background(c.tile, RoundedCornerShape(18.dp))
            .border(1.dp, c.bd, RoundedCornerShape(18.dp)).padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Focus by category", fontSize = 13.5.sp, fontWeight = FontWeight.W700, color = c.tx)
        if (insights.categoryFocus.isEmpty()) {
            Text("Focus a task to start building your breakdown.", fontSize = 11.5.sp, color = c.faint)
        } else insights.categoryFocus.take(5).forEach { item ->
            val category = item.categoryId.takeIf { it.isNotBlank() }?.let(data::categoryOf)
            val color = category?.let { hexColor(it.colorHex) } ?: c.faint
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(color, CircleShape))
                    Text(category?.label ?: "Uncategorised", fontSize = 11.5.sp, fontWeight = FontWeight.W600, color = c.tx, modifier = Modifier.padding(start = 7.dp))
                    Spacer(Modifier.weight(1f))
                    Text(durationLabel(item.focusedMinutes), fontSize = 10.5.sp, color = c.sub)
                }
                Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(4.dp).background(c.inp, CircleShape)) {
                    Box(Modifier.fillMaxWidth(item.focusedMinutes.toFloat() / max).height(4.dp).background(color, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun RecentDaysCard(insights: ProductivityInsights) {
    val c = LocalBento.current
    Column(
        Modifier.fillMaxWidth().background(c.tile, RoundedCornerShape(18.dp))
            .border(1.dp, c.bd, RoundedCornerShape(18.dp)).padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Text("Recent days", fontSize = 13.5.sp, fontWeight = FontWeight.W700, color = c.tx)
        insights.days.takeLast(7).reversed().forEachIndexed { index, day ->
            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
            DayRow(day)
        }
    }
}

@Composable
private fun DayRow(day: ProductivityDay) {
    val c = LocalBento.current
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), fontSize = 11.5.sp, fontWeight = FontWeight.W700, color = c.tx)
            Text("${day.completedBlocks} completed · ${day.skippedBlocks} skipped", fontSize = 9.5.sp, color = c.faint)
        }
        Text("${durationLabel(day.focusedMinutes)} / ${durationLabel(day.plannedMinutes)}", fontSize = 10.5.sp, color = c.sub)
    }
}

private fun durationLabel(minutes: Int): String = when {
    minutes < 60 -> "${minutes}m"
    minutes % 60 == 0 -> "${minutes / 60}h"
    else -> "${minutes / 60}h ${minutes % 60}m"
}
