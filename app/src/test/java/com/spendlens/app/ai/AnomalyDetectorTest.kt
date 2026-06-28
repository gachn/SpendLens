package com.spendlens.app.ai

import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AnomalyDetectorTest {

    private fun makeTxn(
        id: Long,
        counterparty: String,
        amountMinor: Long = 50000,
        categoryId: Long? = 1L,
        daysAgo: Int = 0
    ): TransactionEntity {
        val now = System.currentTimeMillis()
        val occurredAt = now - (daysAgo.toLong() * 24 * 60 * 60 * 1000)
        return TransactionEntity(
            id = id,
            rawSmsId = null,
            amountMinor = amountMinor,
            amountBaseMinor = amountMinor,
            currency = "INR",
            direction = "DEBIT",
            accountKey = "Card1",
            counterparty = counterparty,
            occurredAt = occurredAt,
            channel = "SMS",
            categoryId = categoryId,
        )
    }

    @Test
    fun scoreAnomaly_newMerchant() {
        val txn = makeTxn(10, "UnknownMerchant", 50000, 1)
        val historical = listOf(
            makeTxn(1, "Swiggy", 50000, 1, 10),
            makeTxn(2, "Swiggy", 55000, 1, 5),
            makeTxn(3, "Zomato", 45000, 1, 3),
        )
        val categories = listOf(CategoryEntity(1, "Food", "🍽️", 0xFF0000L))

        val score = AnomalyDetector.scoreAnomaly(txn, historical, categories)
        assertNotNull(score)
        assertTrue(score.score > 0.2f) // New merchant should be flagged
    }

    @Test
    fun scoreAnomaly_amountSpike() {
        val txn = makeTxn(10, "Swiggy", 500000, 1) // 10x normal
        val historical = listOf(
            makeTxn(1, "Swiggy", 50000, 1, 10),
            makeTxn(2, "Swiggy", 55000, 1, 5),
            makeTxn(3, "Swiggy", 45000, 1, 3),
        )
        val categories = listOf(CategoryEntity(1, "Food", "🍽️", 0xFF0000L))

        val score = AnomalyDetector.scoreAnomaly(txn, historical, categories)
        assertNotNull(score)
        assertTrue(score.severity == "HIGH" || score.severity == "MEDIUM")
    }

    @Test
    fun scoreAnomaly_normalTransaction() {
        val txn = makeTxn(10, "Swiggy", 50000, 1)
        val historical = listOf(
            makeTxn(1, "Swiggy", 50000, 1, 10),
            makeTxn(2, "Swiggy", 55000, 1, 5),
            makeTxn(3, "Swiggy", 45000, 1, 3),
        )
        val categories = listOf(CategoryEntity(1, "Food", "🍽️", 0xFF0000L))

        val score = AnomalyDetector.scoreAnomaly(txn, historical, categories)
        // Normal transaction might return null or very low score
        if (score != null) {
            assertTrue(score.score < 0.3f)
        }
    }

    @Test
    fun detectAnomalies_findsHighestScores() {
        val txns = listOf(
            makeTxn(1, "Swiggy", 50000, 1, 5),
            makeTxn(2, "Swiggy", 55000, 1, 3),
            makeTxn(3, "UnknownMerchant", 50000, 1, 1), // anomaly
            makeTxn(4, "Swiggy", 500000, 1, 0), // big anomaly
        )
        val categories = listOf(CategoryEntity(1, "Food", "🍽️", 0xFF0000L))

        val anomalies = AnomalyDetector.detectAnomalies(txns, categories)
        assertTrue(anomalies.isNotEmpty())
        // Should be sorted by score descending
        if (anomalies.size > 1) {
            assertTrue(anomalies[0].score >= anomalies[1].score)
        }
    }

    @Test
    fun markAsNormal_returns() {
        val result = AnomalyDetector.markAsNormal(123L)
        assertTrue(result)
    }
}
