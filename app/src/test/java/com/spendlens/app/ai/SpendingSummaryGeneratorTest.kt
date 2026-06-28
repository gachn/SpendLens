package com.spendlens.app.ai

import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNull
import kotlin.test.assertNotNull

class SpendingSummaryGeneratorTest {

    private fun makeTxn(
        id: Long,
        amountMinor: Long,
        categoryId: Long? = 1L,
        direction: String = "DEBIT",
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
            direction = direction,
            accountKey = "Card1",
            counterparty = "TestMerchant",
            occurredAt = occurredAt,
            channel = "SMS",
            categoryId = categoryId,
        )
    }

    @Test
    fun calculateMetrics_basicSpend() {
        val txns = listOf(
            makeTxn(1, 50000, 1, "DEBIT", 20),
            makeTxn(2, 60000, 1, "DEBIT", 10),
            makeTxn(3, 40000, 1, "DEBIT", 5),
        )
        val categories = listOf(CategoryEntity(1, "Food", "🍽️", 0xFF0000L))

        val metrics = SpendingSummaryGenerator.calculateMetrics(txns, categories, 30)
        assertEquals(metrics.totalSpent, 150000L)
        assertEquals(metrics.transactionCount, 3)
        assertNotNull(metrics.topCategory)
    }

    @Test
    fun calculateMetrics_mixedDebitsCredits() {
        val txns = listOf(
            makeTxn(1, 50000, 1, "DEBIT", 10),
            makeTxn(2, 100000, 1, "CREDIT", 10),
        )
        val categories = listOf(CategoryEntity(1, "Food", "🍽️", 0xFF0000L))

        val metrics = SpendingSummaryGenerator.calculateMetrics(txns, categories, 30)
        assertEquals(metrics.totalSpent, 50000L)
        assertEquals(metrics.totalReceived, 100000L)
    }

    @Test
    fun calculateMetrics_multipleCategories() {
        val txns = listOf(
            makeTxn(1, 50000, 1, "DEBIT", 10),
            makeTxn(2, 80000, 2, "DEBIT", 10),
            makeTxn(3, 40000, 1, "DEBIT", 5),
        )
        val categories = listOf(
            CategoryEntity(1, "Food", "🍽️", 0xFF0000L),
            CategoryEntity(2, "Transport", "🚕", 0xFF0000L),
        )

        val metrics = SpendingSummaryGenerator.calculateMetrics(txns, categories, 30)
        assertEquals(metrics.totalSpent, 170000L)
        assertNotNull(metrics.topCategory)
        assertEquals(metrics.topCategory?.first, "Food") // Food = 90k (2 txns), Transport = 80k
    }

    @Test
    fun calculateMetrics_respects_daysBack() {
        val txns = listOf(
            makeTxn(1, 50000, 1, "DEBIT", 40), // 40 days ago, outside window
            makeTxn(2, 60000, 1, "DEBIT", 10),
            makeTxn(3, 40000, 1, "DEBIT", 5),
        )
        val categories = listOf(CategoryEntity(1, "Food", "🍽️", 0xFF0000L))

        val metrics = SpendingSummaryGenerator.calculateMetrics(txns, categories, 30)
        assertEquals(metrics.totalSpent, 100000L) // Only last 30 days
        assertEquals(metrics.transactionCount, 2)
    }

    @Test
    fun calculateMetrics_emptyReturnsZeros() {
        val metrics = SpendingSummaryGenerator.calculateMetrics(emptyList(), emptyList(), 30)
        assertEquals(metrics.totalSpent, 0L)
        assertEquals(metrics.transactionCount, 0)
        assertNull(metrics.topCategory)
    }

    @Test
    fun buildPrompt_createsValidPrompt() {
        val metrics = SpendingMetrics(
            totalSpent = 150000,
            totalReceived = 0,
            topCategory = "Food" to 50000,
            topMerchant = "Swiggy" to 40000,
            transactionCount = 5,
            periodDays = 30,
        )
        val categories = listOf(CategoryEntity(1, "Food", "🍽️", 0xFF0000L))

        val prompt = SpendingSummaryGenerator.buildPrompt(metrics, categories, "this month")
        assertTrue(prompt.contains("1500")) // totalSpent/100 = 1500
        assertTrue(prompt.contains("this month"))
        assertTrue(prompt.contains("Swiggy"))
    }

    @Test
    fun parseSummary_returnsText() {
        val text = "You spent ₹1500 this month, mostly on food."
        val result = SpendingSummaryGenerator.parseSummary(text)
        assertEquals(result, text)
    }

    @Test
    fun parseSummary_returnsNullForBlank() {
        val result = SpendingSummaryGenerator.parseSummary("   ")
        assertNull(result)
    }

    @Test
    fun calculateCategoryTrend_increase() {
        val txns = listOf(
            // Current month: ₹100k food
            makeTxn(1, 50000, 1, "DEBIT", 10),
            makeTxn(2, 50000, 1, "DEBIT", 5),
            // Previous month: ₹50k food
            makeTxn(3, 25000, 1, "DEBIT", 40),
            makeTxn(4, 25000, 1, "DEBIT", 35),
        )

        val trend = SpendingSummaryGenerator.calculateCategoryTrend(txns, 1)
        assertTrue(trend > 0) // Increased from previous month
    }
}
