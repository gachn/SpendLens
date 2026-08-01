package com.spendlens.app.parser

/**
 * Pulls an available/account balance figure out of a *transaction* SMS body
 * (e.g. "...Rs.500 debited. Avl Bal Rs.12,345.00"). Unlike [BalanceUpdateParser]
 * this does NOT reject messages that contain transaction verbs — it exists to
 * enrich a parsed transaction whose matched pattern lacked a `balance` capture
 * group, so the Accounts screen can show a current balance for every account.
 *
 * Returns the balance in minor units (paise), or null when no balance phrase is
 * present. The cue word ("Bal"/"Balance") must precede the number so the captured
 * figure is the balance and not the transaction amount.
 */
object BalanceExtractor {

    private const val AMT_CAP = "(?:rs\\.?|inr|₹)?\\s*([\\d,]+(?:\\.\\d{1,2})?)"

    // Ordered most-specific first: adjacent "Avl Bal Rs.X" / "A/c Bal: X" / bare "Bal X", then the
    // separated "<cue> ... is <amount>" form (e.g. ICICI "Available Balance is Rs. 3,38,816.55",
    // HSBC "Avl Bal is INR 45,001.00"). The lazy [^.]*? keeps the figure inside the same sentence.
    private val cues = listOf(
        Regex("(?i)(?:avl\\.?|available)\\s*(?:bal\\.?|balance)\\s*[:\\-]?\\s*$AMT_CAP"),
        Regex("(?i)(?:a/?c|acct|account)\\s*(?:balance|bal\\.?)\\s*[:\\-]?\\s*$AMT_CAP"),
        Regex("(?i)(?:avl\\.?|available|a/?c|acct|account)?\\s*(?:bal\\.?|balance)\\b[^.]*?\\bis\\s*$AMT_CAP"),
        Regex("(?i)\\bbal(?:ance)?\\.?\\s*[:\\-]?\\s*$AMT_CAP"),
    )

    fun extractMinor(body: String): Long? {
        for (re in cues) {
            val raw = re.find(body)?.groupValues?.getOrNull(1) ?: continue
            val value = raw.replace(",", "").toBigDecimalOrNull() ?: continue
            return value.movePointRight(2).toLong()
        }
        return null
    }
}
