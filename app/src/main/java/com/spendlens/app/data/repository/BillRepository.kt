package com.spendlens.app.data.repository

import com.spendlens.app.data.db.BillDao
import com.spendlens.app.data.db.BillEntity
import com.spendlens.app.parser.DetectedBill
import kotlinx.coroutines.flow.Flow

class BillRepository(private val dao: BillDao) {

    fun observeAll(): Flow<List<BillEntity>> = dao.observeAll()
    suspend fun allWithReminders(): List<BillEntity> = dao.allWithReminders()
    suspend fun upsert(bill: BillEntity) = dao.upsert(bill)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun setReminderEnabled(id: Long, enabled: Boolean) = dao.setReminderEnabled(id, enabled)
    suspend fun clear() = dao.clear()

    /**
     * Reconcile detected bills with what's stored: update amount/day/category in place,
     * preserving each bill's id and the user's reminder on/off choice.
     */
    suspend fun syncDetected(detected: List<DetectedBill>) {
        val existing = dao.all().associateBy { it.counterparty }
        detected.forEach { d ->
            val prev = existing[d.counterparty]
            dao.upsert(
                BillEntity(
                    id = prev?.id ?: 0,
                    counterparty = d.counterparty,
                    typicalAmountMinor = d.typicalAmountMinor,
                    dayOfMonth = d.dayOfMonth,
                    categoryId = d.categoryId,
                    lastPaidAt = d.lastPaidAt,
                    reminderEnabled = prev?.reminderEnabled ?: true,
                ),
            )
        }
    }
}
