package com.spendlens.app.parser.model

/**
 * Pure-Kotlin domain models for parsing. No Android imports here so this whole
 * package is JVM-unit-testable and extractable into a Kotlin Multiplatform module
 * later (see docs/CROSS_PLATFORM.md). docs/DESIGN.md §1.
 */

enum class TxnDirection { DEBIT, CREDIT }

enum class Channel { UPI, CARD, ATM, NETBANKING, IMPS, NEFT, RTGS, WALLET, UNKNOWN }

/** A single SMS as seen by the parser. */
data class SmsMessage(
    val sender: String,
    val body: String,
    val receivedAt: Long,
)

/** Output of a successful parse. Amounts are integer minor units. */
data class ParsedTransaction(
    val amountMinor: Long,
    val currency: String,
    val direction: TxnDirection,
    val accountKey: String,
    val counterparty: String,
    val balanceMinor: Long?,
    val referenceId: String?,
    val occurredAt: Long,
    val channel: Channel,
)

/** A credit-card statement/bill parsed from an SMS. Amounts are integer minor units. */
data class CardBill(
    val cardKey: String,
    val totalDueMinor: Long,
    val minDueMinor: Long?,
    val currency: String,
    val dueDate: Long?,
    val statementAt: Long,
    /** Day of month (1-31) on which the statement was generated, derived from [statementAt]. */
    val statementCycleDay: Int = 0,
)

/** A credit-card payment detected from an SMS (e.g. "payment of Rs.X received"). */
data class CardPayment(
    val cardKey: String,
    val amountMinor: Long,
    val currency: String,
    val paidAt: Long,
)

/** A standalone balance snapshot from an SMS not tied to a transaction amount. */
data class BalanceSnapshot(
    val accountKey: String,
    val balanceMinor: Long,
    val isCard: Boolean,
    val currency: String,
    val observedAt: Long,
)

/** A pattern compiled and ready for matching by [com.spendlens.app.parser.PatternEngine]. */
data class CompiledPattern(
    val id: Long,
    val priority: Int,
    val body: Regex,
    val sender: Regex?,
)

data class MatchResult(val patternId: Long, val transaction: ParsedTransaction)

/** A pattern produced by a [com.spendlens.app.ai.PatternGenerator], before validation/persistence. */
data class GeneratedPattern(
    val name: String,
    val bodyRegex: String,
    val senderRegex: String?,
    val fieldNotes: String,
)
