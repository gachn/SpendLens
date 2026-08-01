package com.spendlens.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import com.spendlens.app.work.WidgetRefreshWorker

/** 4×2 home-screen widget: top 3 category spends for the current month. */
class CategoryWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WidgetRefreshWorker.enqueue(context)
    }

    override fun onEnabled(context: Context) {
        WidgetRefreshWorker.schedule(context)
    }

    override fun onDisabled(context: Context) {
        // Leave the periodic worker running — SpendWidget may still be active.
    }
}
