package com.spendlens.app.parser

import com.spendlens.app.parser.model.CardBill
import com.spendlens.app.parser.model.CardPayment
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses credit-card *statement* SMS and *payment* SMS.
 *
 * Statement: "Total Amount Due", "Minimum Due", "Due Date", "Total Outstanding" → [CardBill].
 * Payment:   "payment of Rs.X received / processed" → [CardPayment].
 *
 * Both return null for non-matching SMS so they can be tried on every message harmlessly.
 * Account number is extracted by [AccountExtractor]; when absent the sender address is used
 * as the cardKey so statement-only SMS (no card number) are still captured.
 */
object CardBillParser {

    // "Dr." prefix appears in Axis Bank statement SMS before the amount (e.g. "INR  Dr. 59462.57").
    private const val AMT = "(?:rs\\.?|inr|₹)?\\s*(?:dr\\.?\\s*)?([\\d,]+(?:\\.\\d{1,2})?)"

    // Money "amount" word — handles "amount", "amt"/"amt." abbreviations, or omitted.
    private const val AMT_WORD = "(?:amount\\s*|amt\\.?\\s*)?"

    private val totalDue = Regex("(?i)(?:tot|total)\\.?\\s*${AMT_WORD}due\\s*(?:is|:|-|of)?\\s*$AMT")
    // Axis Bank omits "due" and writes "Total amt: INR Dr. X" — matched as a fallback.
    private val totalAmt = Regex("(?i)tot(?:al)?\\s*amt\\.?\\s*[:\\-]\\s*$AMT")
    private val outstanding = Regex(
        "(?i)(?:total\\s*)?(?:outstanding|o/s)(?:\\s*(?:balance|amount|amt\\.?))?\\s*(?:is|:|-|of)?\\s*$AMT",
    )
    private val minDue = Regex("(?i)min(?:imum)?\\.?\\s*${AMT_WORD}due\\s*(?:is|:|-|of)?\\s*$AMT")

    private val dueDate = Regex(
        "(?i)(?:due\\s*(?:date|by|on)|pay(?:able)?\\s*by)\\s*[:\\-]?\\s*(\\d{1,2}[ /\\-](?:\\d{1,2}|[A-Za-z]{3,9})[ /\\-]\\d{2,4})",
    )

    private val statementCue = Regex(
        "(?i)((?:tot|total)\\.?\\s*${AMT_WORD}due|min(?:imum)?\\.?\\s*${AMT_WORD}due|statement|outstanding|o/s|due\\s*(?:date|by)|pay(?:able)?\\s*by)",
    )

    // Payment detection — must NOT be a statement SMS (no totalDue / outstanding keywords).
    private val paymentCue = Regex(
        "(?i)\\b(payment\\s+(?:of|for|towards|received|processed|successful)|" +
            "credit\\s+card\\s+payment|bill\\s+payment\\s+(?:of|for)|" +
            "paid\\s+towards\\s+(?:your\\s+)?(?:credit\\s+card|cc)|" +
            "(?:your\\s+)?(?:credit\\s+card|cc)\\s+payment)\\b",
    )
    private val paymentAmount = Regex(
        "(?i)(?:payment\\s*(?:of|for|towards)?\\s*|amount\\s*(?:of|:)?\\s*)" +
            "(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)",
    )

    private val zone: ZoneId = ZoneId.systemDefault()
    private val dateFormats = listOf(
        "dd-MM-yyyy", "d-M-yyyy", "dd/MM/yyyy", "d/M/yyyy",
        "dd-MM-yy", "d-M-yy", "dd/MM/yy", "d/M/yy",
        "dd MMM yyyy", "d MMM yyyy", "dd-MMM-yyyy", "d-MMM-yyyy",
        "dd MMM yy", "d MMM yy", "dd-MMM-yy", "d-MMM-yy",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    /**
     * Parse a card-bill statement SMS. Falls back to [sender] as the cardKey when no card
     * number is present in the body (some banks send statement-ready SMS without the number).
     */
    fun parse(sender: String, body: String, receivedAt: Long): CardBill? {
        if (!statementCue.containsMatchIn(body)) return null
        val cardKey = AccountExtractor.extract(body) ?: sender.takeIf { it.isNotBlank() } ?: return null

        val total = totalDue.find(body)?.let { money(it.groupValues[1]) }
            ?: totalAmt.find(body)?.let { money(it.groupValues[1]) }
            ?: outstanding.find(body)?.let { money(it.groupValues[1]) }
            ?: return null

        val cycleDay = LocalDate.ofInstant(
            java.time.Instant.ofEpochMilli(receivedAt), zone,
        ).dayOfMonth

        return CardBill(
            cardKey = cardKey,
            totalDueMinor = total,
            minDueMinor = minDue.find(body)?.let { money(it.groupValues[1]) },
            currency = currencyOf(body),
            dueDate = dueDate.find(body)?.groupValues?.getOrNull(1)?.let { parseDate(it.trim()) },
            statementAt = receivedAt,
            statementCycleDay = cycleDay,
        )
    }

    /**
     * Parse a credit-card payment confirmation SMS.
     * Returns null if the SMS is a statement (has totalDue/outstanding) rather than a payment.
     */
    fun parsePayment(sender: String, body: String, receivedAt: Long): CardPayment? {
        if (!paymentCue.containsMatchIn(body)) return null
        // Statement SMS may also mention "payment" — skip them to avoid false positives.
        if (totalDue.containsMatchIn(body) || outstanding.containsMatchIn(body)) return null

        val cardKey = AccountExtractor.extract(body) ?: sender.takeIf { it.isNotBlank() } ?: return null
        val amount = paymentAmount.find(body)?.let { money(it.groupValues[1]) }
            ?: return null
        if (amount == 0L) return null

        return CardPayment(
            cardKey = cardKey,
            amountMinor = amount,
            currency = currencyOf(body),
            paidAt = receivedAt,
        )
    }

    private fun money(raw: String): Long {
        val value = raw.replace(",", "").toBigDecimalOrNull() ?: return 0L
        return value.movePointRight(2).toLong()
    }

    private fun currencyOf(body: String): String = when {
        Regex("(?i)\\busd\\b|\\$").containsMatchIn(body) -> "USD"
        else -> "INR"
    }

    private fun parseDate(token: String): Long? {
        for (fmt in dateFormats) {
            val d = runCatching { LocalDate.parse(token, fmt) }.getOrNull()
            if (d != null) return d.atStartOfDay(zone).toInstant().toEpochMilli()
        }
        return null
    }
}
