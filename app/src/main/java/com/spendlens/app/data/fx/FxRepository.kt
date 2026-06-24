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
        /**
         * Approximate fallback rates (X → INR), used offline / before first refresh. Covers the
         * common currencies; the long tail is filled live by [WebFxProvider] on refresh, and any
         * code still missing is left at 1:1 by [CurrencyConverter] (better than dropping it).
         */
        val BUNDLED = mapOf(
            "INR" to 1.0, "USD" to 83.0, "EUR" to 90.0, "GBP" to 105.0, "AED" to 22.6,
            "SGD" to 61.0, "AUD" to 55.0, "CAD" to 61.0, "JPY" to 0.55, "CHF" to 94.0,
            "CNY" to 11.5, "HKD" to 10.6, "NZD" to 51.0, "SEK" to 7.9, "NOK" to 7.7,
            "DKK" to 12.0, "KRW" to 0.062, "THB" to 2.4, "MYR" to 18.5, "IDR" to 0.0053,
            "PHP" to 1.45, "VND" to 0.0033, "RUB" to 0.92, "ZAR" to 4.5, "TRY" to 2.5,
            "BRL" to 16.5, "MXN" to 4.6, "PLN" to 21.0, "SAR" to 22.1, "QAR" to 22.8,
            "KWD" to 270.0, "BHD" to 220.0, "OMR" to 216.0, "PKR" to 0.30, "BDT" to 0.70,
            "LKR" to 0.28, "NPR" to 0.62, "KES" to 0.64, "NGN" to 0.052, "EGP" to 1.7,
        )
    }
}
