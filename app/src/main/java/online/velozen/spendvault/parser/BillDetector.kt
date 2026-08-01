package com.spendlens.app.parser

import com.spendlens.app.data.db.TransactionEntity
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

/** A recurring bill/subscription inferred from transaction history. */
data class DetectedBill(
    val counterparty: String,
    val typicalAmountMinor: Long,
    val dayOfMonth: Int,
    val categoryId: Long?,
    val lastPaidAt: Long,
)

/**
 * Infers recurring bills from debit history. A counterparty is treated as recurring when it
 * has at least [minOccurrences] debits spread across at least [minDistinctMonths] calendar
 * months and was paid recently (within [recencyDays]). Pure / unit-testable — no Android deps.
 */
object BillDetector {

    fun detect(
        debits: List<TransactionEntity>,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        minOccurrences: Int = 3,
        minDistinctMonths: Int = 3,
        recencyDays: Int = 70,
    ): List<DetectedBill> {
        val recencyCutoff = now - recencyDays * DAY_MS
        return debits
            .filter { it.counterparty.isNotBlank() }
            .groupBy { it.counterparty }
            .mapNotNull { (counterparty, group) ->
                if (group.size < minOccurrences) return@mapNotNull null
                val months = group.map { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) }.distinct()
                if (months.size < minDistinctMonths) return@mapNotNull null
                val lastPaidAt = group.maxOf { it.occurredAt }
                if (lastPaidAt < recencyCutoff) return@mapNotNull null

                val day = medianInt(group.map { Instant.ofEpochMilli(it.occurredAt).atZone(zone).dayOfMonth })
                    .coerceIn(1, 28)
                val amount = medianLong(group.map { it.amountBaseMinor.takeIf { v -> v > 0 } ?: it.amountMinor })
                val category = group.mapNotNull { it.categoryId }
                    .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

                DetectedBill(
                    counterparty = counterparty,
                    typicalAmountMinor = amount,
                    dayOfMonth = day,
                    categoryId = category,
                    lastPaidAt = lastPaidAt,
                )
            }
            .sortedByDescending { it.lastPaidAt }
    }

    private fun medianInt(values: List<Int>): Int = values.sorted()[values.size / 2]

    private fun medianLong(values: List<Long>): Long = values.sorted()[values.size / 2]

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
