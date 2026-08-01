package com.spendlens.app.ui.util

import kotlin.math.ceil
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Predicts a sensible monthly budget for a category from its recent monthly spend history.
 *
 * This is deliberately smarter than a plain 12-month average, which both under-budgets growing
 * categories and over-budgets ones that have tapered off. The forecast combines:
 *
 *  1. **Recency weighting** — an exponentially weighted mean (recent months count more), so a
 *     change in habit is reflected quickly instead of being diluted by a year-old number.
 *  2. **Trend** — a least-squares slope over the series, projected forward, so categories that
 *     are clearly climbing or falling get nudged in that direction.
 *  3. **Volatility buffer** — categories whose spend swings a lot get a small headroom margin
 *     (capped) so the budget realistically covers a typical busy month, not just the mean.
 *  4. **Recent floor** — never predict below the average of the last 3 active months, so the
 *     budget always covers what the user actually spends right now.
 *
 * Leading zero months (before the category had any activity) are dropped so a category that only
 * started 3 months ago isn't averaged against 9 phantom zero months.
 *
 * All amounts are in minor units (paise). Returns 0 when there is no usable history.
 */
object BudgetPredictor {

    /** Recency decay: each older month is worth 1/RECENCY_BASE of the next newer one. */
    private const val RECENCY_BASE = 1.5

    /** Cap on the volatility headroom added on top of the projection. */
    private const val MAX_VOLATILITY_BUFFER = 0.25

    /**
     * @param monthlySpend per-month totals, oldest → newest (caller supplies a fixed window,
     *        e.g. the last 12 months, with 0 for months that had no spend).
     */
    fun predict(monthlySpend: List<Long>): Long {
        // Ignore the stretch before the category first saw any spend.
        val series = monthlySpend.dropWhile { it <= 0L }
        if (series.isEmpty()) return 0L
        if (series.size == 1) return roundNice(series.first())

        val values = series.map { it.toDouble() }
        val n = values.size

        // 1. Exponentially weighted mean — newest months weigh most.
        var weightedSum = 0.0
        var weightTotal = 0.0
        values.forEachIndexed { i, v ->
            val w = RECENCY_BASE.pow(i.toDouble()) // i grows toward the newest entry
            weightedSum += v * w
            weightTotal += w
        }
        val ewma = weightedSum / weightTotal

        // 2. Trend: least-squares slope over the index, projected half a step ahead.
        val slope = slope(values)
        val projected = (ewma + slope * 0.5).coerceAtLeast(0.0)

        // 3. Volatility buffer from the coefficient of variation.
        val mean = values.average()
        val sd = sqrt(values.sumOf { (it - mean).pow(2) } / n)
        val cov = if (mean > 0.0) sd / mean else 0.0
        val buffered = projected * (1.0 + cov.coerceIn(0.0, MAX_VOLATILITY_BUFFER))

        // 4. Floor at the recent (last 3 active months) average.
        val recentFloor = values.takeLast(3).average()

        return roundNice(maxOf(buffered, recentFloor).toLong())
    }

    /** Least-squares slope of [values] against their index (0..n-1). */
    private fun slope(values: List<Double>): Double {
        val n = values.size
        if (n < 2) return 0.0
        val meanX = (n - 1) / 2.0
        val meanY = values.average()
        var num = 0.0
        var den = 0.0
        values.forEachIndexed { i, y ->
            val dx = i - meanX
            num += dx * (y - meanY)
            den += dx * dx
        }
        return if (den == 0.0) 0.0 else num / den
    }

    /**
     * Round to a human-friendly increment scaled to the magnitude of [minor] (₹, so 100 minor = ₹1).
     * e.g. ₹4,237 → ₹4,300; ₹187 → ₹190; ₹52 → ₹55.
     */
    private fun roundNice(minor: Long): Long {
        if (minor <= 0L) return 0L
        val rupees = minor / 100.0
        val step = when {
            rupees >= 10_000 -> 500.0
            rupees >= 2_000 -> 100.0
            rupees >= 500 -> 50.0
            rupees >= 100 -> 10.0
            else -> 5.0
        }
        val roundedRupees = ceil(rupees / step) * step
        return (roundedRupees * 100).toLong()
    }
}
