package com.spendlens.app.parser

import com.spendlens.app.parser.model.Channel
import com.spendlens.app.parser.model.TxnDirection

/** Normalisation helpers shared by the engine and generators. Pure Kotlin. */
object Normalize {

    /**
     * Top-50 ISO-4217 currency codes by global usage. Single source of truth: the parser
     * regexes (via [CURRENCY_TOKEN]) and the FX layer (WebFxProvider) both build off this.
     */
    val CURRENCY_CODES: List<String> = listOf(
        "USD", "EUR", "JPY", "GBP", "AUD", "CAD", "CHF", "CNY", "HKD", "NZD",
        "SEK", "KRW", "SGD", "NOK", "MXN", "INR", "RUB", "ZAR", "TRY", "BRL",
        "TWD", "DKK", "PLN", "THB", "IDR", "HUF", "CZK", "ILS", "CLP", "PHP",
        "AED", "COP", "SAR", "MYR", "RON", "NGN", "ARS", "QAR", "KWD", "BHD",
        "OMR", "JOD", "VND", "EGP", "PKR", "BDT", "LKR", "NPR", "KES", "GHS",
    )

    private val codeSet = CURRENCY_CODES.toSet()

    /** Currency symbols → ISO code. `$` defaults to USD (most common in Indian-bank SMS). */
    private val symbolToCode = mapOf(
        "₹" to "INR", "$" to "USD", "€" to "EUR", "£" to "GBP", "¥" to "JPY",
        "₩" to "KRW", "₺" to "TRY", "฿" to "THB", "₪" to "ILS", "₱" to "PHP",
        "₫" to "VND", "₨" to "PKR", "RM" to "MYR",
    )

    /** Word/abbreviation aliases that are not the ISO code itself. */
    private val aliasMap = mapOf(
        "RS" to "INR", "DH" to "AED", "DHS" to "AED", "AED" to "AED",
        "US$" to "USD", "S$" to "SGD", "A$" to "AUD", "C$" to "CAD", "HK$" to "HKD",
    )

    /**
     * Regex alternation (no capture group) matching any supported currency token: the Rs/Dh
     * word aliases, every ISO-4217 code above, and the common symbols. Inject into pattern
     * bodies as `(?<curr>${Normalize.CURRENCY_TOKEN})`. Used with the (?i) flag.
     */
    val CURRENCY_TOKEN: String = buildString {
        append("(?:")
        append("rs\\.?|dhs?|us\\$|[asch]\\$|hk\\$|")
        append(CURRENCY_CODES.joinToString("|"))
        append("|[₹\\$€£¥₩₺฿₪₱₫₨]")
        append(")")
    }

    private val debitVerbs = setOf(
        "debited", "spent", "withdrawn", "paid", "sent", "purchase", "charged", "deducted",
    )
    private val creditVerbs = setOf(
        "credited", "received", "deposited", "refunded", "added",
        "redemption", "payout", "settlement",
    )

    /**
     * Resolves a captured currency token to an ISO code, falling back to [fallback] (the user's
     * primary currency, or "INR" when the caller has no other preference) when [symbol] is
     * missing or unrecognized.
     */
    fun currency(symbol: String?, fallback: String = "INR"): String {
        if (symbol.isNullOrBlank()) return fallback
        val t = symbol.trim()
        symbolToCode[t]?.let { return it }
        val key = t.trimEnd('.').uppercase()
        symbolToCode[key]?.let { return it }
        aliasMap[key]?.let { return it }
        if (key in codeSet) return key
        return fallback
    }

    /** "1,234.50" → 123450 (minor units, 2 decimal places assumed). */
    fun amountToMinor(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.replace(",", "").trim()
        val value = cleaned.toBigDecimalOrNull() ?: return null
        return value.movePointRight(2).toLong()
    }

    fun direction(verb: String?): TxnDirection {
        val v = verb?.trim()?.lowercase().orEmpty()
        return when {
            creditVerbs.any { v.contains(it) } -> TxnDirection.CREDIT
            debitVerbs.any { v.contains(it) } -> TxnDirection.DEBIT
            else -> TxnDirection.DEBIT
        }
    }

    /** Keep only the last 4 alphanumerics of an account/card token → "••••1234". */
    fun maskAccount(raw: String?): String {
        if (raw.isNullOrBlank()) return "Unknown"
        val tail = raw.filter { it.isLetterOrDigit() }.takeLast(4)
        return if (tail.isEmpty()) "Unknown" else "••••$tail"
    }

    fun cleanParty(raw: String?): String {
        val p = raw?.trim()?.trim('.', '-', ' ', ',').orEmpty()
        return p.ifBlank { "Unknown" }.take(48)
    }

    fun channel(body: String): Channel {
        val b = body.lowercase()
        return when {
            "upi" in b || "vpa" in b || "@" in b -> Channel.UPI
            "atm" in b || "withdrawn" in b -> Channel.ATM
            "imps" in b -> Channel.IMPS
            "neft" in b -> Channel.NEFT
            "rtgs" in b -> Channel.RTGS
            "card" in b || "pos" in b -> Channel.CARD
            "wallet" in b -> Channel.WALLET
            "net banking" in b || "netbanking" in b -> Channel.NETBANKING
            else -> Channel.UNKNOWN
        }
    }
}
