package com.spendlens.app.parser

/**
 * Extracts the card/account tail from an SMS, tolerant of the many ways banks write
 * it: "card ending 1234", "card no XX1234", "a/c XXXXXX1234", "ending with 1234".
 * Returns a masked "••••1234" or null. Pure Kotlin. docs/DESIGN.md §3.2.
 */
object AccountExtractor {

    private val patterns = listOf(
        // card no / number / ending [with|in] 1234
        Regex("(?i)\\bcard\\s*(?:no\\.?|number|ending(?:\\s*(?:with|in))?)?\\s*[:#]?\\s*[x*]{0,}(\\d{4})\\b"),
        // a/c | ac | acct | account [no] XXXX1234
        Regex("(?i)\\b(?:a/c|ac|acct|account)\\s*(?:no\\.?|number)?\\s*[:#]?\\s*[x*]{0,}(\\d{3,4})\\b"),
        // generic "ending with 1234" / "ends in 1234"
        Regex("(?i)\\b(?:ending|ends)\\s*(?:with|in)?\\s*(\\d{4})\\b"),
        // masked token like XXXX1234 / **1234 anywhere
        Regex("[xX*]{2,}(\\d{3,4})\\b"),
    )

    fun extract(body: String): String? {
        for (re in patterns) {
            val tail = re.find(body)?.groupValues?.getOrNull(1)
            if (!tail.isNullOrBlank()) return "••••" + tail.takeLast(4)
        }
        return null
    }
}
