package com.spendlens.app.data.repository

import com.spendlens.app.data.db.TransactionDao
import com.spendlens.app.data.db.TransactionEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

/**
 * Unit tests for the manual-entry CRUD on [TransactionRepository] (PRD AC-1, AC-4, AC-7).
 * Uses a reflective [Proxy] fake DAO (same approach as SmsProcessorTest) to capture the row
 * written and the DAO methods invoked, without an Android/Room runtime.
 */
class TransactionRepositoryManualTest {

    private val rates = mapOf("INR" to 1.0, "USD" to 83.0)

    /** Records invoked method names and the last entity passed to insert/update. */
    private class Capture {
        val methods = mutableListOf<String>()
        var lastEntity: TransactionEntity? = null
        var splits: List<com.spendlens.app.data.db.TransactionSplitEntity>? = null
    }

    private fun repoWith(capture: Capture): TransactionRepository {
        val dao = Proxy.newProxyInstance(
            TransactionDao::class.java.classLoader,
            arrayOf(TransactionDao::class.java),
        ) { _, method, args ->
            capture.methods += method.name
            when (method.name) {
                "insert" -> {
                    capture.lastEntity = args?.get(0) as? TransactionEntity
                    1L
                }
                "update" -> {
                    capture.lastEntity = args?.get(0) as? TransactionEntity
                    Unit
                }
                else -> null
            }
        } as TransactionDao
        val splitDao = Proxy.newProxyInstance(
            com.spendlens.app.data.db.TransactionSplitDao::class.java.classLoader,
            arrayOf(com.spendlens.app.data.db.TransactionSplitDao::class.java),
        ) { _, method, args ->
            capture.methods += method.name
            if (method.name == "insertAll") {
                @Suppress("UNCHECKED_CAST")
                capture.splits = args?.get(0) as? List<com.spendlens.app.data.db.TransactionSplitEntity>
            }
            null
        } as com.spendlens.app.data.db.TransactionSplitDao
        return TransactionRepository(dao, splitDao)
    }

    @Test
    fun `addManual writes a MANUAL row with null rawSmsId and userVerified`() = runTest {
        val capture = Capture()
        val id = repoWith(capture).addManual(
            amountMinor = 50000, currency = "INR", direction = "DEBIT", accountKey = "Cash",
            counterparty = "Groceries", occurredAt = 1000, categoryId = 3L,
            note = "weekly", tags = "home", receiptUri = null, ratesToBase = rates,
        )
        assertEquals(1L, id)
        val e = requireNotNull(capture.lastEntity)
        assertNull("manual row has no backing SMS", e.rawSmsId)
        assertEquals("MANUAL", e.channel)
        assertTrue(e.userVerified)
        assertFalse(e.isDuplicate)
        assertEquals(50000L, e.amountMinor)
    }

    @Test
    fun `splitTransaction distributes base minor with remainder on the last part`() = runTest {
        val capture = Capture()
        val parent = TransactionEntity(
            id = 7L, rawSmsId = null, amountMinor = 1000L, currency = "INR", amountBaseMinor = 1000L,
            direction = "DEBIT", accountKey = "A", counterparty = "Shop", occurredAt = 0L, channel = "X",
        )
        repoWith(capture).splitTransaction(parent, listOf(1L to 333L, 2L to 333L, 3L to 334L))

        val splits = requireNotNull(capture.splits)
        assertEquals(3, splits.size)
        // Base shares sum exactly to the parent base total (no lost/created paise).
        assertEquals(1000L, splits.sumOf { it.amountBaseMinor })
        assertEquals(listOf(333L, 333L, 334L), splits.map { it.amountBaseMinor })
        assertTrue("parent flagged as split", requireNotNull(capture.lastEntity).isSplit)
        assertTrue(capture.methods.contains("deleteForParent"))
    }

    @Test
    fun `splitTransaction rejects parts that do not sum to the total`() = runTest {
        val capture = Capture()
        val parent = TransactionEntity(
            id = 7L, rawSmsId = null, amountMinor = 1000L, currency = "INR", amountBaseMinor = 1000L,
            direction = "DEBIT", accountKey = "A", counterparty = "Shop", occurredAt = 0L, channel = "X",
        )
        var threw = false
        try {
            repoWith(capture).splitTransaction(parent, listOf(1L to 400L, 2L to 400L))
        } catch (e: IllegalArgumentException) {
            threw = true
        }
        assertTrue("must reject parts that don't sum to total", threw)
    }

    @Test
    fun `addManual computes amountBaseMinor for base currency as identity`() = runTest {
        val capture = Capture()
        repoWith(capture).addManual(
            amountMinor = 50000, currency = "INR", direction = "DEBIT", accountKey = "Cash",
            counterparty = "Groceries", occurredAt = 1000, categoryId = null,
            note = null, tags = null, receiptUri = null, ratesToBase = rates,
        )
        assertEquals(50000L, requireNotNull(capture.lastEntity).amountBaseMinor)
    }

    @Test
    fun `addManual converts a non-base currency to base minor units`() = runTest {
        val capture = Capture()
        repoWith(capture).addManual(
            amountMinor = 10000, currency = "USD", direction = "DEBIT", accountKey = "Cash",
            counterparty = "Hotel", occurredAt = 1000, categoryId = null,
            note = null, tags = null, receiptUri = null, ratesToBase = rates,
        )
        // 10000 minor * 83.0 = 830000 base minor units.
        assertEquals(830000L, requireNotNull(capture.lastEntity).amountBaseMinor)
    }

    @Test
    fun `addManual only calls dao insert - no dedup or categorize`() = runTest {
        val capture = Capture()
        repoWith(capture).addManual(
            amountMinor = 50000, currency = "INR", direction = "DEBIT", accountKey = "Cash",
            counterparty = "Groceries", occurredAt = 1000, categoryId = null,
            note = null, tags = null, receiptUri = null, ratesToBase = rates,
        )
        assertEquals(listOf("insert"), capture.methods)
    }

    @Test
    fun `updateManual recomputes amountBaseMinor and preserves id`() = runTest {
        val capture = Capture()
        val edited = TransactionEntity(
            id = 7, rawSmsId = null, amountMinor = 10000, currency = "USD",
            amountBaseMinor = 0, direction = "DEBIT", accountKey = "Cash",
            counterparty = "Hotel", occurredAt = 1000, channel = "MANUAL",
            userVerified = true,
        )
        repoWith(capture).updateManual(edited, rates)
        val e = requireNotNull(capture.lastEntity)
        assertEquals(listOf("update"), capture.methods)
        assertEquals(7L, e.id)
        assertEquals(830000L, e.amountBaseMinor)
    }
}
