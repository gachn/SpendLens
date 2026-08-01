package com.spendlens.app.data.prefs

import android.content.Context
import java.util.concurrent.TimeUnit

/**
 * Remembers when a spending-velocity alert last fired for each category so the daily worker
 * can throttle to at most one notification per category per week (issue #3).
 *
 * Backed by plain [android.content.SharedPreferences] — these timestamps are non-sensitive.
 */
class VelocityAlertStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("spendlens_velocity_alerts", Context.MODE_PRIVATE)

    /** True when no alert for [categoryId] has fired within the last [WEEK_MILLIS]. */
    fun canAlert(categoryId: Long, now: Long): Boolean {
        val last = prefs.getLong(key(categoryId), 0L)
        return now - last >= WEEK_MILLIS
    }

    /** Record that an alert for [categoryId] fired at [now]. */
    fun markAlerted(categoryId: Long, now: Long) {
        prefs.edit().putLong(key(categoryId), now).apply()
    }

    private fun key(categoryId: Long) = "cat_$categoryId"

    private companion object {
        val WEEK_MILLIS = TimeUnit.DAYS.toMillis(7)
    }
}
