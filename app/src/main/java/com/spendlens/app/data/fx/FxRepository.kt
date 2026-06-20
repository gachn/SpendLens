package com.spendlens.app.data.fx

import android.content.Context
import com.spendlens.app.parser.CurrencyConverter

/**
 * Holds currency → rate-to-INR. Starts from bundled fallbacks, persists the latest fetched
 * rates in plain prefs (non-sensitive), and refreshes from [FxProvider] best-effort. Used to
 * freeze a base-currency amount on each transaction at ingest time. docs/DESIGN.md §2.
 */
class FxRepository(context: Context, private val provider: FxProvider) {

    private val prefs = context.applicationContext.getSharedPreferences("fx", Context.MODE_PRIVATE)

    @Volatile
    private var rates: Map<String, Double> = load()

    fun ratesToBase(): Map<String, Double> = rates

    fun toBaseMinor(amountMinor: Long, currency: String): Long =
        CurrencyConverter.toBaseMinor(amountMinor, currency, rates)

    /** Best-effort refresh; keeps existing rates on failure. */
    suspend fun refresh() {
        val fresh = provider.fetchRatesToInr() ?: return
        val merged = BUNDLED + fresh
        rates = merged
        prefs.edit()
            .putString("rates", merged.entries.joinToString(",") { "${it.key}:${it.value}" })
            .putLong("updatedAt", System.currentTimeMillis())
            .apply()
    }

    private fun load(): Map<String, Double> {
        val saved = prefs.getString("rates", null) ?: return BUNDLED
        return runCatching {
            saved.split(",").associate {
                val (k, v) = it.split(":")
                k to v.toDouble()
            }
        }.getOrDefault(BUNDLED)
    }

    companion object {
        /** Approximate fallback rates (X → INR), used offline / before first refresh. */
        val BUNDLED = mapOf(
            "INR" to 1.0, "USD" to 83.0, "EUR" to 90.0, "GBP" to 105.0, "AED" to 22.6,
            "SGD" to 61.0, "AUD" to 55.0, "CAD" to 61.0, "JPY" to 0.55,
        )
    }
}
