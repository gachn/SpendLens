package com.spendlens.app.data.repository

import com.spendlens.app.data.db.BudgetDao
import com.spendlens.app.data.db.BudgetEntity
import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val dao: BudgetDao) {

    fun observeAll(): Flow<List<BudgetEntity>> = dao.observeAll()

    /** One-shot snapshot of all budgets — used by the velocity-alert worker. */
    suspend fun all(): List<BudgetEntity> = dao.all()

    /**
     * Set (or clear, when [limitMinor] <= 0) the monthly budget for a category.
     * [rolloverEnabled] = null preserves the category's existing rollover flag (used by the
     * budget predictor, which only touches limits); pass a concrete value to change it.
     */
    suspend fun setBudget(categoryId: Long, limitMinor: Long, rolloverEnabled: Boolean? = null) {
        if (limitMinor <= 0) {
            dao.delete(categoryId)
            return
        }
        val rollover = rolloverEnabled ?: dao.get(categoryId)?.rolloverEnabled ?: false
        dao.upsert(BudgetEntity(categoryId, limitMinor, rollover))
    }

    /**
     * The effective limit for a month: base [limitMinor] plus any carried-over unspent amount.
     * Rollover = previous-month unspent (limit − spend), floored at 0 and capped at 2× the limit
     * to prevent runaway accumulation.
     */
    fun effectiveLimit(limitMinor: Long, rolloverMinor: Long): Long = limitMinor + rolloverMinor

    fun rolloverAmount(limitMinor: Long, prevSpentMinor: Long): Long =
        (limitMinor - prevSpentMinor).coerceIn(0L, 2L * limitMinor)

    suspend fun clear() = dao.clear()
}
