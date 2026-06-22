package com.spendlens.app.data.db

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.sql.Connection
import java.sql.DriverManager

/**
 * Replays [AppDatabase.MIGRATION_7_8_SQL] against an in-memory stock-SQLite database to prove the
 * v7→v8 table rebuild preserves data and makes rawSmsId nullable (PRD AC-6). Runs as a plain JVM
 * unit test — it exercises the migration's SQL semantics, not SQLCipher or Room's runtime check.
 */
class Migration7to8Test {

    private lateinit var conn: Connection

    /** The v7 `transactions` schema: identical to v8 except rawSmsId is NOT NULL. */
    private val v7CreateSql = listOf(
        "CREATE TABLE transactions (" +
            "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
            "rawSmsId INTEGER NOT NULL, " +
            "amountMinor INTEGER NOT NULL, " +
            "currency TEXT NOT NULL, " +
            "amountBaseMinor INTEGER NOT NULL DEFAULT 0, " +
            "direction TEXT NOT NULL, " +
            "accountKey TEXT NOT NULL, " +
            "counterparty TEXT NOT NULL, " +
            "balanceMinor INTEGER, " +
            "referenceId TEXT, " +
            "occurredAt INTEGER NOT NULL, " +
            "channel TEXT NOT NULL, " +
            "categoryId INTEGER, " +
            "dupGroupId TEXT, " +
            "isDuplicate INTEGER NOT NULL DEFAULT 0, " +
            "userVerified INTEGER NOT NULL DEFAULT 0, " +
            "excludedFromExpense INTEGER NOT NULL DEFAULT 0, " +
            "note TEXT, " +
            "tags TEXT, " +
            "receiptUri TEXT)",
        "CREATE INDEX index_transactions_rawSmsId ON transactions(rawSmsId)",
        "CREATE INDEX index_transactions_amountMinor_accountKey_direction " +
            "ON transactions(amountMinor, accountKey, direction)",
        "CREATE INDEX index_transactions_occurredAt ON transactions(occurredAt)",
        "CREATE INDEX index_transactions_dupGroupId ON transactions(dupGroupId)",
    )

    @Before
    fun setUp() {
        conn = DriverManager.getConnection("jdbc:sqlite::memory:")
        conn.createStatement().use { st -> v7CreateSql.forEach(st::executeUpdate) }
        // Two SMS-sourced rows with non-null rawSmsId.
        conn.createStatement().use { st ->
            st.executeUpdate(
                "INSERT INTO transactions " +
                    "(rawSmsId, amountMinor, currency, amountBaseMinor, direction, accountKey, " +
                    "counterparty, occurredAt, channel) VALUES " +
                    "(11, 50000, 'INR', 50000, 'DEBIT', 'HDFC', 'Swiggy', 1000, 'UPI')",
            )
            st.executeUpdate(
                "INSERT INTO transactions " +
                    "(rawSmsId, amountMinor, currency, amountBaseMinor, direction, accountKey, " +
                    "counterparty, occurredAt, channel) VALUES " +
                    "(22, 120000, 'INR', 120000, 'CREDIT', 'ICICI', 'Salary', 2000, 'NEFT')",
            )
        }
    }

    @After
    fun tearDown() {
        conn.close()
    }

    private fun runMigration() {
        conn.createStatement().use { st -> AppDatabase.MIGRATION_7_8_SQL.forEach(st::executeUpdate) }
    }

    @Test
    fun `migration preserves all rows and their values`() {
        runMigration()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM transactions").use { rs ->
                rs.next()
                assertEquals(2, rs.getInt(1))
            }
            st.executeQuery(
                "SELECT rawSmsId, amountMinor, currency, counterparty, channel " +
                    "FROM transactions WHERE id = 1",
            ).use { rs ->
                rs.next()
                assertEquals(11L, rs.getLong("rawSmsId"))
                assertEquals(50000L, rs.getLong("amountMinor"))
                assertEquals("INR", rs.getString("currency"))
                assertEquals("Swiggy", rs.getString("counterparty"))
                assertEquals("UPI", rs.getString("channel"))
            }
        }
    }

    @Test
    fun `SMS-sourced rows keep a non-null rawSmsId after migration`() {
        runMigration()
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM transactions WHERE rawSmsId IS NULL").use { rs ->
                rs.next()
                assertEquals(0, rs.getInt(1))
            }
        }
    }

    @Test
    fun `migration recreates all four indices`() {
        runMigration()
        val expected = setOf(
            "index_transactions_rawSmsId",
            "index_transactions_amountMinor_accountKey_direction",
            "index_transactions_occurredAt",
            "index_transactions_dupGroupId",
        )
        val found = mutableSetOf<String>()
        conn.createStatement().use { st ->
            st.executeQuery(
                "SELECT name FROM sqlite_master WHERE type = 'index' AND tbl_name = 'transactions'",
            ).use { rs ->
                while (rs.next()) found += rs.getString("name")
            }
        }
        assertTrue("missing indices: ${expected - found}", found.containsAll(expected))
    }

    @Test
    fun `manual row with null rawSmsId can be inserted after migration`() {
        runMigration()
        conn.createStatement().use { st ->
            st.executeUpdate(
                "INSERT INTO transactions " +
                    "(rawSmsId, amountMinor, currency, amountBaseMinor, direction, accountKey, " +
                    "counterparty, occurredAt, channel) VALUES " +
                    "(NULL, 30000, 'INR', 30000, 'DEBIT', 'Cash', 'Groceries', 3000, 'MANUAL')",
            )
            st.executeQuery(
                "SELECT rawSmsId, channel FROM transactions WHERE counterparty = 'Groceries'",
            ).use { rs ->
                rs.next()
                rs.getLong("rawSmsId")
                assertTrue("rawSmsId should be NULL for the manual row", rs.wasNull())
                assertEquals("MANUAL", rs.getString("channel"))
            }
        }
        // And the prior SMS rows are untouched.
        conn.createStatement().use { st ->
            st.executeQuery("SELECT COUNT(*) FROM transactions").use { rs ->
                rs.next()
                assertEquals(3, rs.getInt(1))
            }
        }
    }
}
