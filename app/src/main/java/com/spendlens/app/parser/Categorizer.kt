package com.spendlens.app.parser

/**
 * Maps a counterparty to a category id using keyword rules (most specific match by
 * longest keyword wins). Rules are loaded from the DB; a user re-categorisation adds
 * a USER rule. docs/DESIGN.md §5.
 */
class Categorizer(private val rules: List<Rule>) {

    data class Rule(val matcher: String, val categoryId: Long)

    private val ordered = rules.sortedByDescending { it.matcher.length }

    fun categorize(counterparty: String): Long? {
        val hay = counterparty.lowercase()
        return ordered.firstOrNull { hay.contains(it.matcher) }?.categoryId
    }
}
