package com.spendlens.app.ai

/**
 * Masks sensitive values before an SMS template is handed to any [PatternGenerator].
 * Mandatory on every path that could reach a remote provider (NFR-1.5). The masked
 * text keeps message *structure* but removes account numbers and long digit runs.
 */
object Pii {
    private val longDigits = Regex("\\d{4,}")
    private val accountMask = Regex("(?i)([xX*]{2,}\\d{2,})")

    fun mask(body: String): String =
        body
            .replace(accountMask, "ACCT")
            .replace(longDigits, "#")
}
