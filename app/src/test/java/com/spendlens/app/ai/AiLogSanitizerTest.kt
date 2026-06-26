package com.spendlens.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [AiLogSanitizer] — PII-safe, length-bounded strings for New Relic logs. */
class AiLogSanitizerTest {

    @Test
    fun `sanitizePrompt masks long digit runs via Pii`() {
        val raw = "Body: \"debited INR 4500.00 from card XX1234567890 at SWIGGY\""
        val sanitized = AiLogSanitizer.sanitizePrompt(raw)
        assertFalse(sanitized.contains("1234567890"))
        assertTrue(sanitized.contains("SWIGGY"))
    }

    @Test
    fun `sanitizePrompt truncates very long prompts with total length hint`() {
        val longBody = "x".repeat(AiLogSanitizer.MAX_LOG_CHARS + 500)
        val sanitized = AiLogSanitizer.sanitizePrompt(longBody)
        assertTrue(sanitized.length < longBody.length)
        assertTrue(sanitized.contains("${longBody.length} chars total"))
    }

    @Test
    fun `sanitizeResponse keeps short JSON preview`() {
        val json = """{"bodyRegex":"(?<amount>\\d+)","cleanMerchant":"Swiggy"}"""
        assertEquals(json, AiLogSanitizer.sanitizeResponse(json))
    }

    @Test
    fun `redactSecrets removes bearer tokens`() {
        val text = "Authorization: Bearer sk-secret-key-12345"
        val redacted = AiLogSanitizer.redactSecrets(text)
        assertEquals("Authorization: Bearer [REDACTED]", redacted)
    }

    @Test
    fun `sanitizeResponse truncates long model output`() {
        val long = "a".repeat(AiLogSanitizer.MAX_LOG_CHARS + 100)
        val sanitized = AiLogSanitizer.sanitizeResponse(long)
        assertTrue(sanitized.contains("${long.length} chars total"))
    }
}
