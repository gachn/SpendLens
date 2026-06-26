package com.spendlens.app.ai

import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity
import kotlin.math.abs
import kotlin.math.sqrt

data class AnomalyScore(
    val transactionId: Long,
    val score: Float, // 0.0 to 1.0, higher = more anomalous
    val severity: String, // LOW, MEDIUM, HIGH
    val reason: String, // Why this is anomalous
)

/**
 * Detect unusual spending patterns compared to user's baseline.
 * Pure statistical analysis — works entirely offline.
 */
object AnomalyDetector {

    /**
     * Score a transaction for anomalies.
     * Compares against historical patterns for merchant, category, amount, and frequency.
     */
    fun scoreAnomaly(
        transaction: TransactionEntity,
        allTransactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
    ): AnomalyScore? {
        if (transaction.direction != "DEBIT" || transaction.excludedFromExpense) return null

        val merchant = transaction.counterparty
        val category = transaction.categoryId
        val amount = transaction.amountBaseMinor

        val historicalTxns = allTransactions.filter {
            it.id != transaction.id && it.direction == "DEBIT" && !it.excludedFromExpense
        }

        val reasons = mutableListOf<String>()
        var anomalyScore = 0f

        // Check 1: Merchant anomaly (never shopped there before)
        val merchantHistory = historicalTxns.filter { it.counterparty == merchant }
        if (merchantHistory.isEmpty()) {
            reasons.add("New merchant")
            anomalyScore += 0.3f
        } else {
            // Check amount anomaly for known merchant
            val merchantAmounts = merchantHistory.map { it.amountBaseMinor }
            val avgAmount = merchantAmounts.average().toLong()
            val stdDev = calculateStdDev(merchantAmounts, avgAmount)

            if (stdDev > 0 && amount > avgAmount + 2 * stdDev) {
                reasons.add("Amount unusually high for this merchant")
                anomalyScore += 0.4f
            }
        }

        // Check 2: Category anomaly (rare in this category)
        if (category != null) {
            val categoryHistory = historicalTxns.filter { it.categoryId == category }
            if (categoryHistory.isEmpty()) {
                reasons.add("Rare category")
                anomalyScore += 0.2f
            } else if (categoryHistory.size < 3) {
                reasons.add("Infrequent category")
                anomalyScore += 0.1f
            }
        }

        // Check 3: Overall spending amount (is this user's largest transaction ever?)
        val maxHistorical = historicalTxns.maxOfOrNull { it.amountBaseMinor } ?: 0L
        if (amount > maxHistorical * 1.5f && amount > 50000) {
            reasons.add("Very high amount for this user")
            anomalyScore += 0.3f
        }

        // Check 4: Daily spending spike
        val today = transaction.occurredAt
        val todayStart = today - (today % (24 * 60 * 60 * 1000))
        val todayTransactions = historicalTxns.filter { it.occurredAt >= todayStart }
        val todayTotal = todayTransactions.sumOf { it.amountBaseMinor } + amount

        val dailyAvg = calculateDailyAverage(historicalTxns)
        if (todayTotal > dailyAvg * 2f) {
            reasons.add("Unusual daily spending")
            anomalyScore += 0.2f
        }

        if (anomalyScore == 0f) return null

        val severity = when {
            anomalyScore > 0.7f -> "HIGH"
            anomalyScore > 0.4f -> "MEDIUM"
            else -> "LOW"
        }

        return AnomalyScore(
            transactionId = transaction.id,
            score = anomalyScore.coerceIn(0f, 1f),
            severity = severity,
            reason = reasons.joinToString("; "),
        )
    }

    /**
     * Find all anomalies in transaction list.
     */
    fun detectAnomalies(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
    ): List<AnomalyScore> {
        return transactions
            .mapNotNull { txn -> scoreAnomaly(txn, transactions, categories) }
            .sortedByDescending { it.score }
    }

    /**
     * Mark a transaction as "normal for this user" to adjust baseline.
     * In real implementation, this would update user preferences.
     * For now, just return true to indicate acceptance.
     */
    fun markAsNormal(transactionId: Long): Boolean {
        // This would typically update a "user_anomaly_feedback" table
        // to train the baseline more accurately over time
        return true
    }

    private fun calculateStdDev(values: List<Long>, mean: Long): Double {
        if (values.size < 2) return 0.0
        val variance = values.map { val x = it.toDouble() - mean.toDouble(); x * x }.average()
        return sqrt(variance)
    }

    private fun calculateDailyAverage(transactions: List<TransactionEntity>): Long {
        if (transactions.isEmpty()) return 0L

        val dateGroups = transactions.groupBy { txn ->
            txn.occurredAt - (txn.occurredAt % (24 * 60 * 60 * 1000))
        }

        if (dateGroups.isEmpty()) return 0L

        val dailyTotals = dateGroups.map { (_, txns) -> txns.sumOf { it.amountBaseMinor } }
        return dailyTotals.average().toLong()
    }

}
