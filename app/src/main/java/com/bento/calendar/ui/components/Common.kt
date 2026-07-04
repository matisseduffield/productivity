package com.bento.calendar.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.bento.calendar.data.Category
import com.bento.calendar.ui.theme.LocalBento
import com.bento.calendar.ui.theme.color
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.math.abs
import kotlin.math.roundToInt

/** Press feedback on all tappables: scale to 0.92 over 150ms, no ripple. */
fun Modifier.pressable(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, tween(150), label = "press")
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick)
}

/** Plain click with no ripple and no scale (rows, cells). */
fun Modifier.tap(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        enabled = enabled,
        onClick = onClick,
    )
}

/** 1dp hairline under list rows (prototype px == dp). */
fun Modifier.hairlineBottom(color: Color): Modifier = drawBehind {
    val h = 1.dp.toPx()
    drawRect(
        color = color,
        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - h),
        size = androidx.compose.ui.geometry.Size(size.width, h),
    )
}

/** 38dp circular header button (.gbtn); primary = the fixed-blue create button. */
@Composable
fun GBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    size: Dp = 38.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = LocalBento.current
    val bg = if (primary) com.bento.calendar.ui.theme.CreateBlue else c.tile
    val border = if (primary) com.bento.calendar.ui.theme.CreateBlue else c.bd
    Box(
        modifier = modifier
            .size(size)
            .pressable(onClick = onClick)
            .background(bg, CircleShape)
            .border(1.dp, border, CircleShape),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** Section label (.slab): uppercase, tracked, with optional right-aligned count. */
@Composable
fun SectionLabel(text: String, count: String? = null, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 2.dp, end = 2.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text.uppercase(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.W700,
            letterSpacing = 0.12.em,
            color = c.sub,
        )
        Spacer(Modifier.weight(1f))
        if (count != null) {
            Text(count, fontSize = 11.sp, fontWeight = FontWeight.W500, color = c.faint)
        }
    }
}

/** Category dot (7dp). */
@Composable
fun Dot(color: Color, size: Dp = 7.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(color, CircleShape))
}

/** Rounded-square checkbox with the pop animation. size 22 (17 mini). */
@Composable
fun BentoCheckbox(
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
    corner: Dp = 8.dp,
) {
    val c = LocalBento.current
    val haptics = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    LaunchedEffect(checked) {
        if (checked) {
            scale.snapTo(1f)
            scale.animateTo(1.18f, tween(125))
            scale.animateTo(1f, tween(125))
        }
    }
    Box(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .tap {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .background(if (checked) c.acc else Color.Transparent, RoundedCornerShape(corner))
            .border(1.5.dp, if (checked) c.acc else c.cbb, RoundedCornerShape(corner)),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) {
            androidx.compose.material3.Icon(
                imageVector = com.bento.calendar.ui.theme.BentoIcons.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}

/** 46x27 pill switch. */
@Composable
fun BentoSwitch(on: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    val knobX by animateFloatAsState(if (on) 21f else 4f, tween(200), label = "knob")
    val bg by androidx.compose.animation.animateColorAsState(
        if (on) c.acc else c.inp, tween(200), label = "swbg",
    )
    val knob by androidx.compose.animation.animateColorAsState(
        if (on) Color.White else c.faint, tween(200), label = "swknob",
    )
    Box(
        modifier
            .size(46.dp, 27.dp)
            .tap(onClick = onToggle)
            .background(bg, CircleShape)
            .border(1.dp, if (on) c.acc else c.bd, CircleShape),
    ) {
        Box(
            Modifier
                .offset { IntOffset((knobX * density).roundToInt(), (3 * density).roundToInt()) }
                .size(19.dp)
                .background(knob, CircleShape),
        )
    }
}

/** Field eyebrow label (.flbl). */
@Composable
fun FieldLabel(text: String) {
    val c = LocalBento.current
    Text(
        text.uppercase(),
        fontSize = 9.5.sp,
        fontWeight = FontWeight.W700,
        letterSpacing = 0.12.em,
        color = c.sub,
        modifier = Modifier.padding(bottom = 7.dp),
    )
}

/** Input-styled text field (.tin). */
@Composable
fun BentoTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: androidx.compose.foundation.text.KeyboardActions =
        androidx.compose.foundation.text.KeyboardActions.Default,
    textStyle: TextStyle? = null,
) {
    val c = LocalBento.current
    val style = textStyle ?: TextStyle(
        fontFamily = com.bento.calendar.ui.theme.Sora,
        fontSize = 13.5.sp,
        color = c.tx,
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(c.inp, RoundedCornerShape(12.dp))
            .border(1.dp, c.bd, RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
        textStyle = style,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        cursorBrush = SolidColor(c.acc),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text(placeholder, style = style.copy(color = c.faint))
                }
                inner()
            }
        },
    )
}

