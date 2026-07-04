package com.bento.calendar.widget

import androidx.glance.appwidget.GlanceAppWidget

/**
 * Manifest entry point for the agenda widget. Midnight-alarm lifecycle lives
 * in [BentoFamilyReceiver], shared by every widget in the family.
 */
class BentoWidgetReceiver : BentoFamilyReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BentoWidget()
}
