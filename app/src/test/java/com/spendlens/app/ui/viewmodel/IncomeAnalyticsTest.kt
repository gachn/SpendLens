package com.spendlens.app.ui.viewmodel

import com.spendlens.app.data.db.TransactionChannel
import com.spendlens.app.data.db.TransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

class IncomeAnalyticsTest {

    private val zone = ZoneId.of("UTC")
    private val thisMonth = YearMonth.of(2025, 6)

    private val juneMs = LocalDate.of(2025, 6, 15).atStartOfDay(zone).toInstant().toEpochMilli()
    private val mayMs = LocalDate.of(2025, 5, 15).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun txn(
        direction: String,
        amountBaseMinor: Long,
        occurredAt: Long,
        counterparty: String = "Test Corp",
        categoryId: Long? = null,
        excluded: Boolean = false,
    ) = TransactionEntity(
        rawSmsId = null,
        amountMinor = amountBaseMinor,
        currency = "INR",
        amountBaseMinor = amountBaseMinor,
        direction = direction,
        accountKey = "acc1",
        counterparty = counterparty,
        occurredAt = occurredAt,
        channel = TransactionChannel.MANUAL,
        excludedFromExpense = excluded,
        categoryId = categoryId,
    )

    @Test
    fun `income sources grouped by counterparty when no category assigned`() {
        val txns = listOf(
            txn("CREDIT", 500_000L, juneMs, counterparty = "Employer A"),
            txn("CREDIT", 200_000L, juneMs, counterparty = "Freelance B"),
            txn("CREDIT", 100_000L, juneMs, counterparty = "Employer A"),
            txn("DEBIT",  300_000L, juneMs, counterparty = "Amazon"),
        )
        val months = listOf(MonthlyBar("Jun", 300_000L, 800_000L))
        val result = IncomeAnalytics.compute(txns, thisMonth, months, zone, emptyMap())

        assertEquals(2, result.sources.size)
        assertEquals("Employer A", result.sources[0].name)
        assertEquals(600_000L, result.sources[0].amountMinor)
        assertEquals("Freelance B", result.sources[1].name)
        assertEquals(200_000L, result.sources[1].amountMinor)
    }

    @Test
    fun `excluded transactions are not counted in income sources`() {
        val txns = listOf(
            txn("CREDIT", 500_000L, juneMs, counterparty = "Employer A"),
            txn("CREDIT", 999_000L, juneMs, counterparty = "Self Transfer", excluded = true),
        )
        val result = IncomeAnalytics.compute(txns, thisMonth, emptyList(), zone, emptyMap())

        assertEquals(1, result.sources.size)
        assertEquals("Employer A", result.sources[0].name)
        assertEquals(500_000L, result.sources[0].amountMinor)
    }

    @Test
    fun `debit transactions are not counted as income sources`() {
        val txns = listOf(
            txn("CREDIT", 500_000L, juneMs, counterparty = "Employer"),
            txn("DEBIT",  200_000L, juneMs, counterparty = "Refund Corp"),
        )
        val result = IncomeAnalytics.compute(txns, thisMonth, emptyList(), zone, emptyMap())

        assertEquals(1, result.sources.size)
        assertEquals("Employer", result.sources[0].name)
    }

    @Test
    fun `only selected month transactions counted for source breakdown`() {
        val txns = listOf(
            txn("CREDIT", 500_000L, juneMs, counterparty = "Employer"),
            txn("CREDIT", 200_000L, mayMs,  counterparty = "Employer"), // wrong month
        )
        val result = IncomeAnalytics.compute(txns, thisMonth, emptyList(), zone, emptyMap())

        assertEquals(1, result.sources.size)
        assertEquals(500_000L, result.sources[0].amountMinor)
    }

    @Test
    fun `income sources grouped by category name when category present`() {
        val categoryMap = mapOf(1L to "Salary", 2L to "Freelance")
        val txns = listOf(
            txn("CREDIT", 500_000L, juneMs, counterparty = "ABC Corp", categoryId = 1L),
            txn("CREDIT", 300_000L, juneMs, counterparty = "XYZ Inc",  categoryId = 1L),
            txn("CREDIT", 100_000L, juneMs, counterparty = "Upwork",   categoryId = 2L),
        )
        val result = IncomeAnalytics.compute(txns, thisMonth, emptyList(), zone, categoryMap)

        assertEquals(2, result.sources.size)
        assertEquals("Salary",   result.sources[0].name)
        assertEquals(800_000L,   result.sources[0].amountMinor)
        assertEquals("Freelance", result.sources[1].name)
        assertEquals(100_000L,   result.sources[1].amountMinor)
    }

    @Test
    fun `savings rate is positive when income exceeds spend`() {
        val months = listOf(MonthlyBar("Jun", 80_000L, 100_000L))
        val result = IncomeAnalytics.compute(emptyList(), thisMonth, months, zone, emptyMap())

        assertEquals(1, result.monthlySavingsRates.size)
        assertEquals(20f, result.monthlySavingsRates[0], 0.01f)
    }

    @Test
    fun `savings rate is negative when spend exceeds income`() {
        val months = listOf(MonthlyBar("Jun", 120_000L, 100_000L))
        val result = IncomeAnalytics.compute(emptyList(), thisMonth, months, zone, emptyMap())

        assertEquals(-20f, result.monthlySavingsRates[0], 0.01f)
    }

    @Test
    fun `savings rate is zero when no income in a month`() {
        val months = listOf(MonthlyBar("Jun", 0L, 0L))
        val result = IncomeAnalytics.compute(emptyList(), thisMonth, months, zone, emptyMap())

        assertEquals(0f, result.monthlySavingsRates[0], 0.01f)
    }

    @Test
    fun `savings rates computed across all months`() {
        val months = listOf(
            MonthlyBar("Apr", 80_000L, 100_000L),   // 20% savings
            MonthlyBar("May", 120_000L, 100_000L),  // -20% savings
            MonthlyBar("Jun", 0L,       0L),         // 0% no income
        )
        val result = IncomeAnalytics.compute(emptyList(), thisMonth, months, zone, emptyMap())

        assertEquals(3, result.monthlySavingsRates.size)
        assertEquals( 20f, result.monthlySavingsRates[0], 0.01f)
        assertEquals(-20f, result.monthlySavingsRates[1], 0.01f)
        assertEquals(  0f, result.monthlySavingsRates[2], 0.01f)
    }

    @Test
    fun `maxSources caps the number of income sources returned`() {
        val txns = (1..10).map { i ->
            txn("CREDIT", i * 10_000L, juneMs, counterparty = "Source $i")
        }
        val result = IncomeAnalytics.compute(txns, thisMonth, emptyList(), zone, emptyMap(), maxSources = 5)

        assertEquals(5, result.sources.size)
    }

    @Test
    fun `empty transactions yields empty sources and zero rates`() {
        val months = listOf(MonthlyBar("Jun", 0L, 0L))
        val result = IncomeAnalytics.compute(emptyList(), thisMonth, months, zone, emptyMap())

        assertEquals(0, result.sources.size)
        assertEquals(1, result.monthlySavingsRates.size)
        assertEquals(0f, result.monthlySavingsRates[0], 0.01f)
    }
}
