package com.spendlens.app.ai

import com.spendlens.app.data.db.TransactionEntity
import kotlin.math.roundToInt

data class BudgetAlert(
    val categoryId: Long,
    val categoryName: String,
    val budgetMinor: Long,
    val spentMinor: Long,
    val percentUsed: Int,
    val daysRemaining: Int,
    val status: String, // OK, WARNING, EXCEEDED, ON_TRACK
    val projectedMinor: Long, // Projected spend by end of month
)

data class BudgetPrediction(
    val categoryId: Long,
    val categoryName: String,
    val budgetMinor: Long,
    val currentSpentMinor: Long,
    val daysIntoMonth: Int,
    val daysRemaining: Int,
    val dailyBurnRate: Long,
    val projectedMinor: Long,
    val willExceed: Boolean,
    val daysBudgetWillLast: Int, // -1 if won't exceed
)

/**
 * Monitor budgets and predict spending overruns.
 * Pure statistical calculation — works entirely offline.
 */
object BudgetPredictor {

    /**
     * Generate budget alert for a category.
     * Shows current status vs budget limit.
     */
    fun generateBudgetAlert(
        categoryId: Long,
        categoryName: String,
        budgetMinor: Long,
        transactions: List<TransactionEntity>,
    ): BudgetAlert {
        // Calculate current month spending
        val currentMonthStart = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val currentSpent = transactions
            .filter {
                it.categoryId == categoryId &&
                it.occurredAt >= currentMonthStart &&
                it.direction == "DEBIT" &&
                !it.excludedFromExpense
            }
            .sumOf { it.amountBaseMinor }

        val percentUsed = if (budgetMinor > 0) ((currentSpent * 100) / budgetMinor).toInt() else 0

        val status = when {
            currentSpent > budgetMinor -> "EXCEEDED"
            percentUsed >= 80 -> "WARNING"
            else -> "OK"
        }

        // Project to end of month
        val daysSoFar = 30 - (System.currentTimeMillis() % (30L * 24 * 60 * 60 * 1000)) / (24 * 60 * 60 * 1000)
        val dailyRate = if (daysSoFar > 0) currentSpent / daysSoFar else 0L
        val projected = currentSpent + (dailyRate * (30 - daysSoFar))

        return BudgetAlert(
            categoryId = categoryId,
            categoryName = categoryName,
            budgetMinor = budgetMinor,
            spentMinor = currentSpent,
            percentUsed = percentUsed.coerceIn(0, 100),
            daysRemaining = maxOf(0, (30 - daysSoFar).toInt()),
            status = status,
            projectedMinor = projected,
        )
    }

    /**
     * Predict whether category will exceed budget before month ends.
     */
    fun predictBudgetStatus(
        categoryId: Long,
        categoryName: String,
        budgetMinor: Long,
        transactions: List<TransactionEntity>,
    ): BudgetPrediction {
        // Get last 60 days for burn rate calculation
        val sixtyDaysAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000)
        val recentTxns = transactions.filter {
            it.categoryId == categoryId &&
            it.occurredAt >= sixtyDaysAgo &&
            it.direction == "DEBIT" &&
            !it.excludedFromExpense
        }

        // Current month spending
        val currentMonthStart = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
        val currentSpent = recentTxns
            .filter { it.occurredAt >= currentMonthStart }
            .sumOf { it.amountBaseMinor }

        // Calculate burn rate (average daily spending)
        val daysSinceMonthStart = 30 - ((System.currentTimeMillis() - currentMonthStart) / (24 * 60 * 60 * 1000)).toInt()
        val dailyBurnRate = if (daysSinceMonthStart > 0) currentSpent / daysSinceMonthStart else 0L

        // Project to end of month
        val daysRemaining = maxOf(1, daysSinceMonthStart)
        val projectedTotal = currentSpent + (dailyBurnRate * daysRemaining)

        val willExceed = projectedTotal > budgetMinor

        // Calculate how many days budget will last at current rate
        val daysBudgetWillLast = if (dailyBurnRate > 0) {
            (budgetMinor / dailyBurnRate).toInt()
        } else {
            -1
        }

        return BudgetPrediction(
            categoryId = categoryId,
            categoryName = categoryName,
            budgetMinor = budgetMinor,
            currentSpentMinor = currentSpent,
            daysIntoMonth = maxOf(0, 30 - daysSinceMonthStart),
            daysRemaining = daysRemaining,
            dailyBurnRate = dailyBurnRate,
            projectedMinor = projectedTotal,
            willExceed = willExceed,
            daysBudgetWillLast = daysBudgetWillLast,
        )
    }

    /**
     * Generate budget alerts for all categories.
     */
    fun generateAllAlerts(
        budgets: Map<Long, Pair<String, Long>>, // categoryId to (name, limit)
        transactions: List<TransactionEntity>,
    ): List<BudgetAlert> {
        return budgets.map { (catId, pair) ->
            val (name, limit) = pair
            generateBudgetAlert(catId, name, limit, transactions)
        }
            .sortedBy { it.status } // OK first, then WARNING, then EXCEEDED
    }

    /**
     * Generate suggested budgets based on last 3 months of spending.
     */
    fun suggestBudgets(
        transactions: List<TransactionEntity>,
        categoryNames: Map<Long, String>,
    ): Map<Long, Long> {
        val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
        val recentTxns = transactions.filter {
            it.occurredAt >= ninetyDaysAgo &&
            it.direction == "DEBIT" &&
            !it.excludedFromExpense
        }

        val categorySpending = mutableMapOf<Long, Long>()
        recentTxns.forEach { txn ->
            if (txn.categoryId != null) {
                categorySpending[txn.categoryId] = (categorySpending[txn.categoryId] ?: 0L) + txn.amountBaseMinor
            }
        }

        // Suggest as monthly average with 20% buffer
        return categorySpending.mapValues { (_, total) ->
            (total / 3) + ((total / 3) / 5) // Average + 20%
        }
    }
}
