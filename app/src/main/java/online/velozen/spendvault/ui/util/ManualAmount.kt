package com.spendlens.app.ui.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parses a user-typed amount string into integer minor units (e.g. paise), the only money
 * representation the app stores (docs/DESIGN.md §2). Pure Kotlin → JVM-unit-testable; backs the
 * manual-entry form's amount validation (PRD AC-5 / AC-1).
 */
object ManualAmount {

    /**
     * @return the amount in minor units, or null if [text] is blank, non-numeric, or not strictly
     * positive. A value like "500.5" → 50050; more than two decimals is rounded half-up.
     */
    fun parseMinor(text: String): Long? {
        val trimmed = text.trim().replace(",", "")
        if (trimmed.isEmpty()) return null
        val value = trimmed.toBigDecimalOrNull() ?: return null
        if (value.signum() <= 0) return null
        return value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()
    }

    private fun String.toBigDecimalOrNull(): BigDecimal? = runCatching { BigDecimal(this) }.getOrNull()
}
