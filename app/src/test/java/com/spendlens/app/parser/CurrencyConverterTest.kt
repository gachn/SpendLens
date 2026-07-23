package com.spendlens.app.parser

import org.junit.Assert.assertEquals
import org.junit.Test

class CurrencyConverterTest {

    private val rates = mapOf("INR" to 1.0, "USD" to 83.0, "EUR" to 90.0)

    @Test
    fun `converts usd to inr minor units`() {
        // $10.00 = 1000 minor → ₹830.00 = 83000 minor
        assertEquals(83000L, CurrencyConverter.convert(1000, "USD", "INR", rates))
    }

    @Test
    fun `same currency passes through unchanged`() {
        assertEquals(50000L, CurrencyConverter.convert(50000, "INR", "INR", rates))
    }

    @Test
    fun `unknown source currency is left as-is`() {
        assertEquals(1234L, CurrencyConverter.convert(1234, "ZZZ", "INR", rates))
    }

    @Test
    fun `unknown target currency is left as-is`() {
        assertEquals(1234L, CurrencyConverter.convert(1234, "USD", "ZZZ", rates))
    }

    @Test
    fun `rounding is applied`() {
        // 199 minor * 83 = 16517
        assertEquals(16517L, CurrencyConverter.convert(199, "USD", "INR", rates))
    }

    @Test
    fun `converts between two non-pivot currencies via the pivot`() {
        // $10.00 -> INR (830.00) -> EUR: 830 / 90 = 9.222...  => 1000 * 83 / 90 = 922 (rounded)
        assertEquals(922L, CurrencyConverter.convert(1000, "USD", "EUR", rates))
    }

    @Test
    fun `converting to a different primary currency than INR works`() {
        // Any base can be the target, not just INR: EUR10.00 -> USD
        // 1000 * 90 / 83 = 1084 (rounded)
        assertEquals(1084L, CurrencyConverter.convert(1000, "EUR", "USD", rates))
    }
}
