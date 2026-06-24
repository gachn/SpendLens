package com.spendlens.app.data.repository

import com.spendlens.app.data.db.AccountBalance
import com.spendlens.app.data.db.CategoryTotal
import com.spendlens.app.data.db.MerchantTotal
import com.spendlens.app.data.db.TransactionChannel
import com.spendlens.app.data.db.TransactionDao
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.data.db.TransactionSplitDao
import com.spendlens.app.data.db.TransactionSplitEntity
import com.spendlens.app.parser.CurrencyConverter
import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val dao: TransactionDao,
    private val splitDao: TransactionSplitDao,
) {

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
    suspend fun getAllTransactions(): List<TransactionEntity> = dao.getAllTransactions()


    suspend fun insert(txn: TransactionEntity): Long = dao.insert(txn)
    suspend fun update(txn: TransactionEntity) = dao.update(txn)
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun deleteByRawSmsId(rawSmsId: Long) = dao.deleteByRawSmsId(rawSmsId)
    suspend fun getByRawSmsId(rawSmsId: Long): TransactionEntity? = dao.getByRawSmsId(rawSmsId)

    /**
     * Persist a user-entered (cash / non-SMS) transaction. Manual rows carry [rawSmsId] = null,
     * [TransactionChannel.MANUAL], and are pre-verified, so they bypass the SMS pipeline
     * (parser, duplicate detector, categorizer) entirely. [amountBaseMinor] is frozen at write
     * time via the pure [CurrencyConverter], mirroring how SMS rows are converted at ingest.
     */
    suspend fun addManual(
        amountMinor: Long,
        currency: String,
        direction: String,
        accountKey: String,
        counterparty: String,
        occurredAt: Long,
        categoryId: Long?,
        note: String?,
        tags: String?,
        receiptUri: String?,
        excludedFromExpense: Boolean = false,
        ratesToBase: Map<String, Double>,
    ): Long = dao.insert(
        TransactionEntity(
            rawSmsId = null,
            amountMinor = amountMinor,
            currency = currency,
            amountBaseMinor = CurrencyConverter.toBaseMinor(amountMinor, currency, ratesToBase),
            direction = direction,
            accountKey = accountKey,
            counterparty = counterparty,
            balanceMinor = null,
            referenceId = null,
            occurredAt = occurredAt,
            channel = TransactionChannel.MANUAL,
            categoryId = categoryId,
            dupGroupId = null,
            isDuplicate = false,
            userVerified = true,
            excludedFromExpense = excludedFromExpense,
            note = note,
            tags = tags,
            receiptUri = receiptUri,
        ),
    )

    /**
     * Update an edited manual transaction in place (same row id, no duplicate). Recomputes
     * [amountBaseMinor] from the edited amount/currency so totals stay consistent.
     */
    suspend fun updateManual(txn: TransactionEntity, ratesToBase: Map<String, Double>) =
        dao.update(
            txn.copy(amountBaseMinor = CurrencyConverter.toBaseMinor(txn.amountMinor, txn.currency, ratesToBase)),
        )

    suspend fun findCandidates(
        amount: Long,
        account: String,
        direction: String,
        from: Long,
        to: Long,
    ): List<TransactionEntity> = dao.findCandidates(amount, account, direction, from, to)

    suspend fun findByAmountDirection(
        amount: Long,
        direction: String,
        from: Long,
        to: Long,
    ): List<TransactionEntity> = dao.findByAmountDirection(amount, direction, from, to)

    suspend fun findTransferCounterpart(
        amount: Long,
        oppositeDirection: String,
        account: String,
        from: Long,
        to: Long,
    ): TransactionEntity? = dao.findTransferCounterpart(amount, oppositeDirection, account, from, to)

    suspend fun getById(id: Long): TransactionEntity? = dao.getById(id)

    suspend fun renameCounterparty(old: String, new: String) = dao.renameCounterparty(old, new)

    suspend fun setCategoryForCounterparty(name: String, categoryId: Long?) =
        dao.setCategoryForCounterparty(name, categoryId)

    suspend fun setTagsForCounterparty(name: String, tags: String?) =
        dao.setTagsForCounterparty(name, tags)

    suspend fun setExcludedForCounterparty(name: String, excluded: Boolean) =
        dao.setExcludedForCounterparty(name, excluded)

    suspend fun hasHistoricalIncome(counterparty: String, incomeCategoryId: Long): Boolean {
        return dao.countIncomeTransactions(counterparty, incomeCategoryId) > 0
    }

    // ── Transaction splits (issue #11) ──────────────────────────────────────────

    fun observeSplits(parentId: Long): Flow<List<TransactionSplitEntity>> =
        splitDao.observeForParent(parentId)

    fun observeAllSplits(): Flow<List<TransactionSplitEntity>> = splitDao.observeAll()

    /**
     * Split [parent] across categories. [parts] are (categoryId, displayAmountMinor) pairs that must
     * sum to the parent's [TransactionEntity.amountMinor]. The base-currency share of each part is
     * derived proportionally from the parent's [amountBaseMinor] (last part absorbs the rounding
     * remainder so the children sum exactly to the parent). The parent is flagged [isSplit] = true,
     * which removes it from category totals while keeping it in lists and account/spend totals.
     */
    suspend fun splitTransaction(parent: TransactionEntity, parts: List<Pair<Long?, Long>>) {
        require(parts.isNotEmpty()) { "A split needs at least one part" }
        val displayTotal = parts.sumOf { it.second }
        require(displayTotal == parent.amountMinor) {
            "Split parts ($displayTotal) must sum to the transaction total (${parent.amountMinor})"
        }
        val baseTotal = parent.amountBaseMinor
        var baseAssigned = 0L
        val splits = parts.mapIndexed { i, (categoryId, displayMinor) ->
            val baseMinor = if (i == parts.lastIndex) {
                baseTotal - baseAssigned
            } else {
                val share = if (displayTotal == 0L) 0L else baseTotal * displayMinor / displayTotal
                baseAssigned += share
                share
            }
            TransactionSplitEntity(
                parentId = parent.id,
                categoryId = categoryId,
                amountMinor = displayMinor,
                amountBaseMinor = baseMinor,
            )
        }
        splitDao.deleteForParent(parent.id)
        splitDao.insertAll(splits)
        dao.update(parent.copy(isSplit = true))
    }

    /** Undo a split: drop the child rows and clear the parent's [isSplit] flag. */
    suspend fun clearSplit(parent: TransactionEntity) {
        splitDao.deleteForParent(parent.id)
        dao.update(parent.copy(isSplit = false))
    }
}
