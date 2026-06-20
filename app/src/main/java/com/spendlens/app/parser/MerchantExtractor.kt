package com.spendlens.app.parser

/**
 * Pulls the raw merchant/counterparty span out of an SMS, trying the common structures
 * banks use (UPI VPA, "Merchant:" field, the standalone merchant line in card-spend alerts,
 * "Info:" field, "spent at X", "to/from X"). The raw span is then cleaned by
 * [MerchantNormalizer] and resolved to a canonical brand name. Pure Kotlin. docs/DESIGN.md §3.2.
 */
object MerchantExtractor {

    private val vpa = Regex("(?i)(?:\\bto|\\bfrom|\\bvpa|\\bvia)\\s+([a-z0-9][a-z0-9._-]+@[a-z]{2,})")
    private val merchantField = Regex("(?i)\\bmerchant\\s*[:\\-]\\s*([A-Za-z0-9][A-Za-z0-9 &._/-]{1,39})")
    // Card-spend alerts (Axis/HDFC/etc.) print the merchant on its own line just above the
    // "Avl Limit"/"Avl Bal" line, with no "at/to" cue: e.g. "...\nSWIGGY IN\nAvl Limit: INR ...".
    private val beforeAvlLine = Regex("(?im)\\n\\s*([A-Za-z0-9][A-Za-z0-9 &.*_/-]{1,39})\\s*\\n\\s*Avl\\s*(?:Limit|Bal)")
    // ICICI UPI format: "ICICI Bank Acct XX debited for Rs X; MERCHANT credited. UPI:..."
    private val iciciParty = Regex("(?i);\\s*([A-Za-z][A-Za-z0-9 &._-]{1,38})\\s+(?:credited|received)\\b")
    private val info = Regex("(?i)\\binfo[:\\-/\\s]+([A-Za-z0-9][A-Za-z0-9 &._/-]{1,39})")
    // Include * so aggregator-prefixed names like "CAS*Swiggy" / "RAZ*Swiggy" are captured whole.
    private val atMerchant = Regex("(?i)\\bat\\s+([A-Za-z0-9][A-Za-z0-9 &.*_/-]{1,39}?)(?:\\s+on\\b|\\s+ref\\b|[.,;]|$)")
    private val toMerchant = Regex("(?i)\\b(?:to|from)\\s+([A-Za-z][A-Za-z0-9 &._-]{1,39}?)(?:\\s+on\\b|\\s+ref\\b|[.,;]|$)")

    fun extract(body: String): String? {
        vpa.find(body)?.group()?.let { return it } // VPA carries the merchant handle
        merchantField.find(body)?.group()?.let { if (plausible(it)) return it }
        beforeAvlLine.find(body)?.group()?.let { if (plausible(it)) return it }
        iciciParty.find(body)?.group()?.let { if (plausible(it)) return it }
        atMerchant.find(body)?.group()?.let { if (plausible(it)) return it }
        info.find(body)?.group()?.let { if (plausible(it)) return it }
        toMerchant.find(body)?.group()?.let { if (plausible(it)) return it }
        return null
    }

    /**
     * Derives a merchant hint from the SMS sender when body parsing fails.
     * Indian banking SMS senders are often "XX-BRAND-Y" (XX = operator prefix, Y = route);
     * e.g. "VM-SWIGGY-S" → "SWIGGY". Returns null when the brand is a bank/aggregator/generic
     * word, since those are not the merchant for a card or UPI transaction.
     */
    fun extractFromSender(sender: String): String? {
        if (sender.isBlank()) return null
        var brand = OPERATOR_PREFIX.find(sender.trim())?.groupValues?.getOrNull(1)?.trim() ?: sender.trim()
        // Drop a trailing route suffix like "-S" / "-T" / "-P".
        brand = brand.replace(Regex("-[A-Za-z]$"), "").trim()
        return brand.takeIf {
            it.length in 2..30 &&
                it.any { c -> c.isLetter() } &&
                !SENDER_STOPWORDS.containsMatchIn(it) &&
                !BANK_SENDERS.contains(it.uppercase())
        }
    }

    private fun MatchResult.group(): String? = groupValues.getOrNull(1)?.trim()?.ifBlank { null }

    private fun plausible(s: String): Boolean {
        val t = s.trim()
        if (t.length !in 2..40 || t.none { it.isLetter() }) return false
        return !STOPWORDS.containsMatchIn(t)
    }

    private val STOPWORDS = Regex(
        "(?i)^(your|a/c|account|avl|bal|balance|the|inr|rs|upi|info|ref|txn|not|you|" +
            "report|block|call|reissue|dispute|contact|visit|click|here|now|free)\\b",
    )

    private val OPERATOR_PREFIX = Regex("""^[A-Za-z]{2}-(.+)$""")

    // Generic / infrastructure words that are not useful as merchant names.
    private val SENDER_STOPWORDS = Regex(
        "(?i)^(alerts?|notify|info|bank|update|credit|debit|service|secure|verify|otp|pay|txn|msg)$",
    )

    // Bank/aggregator sender tokens — the merchant lives in the body for these, not the sender.
    private val BANK_SENDERS = setOf(
        "AXISBK", "HDFCBK", "ICICIB", "ICICIT", "SBIINB", "SBICRD", "SBIBNK", "KOTAKB", "KOTAK",
        "PNBSMS", "BOIIND", "CANBNK", "CBSSBI", "YESBNK", "IDFCFB", "INDUSB", "FEDBNK", "RBLBNK",
        "AUBANK", "BOBTXN", "BOBSMS", "CENTBK", "UNIONB", "UCOBNK", "DBSBNK", "HSBCIM", "CITIBK",
        "AMEXIN", "SCBANK", "PAYTMB", "AIRBNK", "ONECRD", "SLICE", "ZERODHA", "NSESMS", "BSELTD",
        "AXISBANK", "HDFCBANK",
    )
}
