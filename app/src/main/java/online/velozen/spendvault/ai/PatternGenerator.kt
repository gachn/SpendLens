package com.spendlens.app.ai

import com.spendlens.app.parser.model.GeneratedPattern

/**
 * The pluggable AI slot. Given an SMS whose PII has already been masked
 * ([Pii.mask]), propose a pattern that can extract a transaction from messages of
 * this format — or null if it can't.
 *
 * v1 ships [HeuristicPatternGenerator] (no network). A remote LLM-backed
 * implementation can be dropped in behind this same interface later; masking is
 * applied by the caller so a remote provider only ever sees the template.
 * docs/DESIGN.md §3.4.
 */
interface PatternGenerator {
    /**
     * If true, the caller masks PII ([Pii.mask]) before calling [generate] — required
     * for any provider that sends text off-device. On-device generators set this false
     * so they can build a regex from the real message structure.
     */
    val requiresMasking: Boolean get() = false

    suspend fun generate(body: String, sender: String): GeneratedPattern?
}
