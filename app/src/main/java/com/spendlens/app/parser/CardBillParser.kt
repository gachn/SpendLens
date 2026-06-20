package com.spendlens.app.parser

import com.spendlens.app.parser.model.CardBill
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Parses credit-card *statement* SMS — "Total Amount Due", "Minimum Due", "Due Date",
 * "Total Outstanding" — into a [CardBill]. This is distinct from transaction parsing: a
 * statement is a balance snapshot, not a debit/credit. Pure Kotlin (java.time only).
 *
 * Returns null for anything that isn't clearly a card bill, so it can be tried on every SMS
 * as a harmless side-effect without swallowing real transactions.
 */
object CardBillParser {

    // A money amount, optionally currency-prefixed: "Rs. 15,230.00", "INR 760", "₹ 1,234.5".
    private const val AMT = "(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"

    private val totalDue = Regex("(?i)total\\s*(?:amount\\s*)?due\\s*(?:is|:|-|of)?\\s*$AMT")
    private val outstanding = Regex("(?i)(?:total\\s*)?outstanding(?:\\s*(?:balance|amount))?\\s*(?:is|:|-|of)?\\s*$AMT")
    private val minDue = Regex("(?i)min(?:imum)?\\.?\\s*(?:amount\\s*)?due\\s*(?:is|:|-|of)?\\s*$AMT")

    // "Due Date 05-07-2026", "due by 05/07/26", "payment due date: 5 Jul 2026", "5-Jul-2026".
    private val dueDate = Regex(
        "(?i)due\\s*(?:date|by)\\s*[:\\-]?\\s*(\\d{1,2}[ /\\-](?:\\d{1,2}|[A-Za-z]{3,9})[ /\\-]\\d{2,4})",
    )

    // Must look like a statement, not a spend alert.
    private val statementCue = Regex(
        "(?i)(total\\s*(?:amount\\s*)?due|min(?:imum)?\\.?\\s*(?:amount\\s*)?due|statement|outstanding|due\\s*date)",
    )

    private val zone: ZoneId = ZoneId.systemDefault()
    private val dateFormats = listOf(
        "dd-MM-yyyy", "d-M-yyyy", "dd/MM/yyyy", "d/M/yyyy",
        "dd-MM-yy", "d-M-yy", "dd/MM/yy", "d/M/yy",
        "dd MMM yyyy", "d MMM yyyy", "dd-MMM-yyyy", "d-MMM-yyyy",
        "dd MMM yy", "d MMM yy", "dd-MMM-yy", "d-MMM-yy",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    fun parse(sender: String, body: String, receivedAt: Long): CardBill? {
        if (!statementCue.containsMatchIn(body)) return null
        val cardKey = AccountExtractor.extract(body) ?: return null

        val total = totalDue.find(body)?.let { money(it.groupValues[1]) }
            ?: outstanding.find(body)?.let { money(it.groupValues[1]) }
            ?: return null

        return CardBill(
            cardKey = cardKey,
            totalDueMinor = total,
            minDueMinor = minDue.find(body)?.let { money(it.groupValues[1]) },
            currency = currencyOf(body),
            dueDate = dueDate.find(body)?.groupValues?.getOrNull(1)?.let { parseDate(it.trim()) },
            statementAt = receivedAt,
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