/** Full-width accent button (.pbtn). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .pressable(onClick = onClick)
            .background(c.acc, RoundedCornerShape(14.dp))
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.W700, color = Color.White)
    }
}

/** Centered danger text button (.dbtn) — used for two-tap deletes. */
@Composable
fun DangerTextButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 13.dp)
            .tap(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.W700, color = c.dng)
    }
}

/** Small bold text link (.scnl / .tchip text). */
@Composable
fun TextLink(text: String, onClick: () -> Unit, color: Color? = null, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    Text(
        text,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.W700,
        color = color ?: c.acc,
        modifier = modifier.pressable(onClick = onClick),
    )
}

/** Faint empty-state copy (.empt). */
@Composable
fun EmptyText(text: String, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    Text(
        text,
        fontSize = 12.sp,
        color = c.faint,
        modifier = modifier.padding(horizontal = 4.dp, vertical = 16.dp),
    )
}

private val SheetEasing = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)

/**
 * Bottom sheet (.sheet): scrim fade 220ms, panel slide-up 320ms from 70px,
 * 24dp top corners, grab handle, max height 86%. Tap scrim to dismiss.
 */
@Composable
fun BentoSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalBento.current
    val enter = remember { Animatable(0f) }
    // Live drag offset (plain state, updated synchronously in the gesture);
    // the settle-back-to-0 uses the Animatable so it can tween.
    var dragY by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    // pointerInput is keyed on Unit so a new drag never resets mid-gesture;
    // read the latest onDismiss through this to avoid a stale closure.
    val currentDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320, easing = SheetEasing)) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val sheetMax = maxHeight * 0.86f
        val dismissPx = with(LocalDensity.current) { 120.dp.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (enter.value / 0.7f).coerceAtMost(1f) }
                .background(c.scrim)
                .tap(onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    alpha = enter.value
                    translationY = (1f - enter.value) * 70.dp.toPx() + dragY
                }
                .fillMaxWidth()
                // Prototype .sheet is max-height:86% — sheets hug their content
                // (the PIN pad is short) and only long editors hit the cap.
                .heightIn(max = sheetMax)
                .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                .background(c.bg)
                .tap {} // consume taps so they don't hit the scrim
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // Grab handle doubles as a drag-down-to-dismiss target.
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            // Cancel any in-flight settle so the new drag owns dragY.
                            onDragStart = { settleJob?.cancel() },
                            onDragEnd = {
                                if (dragY > dismissPx) {
                                    currentDismiss()
                                } else {
                                    val from = dragY
                                    settleJob = scope.launch {
                                        settle.snapTo(from)
                                        settle.animateTo(0f, tween(200)) { dragY = value }
                                    }
                                }
                            },
                        ) { _, dy ->
                            dragY = (dragY + dy).coerceAtLeast(0f)
                        }
                    }
                    // Enlarge the touch target around the 36x4 handle.
                    .padding(top = 8.dp, bottom = 10.dp)
                    .size(52.dp, 20.dp)
                    .padding(top = 4.dp, bottom = 12.dp),
            ) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(36.dp, 4.dp)
                        .background(c.cbb, CircleShape),
                )
            }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 26.dp),
                content = content,
            )
        }
    }
}

