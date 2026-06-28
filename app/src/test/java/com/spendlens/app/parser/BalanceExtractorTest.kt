package com.spendlens.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BalanceExtractorTest {

    @Test
    fun `avl bal after debit captures balance not amount`() {
        val body = "Rs.500.00 debited from A/c XX336. Avl Bal Rs.12,345.67"
        assertEquals(1_234_567L, BalanceExtractor.extractMinor(body))
    }

    @Test
    fun `available balance phrase`() {
        val body = "INR 2,000 credited. Available Balance: Rs 50,000.00"
        assertEquals(5_000_000L, BalanceExtractor.extractMinor(body))
    }

    @Test
    fun `a slash c bal`() {
        val body = "Txn of Rs.100 on card. A/c Bal: 9,999"
        assertEquals(999_900L, BalanceExtractor.extractMinor(body))
    }

    @Test
    fun `bare bal with rupee symbol`() {
        val body = "Payment done. Bal ₹1,500.50"
        assertEquals(150_050L, BalanceExtractor.extractMinor(body))
    }

    @Test
    fun `ICICI available balance is Rs form`() {
        val body = "ICICI Bank Account XX336 credited:Rs. 30,000.00 on 01-Jun-26. " +
            "Info NEFT-HDFCH01028907068-GAURAV. Available Balance is Rs. 3,38,816.55."
        assertEquals(33_881_655L, BalanceExtractor.extractMinor(body))
    }

    @Test
    fun `HSBC avl bal is INR form`() {
        val body = "HSBC: A/c 074-618***-006 is debited with INR 1.00- on 26MAY ... " +
            "Avl Bal is INR 45,001.00 . Report fraud 18002663456."
        assertEquals(4_500_100L, BalanceExtractor.extractMinor(body))
    }

    @Test
    fun `HDFC deposit avl bal adjacent`() {
        val body = "Update! INR 3,50,366.00 deposited in HDFC Bank A/c XX3090 on 28-MAY-26 for NEFT " +
            "Cr-BOFA0CN6215.Avl bal INR 3,51,214.23. Cheque deposits subject to clearing"
        assertEquals(35_121_423L, BalanceExtractor.extractMinor(body))
    }

    @Test
    fun `no balance phrase returns null`() {
        val body = "Rs.500 debited from A/c XX336 at SWIGGY."
        assertNull(BalanceExtractor.extractMinor(body))
    }

    @Test
    fun `amount with no decimals`() {
        val body = "Spent Rs.250. Avl Bal Rs.10,000"
        assertEquals(1_000_000L, BalanceExtractor.extractMinor(body))
    }
}
