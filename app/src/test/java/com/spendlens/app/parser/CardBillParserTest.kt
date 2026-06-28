package com.spendlens.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class CardBillParserTest {

    private val sender = "AD-HDFCBK"
    private val now = Instant.parse("2024-11-15T06:00:00Z").toEpochMilli()

    // -------------------------------------------------------------------------
    // parse() — statement detection
    // -------------------------------------------------------------------------

    @Test
    fun `HDFC statement with card number`() {
        val body = "HDFC Bank Credit Card XX1234: Total Amount Due Rs.5,250.00. Min Due Rs.525.00. Due Date 05-12-2024."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertEquals("••••1234", bill!!.cardKey)
        assertEquals(525_000L, bill.totalDueMinor)
        assertEquals(52_500L, bill.minDueMinor)
    }

    @Test
    fun `statement without card number uses sender as cardKey`() {
        val body = "Total Amount Due is Rs.3,000. Min Due Rs.300. Due Date 10/12/2024."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertEquals(sender, bill!!.cardKey)
        assertEquals(300_000L, bill.totalDueMinor)
    }

    @Test
    fun `outstanding balance variant triggers statement parse`() {
        val body = "Total outstanding amount is INR 12,500.50 for your card XX5678."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertEquals("••••5678", bill!!.cardKey)
        assertEquals(1_250_050L, bill.totalDueMinor)
    }

    @Test
    fun `statement without total due returns null`() {
        val body = "Your OTP is 123456. Do not share."
        val bill = CardBillParser.parse(sender, body, now)
        assertNull(bill)
    }

    @Test
    fun `non-financial SMS returns null`() {
        val body = "Congratulations! You have won a lucky draw. Call 9999999999."
        val bill = CardBillParser.parse(sender, body, now)
        assertNull(bill)
    }

    @Test
    fun `due date parsed in dd-MM-yyyy format`() {
        val body = "Total Amount Due Rs.1,000. Due Date 25-12-2024."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertNotNull(bill!!.dueDate)
        val date = Instant.ofEpochMilli(bill.dueDate!!).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(LocalDate.of(2024, 12, 25), date)
    }

    @Test
    fun `due date parsed in dd MMM yyyy format`() {
        val body = "Total Amount Due Rs.2,500. Due Date 05 Jan 2025."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertNotNull(bill!!.dueDate)
        val date = Instant.ofEpochMilli(bill.dueDate!!).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(LocalDate.of(2025, 1, 5), date)
    }

    @Test
    fun `due date parsed in dd-MMM-yy format`() {
        val body = "Total Amount Due Rs.750. Due Date 20-Feb-25."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertNotNull(bill!!.dueDate)
        val date = Instant.ofEpochMilli(bill.dueDate!!).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(LocalDate.of(2025, 2, 20), date)
    }

    @Test
    fun `statement cycle day computed from receivedAt`() {
        val expected = LocalDate.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault()).dayOfMonth
        val body = "Total Amount Due Rs.1,500. Min Due Rs.150."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertEquals(expected, bill!!.statementCycleDay)
    }

    @Test
    fun `min due missing returns null for minDueMinor`() {
        val body = "Total Amount Due Rs.4,000. Due Date 15-12-2024."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertNull(bill!!.minDueMinor)
    }

    @Test
    fun `ICICI statement format`() {
        val body = "ICICI Bank: Statement for card ending XX9999. Total Amount Due: Rs 8,750.00. Minimum Due: Rs 875.00. Due by 18-12-2024."
        val bill = CardBillParser.parse("AD-ICICIB", body, now)
        assertNotNull(bill)
        assertEquals("••••9999", bill!!.cardKey)
        assertEquals(875_000L, bill.totalDueMinor)
    }

    @Test
    fun `SBI statement with rupee symbol`() {
        val body = "SBI Card: Total Outstanding ₹6,250 for card XX3456. Min Due ₹625."
        val bill = CardBillParser.parse("AD-SBICRDR", body, now)
        assertNotNull(bill)
        assertEquals("••••3456", bill!!.cardKey)
        assertEquals(625_000L, bill.totalDueMinor)
    }

    @Test
    fun `amount with no decimal parses correctly`() {
        val body = "Total Amount Due Rs.10,000. Due Date 01/01/2025."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertEquals(1_000_000L, bill!!.totalDueMinor)
    }

    @Test
    fun `Tot Amt Due abbreviation parses`() {
        val body = "HDFC Card XX1234 Tot Amt Due Rs.7,500.00 Min Amt Due Rs.375.00 Due Date 12-12-2024."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertEquals("••••1234", bill!!.cardKey)
        assertEquals(750_000L, bill.totalDueMinor)
        assertEquals(37_500L, bill.minDueMinor)
    }

    @Test
    fun `Total Amt Due with dot abbreviation`() {
        val body = "Card XX2222: Total Amt. Due Rs.3,200. Min. Amt. Due Rs.160."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertEquals(320_000L, bill!!.totalDueMinor)
        assertEquals(16_000L, bill.minDueMinor)
    }

    @Test
    fun `o slash s outstanding abbreviation`() {
        val body = "Card XX4444 O/S Rs.9,800.50. Pls pay."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertEquals(980_050L, bill!!.totalDueMinor)
    }

    @Test
    fun `due date with pay by phrasing`() {
        val body = "Total Amount Due Rs.1,000. Pay by 28-12-2024."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertNotNull(bill!!.dueDate)
        val date = Instant.ofEpochMilli(bill.dueDate!!).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(LocalDate.of(2024, 12, 28), date)
    }

    @Test
    fun `total and min not confused when min appears first`() {
        val body = "Min Amt Due Rs.500. Total Amt Due Rs.10,000. Due Date 05-01-2025."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertEquals(1_000_000L, bill!!.totalDueMinor)
        assertEquals(50_000L, bill.minDueMinor)
    }

    @Test
    fun `Axis Bank statement with Total amt and Dr prefix`() {
        val body = "Your statement for Axis Bank Credit Card no. XX9496 is generated.\n" +
            "Due on: 08-07-26\n" +
            "Total amt: INR  Dr. 59462.57\n" +
            "Min amt due: INR  Dr. 4169.00\n" +
            "To view/download statement: https://ccm.axis.bank.in/AXISBK/SqhjN4n0"
        val bill = CardBillParser.parse("JM-AXISBK-S", body, now)
        assertNotNull("Axis statement should parse", bill)
        assertEquals("••••9496", bill!!.cardKey)
        assertEquals(5_946_257L, bill.totalDueMinor)
        assertEquals(416_900L, bill.minDueMinor)
        assertNotNull("Due date should be parsed", bill.dueDate)
        val date = Instant.ofEpochMilli(bill.dueDate!!).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(LocalDate.of(2026, 7, 8), date)
    }

    @Test
    fun `due date with due on phrasing`() {
        val body = "Total Amount Due Rs.3,000. Due on: 15-07-2026."
        val bill = CardBillParser.parse(sender, body, now)
        assertNotNull(bill)
        assertNotNull(bill!!.dueDate)
        val date = Instant.ofEpochMilli(bill.dueDate!!).atZone(ZoneId.systemDefault()).toLocalDate()
        assertEquals(LocalDate.of(2026, 7, 15), date)
    }

    // -------------------------------------------------------------------------
    // parsePayment()
    // -------------------------------------------------------------------------

    @Test
    fun `CC payment received`() {
        val body = "Payment of Rs.5,250 received for your HDFC Credit Card XX1234. Thank you."
        val payment = CardBillParser.parsePayment(sender, body, now)
        assertNotNull(payment)
        assertEquals("••••1234", payment!!.cardKey)
        assertEquals(525_000L, payment.amountMinor)
    }

    @Test
    fun `CC payment processed variant`() {
        val body = "Your credit card payment of INR 3,000.00 has been processed successfully."
        val payment = CardBillParser.parsePayment(sender, body, now)
        assertNotNull(payment)
        assertEquals(300_000L, payment!!.amountMinor)
    }

    @Test
    fun `payment SMS that is actually a statement returns null`() {
        val body = "Payment received. Total Amount Due Rs.500. Min Due Rs.50."
        val payment = CardBillParser.parsePayment(sender, body, now)
        assertNull(payment)
    }

    @Test
    fun `non-payment SMS returns null from parsePayment`() {
        val body = "Your card XX1234 has been blocked. Call 1800-XXX-XXXX."
        val payment = CardBillParser.parsePayment(sender, body, now)
        assertNull(payment)
    }

    @Test
    fun `payment amount zero returns null`() {
        val body = "Payment of Rs.0 received for credit card."
        val payment = CardBillParser.parsePayment(sender, body, now)
        assertNull(payment)
    }

    @Test
    fun `bill payment towards credit card`() {
        val body = "Payment of Rs.1,200 made towards your Credit Card XX7890 on 15 Nov 2024."
        val payment = CardBillParser.parsePayment(sender, body, now)
        assertNotNull(payment)
        assertEquals("••••7890", payment!!.cardKey)
        assertEquals(120_000L, payment.amountMinor)
    }

    @Test
    fun `payment without card number uses sender fallback`() {
        val body = "Your credit card payment of Rs.2,000 has been received. Thank you."
        val payment = CardBillParser.parsePayment(sender, body, now)
        assertNotNull(payment)
        assertEquals(sender, payment!!.cardKey)
        assertEquals(200_000L, payment.amountMinor)
    }
}
