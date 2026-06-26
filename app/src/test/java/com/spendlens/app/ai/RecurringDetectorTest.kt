package com.spendlens.app.ai

import com.spendlens.app.data.db.TransactionEntity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class RecurringDetectorTest {

    private fun makeTxn(
        id: Long,
        counterparty: String,
        amountMinor: Long,
        daysAgo: Int
    ): TransactionEntity {
        val now = System.currentTimeMillis()
        val occurredAt = now - (daysAgo.toLong() * 24 * 60 * 60 * 1000)
        return TransactionEntity(
            id = id,
            rawSmsId = null,
            amountMinor = amountMinor,
            currency = "INR",
            direction = "DEBIT",
            accountKey = "Card1",
            counterparty = counterparty,
            occurredAt = occurredAt,
            channel = "SMS",
            categoryId = 1L,
        )
    }

    @Test
    fun detectRecurring_monthlySubscription() {
        val txns = listOf(
            makeTxn(1, "Netflix", 50000, 90),
            makeTxn(2, "Netflix", 50000, 60),
            makeTxn(3, "Netflix", 50000, 30),
        )

        val patterns = RecurringDetector.detectRecurring(txns, minOccurrences = 3)
        assertTrue(patterns.isNotEmpty())
        val netflix = patterns.find { it.merchant == "Netflix" }
        assertNotNull(netflix)
        assertEquals(netflix.frequency, "MONTHLY")
        assertTrue(netflix.confidence > 0.5f)
    }

    @Test
    fun detectRecurring_weeklyGym() {
        val txns = mutableListOf<TransactionEntity>()
        repeat(5) { i ->
            txns.add(makeTxn(i.toLong(), "Gold's Gym", 80000, i * 7))
        }

        val patterns = RecurringDetector.detectRecurring(txns, minOccurrences = 3)
        val gym = patterns.find { it.merchant == "Gold's Gym" }
        assertNotNull(gym)
        assertEquals(gym.frequency, "WEEKLY")
    }

    @Test
    fun detectRecurring_minimumOccurrencesCheck() {
        val txns = listOf(
            makeTxn(1, "Spotify", 50000, 30),
            makeTxn(2, "Spotify", 50000, 0),
        )

        val patterns = RecurringDetector.detectRecurring(txns, minOccurrences = 3)
        assertTrue(patterns.isEmpty()) // Only 2 occurrences
    }

    @Test
    fun calculateTotalSubscriptionCost() {
        val patterns = listOf(
            RecurringPattern("Netflix", 50000, "MONTHLY", 0.9f, 3, 30f, 50000),
            RecurringPattern("Spotify", 30000, "MONTHLY", 0.85f, 3, 30f, 30000),
            RecurringPattern("Gym", 80000, "MONTHLY", 0.8f, 3, 30f, 80000),
        )

        val total = RecurringDetector.calculateTotalSubscriptionCost(patterns)
        assertEquals(total, 160000L)
    }

    @Test
    fun isNewRecurringAlert_detectsNewSubscription() {
        val patterns = listOf(
            RecurringPattern("Netflix", 50000, "MONTHLY", 0.9f, 3, 30f, 50000),
        )

        val newTxn = makeTxn(10, "Netflix", 50000, 0)
        val isAlert = RecurringDetector.isNewRecurringAlert(newTxn, patterns)
        assertTrue(isAlert)
    }

    @Test
    fun isNewRecurringAlert_ignoresDifferentMerchant() {
        val patterns = listOf(
            RecurringPattern("Netflix", 50000, "MONTHLY", 0.9f, 3, 30f, 50000),
        )

        val newTxn = makeTxn(10, "Amazon Prime", 50000, 0)
        val isAlert = RecurringDetector.isNewRecurringAlert(newTxn, patterns)
        assertTrue(!isAlert)
    }

    @Test
    fun isNewRecurringAlert_ignoresWildlyDifferentAmount() {
        val patterns = listOf(
            RecurringPattern("Netflix", 50000, "MONTHLY", 0.9f, 3, 30f, 50000),
        )

        val newTxn = makeTxn(10, "Netflix", 500000, 0) // 10x the normal amount
        val isAlert = RecurringDetector.isNewRecurringAlert(newTxn, patterns)
        assertTrue(!isAlert)
    }
}
