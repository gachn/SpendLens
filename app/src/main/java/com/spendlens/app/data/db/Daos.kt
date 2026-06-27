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

/** Most common SMS sender address for each accountKey — used for bank brand detection. */
data class AccountSender(val accountKey: String, val sender: String)

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

    /** All raw SMS rows that were last parsed by [patternId]. Used for targeted reprocess. */
    @Query("SELECT * FROM raw_sms WHERE patternId = :patternId ORDER BY receivedAt DESC")
    suspend fun listByPatternId(patternId: Long): List<RawSmsEntity>

    /**
     * Latest [receivedAt] timestamp across all stored SMS, or null when the table is empty.
     * Used by [com.spendlens.app.sms.SmsImporter] to skip inbox messages already ingested
     * (incremental import — only pull SMS newer than this timestamp on subsequent syncs).
     */
    @Query("SELECT MAX(receivedAt) FROM raw_sms")
    suspend fun maxReceivedAt(): Long?

    @Query("SELECT COUNT(*) FROM raw_sms")
    suspend fun count(): Int

    /** Mark every SMS from [sender] as IGNORED — called when AI classifies sender as non-financial. */
    @Query("UPDATE raw_sms SET status = 'IGNORED', patternId = NULL WHERE sender = :sender AND status != 'IGNORED'")
    suspend fun ignoreAllForSender(sender: String)

    /** All IGNORED SMS from [sender] — used to re-process if AI later classifies sender as financial. */
    @Query("SELECT * FROM raw_sms WHERE sender = :sender AND status = 'IGNORED'")
    suspend fun listIgnoredForSender(sender: String): List<RawSmsEntity>

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

    /**
     * Per-category debit totals for a window. Split parents (isSplit = 1) are excluded and their
     * [transaction_splits] children are counted under their own categories instead (issue #11).
     */
    @Query(
        "SELECT categoryId AS categoryId, SUM(total) AS total FROM (" +
            "SELECT categoryId AS categoryId, SUM(amountBaseMinor) AS total FROM transactions " +
            "WHERE isDuplicate = 0 AND excludedFromExpense = 0 AND direction = 'DEBIT' AND isSplit = 0 " +
            "AND occurredAt BETWEEN :from AND :to GROUP BY categoryId " +
            "UNION ALL " +
            "SELECT s.categoryId AS categoryId, SUM(s.amountBaseMinor) AS total FROM transaction_splits s " +
            "JOIN transactions t ON t.id = s.parentId " +
            "WHERE t.isDuplicate = 0 AND t.excludedFromExpense = 0 AND t.direction = 'DEBIT' " +
            "AND t.occurredAt BETWEEN :from AND :to GROUP BY s.categoryId" +
            ") GROUP BY categoryId ORDER BY total DESC",
    )
    fun observeCategoryTotals(from: Long, to: Long): Flow<List<CategoryTotal>>

    /** One-shot, split-aware category spend totals for a window — used by the velocity-alert worker. */
    @Query(
        "SELECT categoryId AS categoryId, SUM(total) AS total FROM (" +
            "SELECT categoryId AS categoryId, SUM(amountBaseMinor) AS total FROM transactions " +
            "WHERE isDuplicate = 0 AND excludedFromExpense = 0 AND direction = 'DEBIT' AND isSplit = 0 " +
            "AND occurredAt BETWEEN :from AND :to GROUP BY categoryId " +
            "UNION ALL " +
            "SELECT s.categoryId AS categoryId, SUM(s.amountBaseMinor) AS total FROM transaction_splits s " +
            "JOIN transactions t ON t.id = s.parentId " +
            "WHERE t.isDuplicate = 0 AND t.excludedFromExpense = 0 AND t.direction = 'DEBIT' " +
            "AND t.occurredAt BETWEEN :from AND :to GROUP BY s.categoryId" +
            ") GROUP BY categoryId",
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

    /** Total CREDIT (base minor) into an account at/after [since] — backs savings-goal progress (#12). */
    @Query(
        "SELECT COALESCE(SUM(amountBaseMinor), 0) FROM transactions WHERE isDuplicate = 0 " +
            "AND direction = 'CREDIT' AND accountKey = :accountKey AND occurredAt >= :since",
    )
    suspend fun sumCreditForAccountSince(accountKey: String, since: Long): Long

    @Query("UPDATE transactions SET counterparty = :newName WHERE counterparty = :oldName")
    suspend fun renameCounterparty(oldName: String, newName: String)

    @Query("UPDATE transactions SET categoryId = :categoryId WHERE counterparty = :name")
    suspend fun setCategoryForCounterparty(name: String, categoryId: Long?)

    // ── AI auto-categorisation (uncategorised fallback) ──────────────────────────

    /**
     * Transactions the AI auto-categoriser should consider: no category yet, not already attempted,
     * and not a noise row (duplicate / split parent). Newest first so a partial run still helps the
     * rows the user is most likely looking at.
     */
    @Query(
        "SELECT * FROM transactions WHERE categoryId IS NULL AND aiCategorizeAttempted = 0 " +
            "AND isDuplicate = 0 AND isSplit = 0 ORDER BY occurredAt DESC",
    )
    suspend fun listForAiCategorize(): List<TransactionEntity>

    /** Assign [categoryId] and mark attempted in one write — the AI classified this row. */
    @Query("UPDATE transactions SET categoryId = :categoryId, aiCategorizeAttempted = 1 WHERE id = :id")
    suspend fun setCategoryAndAiAttempted(id: Long, categoryId: Long)

    /** Mark these rows attempted without changing the category — the AI couldn't classify them. */
    @Query("UPDATE transactions SET aiCategorizeAttempted = 1 WHERE id IN (:ids)")
    suspend fun markAiCategorizeAttempted(ids: List<Long>)

    /** Clear the attempted flag on still-uncategorised rows so a user-requested re-run reconsiders them. */
    @Query("UPDATE transactions SET aiCategorizeAttempted = 0 WHERE categoryId IS NULL")
    suspend fun resetAiCategorizeAttempted(): Int

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

    /**
     * Returns one sender address per accountKey (the most frequent one wins via COUNT DESC).
     * Used to extract bank brand from telecom sender codes like "VK-HDFCBK".
     */
    @Query(
        "SELECT t.accountKey AS accountKey, r.sender AS sender " +
            "FROM transactions t INNER JOIN raw_sms r ON r.id = t.rawSmsId " +
            "WHERE t.rawSmsId IS NOT NULL AND t.isDuplicate = 0 " +
            "GROUP BY t.accountKey, r.sender ORDER BY t.accountKey, COUNT(*) DESC",
    )
    suspend fun topSenderPerAccount(): List<AccountSender>

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

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int


    @Query(
        "SELECT COUNT(*) FROM transactions " +
            "WHERE counterparty = :counterparty AND categoryId = :incomeCategoryId " +
            "AND direction = 'CREDIT' AND isDuplicate = 0",
    )
    suspend fun countIncomeTransactions(counterparty: String, incomeCategoryId: Long): Int

    /** Delete all transactions whose source SMS came from [sender] — cleanup for non-financial senders. */
    @Query("DELETE FROM transactions WHERE rawSmsId IN (SELECT id FROM raw_sms WHERE sender = :sender)")
    suspend fun deleteAllForSender(sender: String)

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
interface TransactionSplitDao {
    @Insert
    suspend fun insertAll(splits: List<TransactionSplitEntity>)

    @Query("SELECT * FROM transaction_splits WHERE parentId = :parentId")
    fun observeForParent(parentId: Long): Flow<List<TransactionSplitEntity>>

    @Query("SELECT * FROM transaction_splits")
    fun observeAll(): Flow<List<TransactionSplitEntity>>

    @Query("SELECT * FROM transaction_splits")
    suspend fun all(): List<TransactionSplitEntity>

    @Query("DELETE FROM transaction_splits")
    suspend fun clear()

    @Query("SELECT * FROM transaction_splits WHERE parentId = :parentId")
    suspend fun forParent(parentId: Long): List<TransactionSplitEntity>

    @Query("DELETE FROM transaction_splits WHERE parentId = :parentId")
    suspend fun deleteForParent(parentId: Long)
}

@Dao
interface SavingsGoalDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(goal: SavingsGoalEntity): Long

    @Query("SELECT * FROM savings_goals ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavingsGoalEntity>>

    @Query("SELECT * FROM savings_goals")
    suspend fun all(): List<SavingsGoalEntity>

    @Query("SELECT * FROM savings_goals WHERE linkedAccountKey = :accountKey")
    suspend fun forAccount(accountKey: String): List<SavingsGoalEntity>

    @Query("UPDATE savings_goals SET savedManualMinor = savedManualMinor + :deltaMinor WHERE id = :id")
    suspend fun addManualContribution(id: Long, deltaMinor: Long)

    @Query("UPDATE savings_goals SET notifiedReached = :reached WHERE id = :id")
    suspend fun setNotifiedReached(id: Long, reached: Boolean)

    @Query("DELETE FROM savings_goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM savings_goals")
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

    @Query("DELETE FROM merchant_aliases")
    suspend fun clear()
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

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM category_rules")
    suspend fun clearRules()
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

@Dao
interface PromotionalExclusionDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: PromotionalExclusionEntity): Long

    @Query("SELECT * FROM promotional_exclusions ORDER BY createdAt DESC")
    suspend fun getAll(): List<PromotionalExclusionEntity>

    @Query("DELETE FROM promotional_exclusions")
    suspend fun clear()
}

@Dao
interface SenderClassificationDao {
    /** Insert only if sender not already classified (first classification wins). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entry: SenderClassificationEntity)

    @Query("SELECT * FROM sender_classifications WHERE sender = :sender")
    suspend fun get(sender: String): SenderClassificationEntity?

    /** Distinct senders in raw_sms that have no classification entry yet — AI batch candidates. */
    @Query("SELECT DISTINCT sender FROM raw_sms WHERE sender NOT IN (SELECT sender FROM sender_classifications)")
    suspend fun unclassifiedSenders(): List<String>

    /** Senders the AI labelled non-financial — used to enumerate cleanup targets. */
    @Query("SELECT sender FROM sender_classifications WHERE isFinancial = 0")
    suspend fun nonFinancialSenders(): List<String>

    @Query("DELETE FROM sender_classifications")
    suspend fun clear()
}