/** Full-page overlay (.ovl): slide in from 16dp below + fade, 260ms. */
@Composable
fun FullOverlay(content: @Composable BoxScope.() -> Unit) {
    val c = LocalBento.current
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(260)) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 16.dp.toPx()
            }
            .background(c.bg)
            .tap {},
        content = content,
    )
}

/** Category pill row for editors; includeNone adds the task editor's "None". */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryPills(
    categories: List<Category>,
    selected: String,
    onSelect: (String) -> Unit,
    includeNone: Boolean = false,
) {
    val c = LocalBento.current
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (includeNone) {
            Pill(label = "None", dot = null, active = selected == "") { onSelect("") }
        }
        categories.forEach { cat ->
            Pill(label = cat.label, dot = cat.color, active = selected == cat.id) { onSelect(cat.id) }
        }
    }
}

@Composable
private fun Pill(label: String, dot: Color?, active: Boolean, onClick: () -> Unit) {
    val c = LocalBento.current
    Row(
        Modifier
            .pressable(onClick = onClick)
            .background(if (active) c.accTint(0.12f) else c.inp, CircleShape)
            .border(1.dp, if (active) c.acc else c.bd, CircleShape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (dot != null) Dot(dot)
        Text(
            label,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.W600,
            color = if (active) c.tx else c.sub,
        )
    }
}

/** Input-styled tappable row showing a value; used for date/time/select fields. */
@Composable
fun FieldButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = LocalBento.current
    Box(
        modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .tap(onClick = onClick)
            .background(c.inp, RoundedCornerShape(if (compact) 10.dp else 12.dp))
            .border(1.dp, c.bd, RoundedCornerShape(if (compact) 10.dp else 12.dp))
            .padding(
                horizontal = if (compact) 10.dp else 13.dp,
                vertical = if (compact) 8.dp else 11.dp,
            ),
    ) {
        Text(
            text,
            fontSize = if (compact) 12.sp else 13.5.sp,
            fontWeight = if (compact) FontWeight.W600 else FontWeight.W400,
            color = c.tx,
        )
    }
}

/** Native-style date picker dialog on top of a FieldButton. */
@Composable
fun BentoDateField(
    value: LocalDate?,
    display: String,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    FieldButton(display, onClick = { open = true }, modifier = modifier)
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (value ?: LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = state, showModeToggle = false)
        }
    }
}

