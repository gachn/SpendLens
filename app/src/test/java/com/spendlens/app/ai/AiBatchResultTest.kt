package com.spendlens.app.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for [AiBatchResult] parsing of (possibly batched) AI classification responses. */
class AiBatchResultTest {

    @Test fun `single object shape for a count-1 batch`() {
        val raw = """{"isFinancial": true, "name": "HDFC Card", "senderRegex": null, "bodyRegex": "(?<amount>\\d+)"}"""
        val results = AiBatchResult.parseBatch(1, raw)
        assertEquals(1, results.size)
        val r = results[0]!!
        assertTrue(r.isFinancial)
        assertEquals("HDFC Card", r.name)
        assertNull(r.senderRegex)
        assertEquals("(?<amount>\\d+)", r.bodyRegex)
    }

    @Test fun `array shape maps positionally for a multi-sms batch`() {
        val raw = """
            [
              {"isFinancial": true, "name": "A", "senderRegex": null, "bodyRegex": "(?<amount>\\d+)"},
              {"isFinancial": false, "name": null, "senderRegex": null, "bodyRegex": null}
            ]
        """.trimIndent()
        val results = AiBatchResult.parseBatch(2, raw)
        assertEquals(2, results.size)
        assertTrue(results[0]!!.isFinancial)
        assertEquals(false, results[1]!!.isFinancial)
        assertNull(results[1]!!.bodyRegex)
    }

    @Test fun `truncated array pads missing indices with null`() {
        val raw = """[{"isFinancial": true, "name": "A", "senderRegex": null, "bodyRegex": "(?<amount>\\d+)"}]"""
        val results = AiBatchResult.parseBatch(3, raw)
        assertEquals(3, results.size)
        assertTrue(results[0]!!.isFinancial)
        assertNull(results[1])
        assertNull(results[2])
    }

    @Test fun `malformed json yields all-null results`() {
        val results = AiBatchResult.parseBatch(2, "not json at all")
        assertEquals(listOf(null, null), results)
    }

    @Test fun `invalid regex in bodyRegex is dropped but classification survives`() {
        val raw = """{"isFinancial": true, "name": "A", "senderRegex": null, "bodyRegex": "(unclosed["}"""
        val r = AiBatchResult.parseBatch(1, raw)[0]!!
        assertTrue(r.isFinancial)
        assertNull(r.bodyRegex)
    }

    @Test fun `markdown fences and surrounding text around the array are ignored`() {
        val raw = "```json\n[{\"isFinancial\": false, \"name\": null, \"senderRegex\": null, \"bodyRegex\": null}]\n```"
        val results = AiBatchResult.parseBatch(1, raw)
        assertEquals(false, results[0]!!.isFinancial)
    }

    @Test fun `reminder is financial but flagged distinctly from a completed spend`() {
        val raw = """
            {"isFinancial": true, "isReminder": true, "name": null, "senderRegex": null, "bodyRegex": null}
        """.trimIndent()
        val r = AiBatchResult.parseBatch(1, raw)[0]!!
        assertTrue(r.isFinancial)
        assertTrue(r.isReminder)
        assertNull(r.bodyRegex)
    }

    @Test fun `isReminder defaults to false when absent`() {
        val raw = """{"isFinancial": true, "name": "A", "senderRegex": null, "bodyRegex": "(?<amount>\\d+)"}"""
        val r = AiBatchResult.parseBatch(1, raw)[0]!!
        assertEquals(false, r.isReminder)
    }
}
