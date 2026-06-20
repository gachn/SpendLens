package com.spendlens.app.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyConverterTest {

    private val rates = mapOf("INR" to 1.0, "USD" to 83.0, "EUR" to 90.0)

    @Test
    fun `converts usd to inr minor units`() {
        // $10.00 = 1000 minor → ₹830.00 = 83000 minor
        assertEquals(83000L, CurrencyConverter.toBaseMinor(1000, "USD", rates))
    }

    @Test
    fun `inr passes through unchanged`() {
        assertEquals(50000L, CurrencyConverter.toBaseMinor(50000, "INR", rates))
    }

    @Test
    fun `unknown currency is left as-is`() {
        assertEquals(1234L, CurrencyConverter.toBaseMinor(1234, "ZZZ", rates))
    }

    @Test
    fun `rounding is applied`() {
        // 199 minor * 83 = 16517
        assertEquals(16517L, CurrencyConverter.toBaseMinor(199, "USD", rates))
    }
}
