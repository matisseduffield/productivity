package com.bento.calendar.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.bento.calendar.R

/** Sora variable font, instanced at the four weights the design uses. */
val Sora = FontFamily(
    Font(R.font.sora, weight = FontWeight.W400, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.sora, weight = FontWeight.W500, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.sora, weight = FontWeight.W600, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.sora, weight = FontWeight.W700, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)