/** Native-style time picker dialog on top of a FieldButton. */
@Composable
fun BentoTimeField(
    valueHm: String,
    display: String,
    use24h: Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = LocalBento.current
    var open by remember { mutableStateOf(false) }
    FieldButton(display, onClick = { open = true }, modifier = modifier)
    if (open) {
        val t = runCatching { LocalTime.parse(valueHm) }.getOrDefault(LocalTime.of(9, 0))
        val state = rememberTimePickerState(initialHour = t.hour, initialMinute = t.minute, is24Hour = use24h)
        Dialog(onDismissRequest = { open = false }) {
            Surface(shape = RoundedCornerShape(24.dp), color = c.tile) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = state)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { open = false }) { Text("Cancel") }
                        TextButton(onClick = {
                            onPick("%02d:%02d".format(state.hour, state.minute))
                            open = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

/** One side of a [SwipeActionRow]: icon revealed behind the row + the action. */
data class SwipeAction(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
    val onTrigger: () -> Unit,
)

/**
 * Row wrapper with horizontal swipe actions: drag right to reveal/trigger
 * [right] (e.g. complete/pin, accent), drag left for [left] (e.g. delete,
 * danger). Haptic tick when the trigger threshold is crossed; the row springs
 * back after release. Sides without an action don't move.
 */
@Composable
fun SwipeActionRow(
    modifier: Modifier = Modifier,
    right: SwipeAction? = null,
    left: SwipeAction? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val view = androidx.compose.ui.platform.LocalView.current
    var offsetX by remember { mutableFloatStateOf(0f) }
    val settle = remember { Animatable(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    var crossed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { 84.dp.toPx() }
    val maxReachPx = with(LocalDensity.current) { 120.dp.toPx() }
    val currentRight by rememberUpdatedState(right)
    val currentLeft by rememberUpdatedState(left)

    Box(modifier.fillMaxWidth()) {
        // Revealed action icons behind the row.
        if (offsetX > 0f && right != null) {
            Icon(
                right.icon,
                contentDescription = null,
                tint = right.tint.copy(alpha = (offsetX / thresholdPx).coerceIn(0.25f, 1f)),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(20.dp),
            )
        }
        if (offsetX < 0f && left != null) {
            Icon(
                left.icon,
                contentDescription = null,
                tint = left.tint.copy(alpha = (-offsetX / thresholdPx).coerceIn(0.25f, 1f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(20.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(right != null, left != null) {
                    detectHorizontalDragGestures(
                        onDragStart = {
                            settleJob?.cancel()
                            crossed = false
                        },
                        onDragEnd = {
                            val r = currentRight
                            val l = currentLeft
                            when {
                                offsetX >= thresholdPx && r != null -> r.onTrigger()
                                offsetX <= -thresholdPx && l != null -> l.onTrigger()
                            }
                            val from = offsetX
                            settleJob = scope.launch {
                                settle.snapTo(from)
                                settle.animateTo(0f, tween(220)) { offsetX = value }
                            }
                        },
                        onDragCancel = {
                            val from = offsetX
                            settleJob = scope.launch {
                                settle.snapTo(from)
                                settle.animateTo(0f, tween(220)) { offsetX = value }
                            }
                        },
                    ) { _, dx ->
                        val lo = if (currentLeft != null) -maxReachPx else 0f
                        val hi = if (currentRight != null) maxReachPx else 0f
                        offsetX = (offsetX + dx).coerceIn(lo, hi)
                        // Threshold haptic with hysteresis: arm on cross, only
                        // re-arm below 70% so the boundary doesn't buzz-storm.
                        val over = abs(offsetX) >= thresholdPx
                        if (over && !crossed) {
                            crossed = true
                            if (android.os.Build.VERSION.SDK_INT >= 34) {
                                view.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE,
                                )
                            } else {
                                view.performHapticFeedback(
                                    android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                )
                            }
                        } else if (crossed && abs(offsetX) < thresholdPx * 0.7f) {
                            crossed = false
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }
                },
            content = content,
        )
    }
}

/**
 * Floating "Task deleted · Undo" pill shown above the tab bar while a swipe
 * action can be reverted.
 */
@Composable
fun UndoBanner(label: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalBento.current
    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(220)) }
    Row(
        modifier
            .graphicsLayer {
                alpha = enter.value
                translationY = (1f - enter.value) * 16.dp.toPx()
            }
            .background(c.tile, RoundedCornerShape(14.dp))
            .border(1.dp, c.bd, RoundedCornerShape(14.dp))
            .padding(start = 16.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.W500,
            color = c.tx,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        // Clickable wraps the padding so the whole padded area is tappable
        // (~48dp target); the label area is inert — expiry handles dismissal.
        Text(
            "Undo",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.W700,
            color = c.acc,
            modifier = Modifier
                .pressable(onClick = onUndo)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

/** Select field backed by a dropdown menu (replaces the prototype's <select>). */
@Composable
fun <T> BentoSelectField(
    value: T,
    options: List<Pair<String, T>>,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val c = LocalBento.current
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        FieldButton(
            options.firstOrNull { it.second == value }?.first ?: "—",
            onClick = { open = true },
            compact = compact,
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (label, v) ->
                DropdownMenuItem(
                    text = { Text(label, fontSize = 13.sp, color = if (v == value) c.acc else c.tx) },
                    onClick = {
                        onSelect(v)
                        open = false
                    },
                )
            }
        }
    }
}
