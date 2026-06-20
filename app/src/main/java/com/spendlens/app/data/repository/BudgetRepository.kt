package com.spendlens.app.data.repository

import com.spendlens.app.data.db.BudgetDao
import com.spendlens.app.data.db.BudgetEntity
import kotlinx.coroutines.flow.Flow

class BudgetRepository(private val dao: BudgetDao) {

    fun observeAll(): Flow<List<BudgetEntity>> = dao.observeAll()

    /** Set (or clear, when [limitMinor] <= 0) the monthly budget for a category. */
    suspend fun setBudget(categoryId: Long, limitMinor: Long) {
        if (limitMinor <= 0) dao.delete(categoryId)
        else dao.upsert(BudgetEntity(categoryId, limitMinor))
    }

    suspend fun clear() = dao.clear()
}
