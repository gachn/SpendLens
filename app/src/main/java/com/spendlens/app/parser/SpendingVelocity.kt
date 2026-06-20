package com.spendlens.app.parser

import java.time.Instant
import java.time.ZoneId

/**
 * Mid-month spending-velocity maths. Pure / unit-testable — java.time only.
 *
 * Projects month-end spend from spend-so-far by assuming the current daily pace continues
 * for the rest of the month, then flags categories whose projection blows past their budget.
 * See issue #3 (spending velocity alert).
 */
object SpendingVelocity {

    /**
     * Linearly project total spend for the calendar month containing [now] given that
     * [spentMinor] has been spent through that day. Spend so far is treated as the run-rate
     * over the elapsed days (today inclusive) and extrapolated to the full month length.
     */
    fun projectMonthEnd(spentMinor: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val dayOfMonth = date.dayOfMonth
        val daysInMonth = date.lengthOfMonth()
        if (dayOfMonth <= 0) return spentMinor
        // Integer maths in minor units; rounds down, which is fine for an early-warning signal.
        return spentMinor * daysInMonth / dayOfMonth
    }

    /**
     * True when [spentMinor] is on pace to exceed [limitMinor] by month end. Categories with no
     * budget ([limitMinor] <= 0) and ones with no spend yet never trip the alert.
     */
    fun isOnPaceToExceed(
        spentMinor: Long,
        limitMinor: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Boolean {
        if (limitMinor <= 0 || spentMinor <= 0) return false
        return projectMonthEnd(spentMinor, now, zone) > limitMinor
    }
}
