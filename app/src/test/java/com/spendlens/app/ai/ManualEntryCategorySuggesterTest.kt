package com.spendlens.app.ai

import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

class ManualEntryCategorySuggesterTest {

    private fun makeTxn(
        id: Long,
        counterparty: String,
        categoryId: Long? = 1L,
        amountMinor: Long = 50000,
        direction: String = "DEBIT"
    ): TransactionEntity {
        return TransactionEntity(
            id = id,
            rawSmsId = null,
            amountMinor = amountMinor,
            currency = "INR",
            direction = direction,
            accountKey = "Card1",
            counterparty = counterparty,
            occurredAt = System.currentTimeMillis(),
            channel = "MANUAL",
            categoryId = categoryId,
        )
    }

    @Test
    fun suggestLocally_foodCategory() {
        val categories = listOf(
            CategoryEntity(1, "Food", "🍽️", 0xFF0000L),
            CategoryEntity(2, "Transport", "🚕", 0xFF0000L),
        )
        val transactions = listOf(
            makeTxn(1, "Swiggy", 1),
            makeTxn(2, "Zomato", 1),
            makeTxn(3, "Uber", 2),
        )

        val suggestions = ManualEntryCategorySuggester.suggestLocally("Pizza Hut", transactions, categories)
        assertTrue(suggestions.isNotEmpty())
        assertEquals(suggestions[0].name, "Food")
        assertTrue(suggestions[0].confidence > 0.5f)
    }

    @Test
    fun suggestLocally_transportCategory() {
        val categories = listOf(
            CategoryEntity(1, "Food", "🍽️", 0xFF0000L),
            CategoryEntity(2, "Transport", "🚕", 0xFF0000L),
        )
        val transactions = listOf(
            makeTxn(1, "Uber", 2),
            makeTxn(2, "Ola", 2),
            makeTxn(3, "Swiggy", 1),
        )

        val suggestions = ManualEntryCategorySuggester.suggestLocally("Uber Cab", transactions, categories)
        assertTrue(suggestions.isNotEmpty())
        assertEquals(suggestions[0].name, "Transport")
    }

    @Test
    fun suggestLocally_usesHistoricalMerchants() {
        val categories = listOf(
            CategoryEntity(1, "Food", "🍽️", 0xFF0000L),
        )
        val transactions = listOf(
            makeTxn(1, "SWIGGY", 1),
            makeTxn(2, "SWIGGY DELHI", 1),
            makeTxn(3, "swiggy*delhi", 1),
        )

        val suggestions = ManualEntryCategorySuggester.suggestLocally("Swiggy Mumbai", transactions, categories)
        assertTrue(suggestions.isNotEmpty())
        assertEquals(suggestions[0].name, "Food")
        assertTrue(suggestions[0].confidence > 0.6f)
    }

    @Test
    fun suggestLocally_emptyWhenNoMatch() {
        val categories = listOf(
            CategoryEntity(1, "Food", "🍽️", 0xFF0000L),
        )
        val transactions = listOf(
            makeTxn(1, "Swiggy", 1),
        )

        val suggestions = ManualEntryCategorySuggester.suggestLocally("RandomMerchant", transactions, categories)
        // Should return empty or very low confidence
        assertTrue(suggestions.isEmpty() || suggestions.all { it.confidence < 0.5f })
    }

    @Test
    fun suggestLocally_takesTop3() {
        val categories = listOf(
            CategoryEntity(1, "Food", "🍽️", 0xFF0000L),
            CategoryEntity(2, "Transport", "🚕", 0xFF0000L),
            CategoryEntity(3, "Shopping", "🛍️", 0xFF0000L),
            CategoryEntity(4, "Entertainment", "🎬", 0xFF0000L),
        )
        val transactions = listOf(
            makeTxn(1, "Swiggy", 1),
            makeTxn(2, "Uber", 2),
            makeTxn(3, "Amazon", 3),
            makeTxn(4, "Netflix", 4),
        )

        val suggestions = ManualEntryCategorySuggester.suggestLocally("Pizza Hut", transactions, categories)
        assertTrue(suggestions.size <= 3)
    }

    @Test
    fun suggestLocally_blankInputReturnsEmpty() {
        val categories = listOf(
            CategoryEntity(1, "Food", "🍽️", 0xFF0000L),
        )
        val suggestions = ManualEntryCategorySuggester.suggestLocally("", emptyList(), categories)
        assertTrue(suggestions.isEmpty())
    }
}
