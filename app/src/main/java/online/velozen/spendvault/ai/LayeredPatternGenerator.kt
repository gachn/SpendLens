package com.spendlens.app.ai

import com.spendlens.app.parser.model.GeneratedPattern

/**
 * Tries [primary] first, falling back to [fallback] when primary is unusable (e.g. Free plan, AI
 * off, no key) or proposes nothing. Masks the body itself when handing it to whichever generator
 * declares [PatternGenerator.requiresMasking], so a heuristic fallback still sees the real
 * message structure it needs even when the primary is a remote AI provider. docs/DESIGN.md §3.4.
 */
class LayeredPatternGenerator(
    private val primary: PatternGenerator,
    private val fallback: PatternGenerator,
) : PatternGenerator {

    override val requiresMasking: Boolean get() = false

    override suspend fun generate(body: String, sender: String): GeneratedPattern? {
        val primaryInput = if (primary.requiresMasking) Pii.mask(body) else body
        primary.generate(primaryInput, sender)?.let { return it }

        val fallbackInput = if (fallback.requiresMasking) Pii.mask(body) else body
        return fallback.generate(fallbackInput, sender)
    }
}
