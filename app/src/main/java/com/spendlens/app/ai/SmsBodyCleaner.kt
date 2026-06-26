package com.spendlens.app.ai

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Clean raw SMS bodies by removing bank prefixes, signatures, and formatting
 * to show more readable descriptions to users.
 */
object SmsBodyCleaner {

    fun buildPrompt(body: String): String = buildString {
        append("You are a SMS body cleaner for SpendLens, a personal expense app.\n")
        append("Clean this bank SMS to make it more readable by:\n")
        append("1. Remove bank name prefixes (e.g., 'ICICI Bank', 'HDFC', 'Axis')\n")
        append("2. Remove account/card info like 'Acct XX1234' or 'Card ending XX12'\n")
        append("3. Remove timestamps, URLs, and boilerplate (e.g., 'Ref: ABC123', 'For help visit...')\n")
        append("4. Keep the essential transaction info: amount, type, merchant, balance\n")
        append("5. Make it natural readable English\n\n")
        append("SMS: \"$body\"\n\n")
        append("Respond with ONLY the cleaned text, no markdown, no explanation, no quotes. ")
        append("If you can't clean it, return the original SMS.")
    }

    /**
     * Parse cleaned SMS body from model response.
     * Returns null if response is blank.
     */
    fun parseCleanedBody(text: String?): String? {
        return text?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Common patterns to clean via regex before sending to AI (cheaper than API calls).
     * Returns cleaned body or null if it looks non-financial.
     */
    fun quickClean(body: String): String? {
        // Check if it's likely financial
        if (!isLikelyFinancial(body)) return null

        var cleaned = body
            // Remove common bank prefixes
            .replace(Regex("^(ICICI|HDFC|Axis|SBI|Kotak|Yes|IDBI|BOI|PNB|Union|IndusInd)\\s+Bank\\s*"), "")
            .replace(Regex("^(ICICI|HDFC|Axis|SBI|Kotak|Yes|IDBI|BOI|PNB)\\s*"), "")

            // Remove account/card info
            .replace(Regex("(Acct|Account|Card|A/c)\\s+(XX\\d+|ending\\s+XX\\d+|\\w+\\s+\\d+)"), "")
            .replace(Regex("(ref|Ref|reference|REF).*?:\\s*\\w+"), "")

            // Remove common suffixes and URLs
            .replace(Regex("(For help|For details|Download|Reply|Visit).*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("www\\.\\S+"), "")

            // Remove excessive whitespace
            .replace(Regex("\\s{2,}"), " ")
            .trim()

        return cleaned.takeIf { it.isNotBlank() }
    }

    private fun isLikelyFinancial(body: String): Boolean {
        val indicators = listOf(
            "debited", "credited", "debit", "credit", "amount", "balance",
            "rupees", "inr", "₹", "paid", "spent", "received", "transferred",
            "atm", "upi", "neft", "rtgs", "imps", "card", "account", "acct"
        )
        val lower = body.lowercase()
        return indicators.any { lower.contains(it) }
    }

    /**
     * Batch clean multiple SMS bodies.
     * Uses quickClean for speed; returns a map of original → cleaned.
     */
    fun batchQuickClean(bodies: List<String>): Map<String, String> {
        return bodies.associateWith { quickClean(it) ?: it }
    }
}
