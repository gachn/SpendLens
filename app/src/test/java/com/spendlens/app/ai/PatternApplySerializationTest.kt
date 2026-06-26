package com.spendlens.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [AiPatternTeacher.normalizeBodyRegex] — the function that fixes common AI regex
 * mistakes before a pattern is saved to the database.
 *
 * Note: the earlier PendingMapping / merchant-alias serialization tests have been removed because
 * that mechanism was replaced by system-driven merchant detection (MerchantExtractor pipeline).
 * The AI now only contributes the regex; merchant name / category are determined by the app.
 */
class PatternApplySerializationTest {

    /** Helper to build (sender, body) pairs. */
    private fun senderBodies(vararg pairs: Pair<String, String>) = pairs.toList()

    // ─────────────────────────────────────────────────────────────────────────
    // Python → Java named-group conversion
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `normalizeBodyRegex converts Python-style named groups to Java style`() {
        val pythonStyle = """^(?P<account>XX\d{4})\s+debited"""
        val samples = senderBodies("JD-ICICIT-S" to "XX1234 debited for Rs 100")
        val result = AiPatternTeacher.normalizeBodyRegex(pythonStyle, null, samples)
        assertFalse("Should not contain (?P< syntax", result.contains("(?P<"))
        assertTrue("Should contain Java-style (?< syntax", result.contains("(?<"))
    }

    @Test
    fun `normalizeBodyRegex full ICICI pattern fix — strips caret and relaxes digit count`() {
        // Reproduces the real bug: AI generates ^(?P<account>XX\d{3})... for an ICICI SMS that
        // starts with "ICICI Bank Acct XX1234..." (prefix) and uses 4 digits, not 3.
        val aiRegex = """^(?P<account>XX\d{3})\s+(?P<dir>debited|credited)\s+for\s+(?P<curr>Rs|INR)\s+(?P<amount>\d+(?:\.\d{2})?)"""
        val icicBody = "ICICI Bank Acct XX1234 debited for Rs 92.00 on 25-Jun-26; THE PET PROJECT credited."
        val samples = senderBodies("JD-ICICIT-S" to icicBody)
        val result = AiPatternTeacher.normalizeBodyRegex(aiRegex, null, samples)
        assertTrue(
            "Normalised pattern must match the ICICI SMS body: $result",
            Regex(result, RegexOption.IGNORE_CASE).containsMatchIn(icicBody),
        )
        assertFalse("Must not contain (?P<", result.contains("(?P<"))
        assertFalse("Must not start with ^", result.startsWith("^"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Already-correct patterns returned unchanged
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `normalizeBodyRegex returns Java-style regex unchanged when it already matches`() {
        val regex = """(?i)Rs\.?\s*(?<amount>[\d,]+)\s+debited"""
        val samples = senderBodies("VK-HDFCBK" to "Rs.1250 debited from a/c XX1234")
        assertEquals(regex, AiPatternTeacher.normalizeBodyRegex(regex, null, samples))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // ^ anchor stripping
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `normalizeBodyRegex strips leading caret when it prevents match`() {
        // Regex anchored to ^ but body starts with "ICICI Bank Acct " prefix.
        // Also uses Python-style (?P<...>) which must be converted to Java (?<...>).
        val withCaret = """^(?P<amount>\d+)\s+debited"""
        val samples = senderBodies("JD-ICICIT-S" to "ICICI Bank Acct XX1234 92 debited for Rs 92.00")
        val result = AiPatternTeacher.normalizeBodyRegex(withCaret, null, samples)
        assertFalse("Should strip the leading ^", result.startsWith("^"))
        assertTrue("Stripped pattern must contain Java-style named group", result.contains("""(?<amount>\d+)\s+debited"""))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Digit quantifier relaxation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `normalizeBodyRegex relaxes exact digit count to allow more digits`() {
        // AI generated \d{3} but ICICI uses 4-digit account suffixes (XX1234).
        // Also has Python-style (?P<...>) and leading ^ — all three normalisations stack.
        val withExactCount = """^(?P<account>XX\d{3})\s+debited"""
        val icicBody = "ICICI Bank Acct XX1234 debited for Rs 92.00 on 25-Jun-26; THE PET PROJECT credited."
        val samples = senderBodies("JD-ICICIT-S" to icicBody)
        val result = AiPatternTeacher.normalizeBodyRegex(withExactCount, null, samples)
        assertTrue(
            "Normalized pattern should match the ICICI SMS body: $result",
            Regex(result, RegexOption.IGNORE_CASE).containsMatchIn(icicBody),
        )
        assertFalse("Must not start with ^", result.startsWith("^"))
        assertFalse("Must not contain (?P<", result.contains("(?P<"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fallback behaviour
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `normalizeBodyRegex returns Java-compatible version when normalization cannot help`() {
        // Regex with a completely wrong keyword — no normalisation can fix it — but (?P< is still
        // converted so the pattern at least compiles in Java.
        val bad = "^COMPLETELY_WRONG_PREFIX(?P<amount>\\d+)"
        val samples = senderBodies("VK-HDFCBK" to "Rs.1250 debited from a/c XX1234")
        val result = AiPatternTeacher.normalizeBodyRegex(bad, null, samples)
        assertFalse("Must not contain (?P<", result.contains("(?P<"))
        assertTrue("Must contain Java-style (?<", result.contains("(?<"))
    }

    @Test
    fun `normalizeBodyRegex filters by sender when senderRegex is provided`() {
        // Pattern only matches HDFC sender — ICICI SMS should NOT count as a valid sample.
        val regex = """(?i)Rs\.?\s*(?<amount>[\d,]+)\s+debited"""
        val hdfcSender = Regex("(?i)HDFCBK")
        val mixedSamples = senderBodies(
            "VK-HDFCBK" to "Rs.1250 debited from a/c XX1234",
            "JD-ICICIT-S" to "no match here",
        )
        val result = AiPatternTeacher.normalizeBodyRegex(regex, hdfcSender, mixedSamples)
        assertEquals(regex, result) // original already matches the HDFC row; returned unchanged
    }
}
