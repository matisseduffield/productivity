package com.bento.calendar.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bridge the app's data mutations to the home-screen widget. The ViewModel
 * calls [push] after widget-relevant store updates (events, open-task count,
 * theme prefs — gated by its WidgetSnapshot projection).
 */
object WidgetSync {
    fun push(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            // runCatching: with no widget placed (or a Glance internal hiccup)
            // an update must never take the app down.
            runCatching { BentoWidget().updateAll(app) }
        }
    }
}
