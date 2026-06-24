package com.spendlens.app.parser

import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.parser.model.Channel
import com.spendlens.app.parser.model.ParsedTransaction
import com.spendlens.app.parser.model.TxnDirection
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {

    private fun parsed(ref: String?, party: String, at: Long) = ParsedTransaction(
        amountMinor = 50000, currency = "INR", direction = TxnDirection.DEBIT,
        accountKey = "••••1234", counterparty = party, balanceMinor = null,
        referenceId = ref, occurredAt = at, channel = Channel.UPI,
    )

    private fun existing(ref: String?, party: String, at: Long) = TransactionEntity(
        id = 1, rawSmsId = 1, amountMinor = 50000, currency = "INR", direction = "DEBIT",
        accountKey = "••••1234", counterparty = party, referenceId = ref, occurredAt = at,
        channel = "UPI",
    )

    @Test
    fun `same reference id is a strong duplicate`() {
        val v = DuplicateDetector.classify(
            parsed("TXN999", "AMAZON", 1000),
            listOf(existing("TXN999", "AMAZON", 800)),
        )
        assertTrue(v is DuplicateDetector.Verdict.Duplicate)
    }

    @Test
    fun `same party very close in time is an auto-hidden duplicate`() {
        val v = DuplicateDetector.classify(
            parsed(null, "Amazon Pay", 100_000),
            listOf(existing(null, "AMAZONPAY", 100_000 + 30_000)),
        )
        assertTrue(v is DuplicateDetector.Verdict.Duplicate)
    }

    @Test
    fun `same value in the wider band is a probable duplicate`() {
        val v = DuplicateDetector.classify(
            parsed(null, "Amazon Pay", 100_000),
            listOf(existing(null, "AMAZONPAY", 100_000 + 150_000)),
        )
        assertTrue(v is DuplicateDetector.Verdict.Probable)
    }

    @Test
    fun `far apart is unique`() {
        val v = DuplicateDetector.classify(
            parsed(null, "AMAZON", 100_000),
            listOf(existing(null, "AMAZON", 100_000 + 10 * 60_000)),
        )
        assertTrue(v is DuplicateDetector.Verdict.Unique)
    }

    @Test
    fun `no candidates is unique`() {
        assertTrue(DuplicateDetector.classify(parsed("X", "A", 1), emptyList()) is DuplicateDetector.Verdict.Unique)
    }
}
