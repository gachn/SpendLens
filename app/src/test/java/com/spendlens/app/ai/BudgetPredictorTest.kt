package com.spendlens.app.ai

import com.spendlens.app.data.db.TransactionEntity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class BudgetPredictorTest {

    private fun makeTxn(
        id: Long,
        amountMinor: Long,
        categoryId: Long? = 1L,
        daysAgo: Int = 0
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
            counterparty = "Test",
            occurredAt = occurredAt,
            channel = "SMS",
            categoryId = categoryId,
        )
    }

    @Test
    fun generateBudgetAlert_onTrack() {
        val txns = listOf(
            makeTxn(1, 50000, 1, 20),
            makeTxn(2, 50000, 1, 10),
            makeTxn(3, 50000, 1, 5),
        )

        val alert = BudgetPredictor.generateBudgetAlert(1, "Food", 500000, txns)
        assertTrue(alert.status == "OK")
        assertTrue(alert.percentUsed < 80)
    }

    @Test
    fun generateBudgetAlert_warning() {
        val txns = listOf(
            makeTxn(1, 100000, 1, 20),
            makeTxn(2, 100000, 1, 10),
            makeTxn(3, 100000, 1, 5),
        )

        val alert = BudgetPredictor.generateBudgetAlert(1, "Food", 400000, txns)
        assertTrue(alert.percentUsed >= 75) // At least 75% used
    }

    @Test
    fun generateBudgetAlert_exceeded() {
        val txns = listOf(
            makeTxn(1, 200000, 1, 20),
            makeTxn(2, 200000, 1, 10),
            makeTxn(3, 200000, 1, 5),
        )

        val alert = BudgetPredictor.generateBudgetAlert(1, "Food", 400000, txns)
        assertTrue(alert.status == "EXCEEDED")
        assertTrue(alert.percentUsed > 100)
    }

    @Test
    fun predictBudgetStatus_willNotExceed() {
        val txns = listOf(
            makeTxn(1, 50000, 1, 30),
            makeTxn(2, 50000, 1, 20),
            makeTxn(3, 50000, 1, 10),
            makeTxn(4, 50000, 1, 5),
        )

        val prediction = BudgetPredictor.predictBudgetStatus(1, "Food", 500000, txns)
        assertFalse(prediction.willExceed)
        assertTrue(prediction.daysBudgetWillLast > 30)
    }

    @Test
    fun predictBudgetStatus_willExceed() {
        val txns = listOf(
            makeTxn(1, 200000, 1, 30),
            makeTxn(2, 200000, 1, 20),
            makeTxn(3, 200000, 1, 10),
            makeTxn(4, 200000, 1, 5),
        )

        val prediction = BudgetPredictor.predictBudgetStatus(1, "Food", 300000, txns)
        assertTrue(prediction.willExceed)
        assertTrue(prediction.daysBudgetWillLast <= 30)
    }

    @Test
    fun suggestBudgets_baseOnHistory() {
        val txns = listOf(
            makeTxn(1, 300000, 1, 90),
            makeTxn(2, 300000, 1, 60),
            makeTxn(3, 300000, 1, 30),
        )

        val suggestions = BudgetPredictor.suggestBudgets(txns, mapOf(1L to "Food"))
        assertTrue(suggestions.containsKey(1L))
        val suggested = suggestions[1L]!!
        // Should be approximately average (300000) + 20% = 360000
        assertTrue(suggested > 200000)
    }

    @Test
    fun generateAllAlerts_multipleCategories() {
        val txns = listOf(
            makeTxn(1, 50000, 1, 10),
            makeTxn(2, 30000, 2, 10),
        )
        val budgets = mapOf(
            1L to ("Food" to 500000L),
            2L to ("Transport" to 200000L),
        )

        val alerts = BudgetPredictor.generateAllAlerts(budgets, txns)
        assertEquals(alerts.size, 2)
        assertTrue(alerts.all { it.budgetMinor > 0 })
    }
}
