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

    // Messages that carry a money amount + a transaction verb but represent NO actual debit/credit:
    // mandate setup, pending collect requests, declines, fraud blocks, dunning, EMI conversions.
    // These otherwise pass the positive cues and pile up as UNPARSED in the Review queue.
    private val nonTxnCue = Regex(
        "(?i)(" +
            "autopay activation|e-?mandate|" +                                 // mandate setup/cancel
            "has requested money from you|will be debited from your account on approving|" + // collect request
            "txn declined|declined due to|" +                                  // declines
            "suspicious|has been blocked|" +                                   // fraud block
            "to confirm transaction|sms acpt|" +                               // confirm prompt
            "non[- ]?payment|have been (?:suspended|stopped)|outstanding bill|" + // dunning
            "converted into emi" +                                             // EMI conversion notice
            ")",
    )

    fun isFinancial(sms: SmsMessage): Boolean {
        val body = sms.body
        if (otpCue.containsMatchIn(body)) return false
        if (nonTxnCue.containsMatchIn(body)) return false
        return moneyCue.containsMatchIn(body) && verbCue.containsMatchIn(body)
    }
}
