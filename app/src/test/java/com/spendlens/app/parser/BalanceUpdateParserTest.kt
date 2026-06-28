package com.spendlens.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BalanceUpdateParserTest {

    private val sender = "AD-HDFCBK"
    private val now = Instant.parse("2024-11-15T06:00:00Z").toEpochMilli()

    // -------------------------------------------------------------------------
    // Avl Bal / Available Balance
    // -------------------------------------------------------------------------

    @Test
    fun `avl bal with card number`() {
        val body = "Dear Customer, Avl Bal: Rs.12,345.67 in A/c XX1234."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertEquals("••••1234", snap!!.accountKey)
        assertEquals(1_234_567L, snap.balanceMinor)
        assertFalse(snap.isCard)
        assertEquals("INR", snap.currency)
    }

    @Test
    fun `available balance phrase`() {
        val body = "Available Balance: INR 50,000."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertEquals(sender, snap!!.accountKey)
        assertEquals(5_000_000L, snap.balanceMinor)
    }

    // -------------------------------------------------------------------------
    // Real device formats — "<cue> ... is <amount>" (periodic balance SMS)
    // -------------------------------------------------------------------------

    @Test
    fun `HDFC periodic daily balance SMS`() {
        val body = "Available Bal in HDFC Bank A/c XX3090 as on yesterday:20-JUN-26 is INR 59,706.01. " +
            "Cheques are subject to clearing.For updated A/C Bal dial 18002703333."
        val snap = BalanceUpdateParser.parse("JM-HDFCBK-S", body, now)
        assertNotNull(snap)
        assertEquals("••••3090", snap!!.accountKey)
        assertEquals(5_970_601L, snap.balanceMinor)
        assertFalse(snap.isCard)
    }

    @Test
    fun `HDFC periodic balance with indian grouping lakh`() {
        val body = "Available Bal in HDFC Bank A/c XX3090 as on yesterday:05-JUN-26 is INR 1,14,604.01. " +
            "Cheques are subject to clearing."
        val snap = BalanceUpdateParser.parse("VM-HDFCBK-S", body, now)
        assertNotNull(snap)
        assertEquals(11_460_401L, snap!!.balanceMinor)
    }

    @Test
    fun `deposit SMS with verb is left to engine`() {
        // Has "deposited" → transaction alert, not a standalone balance snapshot.
        val body = "Update! INR 3,50,366.00 deposited in HDFC Bank A/c XX3090 on 28-MAY-26 for NEFT " +
            "Cr-BOFA0CN6215.Avl bal INR 3,51,214.23. Cheque deposits in A/C are subject to clearing"
        val snap = BalanceUpdateParser.parse("JM-HDFCBK-S", body, now)
        assertNull(snap)
    }

    @Test
    fun `avl bal with rupee symbol`() {
        val body = "Avl Bal ₹8,900 for A/c XX6789."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertEquals(890_000L, snap!!.balanceMinor)
    }

    // -------------------------------------------------------------------------
    // Account Balance (acctBal)
    // -------------------------------------------------------------------------

    @Test
    fun `account balance phrase`() {
        val body = "Account Balance: Rs.1,00,000.00 as of 15 Nov 2024."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertEquals(10_000_000L, snap!!.balanceMinor)
        assertFalse(snap.isCard)
    }

    @Test
    fun `a slash c balance phrase`() {
        val body = "A/C Balance Rs.25,500 for XX4321."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertEquals("••••4321", snap!!.accountKey)
    }

    // -------------------------------------------------------------------------
    // Available Credit Limit (card)
    // -------------------------------------------------------------------------

    @Test
    fun `available credit limit marks isCard true`() {
        val body = "Available Credit Limit: Rs.45,000 for card XX9876."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertEquals("••••9876", snap!!.accountKey)
        assertEquals(4_500_000L, snap.balanceMinor)
        assertTrue(snap.isCard)
    }

    @Test
    fun `avl cr lmt variant`() {
        val body = "Avl Cr Lmt Rs.30,000 on card XX1111."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertTrue(snap!!.isCard)
        assertEquals(3_000_000L, snap.balanceMinor)
    }

    @Test
    fun `credit available phrase`() {
        val body = "Credit Available: INR 15,250 on your card XX2222."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertTrue(snap!!.isCard)
    }

    // -------------------------------------------------------------------------
    // Balance as on / Balance is
    // -------------------------------------------------------------------------

    @Test
    fun `balance as on date`() {
        val body = "Balance as on 15/11/2024: Rs.7,800."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertEquals(780_000L, snap!!.balanceMinor)
    }

    @Test
    fun `balance is phrase`() {
        val body = "Your account balance is Rs.3,500 in A/c XX5555."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertEquals("••••5555", snap!!.accountKey)
    }

    // -------------------------------------------------------------------------
    // observedAt stored correctly
    // -------------------------------------------------------------------------

    @Test
    fun `observedAt equals receivedAt`() {
        val body = "Avl Bal: Rs.5,000 in A/c XX0001."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertEquals(now, snap!!.observedAt)
    }

    // -------------------------------------------------------------------------
    // Exclusions — transaction verbs must suppress the result
    // -------------------------------------------------------------------------

    @Test
    fun `SMS with debited verb returns null`() {
        val body = "Rs.500 debited from A/c XX1234. Avl Bal Rs.9,500."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNull(snap)
    }

    @Test
    fun `SMS with credited verb returns null`() {
        val body = "Rs.1,000 credited to A/c XX5678. Available Balance Rs.20,000."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNull(snap)
    }

    @Test
    fun `SMS with transferred verb returns null`() {
        val body = "Rs.2,000 transferred. Account Balance: Rs.18,000."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNull(snap)
    }

    @Test
    fun `SMS with paid verb returns null`() {
        val body = "UPI Rs.300 paid. Avl Bal Rs.4,700."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNull(snap)
    }

    // -------------------------------------------------------------------------
    // No cue — no standalone balance phrase
    // -------------------------------------------------------------------------

    @Test
    fun `SMS with no cue phrase returns null`() {
        val body = "Your OTP is 987654. Do not share with anyone."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNull(snap)
    }

    @Test
    fun `promotional SMS returns null`() {
        val body = "Congratulations! Pre-approved personal loan up to Rs.5,00,000. Apply now."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNull(snap)
    }

    @Test
    fun `blank sender and no account number returns null`() {
        val body = "Avl Bal: Rs.1,000."
        val snap = BalanceUpdateParser.parse("", body, now)
        assertNull(snap)
    }

    // -------------------------------------------------------------------------
    // USD currency
    // -------------------------------------------------------------------------

    @Test
    fun `USD currency detected`() {
        val body = "Available Balance: USD 500.00 in A/c XX3333."
        val snap = BalanceUpdateParser.parse(sender, body, now)
        assertNotNull(snap)
        assertEquals("USD", snap!!.currency)
    }
}
