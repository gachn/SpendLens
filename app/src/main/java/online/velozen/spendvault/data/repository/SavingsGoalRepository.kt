package com.spendlens.app.data.repository

import android.content.Context
import com.spendlens.app.data.db.SavingsGoalDao
import com.spendlens.app.data.db.SavingsGoalEntity
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.sms.GoalNotifier
import kotlinx.coroutines.flow.Flow

/**
 * Savings goals (issue #12). Progress = [SavingsGoalEntity.savedManualMinor] plus, for a goal with a
 * [linkedAccountKey], every CREDIT into that account since the goal was created. SpendLens never
 * moves money — goals only observe transactions already recorded.
 */
class SavingsGoalRepository(
    private val dao: SavingsGoalDao,
    private val txnRepo: TransactionRepository,
) {
    fun observeAll(): Flow<List<SavingsGoalEntity>> = dao.observeAll()

    suspend fun createGoal(
        name: String,
        targetAmountMinor: Long,
        deadline: Long?,
        linkedAccountKey: String?,
    ): Long = dao.upsert(
        SavingsGoalEntity(
            name = name,
            targetAmountMinor = targetAmountMinor,
            deadline = deadline,
            linkedAccountKey = linkedAccountKey,
            createdAt = System.currentTimeMillis(),
        ),
    )

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun addContribution(id: Long, deltaMinor: Long) = dao.addManualContribution(id, deltaMinor)

    /** Auto-tracked savings for [goal]: manual contributions plus linked-account CREDITs since creation. */
    suspend fun savedMinor(goal: SavingsGoalEntity): Long {
        val auto = goal.linkedAccountKey
            ?.let { txnRepo.sumCreditForAccountSince(it, goal.createdAt) }
            ?: 0L
        return goal.savedManualMinor + auto
    }

    /**
     * Called after a CREDIT is committed (e.g. from [SmsSyncWorker]): for each goal linked to the
     * transaction's account that has now reached its target, fire the one-shot "goal reached"
     * notification and flip [notifiedReached] so it never repeats.
     */
    suspend fun onCreditCommitted(context: Context, txn: TransactionEntity) {
        if (txn.direction != "CREDIT") return
        val account = txn.accountKey
        if (account.isBlank()) return
        dao.forAccount(account).forEach { goal ->
            if (goal.notifiedReached) return@forEach
            if (savedMinor(goal) >= goal.targetAmountMinor) {
                GoalNotifier.notify(context, goal)
                dao.setNotifiedReached(goal.id, true)
            }
        }
    }
}
