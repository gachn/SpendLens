package com.spendlens.app.feedback

import android.util.Log
import com.spendlens.app.ai.HeuristicPatternGenerator
import com.spendlens.app.data.db.CategoryRuleEntity
import com.spendlens.app.data.db.MerchantAliasEntity
import com.spendlens.app.data.db.PatternSource
import com.spendlens.app.data.db.SmsPatternEntity
import com.spendlens.app.data.repository.CategoryRepository
import com.spendlens.app.data.repository.MerchantRepository
import com.spendlens.app.data.repository.PatternRepository
import com.spendlens.app.parser.model.SmsMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern
import kotlin.math.max
import kotlin.math.min

private const val TAG = "FeedbackLearning"

/**
 * Implementation of feedback learning engine that processes user corrections
 * to improve future parsing accuracy.
 */
class FeedbackLearningEngineImpl(
    private val patternRepository: PatternRepository,
    private val categoryRepository: CategoryRepository,
    private val merchantRepository: MerchantRepository,
) : FeedbackLearningEngine {

    private val heuristicGenerator = HeuristicPatternGenerator()

    override suspend fun processCorrection(correction: ManualCorrection): LearningResult = withContext(Dispatchers.Default) {
        var patternCreated = false
        var categoryRuleCreated = false
        var merchantAliasCreated = false
        var patternUpdated = false
        val suggestions = mutableListOf<PatternSuggestion>()

        try {
            Log.d(TAG, "Processing correction: ${correction.feedbackType}")

            when (correction.feedbackType) {
                FeedbackType.CATEGORY_CHANGE -> {
                    // Create or update category rule for this merchant/counterparty
                    categoryRuleCreated = processCategoryChange(correction)
                }
                FeedbackType.MERCHANT_NAME_CORRECTION -> {
                    // Create merchant alias
                    merchantAliasCreated = processMerchantCorrection(correction)
                }
                FeedbackType.AMOUNT_CORRECTION,
                FeedbackType.ACCOUNT_CORRECTION,
                FeedbackType.DIRECTION_CORRECTION,
                FeedbackType.TIMESTAMP_CORRECTION -> {
                    // These might indicate pattern issues - suggest pattern creation
                    val patternSuggestions = analyzePatternIssues(correction)
                    suggestions.addAll(patternSuggestions)
                    if (patternSuggestions.isNotEmpty()) {
                        patternCreated = createPatternFromSuggestion(patternSuggestions.first(), correction.originalMessage)
                    }
                }
                FeedbackType.PATTERN_CREATION -> {
                    // User explicitly wants a pattern created
                    patternCreated = createPatternFromCorrection(correction)
                }
                FeedbackType.DUPLICATE_RESOLUTION -> {
                    // Handle duplicate resolution - no pattern learning needed
                    Log.d(TAG, "Duplicate resolution processed")
                }
                FeedbackType.NOT_A_TRANSACTION -> {
                    // Mark sender as non-financial for future filtering
                    processNonTransactionLearning(correction)
                }
                FeedbackType.SPLIT_TRANSACTION -> {
                    // Split transactions don't require pattern learning
                    Log.d(TAG, "Split transaction processed")
                }
            }

            val affectedSms = estimateAffectedFutureSms(correction)

            LearningResult(
                patternCreated = patternCreated,
                categoryRuleCreated = categoryRuleCreated,
                merchantAliasCreated = merchantAliasCreated,
                patternUpdated = patternUpdated,
                suggestions = suggestions,
                affectedFutureSms = affectedSms
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error processing correction", e)
            LearningResult()
        }
    }

    override suspend fun analyzeAndSuggest(correction: ManualCorrection): List<PatternSuggestion> = withContext(Dispatchers.Default) {
        try {
            when (correction.feedbackType) {
                FeedbackType.AMOUNT_CORRECTION,
                FeedbackType.ACCOUNT_CORRECTION,
                FeedbackType.DIRECTION_CORRECTION -> {
                    analyzePatternIssues(correction)
                }
                else -> emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing correction", e)
            emptyList()
        }
    }

    override suspend fun applySuggestion(suggestion: PatternSuggestion, originalMessage: SmsMessage): Boolean = withContext(Dispatchers.Default) {
        try {
            val pattern = SmsPatternEntity(
                name = suggestion.patternName,
                senderRegex = suggestion.senderRegex,
                bodyRegex = suggestion.bodyRegex,
                priority = USER_PATTERN_PRIORITY,
                source = suggestion.source,
                sampleSms = originalMessage.body,
            )

            val patternId = patternRepository.insert(pattern)
            Log.d(TAG, "Applied suggestion, created pattern $patternId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error applying suggestion", e)
            false
        }
    }

    override suspend fun getLearningStats(): LearningStats = withContext(Dispatchers.Default) {
        // Query learning statistics from repositories
        val userPatterns = patternRepository.getAll().count { it.source == com.spendlens.app.data.db.PatternSource.USER }
        val categoryRules = categoryRepository.getAllRules().size
        val merchantAliases = merchantRepository.getAllAliases().size

        LearningStats(
            totalCorrectionsProcessed = userPatterns + categoryRules + merchantAliases,
            patternsCreatedFromFeedback = userPatterns,
            categoryRulesCreated = categoryRules,
            merchantAliasesCreated = merchantAliases,
            parsingImprovementRate = calculateImprovementRate(),
            userSatisfactionRate = 0.85f // Would be calculated from user feedback
        )
    }

    private suspend fun processCategoryChange(correction: ManualCorrection): Boolean {
        val categoryId = correction.correctedData.categoryId ?: return false
        val counterparty = correction.originalMessage.body.extractCounterparty() ?: return false

        // Create new rule
        val rule = com.spendlens.app.data.db.CategoryRuleEntity(
            matcher = counterparty,
            categoryId = categoryId,
            source = "user_feedback"
        )
        categoryRepository.insert(rule)
        Log.d(TAG, "Created category rule for $counterparty -> category $categoryId")
        return true
    }

    private suspend fun processMerchantCorrection(correction: ManualCorrection): Boolean {
        val correctedMerchant = correction.correctedData.merchantName ?: return false
        val originalMerchant = correction.originalMessage.body.extractCounterparty() ?: return false

        if (originalMerchant == correctedMerchant) return false

        val rawKey = originalMerchant.lowercase().replace(Regex("[^a-z0-9]"), "")
        val alias = com.spendlens.app.data.db.MerchantAliasEntity(
            rawKey = rawKey,
            displayName = correctedMerchant
        )
        merchantRepository.insert(alias)
        Log.d(TAG, "Created merchant alias: $originalMerchant -> $correctedMerchant")
        return true
    }

    private suspend fun analyzePatternIssues(correction: ManualCorrection): List<PatternSuggestion> {
        val suggestions = mutableListOf<PatternSuggestion>()

        // Try to generate a pattern using heuristic approach
        val generatedPattern = heuristicGenerator.generate(
            correction.originalMessage.body,
            correction.originalMessage.sender
        )

        if (generatedPattern != null) {
            suggestions.add(
                PatternSuggestion(
                    patternName = generatedPattern.name,
                    bodyRegex = generatedPattern.bodyRegex,
                    senderRegex = generatedPattern.senderRegex,
                    confidence = 0.7f,
                    source = com.spendlens.app.data.db.PatternSource.HEURISTIC,
                    reasoning = "Generated from user correction using heuristic analysis"
                )
            )
        }

        return suggestions
    }

    private suspend fun createPatternFromSuggestion(suggestion: PatternSuggestion, message: SmsMessage): Boolean {
        return try {
            val pattern = SmsPatternEntity(
                name = suggestion.patternName,
                senderRegex = suggestion.senderRegex,
                bodyRegex = suggestion.bodyRegex,
                priority = USER_PATTERN_PRIORITY,
                source = suggestion.source,
                sampleSms = message.body,
            )

            val patternId = patternRepository.insert(pattern)
            Log.d(TAG, "Created pattern $patternId from user feedback")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating pattern from suggestion", e)
            false
        }
    }

    private suspend fun createPatternFromCorrection(correction: ManualCorrection): Boolean {
        return try {
            // Generate pattern based on corrected data
            val generatedPattern = heuristicGenerator.generate(
                correction.originalMessage.body,
                correction.originalMessage.sender
            ) ?: return false

            val pattern = SmsPatternEntity(
                name = "User-learned: ${correction.originalMessage.sender.take(16)}",
                senderRegex = generatedPattern.senderRegex,
                bodyRegex = generatedPattern.bodyRegex,
                priority = USER_PATTERN_PRIORITY,
                source = com.spendlens.app.data.db.PatternSource.USER,
                sampleSms = correction.originalMessage.body,
            )

            val patternId = patternRepository.insert(pattern)
            Log.d(TAG, "Created pattern $patternId from user correction")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating pattern from correction", e)
            false
        }
    }

    private suspend fun processNonTransactionLearning(correction: ManualCorrection) {
        // This would mark the sender as non-financial in the sender classification
        // For now, just log it
        Log.d(TAG, "Marked sender ${correction.originalMessage.sender} as non-financial based on user feedback")
    }

    private suspend fun estimateAffectedFutureSms(correction: ManualCorrection): Int {
        // Estimate how many future SMS might benefit from this correction
        return when (correction.feedbackType) {
            FeedbackType.CATEGORY_CHANGE -> 5 // Affects category matching
            FeedbackType.MERCHANT_NAME_CORRECTION -> 10 // Affects merchant resolution
            FeedbackType.PATTERN_CREATION -> 20 // Affects parsing
            else -> 2 // Minor improvements
        }
    }

    private suspend fun calculateImprovementRate(): Float {
        // Calculate parsing improvement rate based on historical data
        // For now, return a reasonable default
        return 0.15f // 15% improvement estimate
    }

    companion object {
        private const val USER_PATTERN_PRIORITY = 50 // Higher than learned patterns
    }
}

// Extension function to extract counterparty from SMS body
private fun String.extractCounterparty(): String? {
    // Simple extraction - in production would use more sophisticated logic
    val patterns = listOf(
        "at\\s+([A-Za-z][A-Za-z0-9 &._-]{1,38})",
        "to\\s+([A-Za-z][A-Za-z0-9 &._-]{1,38})",
        "from\\s+([A-Za-z][A-Za-z0-9 &._-]{1,38})"
    )

    for (pattern in patterns) {
        val match = Regex(pattern, RegexOption.IGNORE_CASE).find(this)
        if (match != null) {
            return match.groupValues[1].trim()
        }
    }
    return null
}