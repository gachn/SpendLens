package com.spendlens.app.ui.viewmodel

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Delta maths for the month-over-month comparison rows (issue #15). */
class CategoryComparisonRowTest {

    private fun row(a: Long, b: Long) =
        CategoryComparisonRow(name = "Food", color = Color.Gray, categoryId = 1L, amountAMinor = a, amountBMinor = b)

    @Test
    fun `delta is month B minus month A`() {
        assertEquals(-2_000L, row(a = 5_000, b = 3_000).deltaMinor)
        assertEquals(2_000L, row(a = 3_000, b = 5_000).deltaMinor)
    }

    @Test
    fun `percent is relative to month A`() {
        assertEquals(-40f, row(a = 5_000, b = 3_000).deltaPercent!!, 0.001f)
        assertEquals(100f, row(a = 2_000, b = 4_000).deltaPercent!!, 0.001f)
    }

    @Test
    fun `percent is null for a brand-new category to avoid divide-by-zero`() {
        assertNull(row(a = 0, b = 4_000).deltaPercent)
    }
}
