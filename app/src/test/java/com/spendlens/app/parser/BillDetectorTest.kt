package com.spendlens.app.parser

import com.spendlens.app.data.DefaultCategories
import com.spendlens.app.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class BillDetectorTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun epoch(date: LocalDate): Long = date.atStartOfDay(utc).toInstant().toEpochMilli()

    private fun debit(party: String, date: LocalDate, amountMinor: Long, categoryId: Long? = null) =
        TransactionEntity(
            rawSmsId = 0,
            amountMinor = amountMinor,
            currency = "INR",
            amountBaseMinor = amountMinor,
            direction = "DEBIT",
            accountKey = "XX1234",
            counterparty = party,
            occurredAt = epoch(date),
            channel = "UPI",
            categoryId = categoryId,
        )

    @Test
    fun `detects a monthly subscription`() {
        val now = epoch(LocalDate.of(2024, 4, 20))
        val txns = listOf(
            debit("Netflix", LocalDate.of(2024, 1, 15), 64900, categoryId = 6),
            debit("Netflix", LocalDate.of(2024, 2, 15), 64900, categoryId = 6),
            debit("Netflix", LocalDate.of(2024, 3, 16), 64900, categoryId = 6),
            debit("Netflix", LocalDate.of(2024, 4, 15), 64900, categoryId = 6),
        )
        val bills = BillDetector.detect(txns, now = now, zone = utc)
        assertEquals(1, bills.size)
        val bill = bills.first()
        assertEquals("Netflix", bill.counterparty)
        assertEquals(64900L, bill.typicalAmountMinor)
        assertEquals(15, bill.dayOfMonth)
        assertEquals(6L, bill.categoryId)
    }

    @Test
    fun `ignores one-off and stale merchants`() {
        val now = epoch(LocalDate.of(2024, 6, 1))
        val txns = listOf(
            // Only two occurrences → not recurring.
            debit("RandomShop", LocalDate.of(2024, 4, 2), 1000),
            debit("RandomShop", LocalDate.of(2024, 5, 2), 1000),
            // Recurring but last paid long ago (stale / cancelled).
            debit("OldGym", LocalDate.of(2023, 1, 5), 200000),
            debit("OldGym", LocalDate.of(2023, 2, 5), 200000),
            debit("OldGym", LocalDate.of(2023, 3, 5), 200000),
        )
        assertTrue(BillDetector.detect(txns, now = now, zone = utc).isEmpty())
    }

    @Test
    fun `travel merchants categorise as Travel`() {
        val categorizer = Categorizer(DefaultCategories.rules.map { Categorizer.Rule(it.first, it.second) })
        assertEquals(8L, categorizer.categorize("IRCTC"))
        assertEquals(8L, categorizer.categorize("MakeMyTrip"))
        assertEquals(8L, categorizer.categorize("REDBUS"))
        assertEquals(8L, categorizer.categorize("IndiGo Airlines"))
        // A telecom merchant must not get swept into Travel.
        assertEquals(5L, categorizer.categorize("AIRTEL Postpaid"))
    }
}
