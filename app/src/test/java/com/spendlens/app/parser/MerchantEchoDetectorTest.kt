package com.spendlens.app.parser

import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.parser.model.Channel
import com.spendlens.app.parser.model.ParsedTransaction
import com.spendlens.app.parser.model.TxnDirection
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MerchantEchoDetectorTest {

    private fun credit(party: String, at: Long) = ParsedTransaction(
        amountMinor = 49900, currency = "INR", direction = TxnDirection.CREDIT,
        accountKey = "JIO", counterparty = party, balanceMinor = null,
        referenceId = null, occurredAt = at, channel = Channel.WALLET,
    )

    private fun debit(party: String, at: Long, amount: Long = 49900) = TransactionEntity(
        id = 7, rawSmsId = 7, amountMinor = amount, currency = "INR", direction = "DEBIT",
        accountKey = "••••1234", counterparty = party, occurredAt = at, channel = "UPI",
    )

    @Test
    fun `merchant credit shortly after same-amount debit is an echo`() {
        val echo = MerchantEchoDetector.echoedDebit(
            credit("Jio", 100_000),
            "Your Jio account has been credited with Rs.499",
            listOf(debit("Jio Prepaid", 100_000 - 60_000)),
        )
        assertNotNull(echo)
    }

    @Test
    fun `genuine refund is not an echo`() {
        val echo = MerchantEchoDetector.echoedDebit(
            credit("Amazon", 100_000),
            "Rs.499 refund credited to a/c XX1234 from Amazon",
            listOf(debit("Amazon", 100_000 - 60_000)),
        )
        assertNull(echo)
    }

    @Test
    fun `different merchant is not an echo`() {
        val echo = MerchantEchoDetector.echoedDebit(
            credit("Jio", 100_000),
            "Your Jio account has been credited with Rs.499",
            listOf(debit("Airtel", 100_000 - 60_000)),
        )
        assertNull(echo)
    }

    @Test
    fun `outside the window is not an echo`() {
        val echo = MerchantEchoDetector.echoedDebit(
            credit("Jio", 100_000),
            "Your Jio account has been credited with Rs.499",
            listOf(debit("Jio", 100_000 - MerchantEchoDetector.WINDOW_MS - 1)),
        )
        assertNull(echo)
    }
}
