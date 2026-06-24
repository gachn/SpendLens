package com.spendlens.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Projection rows for analytics queries. */
data class CategoryTotal(val categoryId: Long?, val total: Long)
data class DayTotal(val dayEpoch: Long, val debit: Long, val credit: Long)
data class MerchantTotal(val counterparty: String, val total: Long, val txns: Int)

/** Latest known balance for an account, for the Accounts overview. */
data class AccountBalance(val accountKey: String, val balanceMinor: Long, val updatedAt: Long)

@Dao
interface RawSmsDao {
    /** Insert ignoring duplicates (unique contentHash). Returns rowId, or -1 if already present. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(raw: RawSmsEntity): Long

    @Query("UPDATE raw_sms SET status = :status, patternId = :patternId WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, patternId: Long?)

    @Query("SELECT * FROM raw_sms WHERE status = :status ORDER BY receivedAt DESC")
    fun observeByStatus(status: String): Flow<List<RawSmsEntity>>

    @Query("SELECT * FROM raw_sms WHERE status = :status ORDER BY receivedAt DESC")
    suspend fun listByStatus(status: String): List<RawSmsEntity>

    @Query("SELECT * FROM raw_sms WHERE id = :id")
    suspend fun getById(id: Long): RawSmsEntity?

    @Query("SELECT COUNT(*) FROM raw_sms")
    suspend fun count(): Int

    @Query("DELETE FROM raw_sms")
    suspend fun clear()
}

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(txn: TransactionEntity): Long

    @Update
    suspend fun update(txn: TransactionEntity)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM transactions WHERE rawSmsId = :rawSmsId")
    suspend fun deleteByRawSmsId(rawSmsId: Long)

    @Query("SELECT * FROM transactions WHERE rawSmsId = :rawSmsId LIMIT 1")
    suspend fun getByRawSmsId(rawSmsId: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE isDuplicate = 0 ORDER BY occurredAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE isDuplicate = 0 ORDER BY occurredAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<TransactionEntity>>

    @Query(
        "SELECT * FROM transactions WHERE isDuplicate = 0 AND occurredAt BETWEEN :from AND :to " +
            "ORDER BY occurredAt DESC",
    )
    fun observeBetween(from: Long, to: Long): Flow<List<TransactionEntity>>

    /** Candidate rows for duplicate detection: same money/account/direction near the same time. */
    @Query(
        "SELECT * FROM transactions WHERE amountMinor = :amount AND accountKey = :account " +
            "AND direction = :direction AND occurredAt BETWEEN :from AND :to",
    )
    suspend fun findCandidates(
        amount: Long,
        account: String,
        direction: String,
        from: Long,
        to: Long,
    ): List<TransactionEntity>

    /** Same-amount rows in one direction near a time → merchant-echo lookup (account-agnostic). */
    @Query(
        "SELECT * FROM transactions WHERE amountMinor = :amount AND direction = :direction " +
            "AND isDuplicate = 0 AND occurredAt BETWEEN :from AND :to",
    )
    suspend fun findByAmountDirection(
        amount: Long,
        direction: String,
        from: Long,
        to: Long,
    ): List<TransactionEntity>

    /** Opposite-direction match on a different account → likely the other leg of a self-transfer. */
    @Query(
        "SELECT * FROM transactions WHERE amountMinor = :amount AND direction = :oppositeDirection " +
            "AND accountKey != :account AND isDuplicate = 0 AND occurredAt BETWEEN :from AND :to LIMIT 1",
    )
    suspend fun findTransferCounterpart(
        amount: Long,
        oppositeDirection: String,
        account: String,
        from: Long,
        to: Long,
    ): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    /** All non-duplicate transactions with a given merchant, newest first — merchant drill-down. */
    @Query(
        "SELECT * FROM transactions WHERE counterparty = :name AND isDuplicate = 0 " +
            "ORDER BY occurredAt DESC",
    )
    fun observeByCounterparty(name: String): Flow<List<TransactionEntity>>

    /** Transactions linked as probable duplicates, for the Review screen. */
    @Query(
        "SELECT * FROM transactions WHERE dupGroupId IS NOT NULL AND userVerified = 0 " +
            "ORDER BY dupGroupId, occurredAt",
    )
    fun observeFlaggedDuplicates(): Flow<List<TransactionEntity>>

