package com.spendlens.app.data.backup

import android.content.Context
import android.provider.Settings
import com.spendlens.app.BuildConfig
import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.Timestamp

/**
 * Backs up parsed transactions to the Neon PostgreSQL database for AI analysis.
 * Uses a device-scoped primary key (device_id + local_id) so multiple devices
 * can share one table without row collisions.
 *
 * Credentials are read from BuildConfig (sourced from local.properties — gitignored).
 */
class NeonBackupRepository(private val context: Context) {

    data class BackupResult(val rowsUpserted: Int, val backedUpAt: Long)

    suspend fun backup(
        transactions: List<TransactionEntity>,
        categories: Map<Long, CategoryEntity>,
    ): BackupResult = withContext(Dispatchers.IO) {
        // On Android, DriverManager.getConnection() fails to find the PostgreSQL driver even
        // after Class.forName() because Android's classloader model doesn't register drivers
        // the same way as the JVM. Instantiate the driver directly to bypass DriverManager.
        val driver = org.postgresql.Driver()
        val props = java.util.Properties().apply {
            setProperty("connectTimeout", "10")
            setProperty("socketTimeout", "30")
            setProperty("loginTimeout", "10")
            // PGPropertyMaxResultBufferParser.parseProperty(null) calls adjustResultSize(0)
            // which invokes java.lang.management.ManagementFactory — absent on Android.
            // Supplying an explicit byte count short-circuits that path entirely.
            setProperty("maxResultBuffer", "67108864") // 64 MB
        }
        val conn = requireNotNull(driver.connect(BuildConfig.NEON_JDBC_URL, props)) {
            "PostgreSQL driver rejected NEON_JDBC_URL — check local.properties"
        }
        conn.autoCommit = false
        conn.use {
            ensureSchema(conn)
            val rows = upsertTransactions(conn, transactions, categories)
            conn.commit()
            BackupResult(rows, System.currentTimeMillis())
        }
    }

    private fun ensureSchema(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS spendlens_transactions (
                    device_id          TEXT        NOT NULL,
                    local_id           BIGINT      NOT NULL,
                    amount_minor       BIGINT,
                    amount_base_minor  BIGINT,
                    currency           TEXT,
                    direction          TEXT,
                    counterparty       TEXT,
                    category_name      TEXT,
                    occurred_at        TIMESTAMPTZ,
                    channel            TEXT,
                    account_key        TEXT,
                    is_excluded        BOOLEAN     DEFAULT FALSE,
                    note               TEXT,
                    tags               TEXT,
                    backed_up_at       TIMESTAMPTZ DEFAULT NOW(),
                    PRIMARY KEY (device_id, local_id)
                )
                """.trimIndent(),
            )
        }
    }

    private fun upsertTransactions(
        conn: Connection,
        transactions: List<TransactionEntity>,
        categories: Map<Long, CategoryEntity>,
    ): Int {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        val sql = """
            INSERT INTO spendlens_transactions
              (device_id, local_id, amount_minor, amount_base_minor, currency, direction,
               counterparty, category_name, occurred_at, channel, account_key,
               is_excluded, note, tags, backed_up_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,NOW())
            ON CONFLICT (device_id, local_id) DO UPDATE SET
              amount_minor      = EXCLUDED.amount_minor,
              amount_base_minor = EXCLUDED.amount_base_minor,
              counterparty      = EXCLUDED.counterparty,
              category_name     = EXCLUDED.category_name,
              is_excluded       = EXCLUDED.is_excluded,
              note              = EXCLUDED.note,
              tags              = EXCLUDED.tags,
              backed_up_at      = NOW()
        """.trimIndent()

        conn.prepareStatement(sql).use { stmt ->
            transactions.filter { !it.isDuplicate }.forEach { txn ->
                stmt.setString(1, deviceId)
                stmt.setLong(2, txn.id)
                stmt.setLong(3, txn.amountMinor)
                stmt.setLong(4, txn.amountBaseMinor)
                stmt.setString(5, txn.currency)
                stmt.setString(6, txn.direction)
                stmt.setString(7, txn.counterparty)
                stmt.setString(8, categories[txn.categoryId]?.name)
                stmt.setTimestamp(9, Timestamp(txn.occurredAt))
                stmt.setString(10, txn.channel)
                stmt.setString(11, txn.accountKey)
                stmt.setBoolean(12, txn.excludedFromExpense)
                stmt.setString(13, txn.note)
                stmt.setString(14, txn.tags)
                stmt.addBatch()
            }
            return stmt.executeBatch().size
        }
    }

    companion object {
        /** True if a Neon URL was configured at build time. */
        val isConfigured: Boolean get() = BuildConfig.NEON_JDBC_URL.isNotBlank()
    }
}
