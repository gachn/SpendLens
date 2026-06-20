package com.spendlens.app.parser

/**
 * Converts a minor-unit amount in some currency to the base currency (INR) minor units,
 * given a map of currency → rate-to-base. Pure Kotlin (JVM-testable). docs/DESIGN.md §2.
 */
object CurrencyConverter {

    const val BASE = "INR"

    fun toBaseMinor(amountMinor: Long, currency: String, ratesToBase: Map<String, Double>): Long {
        if (currency.equals(BASE, ignoreCase = true)) return amountMinor
        val rate = ratesToBase[currency.uppercase()] ?: return amountMinor // unknown → leave as-is
        return Math.round(amountMinor * rate)
    }
}
