package com.spendlens.app.data.backup

import androidx.room.withTransaction
import com.spendlens.app.data.db.AppDatabase
import com.spendlens.app.data.db.BillEntity
import com.spendlens.app.data.db.BudgetEntity
import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.CategoryRuleEntity
import com.spendlens.app.data.db.MerchantAliasEntity
import com.spendlens.app.data.db.SavingsGoalEntity
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.data.db.TransactionSplitEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Encrypted, fully-offline local backup/restore (issue #13). The user's financial data is serialised
 * to JSON and sealed with a password-derived AES-256-GCM key via [BackupCrypto]. No cloud, no
 * network — the encrypted blob is written wherever the user chooses via the Storage Access Framework.
 */
class BackupManager(private val db: AppDatabase) {

    /** Re-exported so callers can catch a wrong-password failure without depending on [BackupCrypto]. */
    class BadPasswordException : Exception("Wrong password or corrupt backup file")

    /** Serialise every backed-up table, then encrypt under [password]. */
    suspend fun export(password: CharArray): ByteArray = withContext(Dispatchers.IO) {
        val json = serialize().toString().toByteArray(Charsets.UTF_8)
        BackupCrypto.encrypt(json, password)
    }

    /** Decrypt [blob] with [password] and replace the current data with its contents. */
    suspend fun import(blob: ByteArray, password: CharArray) = withContext(Dispatchers.IO) {
        val plaintext = try {
            BackupCrypto.decrypt(blob, password)
        } catch (e: BackupCrypto.BadPasswordException) {
            throw BadPasswordException()
        }
        restore(JSONObject(String(plaintext, Charsets.UTF_8)))
    }

    // ── Serialisation ───────────────────────────────────────────────────────

    private suspend fun serialize(): JSONObject {
        val tables = JSONObject()
        tables.put("categories", JSONArray().apply { db.categoryDao().allCategories().forEach { put(it.toJson()) } })
        tables.put("category_rules", JSONArray().apply { db.categoryDao().allRules().forEach { put(it.toJson()) } })
        tables.put("merchant_aliases", JSONArray().apply { db.merchantDao().getAll().forEach { put(it.toJson()) } })
        tables.put("transactions", JSONArray().apply { db.transactionDao().getAllTransactions().forEach { put(it.toJson()) } })
        tables.put("transaction_splits", JSONArray().apply { db.transactionSplitDao().all().forEach { put(it.toJson()) } })
        tables.put("budgets", JSONArray().apply { db.budgetDao().all().forEach { put(it.toJson()) } })
        tables.put("bills", JSONArray().apply { db.billDao().all().forEach { put(it.toJson()) } })
        tables.put("savings_goals", JSONArray().apply { db.savingsGoalDao().all().forEach { put(it.toJson()) } })
        return JSONObject().apply {
            put("schema", SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("tables", tables)
        }
    }

    private suspend fun restore(root: JSONObject) = db.withTransaction {
        val tables = root.getJSONObject("tables")
        // Wipe then reinsert with original ids so categoryId / parentId references stay valid.
        db.transactionSplitDao().clear()
        db.transactionDao().clear()
        db.budgetDao().clear()
        db.billDao().clear()
        db.savingsGoalDao().clear()
        db.merchantDao().clear()
        db.categoryDao().clearRules()
        db.categoryDao().clearCategories()

        tables.optJSONArray("categories")?.forEachObj { db.categoryDao().insertCategories(listOf(it.toCategory())) }
        tables.optJSONArray("category_rules")?.forEachObj { db.categoryDao().insertRule(it.toCategoryRule()) }
        tables.optJSONArray("merchant_aliases")?.forEachObj { db.merchantDao().upsert(it.toMerchant()) }
        tables.optJSONArray("transactions")?.forEachObj { db.transactionDao().insert(it.toTransaction()) }
        tables.optJSONArray("transaction_splits")?.forEachObj { db.transactionSplitDao().insertAll(listOf(it.toSplit())) }
        tables.optJSONArray("budgets")?.forEachObj { db.budgetDao().upsert(it.toBudget()) }
        tables.optJSONArray("bills")?.forEachObj { db.billDao().upsert(it.toBill()) }
        tables.optJSONArray("savings_goals")?.forEachObj { db.savingsGoalDao().upsert(it.toGoal()) }
    }

    private companion object {
        const val SCHEMA_VERSION = 1
    }
}

// ── JSON mapping helpers (kept verbose & explicit so a schema change is obvious) ──

private inline fun JSONArray.forEachObj(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) block(getJSONObject(i))
}

private fun JSONObject.optLongOrNull(key: String): Long? = if (isNull(key)) null else getLong(key)
private fun JSONObject.optStringOrNull(key: String): String? = if (isNull(key)) null else getString(key)

private fun CategoryEntity.toJson() = JSONObject().put("id", id).put("name", name).put("icon", icon).put("color", color)
private fun JSONObject.toCategory() = CategoryEntity(getLong("id"), getString("name"), getString("icon"), getLong("color"))

private fun CategoryRuleEntity.toJson() =
    JSONObject().put("id", id).put("matcher", matcher).put("categoryId", categoryId).put("source", source)
private fun JSONObject.toCategoryRule() =
    CategoryRuleEntity(getLong("id"), getString("matcher"), getLong("categoryId"), getString("source"))

private fun MerchantAliasEntity.toJson() = JSONObject()
    .put("rawKey", rawKey).put("displayName", displayName).put("source", source)
    .put("tags", tags).put("logoEmoji", logoEmoji).put("excludedFromExpense", excludedFromExpense)
private fun JSONObject.toMerchant() = MerchantAliasEntity(
    rawKey = getString("rawKey"), displayName = getString("displayName"), source = getString("source"),
    tags = optStringOrNull("tags"), logoEmoji = optStringOrNull("logoEmoji"),
    excludedFromExpense = optBoolean("excludedFromExpense", false),
)

private fun TransactionEntity.toJson() = JSONObject()
    .put("id", id).put("rawSmsId", rawSmsId).put("amountMinor", amountMinor).put("currency", currency)
    .put("amountBaseMinor", amountBaseMinor).put("direction", direction).put("accountKey", accountKey)
    .put("counterparty", counterparty).put("balanceMinor", balanceMinor).put("referenceId", referenceId)
    .put("occurredAt", occurredAt).put("channel", channel).put("categoryId", categoryId)
    .put("dupGroupId", dupGroupId).put("isDuplicate", isDuplicate).put("userVerified", userVerified)
    .put("excludedFromExpense", excludedFromExpense).put("note", note).put("tags", tags)
    .put("receiptUri", receiptUri).put("isSplit", isSplit)
private fun JSONObject.toTransaction() = TransactionEntity(
    id = getLong("id"), rawSmsId = optLongOrNull("rawSmsId"), amountMinor = getLong("amountMinor"),
    currency = getString("currency"), amountBaseMinor = getLong("amountBaseMinor"), direction = getString("direction"),
    accountKey = getString("accountKey"), counterparty = getString("counterparty"),
    balanceMinor = optLongOrNull("balanceMinor"), referenceId = optStringOrNull("referenceId"),
    occurredAt = getLong("occurredAt"), channel = getString("channel"), categoryId = optLongOrNull("categoryId"),
    dupGroupId = optStringOrNull("dupGroupId"), isDuplicate = optBoolean("isDuplicate", false),
    userVerified = optBoolean("userVerified", false), excludedFromExpense = optBoolean("excludedFromExpense", false),
    note = optStringOrNull("note"), tags = optStringOrNull("tags"), receiptUri = optStringOrNull("receiptUri"),
    isSplit = optBoolean("isSplit", false),
)

private fun TransactionSplitEntity.toJson() = JSONObject()
    .put("id", id).put("parentId", parentId).put("categoryId", categoryId)
    .put("amountMinor", amountMinor).put("amountBaseMinor", amountBaseMinor)
private fun JSONObject.toSplit() = TransactionSplitEntity(
    id = getLong("id"), parentId = getLong("parentId"), categoryId = optLongOrNull("categoryId"),
    amountMinor = getLong("amountMinor"), amountBaseMinor = getLong("amountBaseMinor"),
)

private fun BudgetEntity.toJson() =
    JSONObject().put("categoryId", categoryId).put("monthlyLimitMinor", monthlyLimitMinor).put("rolloverEnabled", rolloverEnabled)
private fun JSONObject.toBudget() =
    BudgetEntity(getLong("categoryId"), getLong("monthlyLimitMinor"), optBoolean("rolloverEnabled", false))

private fun BillEntity.toJson() = JSONObject()
    .put("id", id).put("counterparty", counterparty).put("typicalAmountMinor", typicalAmountMinor)
    .put("dayOfMonth", dayOfMonth).put("categoryId", categoryId).put("lastPaidAt", lastPaidAt)
    .put("reminderEnabled", reminderEnabled)
private fun JSONObject.toBill() = BillEntity(
    id = getLong("id"), counterparty = getString("counterparty"), typicalAmountMinor = getLong("typicalAmountMinor"),
    dayOfMonth = getInt("dayOfMonth"), categoryId = optLongOrNull("categoryId"), lastPaidAt = getLong("lastPaidAt"),
    reminderEnabled = optBoolean("reminderEnabled", true),
)

private fun SavingsGoalEntity.toJson() = JSONObject()
    .put("id", id).put("name", name).put("targetAmountMinor", targetAmountMinor).put("deadline", deadline)
    .put("linkedAccountKey", linkedAccountKey).put("createdAt", createdAt)
    .put("savedManualMinor", savedManualMinor).put("notifiedReached", notifiedReached)
private fun JSONObject.toGoal() = SavingsGoalEntity(
    id = getLong("id"), name = getString("name"), targetAmountMinor = getLong("targetAmountMinor"),
    deadline = optLongOrNull("deadline"), linkedAccountKey = optStringOrNull("linkedAccountKey"),
    createdAt = getLong("createdAt"), savedManualMinor = getLong("savedManualMinor"),
    notifiedReached = optBoolean("notifiedReached", false),
)
