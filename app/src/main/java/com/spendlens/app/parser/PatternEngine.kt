package com.spendlens.app.parser

import com.spendlens.app.parser.model.CompiledPattern
import com.spendlens.app.parser.model.MatchResult
import com.spendlens.app.parser.model.ParsedTransaction
import com.spendlens.app.parser.model.SmsMessage

/**
 * Matches an SMS against the supplied patterns (already ordered by priority desc)
 * and extracts a [ParsedTransaction] from the first specific match. Pure Kotlin.
 * docs/DESIGN.md §3.2.
 */
class PatternEngine {

    fun match(sms: SmsMessage, patterns: List<CompiledPattern>): MatchResult? {
        for (pattern in patterns) {
            if (pattern.sender != null && !pattern.sender.containsMatchIn(sms.sender)) continue
            val m = pattern.body.find(sms.body) ?: continue

            val amount = Normalize.amountToMinor(m.groupOrNull("amount")) ?: continue
            val txn = ParsedTransaction(
                amountMinor = amount,
                currency = Normalize.currency(m.groupOrNull("curr")),
                direction = Normalize.direction(m.groupOrNull("dir")),
                accountKey = Normalize.maskAccount(m.groupOrNull("account")),
                counterparty = Normalize.cleanParty(m.groupOrNull("party")),
                balanceMinor = Normalize.amountToMinor(m.groupOrNull("balance")),
                referenceId = m.groupOrNull("ref"),
                occurredAt = sms.receivedAt,
                channel = Normalize.channel(sms.body),
            )
            return MatchResult(pattern.id, txn)
        }
        return null
    }
}

/** Returns the value of a named group, or null if it doesn't exist / didn't participate. */
internal fun kotlin.text.MatchResult.groupOrNull(name: String): String? =
    try {
        groups[name]?.value?.trim()?.ifBlank { null }
    } catch (_: IllegalArgumentException) {
        null // pattern has no group of this name
    }
