package online.velozen.spendvault.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DebugAnalyticsStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("debug_analytics_prefs", Context.MODE_PRIVATE)

    private companion object {
        const val KEY_LAST_ANALYTICS_HASH = "last_analytics_hash"
        const val KEY_LAST_ANALYTICS_TIMESTAMP = "last_analytics_timestamp"
        const val KEY_LAST_ANALYTICS_DATA = "last_analytics_data"
        const val DEFAULT_SYNC_PERIOD_HOURS = 5L
    }

    fun getLastAnalyticsTimestamp(): Long {
        return prefs.getLong(KEY_LAST_ANALYTICS_TIMESTAMP, 0L)
    }

    fun setLastAnalyticsTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_ANALYTICS_TIMESTAMP, timestamp).apply()
    }

    fun getLastAnalyticsHash(): String {
        return prefs.getString(KEY_LAST_ANALYTICS_HASH, "") ?: ""
    }

    fun setLastAnalyticsHash(hash: String) {
        prefs.edit().putString(KEY_LAST_ANALYTICS_HASH, hash).apply()
    }

    fun getLastAnalyticsData(): String {
        return prefs.getString(KEY_LAST_ANALYTICS_DATA, "") ?: ""
    }

    fun setLastAnalyticsData(data: String) {
        prefs.edit().putString(KEY_LAST_ANALYTICS_DATA, data).apply()
    }

    fun clearLastAnalytics() {
        prefs.edit()
            .remove(KEY_LAST_ANALYTICS_HASH)
            .remove(KEY_LAST_ANALYTICS_TIMESTAMP)
            .remove(KEY_LAST_ANALYTICS_DATA)
            .apply()
    }

    fun getDefaultSyncPeriodHours(): Long {
        return DEFAULT_SYNC_PERIOD_HOURS
    }
}
