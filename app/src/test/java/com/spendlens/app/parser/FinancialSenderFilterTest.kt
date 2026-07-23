package com.spendlens.app.parser

import com.spendlens.app.parser.model.SmsMessage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FinancialSenderFilterTest {

    private fun sms(sender: String) = SmsMessage(sender, "Rs.500 debited from a/c XX1234", 1_700_000_000_000L)

    // ---- known bank DLT senders should pass ----

    @Test fun `HDFC bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-HDFCBK"))
    }

    @Test fun `ICICI bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("AD-ICICIB"))
    }

    @Test fun `SBI sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("BW-SBIINB"))
    }

    @Test fun `Axis bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("DM-AXISBK"))
    }

    @Test fun `Kotak bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("CP-KOTAKB"))
    }

    @Test fun `Yes bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-YESBK"))
    }

    @Test fun `RBL bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-RBLBNK"))
    }

    @Test fun `IDFC First bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("AD-IDFCFB"))
    }

    @Test fun `IndusInd bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-INDUSL"))
    }

    @Test fun `Federal bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-FDRLBK"))
    }

    @Test fun `Bank of Baroda sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-BOBCRD"))
    }

    @Test fun `PNB sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-PNBSMS"))
    }

    @Test fun `Canara bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-CANBNK"))
    }

    @Test fun `IDBI bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-IDBIBK"))
    }

    @Test fun `DCB bank sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-DCBBNK"))
    }

    @Test fun `Standard Chartered sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-SCBANK"))
    }

    @Test fun `HSBC sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("AD-HSBCIN-S"))
    }

    // ---- payment wallets and UPI apps ----

    @Test fun `Paytm sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("CP-PAYTMB"))
    }

    @Test fun `PhonePe sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-PHONEP"))
    }

    @Test fun `Amazon Pay sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("AD-AMAZPAY"))
    }

    @Test fun `Bajaj Finance sender is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("VK-BAJAJF"))
    }

    // ---- sender without DLT prefix (plain brand code) ----

    @Test fun `plain HDFC sender without prefix is financial`() {
        assertTrue(FinancialSenderFilter.isFinancialSender("HDFCBK"))
    }

    // ---- non-financial senders should NOT pass ----

    @Test fun `Zomato food-delivery sender is not financial`() {
        assertFalse(FinancialSenderFilter.isFinancialSender("VK-ZOMATO"))
    }

    @Test fun `Swiggy sender is not financial`() {
        assertFalse(FinancialSenderFilter.isFinancialSender("VK-SWIGGY"))
    }

    @Test fun `Flipkart ecommerce sender is not financial`() {
        assertFalse(FinancialSenderFilter.isFinancialSender("AD-FKRTL"))
    }

    @Test fun `Airtel telecom sender is not financial`() {
        assertFalse(FinancialSenderFilter.isFinancialSender("AD-AIRTLT"))
    }

    @Test fun `OTP sender (VMHDFCB) for non-bank is not financial`() {
        assertFalse(FinancialSenderFilter.isFinancialSender("VM-NYKAA"))
    }

    @Test fun `numeric sender is not financial`() {
        assertFalse(FinancialSenderFilter.isFinancialSender("9876543210"))
    }

    // ---- SmsMessage overload ----

    @Test fun `isFinancialSender(SmsMessage) works for bank sender`() {
        assertTrue(FinancialSenderFilter.isFinancialSender(sms("VK-HDFCBK")))
    }

    @Test fun `isFinancialSender(SmsMessage) works for non-bank sender`() {
        assertFalse(FinancialSenderFilter.isFinancialSender(sms("VK-ZOMATO")))
    }
}
