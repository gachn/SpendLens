package com.spendlens.app.ui.util

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object Money {
    fun format(amountMinor: Long, currency: String): String {
        val symbol = when (currency) {
            "INR" -> "₹"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; "JPY", "CNY" -> "¥"
            "AUD", "CAD", "SGD", "HKD", "NZD" -> "$"; "KRW" -> "₩"; "THB" -> "฿"
            else -> "$currency "
        }
        return symbol + String.format(Locale.getDefault(), "%,.2f", amountMinor / 100.0)
    }

    fun compact(amountMinor: Long, currency: String): String {
        val symbol = when (currency) { "INR" -> "₹"; "USD" -> "$"; "EUR" -> "€"; "GBP" -> "£"; else -> "" }
        val v = amountMinor / 100.0
        return symbol + when {
            v >= 10_000_000 -> String.format(Locale.getDefault(), "%.1fCr", v / 10_000_000)
            v >= 100_000 -> String.format(Locale.getDefault(), "%.1fL", v / 100_000)
            v >= 1_000 -> String.format(Locale.getDefault(), "%.1fK", v / 1_000)
            else -> String.format(Locale.getDefault(), "%.0f", v)
        }
    }
}

object Dates {
    private val zone: ZoneId = ZoneId.systemDefault()
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val fullDateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val dateTimeFmt = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.getDefault())
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    private val shortMonthFmt = DateTimeFormatter.ofPattern("MMM", Locale.getDefault())

    /** [from, to] epoch-millis bounds of the current calendar month. */
    fun currentMonth(): Pair<Long, Long> {
        val now = java.time.LocalDate.now(zone)
        val start = now.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = now.with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
        return start to end
    }

    fun monthLabel(epoch: Long = System.currentTimeMillis()): String =
        Instant.ofEpochMilli(epoch).atZone(zone).format(monthFmt)

    /** Epoch-millis of Jan 1 (start of day) of the current year. */
    fun yearStart(): Long =
        java.time.LocalDate.now(zone).withDayOfYear(1).atStartOfDay(zone).toInstant().toEpochMilli()

    /** [from, to) epoch-millis bounds of [ym] (start inclusive, next-month start exclusive). */
    fun monthRange(ym: YearMonth): Pair<Long, Long> {
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to end
    }

    /** "June 2026" for a given [YearMonth]. */
    fun label(ym: YearMonth): String = ym.atDay(1).atStartOfDay(zone).format(monthFmt)

    /** Last [count] months, newest first, ending with the current month. */
    fun recentMonths(count: Int = 12): List<YearMonth> {
        val now = YearMonth.now(zone)
        return (0 until count).map { now.minusMonths(it.toLong()) }
    }

    /** The [YearMonth] an epoch-millis instant falls in (device zone). */
    fun monthOf(epoch: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(epoch).atZone(zone))

    /** Short month label e.g. "Jun" for charts/axes. */
    fun shortMonth(ym: YearMonth): String = ym.format(shortMonthFmt)

    fun day(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(zone).format(dayFmt)
    fun date(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(zone).format(fullDateFmt)
    fun dateTime(epoch: Long): String = Instant.ofEpochMilli(epoch).atZone(zone).format(dateTimeFmt)
}
