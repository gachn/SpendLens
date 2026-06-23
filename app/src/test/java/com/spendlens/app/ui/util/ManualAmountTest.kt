package com.spendlens.app.ui.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for [ManualAmount.parseMinor] — manual-entry amount validation (PRD AC-5 / AC-1). */
class ManualAmountTest {

    @Test fun `whole rupees parse to minor units`() {
        assertEquals(50000L, ManualAmount.parseMinor("500"))
    }

    @Test fun `two decimals parse exactly`() {
        assertEquals(50050L, ManualAmount.parseMinor("500.50"))
        assertEquals(1L, ManualAmount.parseMinor("0.01"))
    }

    @Test fun `more than two decimals rounds half up`() {
        assertEquals(50056L, ManualAmount.parseMinor("500.555"))
    }

    @Test fun `thousands separators are tolerated`() {
        assertEquals(123456L, ManualAmount.parseMinor("1,234.56"))
    }

    @Test fun `blank is rejected`() {
        assertNull(ManualAmount.parseMinor(""))
        assertNull(ManualAmount.parseMinor("   "))
    }

    @Test fun `non-numeric is rejected`() {
        assertNull(ManualAmount.parseMinor("abc"))
        assertNull(ManualAmount.parseMinor("12x"))
    }

    @Test fun `zero and negative are rejected`() {
        assertNull(ManualAmount.parseMinor("0"))
        assertNull(ManualAmount.parseMinor("0.00"))
        assertNull(ManualAmount.parseMinor("-5"))
    }
}
