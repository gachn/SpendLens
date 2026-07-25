package com.spendlens.app.feedback

import com.spendlens.app.parser.model.SmsMessage
import com.spendlens.app.data.db.PatternSource

/**
 * Result of a manual correction by the user.
 * Contains information about what was changed and how to apply learning.
 */
data class ManualCorrection(
    val originalMessage: SmsMessage,
    val correctedData: CorrectedData,
    val feedbackType: FeedbackType,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Types of manual corrections users can make.
 */
enum class FeedbackType {
    CATEGORY_CHANGE,
    MERCHANT_NAME_CORRECTION,
    AMOUNT_CORRECTION,
    ACCOUNT_CORRECTION,
    DIRECTION_CORRECTION,
    TIMESTAMP_CORRECTION,
    PATTERN_CREATION,
    DUPLICATE_RESOLUTION,
    NOT_A_TRANSACTION,
    SPLIT_TRANSACTION
}

/**
 * Corrected transaction data from user feedback.
 */
data class CorrectedData(
    val categoryId: Long? = null,
    val merchantName: String? = null,
    val amountMinor: Long? = null,
    val accountKey: String? = null,
    val direction: com.spendlens.app.parser.model.TxnDirection? = null,
    val occurredAt: Long? = null,
    val counterparty: String? = null,
    val referenceId: String? = null,
    val channel: com.spendlens.app.parser.model.Channel? = null
)

/**
 * Suggestion for pattern improvement based on user corrections.
 */
data class PatternSuggestion(
    val patternName: String,
    val bodyRegex: String,
    val senderRegex: String?,
    val confidence: Float,
    val source: com.spendlens.app.data.db.PatternSource = com.spendlens.app.data.db.PatternSource.USER,
    val reasoning: String
)

/**
 * Result of applying learning from user feedback.
 */
data class LearningResult(
    val patternCreated: Boolean = false,
    val categoryRuleCreated: Boolean = false,
    val merchantAliasCreated: Boolean = false,
    val patternUpdated: Boolean = false,
    val suggestions: List<PatternSuggestion> = emptyList(),
    val affectedFutureSms: Int = 0
)

/**
 * Learning engine that processes user corrections to improve future parsing.
 */
interface FeedbackLearningEngine {
    
    /**
     * Process a manual correction and apply learning.
     */
    suspend fun processCorrection(correction: ManualCorrection): LearningResult
    
    /**
     * Analyze corrections to suggest pattern improvements.
     */
    suspend fun analyzeAndSuggest(correction: ManualCorrection): List<PatternSuggestion>
    
    /**
     * Apply a suggested pattern improvement.
     */
    suspend fun applySuggestion(suggestion: PatternSuggestion, originalMessage: SmsMessage): Boolean
    
    /**
     * Get learning statistics.
     */
    suspend fun getLearningStats(): LearningStats
}

/**
 * Statistics about learning effectiveness.
 */
data class LearningStats(
    val totalCorrectionsProcessed: Int = 0,
    val patternsCreatedFromFeedback: Int = 0,
    val categoryRulesCreated: Int = 0,
    val merchantAliasesCreated: Int = 0,
    val parsingImprovementRate: Float = 0f,
    val userSatisfactionRate: Float = 0f
)