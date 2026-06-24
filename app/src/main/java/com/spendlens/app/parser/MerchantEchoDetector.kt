package com.spendlens.app.parser

import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.parser.model.ParsedTransaction

/**
 * A "merchant echo" is a CREDIT the user never actually received: after you pay a merchant
 * (e.g. Jio), your bank sends the real DEBIT, then the merchant sends its own "your account
 * credited with ₹X" acknowledgement. Recorded naively, that credit nets the spend back to ~zero.
 *
 * It's distinguished from a genuine refund by: same amount as a very recent DEBIT, same merchant,
 * arriving within minutes — and crucially *not* mentioning a refund/reversal/cashback. The echo
 * credit is suppressed (hidden, excluded from totals); the original debit stays as the real spend.
 * docs/DESIGN.md §4.
 */
object MerchantEchoDetector {

    const val WINDOW_MS = 1_800_000L // 30 min — the echo lands within minutes of the debit

    private val refundCue = Regex("(?i)\\b(refund|reversed|reversal|cashback|chargeback)\\b")

    /**
     * The recent DEBIT this CREDIT is merely echoing, or null when the credit is genuine income/refund.
     * [recentDebits] should already be the same-amount DEBIT rows within [WINDOW_MS] of the credit.
     */
    fun echoedDebit(
        credit: ParsedTransaction,
        body: String,
        recentDebits: List<TransactionEntity>,
    ): TransactionEntity? {
        if (refundCue.containsMatchIn(body)) return null // real money back → keep as a credit
        val party = normalize(credit.counterparty)
        if (party.isBlank()) return null
        return recentDebits.firstOrNull { d ->
            d.amountMinor == credit.amountMinor &&
                !d.isDuplicate &&
                kotlin.math.abs(d.occurredAt - credit.occurredAt) <= WINDOW_MS &&
                partyMatches(party, normalize(d.counterparty))
        }
    }

    private fun normalize(p: String): String = p.lowercase().filter { it.isLetterOrDigit() }

    /** Same merchant if either normalized name contains the other (handles "Jio" vs "Jio Prepaid"). */
    private fun partyMatches(a: String, b: String): Boolean =
        a.isNotBlank() && b.isNotBlank() && (a.contains(b) || b.contains(a))
}
