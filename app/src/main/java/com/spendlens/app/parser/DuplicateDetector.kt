package com.spendlens.app.parser

import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.parser.model.ParsedTransaction

/**
 * Classifies a freshly parsed transaction against existing candidates that already
 * share amount + account + direction (supplied by the caller). docs/DESIGN.md §4.
 */
object DuplicateDetector {

    /** ± window for treating two transactions of the same value as the same event. */
    const val WINDOW_MS = 180_000L

    sealed interface Verdict {
        data object Unique : Verdict
        /** Same event for certain (matching reference id, or identical content). */
        data class Duplicate(val originalId: Long, val groupId: String) : Verdict
        /** Likely the same event — flag for user review rather than auto-dropping. */
        data class Probable(val originalId: Long, val groupId: String) : Verdict
    }

    fun classify(candidate: ParsedTransaction, existing: List<TransactionEntity>): Verdict {
        if (existing.isEmpty()) return Verdict.Unique

        // Strong: matching, non-blank reference id.
        candidate.referenceId?.takeIf { it.isNotBlank() }?.let { ref ->
            existing.firstOrNull { it.referenceId == ref }?.let {
                return Verdict.Duplicate(it.id, it.dupGroupId ?: "grp-${it.id}")
            }
        }

        // Heuristic: within time window and similar counterparty.
        val party = normalizeParty(candidate.counterparty)
        existing.firstOrNull { e ->
            kotlin.math.abs(e.occurredAt - candidate.occurredAt) <= WINDOW_MS &&
                normalizeParty(e.counterparty) == party
        }?.let {
            return Verdict.Probable(it.id, it.dupGroupId ?: "grp-${it.id}")
        }

        return Verdict.Unique
    }

    private fun normalizeParty(p: String): String =
        p.lowercase().filter { it.isLetterOrDigit() }
}