    @Query(
        "SELECT categoryId AS categoryId, SUM(amountBaseMinor) AS total FROM transactions " +
            "WHERE isDuplicate = 0 AND excludedFromExpense = 0 AND direction = 'DEBIT' " +
            "AND occurredAt BETWEEN :from AND :to GROUP BY categoryId ORDER BY total DESC",
    )
    fun observeCategoryTotals(from: Long, to: Long): Flow<List<CategoryTotal>>

    /** One-shot category spend totals for a window — used by the velocity-alert worker. */
    @Query(
        "SELECT categoryId AS categoryId, SUM(amountBaseMinor) AS total FROM transactions " +
            "WHERE isDuplicate = 0 AND excludedFromExpense = 0 AND direction = 'DEBIT' " +
            "AND occurredAt BETWEEN :from AND :to GROUP BY categoryId",
    )
    suspend fun categoryTotalsBetween(from: Long, to: Long): List<CategoryTotal>

    @Query(
        "SELECT counterparty AS counterparty, SUM(amountBaseMinor) AS total, COUNT(*) AS txns " +
            "FROM transactions WHERE isDuplicate = 0 AND excludedFromExpense = 0 AND direction = 'DEBIT' " +
            "AND occurredAt BETWEEN :from AND :to GROUP BY counterparty ORDER BY total DESC LIMIT :limit",
    )
    fun observeTopMerchants(from: Long, to: Long, limit: Int): Flow<List<MerchantTotal>>

    @Query(
        "SELECT COALESCE(SUM(amountBaseMinor), 0) FROM transactions WHERE isDuplicate = 0 " +
            "AND excludedFromExpense = 0 AND direction = :direction AND occurredAt BETWEEN :from AND :to",
    )
    fun observeTotal(direction: String, from: Long, to: Long): Flow<Long>

    @Query("UPDATE transactions SET counterparty = :newName WHERE counterparty = :oldName")
    suspend fun renameCounterparty(oldName: String, newName: String)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE counterparty = :name")
    suspend fun setCategoryForCounterparty(name: String, categoryId: Long?)

    @Query("UPDATE transactions SET tags = :tags WHERE counterparty = :name")
    suspend fun setTagsForCounterparty(name: String, tags: String?)

    @Query("UPDATE transactions SET excludedFromExpense = :excluded WHERE counterparty = :name")
    suspend fun setExcludedForCounterparty(name: String, excluded: Boolean)

    /** Latest non-null balance per account, newest first. Used by the Accounts overview. */
    @Query(
        "SELECT accountKey AS accountKey, balanceMinor AS balanceMinor, occurredAt AS updatedAt " +
            "FROM transactions t WHERE balanceMinor IS NOT NULL AND isDuplicate = 0 " +
            "AND occurredAt = (SELECT MAX(occurredAt) FROM transactions WHERE accountKey = t.accountKey " +
            "AND balanceMinor IS NOT NULL AND isDuplicate = 0) " +
            "GROUP BY accountKey ORDER BY updatedAt DESC",
    )
    fun observeAccountBalances(): Flow<List<AccountBalance>>

    /** All non-duplicate debits, oldest first — used by recurring-bill detection. */
    @Query(
        "SELECT * FROM transactions WHERE isDuplicate = 0 AND excludedFromExpense = 0 " +
            "AND direction = 'DEBIT' ORDER BY occurredAt ASC",
    )
    suspend fun allDebits(): List<TransactionEntity>

    /** All non-duplicate transactions, newest first — used by debug export. */
    @Query("SELECT * FROM transactions WHERE isDuplicate = 0 ORDER BY occurredAt DESC")
    suspend fun allTransactions(): List<TransactionEntity>

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactions(): List<TransactionEntity>


    @Query(
        "SELECT COUNT(*) FROM transactions " +
            "WHERE counterparty = :counterparty AND categoryId = :incomeCategoryId " +
            "AND direction = 'CREDIT' AND isDuplicate = 0",
    )
    suspend fun countIncomeTransactions(counterparty: String, incomeCategoryId: Long): Int

