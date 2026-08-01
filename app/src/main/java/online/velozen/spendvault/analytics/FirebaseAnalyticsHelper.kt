package online.velozen.spendvault.analytics

import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.ktx.Firebase

object FirebaseAnalyticsHelper {
    
    private val analytics: FirebaseAnalytics = Firebase.analytics
    
    fun logSmsReceived(success: Boolean, categoryCount: Int = 0) {
        val params = Bundle().apply {
            putBoolean("success", success)
            putLong("category_count", categoryCount.toLong())
        }
        analytics.logEvent("sms_received", params)
    }
    
    fun logPatternMatched(source: String, patternId: Long) {
        analytics.logEvent("pattern_matched") {
            param("source", source)
            param("pattern_id", patternId)
        }
    }
    
    fun logTransactionCreated(amountMinor: Long, currency: String, categoryId: Long?) {
        analytics.logEvent("transaction_created") {
            param("amount_minor", amountMinor)
            param("currency", currency)
            param("category_id", categoryId ?: -1L)
        }
    }
    
    fun logAiCategorizationAttempt(success: Boolean, model: String) {
        val params = Bundle().apply {
            putBoolean("success", success)
            putString("model", model)
        }
        analytics.logEvent("ai_categorization_attempt", params)
    }
    
    fun logSyncResult(patternsDownloaded: Int, patternsUploaded: Int, durationMs: Long) {
        val params = Bundle().apply {
            putLong("patterns_downloaded", patternsDownloaded.toLong())
            putLong("patterns_uploaded", patternsUploaded.toLong())
            putLong("duration_ms", durationMs)
        }
        analytics.logEvent("firebase_sync_result", params)
    }
}
