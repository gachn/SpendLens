package com.spendlens.app.util

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.logEvent

/**
 * Firebase crash reporting and analytics helper.
 * Provides centralized access to Crashlytics for crash reporting and Analytics for event logging.
 */
object FirebaseHelper {

    private var crashlytics: FirebaseCrashlytics? = null
    private var analytics: FirebaseAnalytics? = null

    fun initialize(context: Context) {
        crashlytics = FirebaseCrashlytics.getInstance()
        analytics = FirebaseAnalytics.getInstance(context)
    }

    fun setUserId(userId: String?) {
        crashlytics?.setUserId(userId ?: "")
        analytics?.setUserId(userId ?: "")
    }

    fun setCustomKey(key: String, value: String) {
        crashlytics?.setCustomKey(key, value)
    }

    fun logEvent(eventName: String, params: Map<String, Any?> = emptyMap()) {
        val bundle = android.os.Bundle().apply {
            params.forEach { (k, v) ->
                when (v) {
                    is String -> putString(k, v)
                    is Long -> putLong(k, v)
                    is Double -> putDouble(k, v)
                    is Float -> putDouble(k, v.toDouble())
                    is Int -> putLong(k, v.toLong())
                    is Boolean -> putBoolean(k, v)
                }
            }
        }
        analytics?.logEvent(eventName, bundle)
    }

    fun recordException(throwable: Throwable) {
        crashlytics?.recordException(throwable)
    }

    fun log(message: String) {
        crashlytics?.log(message)
    }

    fun setCrashlyticsCollectionEnabled(enabled: Boolean) {
        crashlytics?.setCrashlyticsCollectionEnabled(enabled)
    }

    fun setAnalyticsCollectionEnabled(enabled: Boolean) {
        analytics?.setAnalyticsCollectionEnabled(enabled)
    }
}