package com.spendlens.app.parser

import com.spendlens.app.parser.model.SmsMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialSmsFilterTest {

    private fun sms(body: String) = SmsMessage("VK-HDFCBK", body, 1_700_000_000_000L)

    @Test
    fun `real debit is financial`() {
        assertTrue(FinancialSmsFilter.isFinancial(sms("Rs.499 debited from a/c XX1234 for Netflix. Ref 998877")))
    }

    @Test
    fun `autopay pre-notification is not financial`() {
        assertFalse(FinancialSmsFilter.isFinancial(sms("Rs.499 will be debited tomorrow from a/c XX1234 via autopay for Netflix")))
    }

    @Test
    fun `mandate due reminder is not financial`() {
        assertFalse(FinancialSmsFilter.isFinancial(sms("Your NACH mandate of Rs.1,200 is due on 25-06 for SIP. Ensure balance.")))
    }

    @Test
    fun `scheduled debit notice is not financial`() {
        assertFalse(FinancialSmsFilter.isFinancial(sms("Rs.799 scheduled to be deducted on 01-07 via e-NACH for Spotify")))
    }
}
