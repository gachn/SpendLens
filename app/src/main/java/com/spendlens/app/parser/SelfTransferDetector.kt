package com.spendlens.app.parser

import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.parser.model.ParsedTransaction

/**
 * A self-transfer is money moved between two of the user's own accounts. Since the
 * app only ever reads the user's own SMS, every account it sees is the user's — so an
 * opposite-direction transaction of the same amount on a *different* account within a
 * short window is the other leg of a transfer. Both legs are excluded from spend/income.
 * docs/DESIGN.md §4.
 */
object SelfTransferDetector {

    const val WINDOW_MS = 600_000L // 10 minutes

    fun isCounterLeg(candidate: ParsedTransaction, other: TransactionEntity): Boolean =
        other.amountMinor == candidate.amountMinor &&
            other.direction != candidate.direction.name &&
            other.accountKey != candidate.accountKey &&
            !other.isDuplicate &&
            kotlin.math.abs(other.occurredAt - candidate.occurredAt) <= WINDOW_MS
}
