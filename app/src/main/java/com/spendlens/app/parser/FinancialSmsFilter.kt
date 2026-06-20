package com.spendlens.app.parser

import com.spendlens.app.parser.model.SmsMessage

/**
 * Cheap pre-filter that rejects obviously non-financial SMS before any pattern
 * matching runs. Requires a money cue AND a transaction verb, and excludes
 * OTP/verification messages. docs/DESIGN.md §3.1.
 */
object FinancialSmsFilter {

    private val moneyCue = Regex(
        "(?i)(?:inr|rs\\.?|usd|eur|gbp|aed|[₹$€£])\\s?[\\d,]+(?:\\.\\d{1,2})?",
    )
    private val verbCue = Regex(
        "(?i)\\b(debited|credited|spent|withdrawn|received|paid|purchase|txn|transaction|" +
            "transferred|sent|deposited|refunded|charged)\\b",
    )
    private val otpCue = Regex(
        "(?i)\\b(otp|one[- ]?time\\s?password|verification code|do not share|never share)\\b",
    )

    fun isFinancial(sms: SmsMessage): Boolean {
        val body = sms.body
        if (otpCue.containsMatchIn(body)) return false
        return moneyCue.containsMatchIn(body) && verbCue.containsMatchIn(body)
    }
}
