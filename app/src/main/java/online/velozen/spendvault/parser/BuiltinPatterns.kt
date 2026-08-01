package com.spendlens.app.parser

/**
 * Seed pattern set (generic / multi-region). These are deliberately broad; learned
 * patterns (priority > 50) refine them over time. Each body regex uses named groups:
 * curr, amount, dir, account, party, ref, balance. Counterparty capture is
 * single-token (stops at whitespace) for robustness. docs/DESIGN.md §3.3.
 */
data class PatternSeed(
    val name: String,
    val senderRegex: String?,
    val bodyRegex: String,
    val priority: Int,
)

object BuiltinPatterns {

    val seeds: List<PatternSeed> = listOf(
        // UPI sent / paid
        PatternSeed(
            name = "UPI sent",
            senderRegex = null,
            priority = 40,
            bodyRegex = "(?i).*?(?<curr>${Normalize.CURRENCY_TOKEN})\\s?(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+" +
                "(?<dir>sent|paid|debited)\\b.*?\\b(?:to|vpa)\\s+(?<party>[A-Za-z0-9@._\\-]{2,40})" +
                ".*?(?:upi|vpa).*?(?:(?:ref|rrn|utr|txn)[^A-Za-z0-9]{0,4}(?<ref>[A-Za-z0-9]{4,}))?",
        ),
        // UPI received
        PatternSeed(
            name = "UPI received",
            senderRegex = null,
            priority = 40,
            bodyRegex = "(?i).*?(?<curr>${Normalize.CURRENCY_TOKEN})\\s?(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+" +
                "(?<dir>received|credited)\\b.*?\\bfrom\\s+(?<party>[A-Za-z0-9@._\\-]{2,40})" +
                ".*?(?:upi|vpa).*?(?:(?:ref|rrn|utr)[^A-Za-z0-9]{0,4}(?<ref>[A-Za-z0-9]{4,}))?",
        ),
        // Card spend
        PatternSeed(
            name = "Card spend",
            senderRegex = null,
            priority = 40,
            // Party allows * so aggregator-prefixed names ("CAS*Swiggy") are captured whole.
            bodyRegex = "(?i).*?(?<dir>spent|charged|debited)\\s+(?<curr>${Normalize.CURRENCY_TOKEN})\\s?" +
                "(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\b.*?card[^\\dxX*]{0,8}(?<account>[xX*]*\\d{3,})" +
                ".*?\\b(?:at|@|on)\\s+(?<party>[A-Za-z0-9@.*_\\-]{2,40})",
        ),
        // ATM withdrawal
        PatternSeed(
            name = "ATM withdrawal",
            senderRegex = null,
            priority = 40,
            bodyRegex = "(?i).*?(?<curr>${Normalize.CURRENCY_TOKEN})\\s?(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+" +
                "(?<dir>withdrawn|debited)\\b.*?atm.*?(?:a/c|ac|card)[^\\dxX*]{0,8}" +
                "(?<account>[xX*]*\\d{3,})?",
        ),
        // Mutual-fund SIP / lump-sum purchase. No debit verb sits next to the amount
        // ("SIP Purchase of Rs.X", "Purchase transaction ... for amount of INR X"), so the
        // generic seeds miss it. The first money token after the SIP keyword is the invested
        // amount; the NAV value always appears later, so a lazy match grabs the right one.
        // Direction defaults to DEBIT (no dir group); SmsProcessor files it under Transfers,
        // excluded from spend.
        PatternSeed(
            name = "Investment SIP purchase",
            senderRegex = null,
            priority = 50,
            bodyRegex = "(?i).*?(?:sip|purchase transaction|mutual fund)" +
                ".*?(?<curr>rs\\.?|inr)\\s?(?<amount>[\\d,]+(?:\\.\\d{1,2})?)",
        ),
        // Mutual-fund redemption / dividend / settlement payout — money in.
        PatternSeed(
            name = "Investment redemption/payout",
            senderRegex = null,
            priority = 50,
            bodyRegex = "(?i).*?(?<dir>redemption|payout|settlement)" +
                ".*?(?<curr>rs\\.?|inr)\\s?(?<amount>[\\d,]+(?:\\.\\d{1,2})?)",
        ),
        // Prepaid mobile recharge / govt-fee payment: "Recharge of INR X is successful",
        // "Payment of Rs. X is successful". No verb adjacent to the amount.
        PatternSeed(
            name = "Recharge/payment success",
            senderRegex = null,
            priority = 50,
            bodyRegex = "(?i).*?(?:recharge|payment) of\\s+(?<curr>inr|rs\\.?)\\s?" +
                "(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+is successful",
        ),
        // Insurance premium debit: "transaction of Rs X for your <insurer> policy".
        PatternSeed(
            name = "Insurance premium",
            senderRegex = null,
            priority = 50,
            bodyRegex = "(?i).*?transaction of\\s+(?<curr>rs\\.?|inr)\\s?" +
                "(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+for your",
        ),
        // ICICI UPI debit: "ICICI Bank Acct XX5678 debited for Rs 150.00 on …; MERCHANT credited. UPI:…"
        PatternSeed(
            name = "ICICI UPI debit",
            senderRegex = null,
            priority = 50,
            bodyRegex = "(?i).*?(?:icici\\s+bank\\s+)?(?:a/c|ac|acct|account)\\s+(?<account>[xX*]*\\d{2,})\\s+" +
                "(?<dir>debited)\\s+for\\s+(?<curr>rs\\.?|inr)\\s?(?<amount>[\\d,]+(?:\\.\\d{1,2})?)" +
                ".*?;\\s*(?<party>[A-Za-z][A-Za-z0-9 &._-]{1,38})\\s+credited" +
                ".*?(?:upi|vpa)\\s*[:#]?\\s*(?<ref>[A-Za-z0-9]{4,})?",
        ),
        // HSBC (and similar) phrasing: "...is credited/debited with INR X+/-..." — the direction
        // verb sits BEFORE the amount ("credited with INR 289.48"), the opposite order every seed
        // above assumes, so none of them match. Trailing +/- after the amount (HSBC's convention)
        // is consumed and discarded.
        PatternSeed(
            name = "Credited/debited with amount (dir before amount)",
            senderRegex = null,
            priority = 20,
            bodyRegex = "(?i).*?\\b(?<dir>credited|debited)\\s+with\\s+(?<curr>${Normalize.CURRENCY_TOKEN})\\s?" +
                "(?<amount>[\\d,]+(?:\\.\\d{1,2})?)[+-]?" +
                ".*?(?:(?:a/c|ac|acct|account|card)[^\\dxX*]{0,8}(?<account>[xX*]*\\d{3,}))?" +
                ".*?(?:(?:at|to|from|by|vpa|in favou?r of)\\s+(?<party>[A-Za-z0-9@._\\-]{2,40}))?" +
                ".*?(?:(?:ref|txn|utr|rrn)[^A-Za-z0-9]{0,4}(?<ref>[A-Za-z0-9]{4,}))?" +
                ".*?(?:(?:avl\\s?bal|bal|balance)[^\\d]{0,8}(?<balance>[\\d,]+(?:\\.\\d{1,2})?))?",
        ),
        // Generic debit/credit account alert — broad fallback
        PatternSeed(
            name = "Generic debit/credit",
            senderRegex = null,
            priority = 10,
            bodyRegex = "(?i).*?(?<curr>${Normalize.CURRENCY_TOKEN})\\s?" +
                "(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+" +
                "(?<dir>debited|credited|spent|withdrawn|deposited|received|paid|sent|charged)\\b" +
                ".*?(?:(?:a/c|ac|acct|account|card)[^\\dxX*]{0,8}(?<account>[xX*]*\\d{3,}))?" +
                ".*?(?:(?:at|to|from|by|vpa|in favou?r of)\\s+(?<party>[A-Za-z0-9@._\\-]{2,40}))?" +
                ".*?(?:(?:ref|txn|utr|rrn)[^A-Za-z0-9]{0,4}(?<ref>[A-Za-z0-9]{4,}))?" +
                ".*?(?:(?:avl\\s?bal|bal|balance)[^\\d]{0,8}(?<balance>[\\d,]+(?:\\.\\d{1,2})?))?",
        ),
    )
}
