package com.spendlens.app.ai

import com.spendlens.app.parser.model.GeneratedPattern
import java.util.regex.Pattern

/**
 * On-device, no-network pattern learner. Locates the amount, direction verb, and
 * (optionally) account / reference / counterparty spans in a real SMS, then emits a
 * named-group regex by replacing those spans with capture groups and quoting the
 * literal scaffolding between them. The result re-matches its source message and any
 * future SMS of the same template. docs/DESIGN.md §3.4.
 */
class HeuristicPatternGenerator : PatternGenerator {

    override val requiresMasking: Boolean get() = false

    private val moneyRe = Regex("(?i)(?<curr>inr|rs\\.?|usd|eur|gbp|aed|[₹$€£])\\s?(?<amt>[\\d,]+(?:\\.\\d{1,2})?)")
    private val verbRe = Regex("(?i)\\b(?<dir>$VERBS)\\b")
    private val accountRe = Regex("(?i)(?:a/c|ac|acct|account|card)\\s*(?:no\\.?)?\\s*(?<acc>[xX*]*\\d{3,})")
    private val refRe = Regex("(?i)(?:ref|utr|rrn|txn)\\s*(?:no\\.?|id)?[:#\\s]*(?<r>[A-Za-z0-9]{4,})")
    private val partyRe = Regex("(?i)(?:to|at|from|vpa)\\s+(?<p>[A-Za-z0-9@][A-Za-z0-9@.\\-_ ]{1,38})")
    // ICICI UPI: "… debited for Rs X on …; MERCHANT credited. UPI:…"
    private val iciciPartyRe = Regex("(?i);\\s*(?<p>[A-Za-z][A-Za-z0-9 &._-]{1,38})\\s+(?:credited|received)\\b")
    private val upiRefRe = Regex("(?i)(?:upi|vpa)\\s*[:#]\\s*(?<r>[A-Za-z0-9]{4,})")

    override suspend fun generate(body: String, sender: String): GeneratedPattern? {
        val amount = moneyRe.find(body) ?: return null
        val verb = verbRe.find(body) ?: return null

        // Each span = the source range to replace and the regex to substitute in.
        val spans = mutableListOf<Span>()
        spans += Span(amount.range, CURR_AMOUNT)
        spans += Span(verb.range, "(?<dir>$VERBS)")
        accountRe.find(body)?.groups?.get("acc")?.let { spans += Span(it.range, "(?<account>[xX*]*\\d{3,})") }
        refRe.find(body)?.groups?.get("r")?.let { spans += Span(it.range, "(?<ref>[A-Za-z0-9]{4,})") }
            ?: upiRefRe.find(body)?.groups?.get("r")?.let { spans += Span(it.range, "(?<ref>[A-Za-z0-9]{4,})") }
        partyRe.find(body)?.groups?.get("p")?.let { spans += Span(it.range, "(?<party>[A-Za-z0-9@.\\-_ ]{2,40}?)") }
            ?: iciciPartyRe.find(body)?.groups?.get("p")?.let {
                spans += Span(it.range, "(?<party>[A-Za-z][A-Za-z0-9 &._-]{1,38})")
            }

        // Order by start, drop overlaps (keep the earliest of any overlapping pair).
        val ordered = spans.sortedBy { it.range.first }
        val chosen = mutableListOf<Span>()
        var lastEnd = -1
        for (s in ordered) {
            if (s.range.first > lastEnd) {
                chosen += s
                lastEnd = s.range.last
            }
        }

        val sb = StringBuilder("(?i)")
        var cursor = 0
        for (s in chosen) {
            if (s.range.first > cursor) sb.append(quoteLiteral(body.substring(cursor, s.range.first)))
            sb.append(s.replacement)
            cursor = s.range.last + 1
        }
        // Trailing literal is dropped — the trailing portion (balance etc.) is optional.

        val regex = sb.toString()
        // Compile-check before returning.
        runCatching { Regex(regex) }.getOrNull() ?: return null

        return GeneratedPattern(
            name = "Learned: ${sender.take(16)}",
            bodyRegex = regex,
            senderRegex = if (sender.isNotBlank()) Pattern.quote(sender) else null,
            fieldNotes = "heuristic span-replacement",
        )
    }

    /**
     * Quote literal text, letting runs of whitespace match flexibly. Boundary
     * whitespace is preserved as `\s+` so separators between a literal and an
     * adjacent capture group aren't lost.
     */
    private fun quoteLiteral(text: String): String {
        if (text.isEmpty()) return ""
        if (text.isBlank()) return "\\s+"
        val sb = StringBuilder()
        if (text.first().isWhitespace()) sb.append("\\s+")
        sb.append(
            text.trim().split(Regex("\\s+")).joinToString("\\s+") { Pattern.quote(it) },
        )
        if (text.last().isWhitespace()) sb.append("\\s+")
        return sb.toString()
    }

    private data class Span(val range: IntRange, val replacement: String)

    private companion object {
        const val VERBS =
            "debited|credited|spent|withdrawn|received|paid|sent|deposited|charged|refunded"
        const val CURR_AMOUNT =
            "(?<curr>inr|rs\\.?|usd|eur|gbp|aed|[₹$€£])\\s?(?<amount>[\\d,]+(?:\\.\\d{1,2})?)"
    }
}
