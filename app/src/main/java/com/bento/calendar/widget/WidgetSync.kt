package com.bento.calendar.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Bridge the app's data mutations to the home-screen widget family. The
 * ViewModel calls [push] after widget-relevant store updates (events, tasks,
 * notes, theme prefs — gated by its WidgetSnapshot projection).
 */
object WidgetSync {
    fun push(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            pushNow(app)
        }
    }

    /** Update every family widget; per-widget runCatching so one Glance
     *  hiccup (or no placement) never takes the rest — or the app — down. */
    suspend fun pushNow(context: Context) {
        val app = context.applicationContext
        familyWidgets().forEach { widget ->
            runCatching { widget.updateAll(app) }
        }
    }
}
