package com.spendlens.app.parser

import com.spendlens.app.parser.model.BalanceSnapshot

/**
 * Detects standalone balance-notification SMS that carry no transaction amount — purely
 * informational messages such as "Avl Bal: Rs. 12,345" or "Available credit limit: Rs. 50,000".
 *
 * Deliberately excluded: any SMS that contains a transaction verb (debited/credited/sent/etc.)
 * because those are handled by the PatternEngine and already capture balanceMinor as a named group.
 *
 * Returns null for non-matching SMS so it can be safely tried on every message.
 */
object BalanceUpdateParser {

    private const val AMT_CAP = "(?:rs\\.?|inr|usd|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"

    // "Avl Bal", "Avl Balance", "Available Balance", "Available Bal"
    private val avlBal = Regex(
        "(?i)(?:avl\\.?|available)\\s*(?:bal\\.?|balance)\\s*[:\\-]?\\s*$AMT_CAP",
    )

    // "Account Balance", "A/C Balance", "Acct Balance"
    private val acctBal = Regex(
        "(?i)(?:a/?c|acct|account)\\s*(?:balance|bal)\\s*[:\\-]?\\s*$AMT_CAP",
    )

    // "Available Credit Limit", "Avl Cr Lmt", "Avl Limit", "Credit Available"
    private val avlLimit = Regex(
        "(?i)(?:avl\\.?\\s*(?:cr\\.?\\s*)?(?:lmt|limit)|available\\s*(?:credit\\s*)?(?:limit|lmt)|credit\\s*available)\\s*[:\\-]?\\s*$AMT_CAP",
    )

    // "Balance as on", "Balance is Rs."
    private val balanceIs = Regex(
        "(?i)balance\\s*(?:as\\s*on\\s*[\\d/\\-]+\\s*[:\\-]?|is\\s*[:\\-]?)\\s*$AMT_CAP",
    )

    // "<Avl/Available> Bal[ance] ... is <amount>" — the cue and the figure are separated by filler
    // such as "in HDFC Bank A/c XX3090 as on yesterday:20-JUN-26". The lazy [^.]*? stays within the
    // sentence so the captured number is the balance, not a later amount. Matches the dominant
    // periodic HDFC daily-balance SMS and ICICI/HSBC "Available Balance is Rs.X" alerts.
    private val balCueIs = Regex(
        "(?i)(?:avl\\.?|available)\\s*(?:bal\\.?|balance)\\b[^.]*?\\bis\\s*$AMT_CAP",
    )

    /** At least one of these cue phrases must be present for the SMS to qualify. */
    private val standaloneCue = Regex(
        "(?i)\\b(avl\\s*bal|avl\\s*cr|avl\\s*lmt|avl\\s*limit|available\\s*bal(?:ance)?|available\\s*(?:credit\\s*)?limit|" +
            "account\\s*balance|a/?c\\s*balance|balance\\s*as\\s*on|credit\\s*available)\\b",
    )

    /** If any of these verbs appear the SMS is a transaction alert — leave it for PatternEngine. */
    private val txnVerbs = Regex(
        "(?i)\\b(debited|credited|sent|received|paid|withdrawn|spent|charged|deposited|transferred)\\b",
    )

    fun parse(sender: String, body: String, receivedAt: Long): BalanceSnapshot? {
        if (!standaloneCue.containsMatchIn(body)) return null
        if (txnVerbs.containsMatchIn(body)) return null

        val accountKey = AccountExtractor.extract(body)
            ?: sender.takeIf { it.isNotBlank() }
            ?: return null

        val isCard = Regex("(?i)\\bcredit\\s*(?:card|limit|available)\\b|avl\\s*cr\\b").containsMatchIn(body)

        // For cards prefer available-limit; for banks prefer available-balance. The "<cue> ... is
        // <amount>" form (balCueIs / balanceIs) is the fallback when no adjacent figure was found.
        val amount = if (isCard) {
            (avlLimit.find(body) ?: avlBal.find(body) ?: acctBal.find(body)
                ?: balCueIs.find(body) ?: balanceIs.find(body))
                ?.groupValues?.getOrNull(1)?.let { money(it) }
        } else {
            (avlBal.find(body) ?: acctBal.find(body) ?: avlLimit.find(body)
                ?: balCueIs.find(body) ?: balanceIs.find(body))
                ?.groupValues?.getOrNull(1)?.let { money(it) }
        } ?: return null

        return BalanceSnapshot(
            accountKey = accountKey,
            balanceMinor = amount,
            isCard = isCard,
            currency = if (Regex("(?i)\\busd\\b|\\$").containsMatchIn(body)) "USD" else "INR",
            observedAt = receivedAt,
        )
    }

    private fun money(raw: String): Long {
        val value = raw.replace(",", "").toBigDecimalOrNull() ?: return 0L
        return value.movePointRight(2).toLong()
    }
}
