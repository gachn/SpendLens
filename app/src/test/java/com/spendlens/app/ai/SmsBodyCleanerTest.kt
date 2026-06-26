package com.spendlens.app.ai

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class SmsBodyCleanerTest {
    @Test
    fun quickClean_removesBankPrefix() {
        val body = "ICICI Bank Acct XX1234 debited ₹500 for groceries"
        val result = SmsBodyCleaner.quickClean(body)
        assertNotNull(result)
        assert(!result!!.contains("ICICI Bank"))
        assert(!result.contains("Acct"))
    }

    @Test
    fun quickClean_preservesTransactionInfo() {
        val body = "HDFC Bank Acct XX5678 debited ₹1500 to SWIGGY*DELHI"
        val result = SmsBodyCleaner.quickClean(body)
        assertNotNull(result)
        assert(result!!.contains("1500"))
        assert(result.contains("debited"))
    }

    @Test
    fun quickClean_removesBillUrls() {
        val body = "Axis Bank: ₹2000 spent. For details visit www.axisbank.com"
        val result = SmsBodyCleaner.quickClean(body)
        assertNotNull(result)
        assert(!result!!.contains("www"))
    }

    @Test
    fun quickClean_rejectsNonFinancialSms() {
        val body = "Hi, how are you doing today?"
        val result = SmsBodyCleaner.quickClean(body)
        assertNull(result)
    }

    @Test
    fun buildPrompt_createsValidPrompt() {
        val body = "ICICI Bank debited ₹500"
        val prompt = SmsBodyCleaner.buildPrompt(body)
        assert(prompt.contains(body))
        assert(prompt.contains("clean"))
    }

    @Test
    fun parseCleanedBody_returnsText() {
        val text = "You spent ₹500 at SWIGGY"
        val result = SmsBodyCleaner.parseCleanedBody(text)
        assertEquals(result, text)
    }

    @Test
    fun parseCleanedBody_returnsNullForBlank() {
        val result = SmsBodyCleaner.parseCleanedBody("   ")
        assertNull(result)
    }

    @Test
    fun batchQuickClean_processesManyBodies() {
        val bodies = listOf(
            "ICICI Bank debited ₹500",
            "HDFC Bank credited ₹10000",
            "Random message"
        )
        val result = SmsBodyCleaner.batchQuickClean(bodies)
        assertEquals(result.size, 3)
        // First two should be cleaned, third should be original
        assert(result[bodies[0]]?.length ?: 0 < bodies[0].length)
    }
}
