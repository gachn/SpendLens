package com.spendlens.app.parser

import com.spendlens.app.parser.model.Channel
import com.spendlens.app.parser.model.TxnDirection

/** Normalisation helpers shared by the engine and generators. Pure Kotlin. */
object Normalize {

    private val currencyMap = mapOf(
        "₹" to "INR", "RS" to "INR", "RS." to "INR", "INR" to "INR",
        "$" to "USD", "USD" to "USD",
        "€" to "EUR", "EUR" to "EUR",
        "£" to "GBP", "GBP" to "GBP",
        "AED" to "AED", "DH" to "AED",
    )

    private val debitVerbs = setOf(
        "debited", "spent", "withdrawn", "paid", "sent", "purchase", "charged", "deducted",
    )
    private val creditVerbs = setOf(
        "credited", "received", "deposited", "refunded", "added",
        "redemption", "payout", "settlement",
    )

    fun currency(symbol: String?): String {
        if (symbol.isNullOrBlank()) return "INR"
        return currencyMap[symbol.trim().uppercase()] ?: "INR"
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
