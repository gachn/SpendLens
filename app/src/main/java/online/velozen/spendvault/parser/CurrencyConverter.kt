package com.spendlens.app.parser

/**
 * Converts a minor-unit amount between two currencies, given a map of currency → rate-to-pivot
 * (any single fixed anchor currency — the map's own denomination, e.g. "X → INR" rates already
 * fetched for the FX layer). Pure Kotlin (JVM-testable). docs/DESIGN.md §2.
 */
object CurrencyConverter {

    fun convert(amountMinor: Long, from: String, to: String, ratesToPivot: Map<String, Double>): Long {
        if (from.equals(to, ignoreCase = true)) return amountMinor
        val fromRate = ratesToPivot[from.uppercase()] ?: return amountMinor // unknown → leave as-is
        val toRate = ratesToPivot[to.uppercase()] ?: return amountMinor
        return Math.round(amountMinor * fromRate / toRate)
    }
}
