package com.spendlens.app.data.repository

import com.spendlens.app.data.db.AccountBalance
import com.spendlens.app.data.db.CategoryTotal
import com.spendlens.app.data.db.MerchantTotal
import com.spendlens.app.data.db.TransactionDao
import com.spendlens.app.data.db.TransactionEntity
import kotlinx.coroutines.flow.Flow

class TransactionRepository(private val dao: TransactionDao) {

    fun observeRecent(limit: Int = 20): Flow<List<TransactionEntity>> = dao.observeRecent(limit)
    fun observeAll(): Flow<List<TransactionEntity>> = dao.observeAll()
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>> =
        dao.observeBetween(from, to)

    fun observeCategoryTotals(from: Long, to: Long): Flow<List<CategoryTotal>> =
        dao.observeCategoryTotals(from, to)

    suspend fun categoryTotalsBetween(from: Long, to: Long): List<CategoryTotal> =
        dao.categoryTotalsBetween(from, to)

    fun observeTopMerchants(from: Long, to: Long, limit: Int = 5): Flow<List<MerchantTotal>> =
        dao.observeTopMerchants(from, to, limit)

    fun observeTotal(direction: String, from: Long, to: Long): Flow<Long> =
        dao.observeTotal(direction, from, to)

    fun observeFlaggedDuplicates(): Flow<List<TransactionEntity>> = dao.observeFlaggedDuplicates()

    /** All transactions with [name] as counterparty, newest first — backs the merchant drill-down. */
    fun observeByCounterparty(name: String): Flow<List<TransactionEntity>> =
        dao.observeByCounterparty(name)

    fun observeAccountBalances(): Flow<List<AccountBalance>> = dao.observeAccountBalances()

    suspend fun allDebits(): List<TransactionEntity> = dao.allDebits()

    suspend fun allTransactions(): List<TransactionEntity> = dao.allTransactions()

    suspend fun insert(txn: TransactionEntity): Long = dao.insert(txn)
    suspend fun update(txn: TransactionEntity) = dao.update(txn)
    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun findCandidates(
        amount: Long,
        account: String,
        direction: String,
        from: Long,
        to: Long,
    ): List<TransactionEntity> = dao.findCandidates(amount, account, direction, from, to)

    suspend fun findTransferCounterpart(
        amount: Long,
        oppositeDirection: String,
        account: String,
        from: Long,
        to: Long,
    ): TransactionEntity? = dao.findTransferCounterpart(amount, oppositeDirection, account, from, to)

    suspend fun getById(id: Long): TransactionEntity? = dao.getById(id)

    suspend fun renameCounterparty(old: String, new: String) = dao.renameCounterparty(old, new)
}
