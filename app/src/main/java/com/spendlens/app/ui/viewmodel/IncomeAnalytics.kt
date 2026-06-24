package com.spendlens.app.ui.viewmodel

import com.spendlens.app.data.db.TransactionEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** A single income source with its name and total amount for a given month. */
data class IncomeSource(val name: String, val amountMinor: Long)

/** Computed income analytics data — pure, no Compose/Color dependency. */
data class IncomeAnalyticsData(
    /** Top income sources for the selected month, sorted descending by amount. */
    val sources: List<IncomeSource>,
    /** Savings rate per month (0–100 %, negative when spending exceeds income), aligns with MonthlyBar list. */
    val monthlySavingsRates: List<Float>,
)

/**
 * Pure income analytics computations. All inputs are plain Kotlin types so the logic
 * can be exercised in standard JUnit tests without Android instrumentation.
 */
object IncomeAnalytics {
    /**
     * Compute income analytics from a pre-filtered list of spendable transactions.
     *
     * @param spendable Transactions already filtered by `!excludedFromExpense`.
     * @param breakdownMonth Month for which to compute income source breakdown.
     * @param months 6-month bar data (already computed) used for savings rate calculation.
     * @param zone Time zone to use for month-boundary comparisons.
     * @param categories Map of category id → category name; used to label sources by category first,
     *                   falling back to counterparty name when no category is assigned.
     * @param maxSources Maximum number of top sources to return (default 8).
     */
    fun compute(
        transactions: List<TransactionEntity>,
        breakdownMonth: YearMonth,
        months: List<MonthlyBar>,
        zone: ZoneId,
        categories: Map<Long, String>,
        maxSources: Int = 8,
    ): IncomeAnalyticsData {
        val inMonthCredits = transactions.filter { txn ->
            !txn.excludedFromExpense &&
                txn.direction == "CREDIT" &&
                YearMonth.from(Instant.ofEpochMilli(txn.occurredAt).atZone(zone)) == breakdownMonth
        }

        // Group by category name when present, otherwise by counterparty.
        val sources = inMonthCredits
            .groupBy { txn -> txn.categoryId?.let { categories[it] } ?: txn.counterparty }
            .map { (name, list) -> IncomeSource(name, list.sumOf { it.amountBaseMinor }) }
            .sortedByDescending { it.amountMinor }
            .take(maxSources)

        val savingsRates = months.map { m ->
            if (m.creditMinor > 0L) {
                ((m.creditMinor - m.debitMinor).toFloat() / m.creditMinor.toFloat() * 100f)
                    .coerceIn(-999f, 100f)
            } else {
                0f
            }
        }

        return IncomeAnalyticsData(sources, savingsRates)
    }
}
