package com.spendlens.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetPredictorTest {

    /** Rupees → minor units helper for readability. */
    private fun rs(amount: Int): Long = amount * 100L

    @Test
    fun `no history predicts zero`() {
        assertEquals(0L, BudgetPredictor.predict(emptyList()))
        assertEquals(0L, BudgetPredictor.predict(listOf(0L, 0L, 0L)))
    }

    @Test
    fun `single month rounds up to a nice number`() {
        // ₹4,237 → rounded up to the nearest ₹100 = ₹4,300
        assertEquals(rs(4_300), BudgetPredictor.predict(listOf(rs(4_237))))
    }

    @Test
    fun `steady spend predicts near that level`() {
        val series = List(12) { rs(5_000) }
        val predicted = BudgetPredictor.predict(series)
        // Flat history → budget hugs the spend, with at most the rounding step of headroom.
        assertTrue("predicted=$predicted", predicted in rs(5_000)..rs(5_500))
    }

    @Test
    fun `rising trend predicts above the plain average`() {
        // Climbing every month; plain average would under-budget the coming month.
        val series = listOf(
            rs(1_000), rs(1_200), rs(1_500), rs(1_800),
            rs(2_100), rs(2_500), rs(3_000), rs(3_600),
        )
        val plainAverage = series.average().toLong()
        val predicted = BudgetPredictor.predict(series)
        assertTrue("predicted=$predicted avg=$plainAverage", predicted > plainAverage)
        // …but still anchored to recent reality, not wildly above the latest month.
        assertTrue("predicted=$predicted", predicted >= rs(3_600))
    }

    @Test
    fun `leading inactive months are ignored`() {
        // Category only started 3 months ago; the 9 leading zeros must not deflate the forecast.
        val series = listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, rs(2_000), rs(2_000), rs(2_000))
        val predicted = BudgetPredictor.predict(series)
        assertTrue("predicted=$predicted", predicted >= rs(2_000))
    }

    @Test
    fun `volatile spend gets headroom above the mean`() {
        val series = listOf(rs(1_000), rs(8_000), rs(1_000), rs(9_000), rs(1_000), rs(8_500))
        val mean = series.average().toLong()
        val predicted = BudgetPredictor.predict(series)
        assertTrue("predicted=$predicted mean=$mean", predicted >= mean)
    }
}
