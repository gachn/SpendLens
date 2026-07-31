package com.spendlens.app.analytics

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import com.spendlens.app.config.RemoteConfigManager
import com.spendlens.app.data.prefs.DebugAnalyticsStore
import com.spendlens.app.data.prefs.DebugCounts
import com.spendlens.app.data.prefs.PatternSource
import com.spendlens.app.data.prefs.RawStatus
import com.spendlens.app.data.repository.PatternRepository
import com.spendlens.app.sms.SmsProcessingStats
import com.spendlens.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class DebugAnalyticsData(
    val totalRawSms: Int,
    val parsedCount: Int,
    val unparsedCount: Int,
    val ignoredCount: Int,
    val pendingAiCount: Int,
    val aiParsedCount: Int,
    val aiPatternParsedCount: Int,
    val totalTransactions: Int,
    val duplicateTransactions: Int,
    val patternBuiltin: Int,
    val patternAi: Int,
    val patternHeuristic: Int,
    val patternUser: Int,
    val patternFirebase: Int,
    val aiBatchCount: Int,
    val aiBatchLastSmsCount: Int,
    val aiBatchTotalMs: Long,
    val regexRunCount: Int,
    val regexLastSmsCount: Int,
    val regexTotalMs: Long,
    val timestamp: Long = System.currentTimeMillis()
)

class DebugAnalyticsManager(
    private val context: Context,
    private val remoteConfig: RemoteConfigManager,
    private val analyticsStore: DebugAnalyticsStore,
    private val patternRepository: PatternRepository,
) {
    
    companion object {
        private const val TAG = "DebugAnalytics"
        private const val DEBUG_ANALYTICS_USER_PROPERTY = "debug_analytics_data"
    }
    
    private val json = Json { ignoreUnknownKeys = true }
    private val firebaseAnalytics: FirebaseAnalytics = Firebase.analytics
    
    suspend fun syncDebugAnalytics(
        debugCounts: DebugCounts,
        processingStats: SmsProcessingStats,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val syncPeriodMs = remoteConfig.getDebugAnalyticsSyncPeriodHours() * 60 * 60 * 1000
                val lastSyncTime = analyticsStore.getLastAnalyticsTimestamp()
                val timeSinceLastSync = System.currentTimeMillis() - lastSyncTime
                
                if (timeSinceLastSync < syncPeriodMs) {
                    AppLog.d(TAG, "Skipping sync: last sync was ${timeSinceLastSync}ms ago, period is ${syncPeriodMs}ms")
                    return@withContext
                }
                
                val currentData = DebugAnalyticsData(
                    totalRawSms = debugCounts.totalRawSms,
                    parsedCount = debugCounts.parsedCount,
                    unparsedCount = debugCounts.unparsedCount,
                    ignoredCount = debugCounts.ignoredCount,
                    pendingAiCount = debugCounts.pendingAiCount,
                    aiParsedCount = debugCounts.aiParsedCount,
                    aiPatternParsedCount = debugCounts.aiPatternParsedCount,
                    totalTransactions = debugCounts.totalTransactions,
                    duplicateTransactions = debugCounts.duplicateTransactions,
                    patternBuiltin = debugCounts.patternBuiltin,
                    patternAi = debugCounts.patternAi,
                    patternHeuristic = debugCounts.patternHeuristic,
                    patternUser = debugCounts.patternUser,
                    patternFirebase = debugCounts.patternFirebase,
                    aiBatchCount = processingStats.aiBatchCount,
                    aiBatchLastSmsCount = processingStats.aiBatchLastSmsCount,
                    aiBatchTotalMs = processingStats.aiBatchTotalMs,
                    regexRunCount = processingStats.regexRunCount,
                    regexLastSmsCount = processingStats.regexLastSmsCount,
                    regexTotalMs = processingStats.regexTotalMs
                )
                
                val currentDataJson = json.encodeToString(currentData)
                val currentHash = generateHash(currentDataJson)
                val lastHash = analyticsStore.getLastAnalyticsHash()
                
                if (currentHash == lastHash) {
                    AppLog.d(TAG, "Data unchanged since last sync, skipping")
                    return@withContext
                }
                
                uploadToFirebase(currentDataJson)
                
                analyticsStore.setLastAnalyticsHash(currentHash)
                analyticsStore.setLastAnalyticsTimestamp(System.currentTimeMillis())
                analyticsStore.setLastAnalyticsData(currentDataJson)
                
                AppLog.i(TAG, "Debug analytics synced successfully")
                
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to sync debug analytics: ${e.message}", e)
            }
        }
    }
    
    private fun uploadToFirebase(dataJson: String) {
        try {
            val compressedData = compressData(dataJson)
            
            firebaseAnalytics.setUserProperty(DEBUG_ANALYTICS_USER_PROPERTY, compressedData)
            
            firebaseAnalytics.logEvent("debug_analytics_sync") {
                param("success", true)
                param("data_size", dataJson.length)
            }
            
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to upload to Firebase: ${e.message}", e)
            throw e
        }
    }
    
    private fun compressData(data: String): String {
        return data.take(100) + "... [${data.length} chars]"
    }
    
    private fun generateHash(data: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(data.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            AppLog.e(TAG, "Failed to generate hash: ${e.message}", e)
            data.hashCode().toString()
        }
    }
    
    suspend fun clearDebugAnalytics() {
        withContext(Dispatchers.IO) {
            try {
                firebaseAnalytics.setUserProperty(DEBUG_ANALYTICS_USER_PROPERTY, null)
                analyticsStore.clearLastAnalytics()
                AppLog.i(TAG, "Debug analytics cleared")
            } catch (e: Exception) {
                AppLog.e(TAG, "Failed to clear debug analytics: ${e.message}", e)
            }
        }
    }
    
    fun getLastSyncTime(): Long {
        return analyticsStore.getLastAnalyticsTimestamp()
    }
}