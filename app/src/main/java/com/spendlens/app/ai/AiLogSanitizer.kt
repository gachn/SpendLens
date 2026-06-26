package com.spendlens.app.ai

/**
 * Pure helpers that prepare AI prompt/response text for [AppLog] before it reaches New Relic.
 * Masks PII (via [Pii]) and caps length so log payloads stay useful without leaking secrets.
 */
object AiLogSanitizer {

    const val MAX_LOG_CHARS = 4_000

    private val bearerToken = Regex("(?i)Bearer\\s+\\S+")

    /** Mask PII and truncate [prompt] for safe log emission. */
    fun sanitizePrompt(prompt: String): String = truncate(Pii.mask(prompt))

    /** Truncate [content] for response logging; no PII mask (model output is already structured JSON). */
    fun sanitizeResponse(content: String): String = truncate(content)

    /** Strip auth headers/tokens that might appear in error bodies. */
    fun redactSecrets(text: String): String = text.replace(bearerToken, "Bearer [REDACTED]")

    private fun truncate(text: String): String {
        if (text.length <= MAX_LOG_CHARS) return text
        return text.take(MAX_LOG_CHARS) + "…(${text.length} chars total)"
    }
}
