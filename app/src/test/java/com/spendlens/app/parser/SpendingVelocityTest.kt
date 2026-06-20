package com.spendlens.app.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

class SpendingVelocityTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private fun epoch(date: LocalDate): Long = date.atStartOfDay(utc).toInstant().toEpochMilli()

    @Test
    fun `projects month-end from current pace`() {
        // Day 10 of a 31-day month, 1000 spent → 1000 * 31 / 10 = 3100.
        val now = epoch(LocalDate.of(2024, 1, 10))
        assertEquals(3100L, SpendingVelocity.projectMonthEnd(1000L, now, utc))
    }

    @Test
    fun `on pace to exceed when projection beats limit`() {
        val now = epoch(LocalDate.of(2024, 1, 10)) // projects to 3100
        assertTrue(SpendingVelocity.isOnPaceToExceed(1000L, 3000L, now, utc))
    }

    @Test
    fun `not on pace when projection within limit`() {
        val now = epoch(LocalDate.of(2024, 1, 10)) // projects to 3100
        assertFalse(SpendingVelocity.isOnPaceToExceed(1000L, 4000L, now, utc))
    }

    @Test
    fun `no alert without a budget`() {
        val now = epoch(LocalDate.of(2024, 1, 10))
        assertFalse(SpendingVelocity.isOnPaceToExceed(5000L, 0L, now, utc))
    }

    @Test
    fun `no alert with zero spend`() {
        val now = epoch(LocalDate.of(2024, 1, 10))
        assertFalse(SpendingVelocity.isOnPaceToExceed(0L, 1000L, now, utc))
    }

    @Test
    fun `late month on-track spend stays under limit`() {
        // Day 28 of 31, 900 spent, 1000 limit → projects to 900 * 31 / 28 = 996 < 1000.
        val now = epoch(LocalDate.of(2024, 1, 28))
        assertFalse(SpendingVelocity.isOnPaceToExceed(900L, 1000L, now, utc))
    }
}
