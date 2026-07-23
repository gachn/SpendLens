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
    fun `detects foreign currency on card spend`() {
        val r = engine.match(sms("Spent USD 50.00 on card XX9999 at NETFLIX"), builtins)
        assertNotNull(r)
        assertEquals(5000L, r!!.transaction.amountMinor)
        assertEquals("USD", r.transaction.currency)
    }

    @Test
    fun `detects iso code currencies generically`() {
        val sgd = engine.match(sms("SGD 120.00 debited from a/c XX1234 at GRAB"), builtins)
        assertEquals("SGD", sgd!!.transaction.currency)

        val aed = engine.match(sms("AED 75.50 spent at CARREFOUR from a/c XX9"), builtins)
        assertEquals("AED", aed!!.transaction.currency)

        val jpy = engine.match(sms("¥3000 debited from a/c XX1 at SUSHIRO"), builtins)
        assertEquals("JPY", jpy!!.transaction.currency)
    }

    @Test
    fun `unknown token still defaults to INR`() {
        val r = engine.match(sms("Rs.1,250.00 debited from a/c XX1234 at AMAZON"), builtins)
        assertEquals("INR", r!!.transaction.currency)
    }

    @Test
    fun `filter rejects otp and accepts transaction`() {
        assertTrue(!FinancialSmsFilter.isFinancial(sms("Your OTP is 123456. Do not share. Rs.500")))
        assertTrue(FinancialSmsFilter.isFinancial(sms("Rs.500 debited from a/c XX1")))
        assertTrue(!FinancialSmsFilter.isFinancial(sms("Hi, see you at 5pm")))
    }

    @Test
    fun `heuristic learns a format the builtins miss`() = runTest {
        // "charged with" (verb directly followed by "with", not the currency) matches none of the
        // built-ins — including the "credited/debited with" seed above, which only covers those
        // two verbs, not "charged".
        val unseen = sms("Your card was charged with INR 320.50 for ELECTRICITY bill.")
        assertNull("built-ins should miss this", engine.match(unseen, builtins))

        val gen = HeuristicPatternGenerator().generate(unseen.body, unseen.sender)
        assertNotNull("heuristic should produce a pattern", gen)

        val learned = CompiledPattern(99, 60, Regex(gen!!.bodyRegex), gen.senderRegex?.let { Regex(it) })
        val r = engine.match(unseen, listOf(learned))
        assertNotNull("learned pattern must re-match its source", r)
        assertEquals(32050L, r!!.transaction.amountMinor)
        assertEquals(TxnDirection.DEBIT, r.transaction.direction)
    }

    @Test
    fun `parses ICICI UPI debit with semicolon party`() {
        val body = "ICICI Bank Acct XX5678 debited for Rs 150.00 on 25-Jun-26; THE PET PROJECT credited. UPI:123456789012. Call 1800 for dispute."
        val msg = sms(body, "JD-ICICIT-S")
        val r = engine.match(msg, builtins)
        assertNotNull("built-ins should parse ICICI UPI debit", r)
        assertEquals(15000L, r!!.transaction.amountMinor)
        assertEquals(TxnDirection.DEBIT, r.transaction.direction)
        assertEquals("THE PET PROJECT", r.transaction.counterparty)
    }

    @Test
    fun `parses HSBC credit interest with dir-before-amount phrasing`() {
        val body = "HSBC: Dear Customer, your HSBC A/c 074-618***-006 has been credited with INR 289.48+ " +
            "on 01JUL as CREDIT INTEREST . Your available Bal is 454,308.48 ."
        val r = engine.match(sms(body, "VM-HSBCIN-S"), builtins)
        assertNotNull("built-ins should parse HSBC's 'credited with' phrasing", r)
        assertEquals(28948L, r!!.transaction.amountMinor)
        assertEquals(TxnDirection.CREDIT, r.transaction.direction)
    }

    @Test
    fun `parses HSBC NEFT credit with dir-before-amount phrasing`() {
        val body = "HSBC: A/c 074-618***-006 is credited with INR 309,018.00+ on 29JUN at 08.33.29 " +
            "with UTR BOFAH26180024463 as NEFT from BANSAL A/c"
        val r = engine.match(sms(body, "JM-HSBCIN-S"), builtins)
        assertNotNull("built-ins should parse HSBC's NEFT credit phrasing", r)
        assertEquals(30_901_800L, r!!.transaction.amountMinor)
        assertEquals(TxnDirection.CREDIT, r.transaction.direction)
    }

    @Test
    fun `parses HSBC debit with trailing minus sign`() {
        val body = "HSBC: A/c 074-618***-006 is debited with INR 1.00- on 26MAY. Avl Bal is INR 45,001.00 ."
        val r = engine.match(sms(body, "VM-HSBCIN-S"), builtins)
        assertNotNull("built-ins should parse HSBC's debited-with phrasing", r)
        assertEquals(100L, r!!.transaction.amountMinor)
        assertEquals(TxnDirection.DEBIT, r.transaction.direction)
    }
}
