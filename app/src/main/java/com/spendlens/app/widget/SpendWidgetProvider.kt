package com.spendlens.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.spendlens.app.work.WidgetRefreshWorker

/** 2×1 home-screen widget: current month spend vs total budget with a progress bar. */
class SpendWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRefreshWorker.enqueue(context)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        // Leave the periodic worker running — CategoryWidget may still be active.
    }
}
