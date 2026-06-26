package com.spendlens.app.ai

import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.json.JSONException
import org.json.JSONObject

data class SpendingMetrics(
    val totalSpent: Long,
    val totalReceived: Long,
    val topCategory: Pair<String, Long>?,
    val topMerchant: Pair<String, Long>?,
    val transactionCount: Int,
    val periodDays: Int,
)

/**
 * Generate natural language spending summaries and insights.
 * Pure (Android-free) prompt builder + response parser.
 */
object SpendingSummaryGenerator {

    fun buildPrompt(
        metrics: SpendingMetrics,
        categories: List<CategoryEntity>,
        period: String = "this month",
    ): String = buildString {
        append("You are a financial insights assistant for SpendLens, a personal expense app.\n")
        append("Generate a brief, conversational spending summary (2-3 sentences max).\n\n")

        append("Spending metrics for $period:\n")
        append("- Total spent: ₹${metrics.totalSpent / 100}\n")
        append("- Total received: ₹${metrics.totalReceived / 100}\n")
        append("- Transactions: ${metrics.transactionCount}\n")

        metrics.topCategory?.let { (name, amount) ->
            append("- Top category: $name (₹${amount / 100})\n")
        }
        metrics.topMerchant?.let { (name, amount) ->
            append("- Top merchant: $name (₹${amount / 100})\n")
        }

        append("\nGenerate insights about this spending pattern. Be concise and natural.\n")
        append("Examples: 'You spent ₹12,000 this month, mostly on food and travel.'\n")
        append("Or: 'Food is your largest expense at ₹8,500.'\n\n")
        append("Respond with ONLY the summary text (1-3 sentences), no markdown or explanation.")
    }

    fun parseSummary(text: String?): String? {
        return text?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Generate metrics from transactions for a date range.
     * [daysBack] = look back N days; 0 = today only.
     */
    fun calculateMetrics(
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>,
        daysBack: Int = 30,
    ): SpendingMetrics {
        val cutoffTime = System.currentTimeMillis() - (daysBack.toLong() * 24 * 60 * 60 * 1000)
        val filtered = transactions.filter { it.occurredAt >= cutoffTime && !it.excludedFromExpense }

        var totalSpent = 0L
        var totalReceived = 0L

        val categorySpending = mutableMapOf<Long, Long>()
        val merchantSpending = mutableMapOf<String, Long>()

        filtered.forEach { txn ->
            if (txn.direction == "DEBIT") {
                totalSpent += txn.amountBaseMinor
                categorySpending[txn.categoryId ?: -1L] = (categorySpending[txn.categoryId ?: -1L] ?: 0L) + txn.amountBaseMinor
                merchantSpending[txn.counterparty] = (merchantSpending[txn.counterparty] ?: 0L) + txn.amountBaseMinor
            } else {
                totalReceived += txn.amountBaseMinor
            }
        }

        val topCategory = categorySpending.maxByOrNull { it.value }?.let { (catId, amount) ->
            val catName = if (catId == -1L) "Uncategorized" else categories.find { it.id == catId }?.name ?: "Unknown"
            catName to amount
        }

        val topMerchant = merchantSpending.maxByOrNull { it.value }?.let { (merchant, amount) ->
            merchant to amount
        }

        return SpendingMetrics(
            totalSpent = totalSpent,
            totalReceived = totalReceived,
            topCategory = topCategory,
            topMerchant = topMerchant,
            transactionCount = filtered.size,
            periodDays = daysBack,
        )
    }

    /**
     * Calculate month-over-month trend for a category.
     * Returns percentage change (positive = increase).
     */
    fun calculateCategoryTrend(
        transactions: List<TransactionEntity>,
        categoryId: Long,
    ): Float {
        val now = System.currentTimeMillis()
        val currentMonthStart = now - (30L * 24 * 60 * 60 * 1000)
        val previousMonthStart = currentMonthStart - (30L * 24 * 60 * 60 * 1000)

        val currentMonth = transactions
            .filter { it.categoryId == categoryId && it.occurredAt >= currentMonthStart && it.direction == "DEBIT" && !it.excludedFromExpense }
            .sumOf { it.amountBaseMinor }

        val previousMonth = transactions
            .filter { it.categoryId == categoryId && it.occurredAt >= previousMonthStart && it.occurredAt < currentMonthStart && it.direction == "DEBIT" && !it.excludedFromExpense }
            .sumOf { it.amountBaseMinor }

        if (previousMonth == 0L) return if (currentMonth > 0) 100f else 0f

        return ((currentMonth - previousMonth).toFloat() / previousMonth.toFloat()) * 100f
    }
}
