package com.spendlens.app.ai

import com.spendlens.app.parser.model.GeneratedPattern

/** Pure pattern-only mode: never proposes a new pattern. */
class StubPatternGenerator : PatternGenerator {
    override suspend fun generate(maskedBody: String, sender: String): GeneratedPattern? = null
}
