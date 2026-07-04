package com.bento.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * Manifest entry point for the Glance home-screen widget. Also keeps the
 * [MidnightTickReceiver] date-rollover alarm armed while any widget is
 * placed: onEnabled covers first placement, onUpdate re-arms after reboot
 * (alarms do not survive one, but the boot-time APPWIDGET_UPDATE does) and
 * onDisabled cancels it when the last widget is removed.
 */
class BentoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BentoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        MidnightTickReceiver.arm(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        MidnightTickReceiver.arm(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        MidnightTickReceiver.cancel(context)
    }
}
