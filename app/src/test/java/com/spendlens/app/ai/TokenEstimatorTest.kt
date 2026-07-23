package com.spendlens.app.ai

import org.junit.Assert.assertEquals
import org.junit.Test

/** Unit tests for [TokenEstimator]'s chars/4 heuristic. */
class TokenEstimatorTest {

    @Test fun `empty string is zero tokens`() {
        assertEquals(0, TokenEstimator.estimate(""))
    }

    @Test fun `rounds up rather than truncating`() {
        assertEquals(1, TokenEstimator.estimate("a"))
        assertEquals(1, TokenEstimator.estimate("abcd"))
        assertEquals(2, TokenEstimator.estimate("abcde"))
    }

    @Test fun `scales with length`() {
        val text = "x".repeat(400)
        assertEquals(100, TokenEstimator.estimate(text))
    }
}
