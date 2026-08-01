package com.spendlens.app.parser

/**
 * Turns a raw merchant span into (a) a canonical lookup [key] used for caching/dictionary
 * matching, and (b) a tidy [display] fallback name. Pure Kotlin. docs/DESIGN.md §3.2.
 */
object MerchantNormalizer {

    // Dropped when building the matching key (aggressive) so "SWIGGY STORES" == "Swiggy".
    private val keyNoise = setOf(
        "pvt", "ltd", "limited", "private", "inc", "llp", "co", "corp", "india", "in", "the",
        "payments", "payment", "pay", "bnpl", "technologies", "technology", "tech", "services",
        "service", "solutions", "retail", "stores", "store", "online", "digital", "enterprises",
        "enterprise", "company", "merchant",
    )
    // Dropped from the display name (minimal — keep brand words intact).
    private val displayNoise = setOf("pvt", "ltd", "limited", "private", "inc", "llp")

    /**
     * Strips a payment-aggregator prefix before a '*' (e.g. "RAZ*Swiggy", "CAS*Swiggy",
     * "PAYU*Bookmyshow" → keep the part after the last '*') so the real brand is matched.
     */
    private fun stripAggregator(raw: String): String =
        if (raw.contains('*')) raw.substringAfterLast('*').trim().ifBlank { raw } else raw

    /** Canonical, spaceless, lowercase key, e.g. "swiggystores@hdfc" → "swiggy". */
    fun key(raw: String): String =
        stripAggregator(raw).substringBefore('@')
            .lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it !in keyNoise && !it.all { c -> c.isDigit() } }
            .joinToString("")
            .take(28)

    /** Human fallback name when no dictionary/web match exists, e.g. "swiggy.stores" → "Swiggy Stores". */
    fun display(raw: String): String {
        val words = stripAggregator(raw).substringBefore('@')
            .replace(Regex("[._/]"), " ")
            .replace(Regex("[^A-Za-z0-9 &]"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() && it.lowercase() !in displayNoise }
        val titled = words.joinToString(" ") { w ->
            if (w.length <= 3 && w == w.uppercase()) w // keep short acronyms (KFC, HP)
            else w.lowercase().replaceFirstChar { it.uppercaseChar() }
        }
        return titled.trim().ifBlank { raw.trim() }.take(40)
    }
}
