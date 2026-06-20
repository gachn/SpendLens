package com.spendlens.app.parser

import com.spendlens.app.ai.HeuristicPatternGenerator
import com.spendlens.app.parser.model.CompiledPattern
import com.spendlens.app.parser.model.SmsMessage
import com.spendlens.app.parser.model.TxnDirection
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserTest {

    private val engine = PatternEngine()

    private val builtins: List<CompiledPattern> = BuiltinPatterns.seeds
        .sortedByDescending { it.priority }
        .mapIndexed { i, seed ->
            CompiledPattern(
                id = i.toLong(),
                priority = seed.priority,
                body = Regex(seed.bodyRegex),
                sender = seed.senderRegex?.let { Regex(it) },
            )
        }

    private fun sms(body: String, sender: String = "VK-HDFCBK") =
        SmsMessage(sender, body, 1_700_000_000_000L)

    @Test
    fun `parses generic debit alert`() {
        val r = engine.match(
            sms("Rs.1,250.00 debited from a/c XX1234 at AMAZON. Avl bal Rs.5,000.00 Ref 998877"),
            builtins,
        )
        assertNotNull(r)
        assertEquals(125000L, r!!.transaction.amountMinor)
        assertEquals(TxnDirection.DEBIT, r.transaction.direction)
        assertEquals("INR", r.transaction.currency)
    }

    @Test
    fun `parses credit alert`() {
        val r = engine.match(sms("INR 45,000.00 credited to a/c XX2222 by ACME. Ref 111222"), builtins)
        assertNotNull(r)
        assertEquals(4_500_000L, r!!.transaction.amountMinor)
        assertEquals(TxnDirection.CREDIT, r.transaction.direction)
    }

    @Test
    fun `parses upi sent`() {
        val r = engine.match(sms("Rs.500 sent to john@okhdfc via UPI Ref 123456789"), builtins)
        assertNotNull(r)
        assertEquals(50000L, r!!.transaction.amountMinor)
        assertEquals(TxnDirection.DEBIT, r.transaction.direction)
    }

    @Test
    fun `parses card spend`() {
        val r = engine.match(sms("Spent Rs.2000 on card XX9999 at STARBUCKS"), builtins)
        assertNotNull(r)
        assertEquals(200000L, r!!.transaction.amountMinor)
        assertEquals(TxnDirection.DEBIT, r.transaction.direction)
    }

    @Test
    fun `filter rejects otp and accepts transaction`() {
        assertTrue(!FinancialSmsFilter.isFinancial(sms("Your OTP is 123456. Do not share. Rs.500")))
        assertTrue(FinancialSmsFilter.isFinancial(sms("Rs.500 debited from a/c XX1")))
        assertTrue(!FinancialSmsFilter.isFinancial(sms("Hi, see you at 5pm")))
    }

    @Test
    fun `heuristic learns a format the builtins miss`() = runTest {
        // Verb precedes amount → built-ins don't match.
        val unseen = sms("Your account was debited with INR 320.50 for ELECTRICITY bill.")
        assertNull("built-ins should miss this", engine.match(unseen, builtins))

        val gen = HeuristicPatternGenerator().generate(unseen.body, unseen.sender)
        assertNotNull("heuristic should produce a pattern", gen)

        val learned = CompiledPattern(99, 60, Regex(gen!!.bodyRegex), gen.senderRegex?.let { Regex(it) })
        val r = engine.match(unseen, listOf(learned))
        assertNotNull("learned pattern must re-match its source", r)
        assertEquals(32050L, r!!.transaction.amountMinor)
        assertEquals(TxnDirection.DEBIT, r.transaction.direction)
    }

}
