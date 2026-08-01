package com.spendlens.app.data.prefs

import android.content.Context

/**
 * Caches the Premium AI monthly spending recap so it survives navigating away from the Dashboard
 * and isn't silently re-generated (and re-billed) every time the screen recomposes — only an
 * explicit refresh or a new month invalidates it. Non-sensitive: plain [android.content.SharedPreferences].
 */
class InsightsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("spendlens_insights", Context.MODE_PRIVATE)

    data class CachedRecap(val monthKey: String, val text: String, val generatedAt: Long)

    /** The last generated recap, or null if none has been generated yet. */
    fun lastRecap(): CachedRecap? {
        val monthKey = prefs.getString(KEY_MONTH, null) ?: return null
        val text = prefs.getString(KEY_TEXT, null) ?: return null
        val generatedAt = prefs.getLong(KEY_GENERATED_AT, 0L)
        return CachedRecap(monthKey, text, generatedAt)
    }

    fun saveRecap(monthKey: String, text: String, generatedAt: Long) {
        prefs.edit()
            .putString(KEY_MONTH, monthKey)
            .putString(KEY_TEXT, text)
            .putLong(KEY_GENERATED_AT, generatedAt)
            .apply()
    }

    private companion object {
        const val KEY_MONTH = "recap_month"
        const val KEY_TEXT = "recap_text"
        const val KEY_GENERATED_AT = "recap_generated_at"
    }
}
