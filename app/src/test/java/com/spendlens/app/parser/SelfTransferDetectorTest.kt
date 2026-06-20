package com.spendlens.app.parser

import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.parser.model.Channel
import com.spendlens.app.parser.model.ParsedTransaction
import com.spendlens.app.parser.model.TxnDirection
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfTransferDetectorTest {

    private fun debit(account: String, at: Long) = ParsedTransaction(
        amountMinor = 100000, currency = "INR", direction = TxnDirection.DEBIT,
        accountKey = account, counterparty = "Self", balanceMinor = null,
        referenceId = null, occurredAt = at, channel = Channel.IMPS,
    )

    private fun other(direction: String, account: String, at: Long) = TransactionEntity(
        id = 5, rawSmsId = 5, amountMinor = 100000, currency = "INR", direction = direction,
        accountKey = account, counterparty = "Self", occurredAt = at, channel = "IMPS",
    )

    @Test
    fun `opposite leg on another account within window is a transfer`() {
        assertTrue(
            SelfTransferDetector.isCounterLeg(debit("••••1111", 1_000_000), other("CREDIT", "••••2222", 1_000_000 + 60_000)),
        )
    }

    @Test
    fun `same account is not a self-transfer`() {
        assertFalse(
            SelfTransferDetector.isCounterLeg(debit("••••1111", 1_000_000), other("CREDIT", "••••1111", 1_000_000)),
        )
    }

    @Test
    fun `same direction is not a self-transfer`() {
        assertFalse(
            SelfTransferDetector.isCounterLeg(debit("••••1111", 1_000_000), other("DEBIT", "••••2222", 1_000_000)),
        )
    }

    @Test
    fun `outside window is not a self-transfer`() {
        assertFalse(
            SelfTransferDetector.isCounterLeg(debit("••••1111", 1_000_000), other("CREDIT", "••••2222", 1_000_000 + 20 * 60_000)),
        )
    }
}
