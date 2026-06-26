package com.spendlens.app.ai

import com.spendlens.app.data.db.TransactionEntity
import kotlin.math.abs

data class RecurringPattern(
    val merchant: String,
    val averageAmountMinor: Long,
    val frequency: String, // DAILY, WEEKLY, MONTHLY
    val confidence: Float, // 0.0 to 1.0
    val count: Int,
    val averageDaysBetween: Float,
    val estimatedMonthlyMinor: Long,
)

/**
 * Detect recurring transactions (subscriptions, regular bills, gym memberships, etc).
 * Pure pattern matching without AI — works entirely offline.
 */
object RecurringDetector {

    /**
     * Detect recurring patterns in transactions.
     * Returns merchants with regular payment patterns.
     */
    fun detectRecurring(
        transactions: List<TransactionEntity>,
        minOccurrences: Int = 3,
    ): List<RecurringPattern> {
        val recurring = mutableListOf<RecurringPattern>()

        // Group by merchant
        transactions
            .filter { it.direction == "DEBIT" && !it.excludedFromExpense }
            .groupBy { it.counterparty }
            .forEach { (merchant, txns) ->
                if (txns.size < minOccurrences) return@forEach

                val sorted = txns.sortedBy { it.occurredAt }
                val intervals = mutableListOf<Long>()

                for (i in 1 until sorted.size) {
                    val daysBetween = (sorted[i].occurredAt - sorted[i - 1].occurredAt) / (24 * 60 * 60 * 1000)
                    intervals.add(daysBetween)
                }

                if (intervals.isEmpty()) return@forEach

                val avgInterval = intervals.average().toFloat()
                val avgAmount = txns.map { it.amountBaseMinor }.average().toLong()

                // Determine frequency and confidence based on interval regularity
                val intervalStdDev = calculateStdDev(intervals, avgInterval.toDouble())
                val variance = intervalStdDev / avgInterval.toDouble()
                val confidence = (1f - (variance / 3f).toFloat()).coerceAtLeast(0f) // Lower variance = higher confidence

                val (frequency, estimatedMonthly) = when {
                    avgInterval < 2 -> "DAILY" to avgAmount * 30
                    avgInterval < 10 -> "WEEKLY" to avgAmount * 4
                    avgInterval < 40 -> "MONTHLY" to avgAmount
                    avgInterval < 370 -> "YEARLY" to avgAmount
                    else -> "UNKNOWN" to 0L
                }

                if (confidence > 0.4f && frequency != "UNKNOWN") {
                    recurring.add(
                        RecurringPattern(
                            merchant = merchant,
                            averageAmountMinor = avgAmount,
                            frequency = frequency,
                            confidence = confidence.coerceIn(0f, 1f),
                            count = txns.size,
                            averageDaysBetween = avgInterval,
                            estimatedMonthlyMinor = estimatedMonthly,
                        )
                    )
                }
            }

        return recurring.sortedByDescending { it.confidence }
    }

    /**
     * Check if a transaction looks like a new recurring pattern.
     * Returns true if the merchant matches a known pattern but this is a first/early transaction.
     */
    fun isNewRecurringAlert(
        transaction: TransactionEntity,
        knownRecurring: List<RecurringPattern>,
    ): Boolean {
        return knownRecurring.any { pattern ->
            transaction.counterparty.equals(pattern.merchant, ignoreCase = true) &&
                abs(transaction.amountBaseMinor - pattern.averageAmountMinor) < (pattern.averageAmountMinor * 0.2f)
        }
    }

    /**
     * Calculate total monthly subscription costs.
     */
    fun calculateTotalSubscriptionCost(patterns: List<RecurringPattern>): Long {
        return patterns.sumOf { it.estimatedMonthlyMinor }
    }

    private fun calculateStdDev(values: List<Long>, mean: Double): Double {
        if (values.isEmpty()) return 0.0
        val variance = values.map { val x = it.toDouble() - mean; x * x }.average()
        return kotlin.math.sqrt(variance)
    }
}
