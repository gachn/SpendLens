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
            bodyRegex = "(?i).*?(?<curr>inr|rs\\.?|[₹$])\\s?(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+" +
                "(?<dir>sent|paid|debited)\\b.*?\\b(?:to|vpa)\\s+(?<party>[A-Za-z0-9@._\\-]{2,40})" +
                ".*?(?:upi|vpa).*?(?:(?:ref|rrn|utr|txn)[^A-Za-z0-9]{0,4}(?<ref>[A-Za-z0-9]{4,}))?",
        ),
        // UPI received
        PatternSeed(
            name = "UPI received",
            senderRegex = null,
            priority = 40,
            bodyRegex = "(?i).*?(?<curr>inr|rs\\.?|[₹$])\\s?(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+" +
                "(?<dir>received|credited)\\b.*?\\bfrom\\s+(?<party>[A-Za-z0-9@._\\-]{2,40})" +
                ".*?(?:upi|vpa).*?(?:(?:ref|rrn|utr)[^A-Za-z0-9]{0,4}(?<ref>[A-Za-z0-9]{4,}))?",
        ),
        // Card spend
        PatternSeed(
            name = "Card spend",
            senderRegex = null,
            priority = 40,
            // Party allows * so aggregator-prefixed names ("CAS*Swiggy") are captured whole.
            bodyRegex = "(?i).*?(?<dir>spent|charged|debited)\\s+(?<curr>inr|rs\\.?|usd|[₹$])\\s?" +
                "(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\b.*?card[^\\dxX*]{0,8}(?<account>[xX*]*\\d{3,})" +
                ".*?\\b(?:at|@|on)\\s+(?<party>[A-Za-z0-9@.*_\\-]{2,40})",
        ),
        // ATM withdrawal
        PatternSeed(
            name = "ATM withdrawal",
            senderRegex = null,
            priority = 40,
            bodyRegex = "(?i).*?(?<curr>inr|rs\\.?|[₹$])\\s?(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+" +
                "(?<dir>withdrawn|debited)\\b.*?atm.*?(?:a/c|ac|card)[^\\dxX*]{0,8}" +
                "(?<account>[xX*]*\\d{3,})?",
        ),
        // Generic debit/credit account alert — broad fallback
        PatternSeed(
            name = "Generic debit/credit",
            senderRegex = null,
            priority = 10,
            bodyRegex = "(?i).*?(?<curr>inr|rs\\.?|usd|eur|gbp|aed|[₹$€£])\\s?" +
                "(?<amount>[\\d,]+(?:\\.\\d{1,2})?)\\s+" +
                "(?<dir>debited|credited|spent|withdrawn|deposited|received|paid|sent|charged)\\b" +
                ".*?(?:(?:a/c|ac|acct|account|card)[^\\dxX*]{0,8}(?<account>[xX*]*\\d{3,}))?" +
                ".*?(?:(?:at|to|from|by|vpa|in favou?r of)\\s+(?<party>[A-Za-z0-9@._\\-]{2,40}))?" +
                ".*?(?:(?:ref|txn|utr|rrn)[^A-Za-z0-9]{0,4}(?<ref>[A-Za-z0-9]{4,}))?" +
                ".*?(?:(?:avl\\s?bal|bal|balance)[^\\d]{0,8}(?<balance>[\\d,]+(?:\\.\\d{1,2})?))?",
        ),
    )
}
