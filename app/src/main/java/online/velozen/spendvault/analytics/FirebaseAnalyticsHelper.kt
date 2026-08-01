package online.velozen.spendvault.analytics

import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.logEvent
import com.google.firebase.ktx.Firebase

object FirebaseAnalyticsHelper {
    
    private val analytics: FirebaseAnalytics = Firebase.analytics
    
    fun logSmsReceived(success: Boolean, categoryCount: Int = 0) {
        analytics.logEvent("sms_received") {
            param("success", success)
            param("category_count", categoryCount.toLong())
        }
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
        analytics.logEvent("ai_categorization_attempt") {
            param("success", success)
            param("model", model)
        }
    }
    
    fun logSyncResult(patternsDownloaded: Int, patternsUploaded: Int, durationMs: Long) {
        analytics.logEvent("firebase_sync_result") {
            param("patterns_downloaded", patternsDownloaded.toLong())
            param("patterns_uploaded", patternsUploaded.toLong())
            param("duration_ms", durationMs)
        }
    }
}
