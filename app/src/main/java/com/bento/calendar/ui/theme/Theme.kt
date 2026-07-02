package com.bento.calendar.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.bento.calendar.data.Prefs

@Composable
fun BentoTheme(prefs: Prefs, content: @Composable () -> Unit) {
    val base = if (prefs.theme == "light") LightColors else DarkColors
    val colors = base.copy(acc = hexColor(prefs.accent))

    // Material3 scheme mapped onto the design tokens so the few M3 pieces we
    // use (date/time pickers, dropdown menus) match the app.
    val scheme = (if (colors.isLight) lightColorScheme() else darkColorScheme()).copy(
        primary = colors.acc,
        onPrimary = Color.White,
        secondary = colors.acc,
        background = colors.bg,
        onBackground = colors.tx,
        surface = colors.tile,
        onSurface = colors.tx,
        surfaceVariant = colors.inp,
        onSurfaceVariant = colors.sub,
        surfaceContainer = colors.tile,
        surfaceContainerHigh = colors.tile,
        surfaceContainerHighest = colors.inp,
        surfaceContainerLow = colors.tile,
        outline = colors.bd,
        outlineVariant = colors.line,
        error = colors.dng,
        primaryContainer = colors.acc,
        onPrimaryContainer = Color.White,
        secondaryContainer = colors.acc.copy(alpha = 0.16f),
        onSecondaryContainer = colors.tx,
        tertiaryContainer = colors.inp,
        onTertiaryContainer = colors.tx,
    )

    val baseStyle = TextStyle(fontFamily = Sora, color = colors.tx, fontSize = 13.sp)
    val typography = Typography().run {
        copy(
            displayLarge = displayLarge.copy(fontFamily = Sora),
            displayMedium = displayMedium.copy(fontFamily = Sora),
            displaySmall = displaySmall.copy(fontFamily = Sora),
            headlineLarge = headlineLarge.copy(fontFamily = Sora),
            headlineMedium = headlineMedium.copy(fontFamily = Sora),
            headlineSmall = headlineSmall.copy(fontFamily = Sora),
            titleLarge = titleLarge.copy(fontFamily = Sora),
            titleMedium = titleMedium.copy(fontFamily = Sora),
            titleSmall = titleSmall.copy(fontFamily = Sora),
            bodyLarge = bodyLarge.copy(fontFamily = Sora),
            bodyMedium = bodyMedium.copy(fontFamily = Sora),
            bodySmall = bodySmall.copy(fontFamily = Sora),
            labelLarge = labelLarge.copy(fontFamily = Sora),
            labelMedium = labelMedium.copy(fontFamily = Sora),
            labelSmall = labelSmall.copy(fontFamily = Sora),
        )
    }

    val selection = TextSelectionColors(
        handleColor = colors.acc,
        backgroundColor = colors.acc.copy(alpha = 0.35f),
    )

    MaterialTheme(colorScheme = scheme, typography = typography) {
        CompositionLocalProvider(
            LocalBento provides colors,
            LocalContentColor provides colors.tx,
            LocalTextStyle provides baseStyle,
            LocalTextSelectionColors provides selection,
            content = content,
        )
    }
}
