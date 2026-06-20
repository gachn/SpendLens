package com.spendlens.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class BillRemindersTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private fun epoch(date: LocalDate): Long = date.atStartOfDay(utc).toInstant().toEpochMilli()

    @Test
    fun `next due date is this month when day not yet passed`() {
        val now = epoch(LocalDate.of(2024, 3, 10))
        assertEquals(LocalDate.of(2024, 3, 15), BillReminders.nextDueDate(15, now, utc))
        assertEquals(5L, BillReminders.daysUntilDue(15, now, utc))
    }

    @Test
    fun `next due date rolls to next month when day has passed`() {
        val now = epoch(LocalDate.of(2024, 3, 20))
        assertEquals(LocalDate.of(2024, 4, 15), BillReminders.nextDueDate(15, now, utc))
    }

    @Test
    fun `reminds when due soon and unpaid this cycle`() {
        val now = epoch(LocalDate.of(2024, 3, 13)) // 2 days before the 15th
        val lastPaid = epoch(LocalDate.of(2024, 2, 14)) // before cycle start (2024-02-15)
        assertTrue(BillReminders.shouldRemind(15, lastPaid, now, utc, withinDays = 3))
    }

    @Test
    fun `does not remind when already paid this cycle`() {
        val now = epoch(LocalDate.of(2024, 3, 13))
        val lastPaid = epoch(LocalDate.of(2024, 3, 1)) // within the current cycle (>= 2024-02-15)
        assertFalse(BillReminders.shouldRemind(15, lastPaid, now, utc, withinDays = 3))
    }

    @Test
    fun `does not remind when due is far off`() {
        val now = epoch(LocalDate.of(2024, 3, 1)) // 14 days before the 15th
        val lastPaid = epoch(LocalDate.of(2024, 1, 15))
        assertFalse(BillReminders.shouldRemind(15, lastPaid, now, utc, withinDays = 3))
    }
}
