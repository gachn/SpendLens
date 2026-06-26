package com.spendlens.app.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure helper functions in [PatternApplyWorker]:
 * - [PatternApplyWorker.patternIdsFromData] — deserialises the comma-joined string of pattern IDs
 *   stored as WorkManager input data.
 * - [PatternApplyWorker.patternIdsToString] — serialises a list of Long IDs into the work-data
 *   string.
 *
 * These functions contain all the branching logic for targeted vs full reprocess; the rest of the
 * worker delegates to [com.spendlens.app.sms.SmsProcessor] which is tested separately.
 */
class PatternApplyWorkerLogicTest {

    // ─────────────────────────────────────────────────────────────────────────
    // patternIdsFromData — deserialisation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `patternIdsFromData returns empty list for null input`() {
        val result = PatternApplyWorker.patternIdsFromData(null)
        assertTrue("Expected empty list for null", result.isEmpty())
    }

    @Test
    fun `patternIdsFromData returns empty list for blank string`() {
        val result = PatternApplyWorker.patternIdsFromData("   ")
        assertTrue("Expected empty list for blank string", result.isEmpty())
    }

    @Test
    fun `patternIdsFromData parses a single ID`() {
        assertEquals(listOf(42L), PatternApplyWorker.patternIdsFromData("42"))
    }

    @Test
    fun `patternIdsFromData parses multiple comma-separated IDs`() {
        assertEquals(listOf(1L, 2L, 3L), PatternApplyWorker.patternIdsFromData("1,2,3"))
    }

    @Test
    fun `patternIdsFromData ignores non-numeric tokens`() {
        assertEquals(listOf(1L, 3L), PatternApplyWorker.patternIdsFromData("1,abc,3"))
    }

    @Test
    fun `patternIdsFromData trims whitespace around IDs`() {
        assertEquals(listOf(1L, 2L, 3L), PatternApplyWorker.patternIdsFromData(" 1 , 2 , 3 "))
    }

    @Test
    fun `patternIdsFromData handles large ID values`() {
        assertEquals(listOf(9_999_999_999L), PatternApplyWorker.patternIdsFromData("9999999999"))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // patternIdsToString — serialisation
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `patternIdsToString returns null for empty list`() {
        assertEquals(null, PatternApplyWorker.patternIdsToString(emptyList()))
    }

    @Test
    fun `patternIdsToString serialises single ID`() {
        assertEquals("7", PatternApplyWorker.patternIdsToString(listOf(7L)))
    }

    @Test
    fun `patternIdsToString serialises multiple IDs as comma-separated string`() {
        assertEquals("1,2,3", PatternApplyWorker.patternIdsToString(listOf(1L, 2L, 3L)))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Round-trip: serialise then deserialise
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `round-trip serialisation preserves ID list`() {
        val ids = listOf(10L, 20L, 30L)
        val serialised = PatternApplyWorker.patternIdsToString(ids)
        assertEquals(ids, PatternApplyWorker.patternIdsFromData(serialised))
    }

    @Test
    fun `round-trip with empty list yields empty on deserialisation`() {
        val serialised = PatternApplyWorker.patternIdsToString(emptyList())
        assertTrue(PatternApplyWorker.patternIdsFromData(serialised).isEmpty())
    }
}