    @Query("DELETE FROM transactions")
    suspend fun clear()
}

@Dao
interface BudgetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: Long)

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId")
    suspend fun get(categoryId: Long): BudgetEntity?

    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun all(): List<BudgetEntity>

    @Query("DELETE FROM budgets")
    suspend fun clear()
}

@Dao
interface BillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bill: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM bills ORDER BY dayOfMonth ASC")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills")
    suspend fun all(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE reminderEnabled = 1")
    suspend fun allWithReminders(): List<BillEntity>

    @Query("UPDATE bills SET reminderEnabled = :enabled WHERE id = :id")
    suspend fun setReminderEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM bills")
    suspend fun clear()
}

@Dao
interface MerchantDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(alias: MerchantAliasEntity)

    @Query("SELECT * FROM merchant_aliases WHERE rawKey = :key")
    suspend fun getByKey(key: String): MerchantAliasEntity?

    @Query("SELECT * FROM merchant_aliases WHERE rawKey = :key")
    fun observeByKey(key: String): kotlinx.coroutines.flow.Flow<MerchantAliasEntity?>

    /** All raw tokens that resolve to one display name — the "patterns" shown in the editor. */
    @Query("SELECT * FROM merchant_aliases WHERE displayName = :name")
    suspend fun getByDisplayName(name: String): List<MerchantAliasEntity>

    @Query("SELECT * FROM merchant_aliases WHERE displayName = :name")
    fun observeByDisplayName(name: String): kotlinx.coroutines.flow.Flow<List<MerchantAliasEntity>>

    @Query("UPDATE merchant_aliases SET displayName = :newName WHERE displayName = :oldName")
    suspend fun renameDisplayName(oldName: String, newName: String)

    @Query("DELETE FROM merchant_aliases WHERE rawKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("SELECT * FROM merchant_aliases")
    suspend fun getAll(): List<MerchantAliasEntity>

    @Query("SELECT * FROM merchant_aliases")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<MerchantAliasEntity>>
}

@Dao
interface PatternDao {
    @Query("SELECT * FROM sms_patterns WHERE enabled = 1 ORDER BY priority DESC, matchCount DESC")
    suspend fun enabledOrdered(): List<SmsPatternEntity>

    @Query("SELECT * FROM sms_patterns ORDER BY priority DESC, matchCount DESC")
    fun observeAll(): Flow<List<SmsPatternEntity>>

    @Insert
    suspend fun insert(pattern: SmsPatternEntity): Long

    @Query("UPDATE sms_patterns SET matchCount = matchCount + 1, lastMatchedAt = :at WHERE id = :id")
    suspend fun incrementMatch(id: Long, at: Long)

    @Query("UPDATE sms_patterns SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM sms_patterns WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM sms_patterns")
    suspend fun count(): Int

    @Query("SELECT name FROM sms_patterns")
    suspend fun names(): List<String>

    @Query("DELETE FROM sms_patterns")
    suspend fun clear()
}

@Dao
interface CategoryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCategories(categories: List<CategoryEntity>): List<Long>

    /** Insert a user-created category (id auto-generated). */
    @Insert
    suspend fun insertCategory(category: CategoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRule(rule: CategoryRuleEntity): Long

    /** Drop a prior USER rule for this matcher so a re-categorisation replaces it (latest wins). */
    @Query("DELETE FROM category_rules WHERE matcher = :matcher AND source = 'USER'")
    suspend fun deleteUserRule(matcher: String)

    @Query("SELECT * FROM categories ORDER BY id")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun allCategories(): List<CategoryEntity>

    @Query("SELECT * FROM category_rules")
    suspend fun allRules(): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rules")
    fun observeRules(): Flow<List<CategoryRuleEntity>>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun categoryCount(): Int
}

@Dao
interface CardBillDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(bill: CardBillEntity)

    @Query("SELECT * FROM card_bills WHERE cardKey = :cardKey")
    suspend fun get(cardKey: String): CardBillEntity?

    @Query("SELECT * FROM card_bills")
    fun observeAll(): Flow<List<CardBillEntity>>

    @Query("DELETE FROM card_bills")
    suspend fun clear()
}
