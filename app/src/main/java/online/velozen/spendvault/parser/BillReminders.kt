package com.spendlens.app.parser

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Due-date math for recurring bills. Pure / unit-testable — java.time only. */
object BillReminders {

    /** The next occurrence of [dayOfMonth] on or after today. */
    fun nextDueDate(dayOfMonth: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val thisMonth = today.withDayOfMonth(dayOfMonth.coerceIn(1, today.lengthOfMonth()))
        if (!thisMonth.isBefore(today)) return thisMonth
        val next = today.plusMonths(1)
        return next.withDayOfMonth(dayOfMonth.coerceIn(1, next.lengthOfMonth()))
    }

    /** Whole days from today until the next due date (0 = due today). */
    fun daysUntilDue(dayOfMonth: Int, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, nextDueDate(dayOfMonth, now, zone))
    }

    /**
     * True when a reminder should fire: the bill is due within [withinDays] and hasn't already
     * been paid in the current billing cycle (the month leading up to the next due date).
     */
    fun shouldRemind(
        dayOfMonth: Int,
        lastPaidAt: Long,
        now: Long,
        zone: ZoneId = ZoneId.systemDefault(),
        withinDays: Int = 3,
    ): Boolean {
        val days = daysUntilDue(dayOfMonth, now, zone)
        if (days !in 0..withinDays.toLong()) return false
        val cycleStart = nextDueDate(dayOfMonth, now, zone).minusMonths(1)
        val lastPaidDate = Instant.ofEpochMilli(lastPaidAt).atZone(zone).toLocalDate()
        return lastPaidDate.isBefore(cycleStart)
    }
}
