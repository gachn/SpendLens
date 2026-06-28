package com.spendlens.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.spendlens.app.data.crypto.DatabaseKeyManager
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * The encrypted on-device database. Opened through SQLCipher with a Keystore-backed
 * passphrase (see [DatabaseKeyManager]); data is AES-256 encrypted at rest.
 */
@Database(
    entities = [
        RawSmsEntity::class,
        TransactionEntity::class,
        SmsPatternEntity::class,
        CategoryEntity::class,
        CategoryRuleEntity::class,
        MerchantAliasEntity::class,
        BudgetEntity::class,
        BillEntity::class,
        CardBillEntity::class,
        TransactionSplitEntity::class,
        SavingsGoalEntity::class,
        SenderClassificationEntity::class,
        PromotionalExclusionEntity::class,
        BalanceSnapshotEntity::class,
    ],
    version = 18,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawSmsDao(): RawSmsDao
    abstract fun transactionDao(): TransactionDao
    abstract fun patternDao(): PatternDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun balanceSnapshotDao(): BalanceSnapshotDao
    abstract fun budgetDao(): BudgetDao
    abstract fun billDao(): BillDao
    abstract fun cardBillDao(): CardBillDao
    abstract fun transactionSplitDao(): TransactionSplitDao
    abstract fun savingsGoalDao(): SavingsGoalDao
    abstract fun senderClassificationDao(): SenderClassificationDao
    abstract fun promotionalExclusionDao(): PromotionalExclusionDao

    companion object {
        private const val DB_NAME = "spendlens.db"

        /** v1 → v2: add the excludedFromExpense flag (self-transfers, salary, user-excluded). */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN excludedFromExpense INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** v2 → v3: add the merchant-alias cache table. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS merchant_aliases (" +
                        "rawKey TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, source TEXT NOT NULL)",
                )
            }
        }

        /** v3 → v4: add amountBaseMinor; backfill existing rows assuming they were INR. */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN amountBaseMinor INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE transactions SET amountBaseMinor = amountMinor")
            }
        }

        /**
         * v4 → v5: transaction note/tags/receipt columns, plus the budgets and recurring-bills
         * tables. All additive — existing rows keep their data.
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN note TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN tags TEXT")
                db.execSQL("ALTER TABLE transactions ADD COLUMN receiptUri TEXT")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS budgets (" +
                        "categoryId INTEGER NOT NULL PRIMARY KEY, monthlyLimitMinor INTEGER NOT NULL)",
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS bills (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, counterparty TEXT NOT NULL, " +
                        "typicalAmountMinor INTEGER NOT NULL, dayOfMonth INTEGER NOT NULL, " +
                        "categoryId INTEGER, lastPaidAt INTEGER NOT NULL, reminderEnabled INTEGER NOT NULL)",
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bills_counterparty ON bills(counterparty)")
            }
        }

        /** v5 → v6: add the card_bills table (latest credit-card statement per card). Additive. */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS card_bills (" +
                        "cardKey TEXT NOT NULL PRIMARY KEY, totalDueMinor INTEGER NOT NULL, " +
                        "minDueMinor INTEGER, currency TEXT NOT NULL, dueDate INTEGER, " +
                        "statementAt INTEGER NOT NULL, rawSmsId INTEGER NOT NULL, updatedAt INTEGER NOT NULL)",
                )
            }
        }

        /** v6 → v7: remember user tags per merchant so future parsed transactions inherit them. */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE merchant_aliases ADD COLUMN tags TEXT")
            }
        }

        /**
         * v7 → v8: make transactions.rawSmsId nullable so manually-entered rows can have no
         * backing SMS. SQLite cannot drop a NOT NULL constraint via ALTER, so this is an explicit
         * table rebuild: create the new table with rawSmsId nullable, copy every row, drop the old
         * table, rename, and recreate the four indices. The statements live in [MIGRATION_7_8_SQL]
         * so the migration test can replay the identical SQL.
         */
        internal val MIGRATION_7_8_SQL: List<String> = listOf(
            "CREATE TABLE transactions_new (" +
                "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                "rawSmsId INTEGER, " +
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
            "INSERT INTO transactions_new (" +
                "id, rawSmsId, amountMinor, currency, amountBaseMinor, direction, accountKey, " +
                "counterparty, balanceMinor, referenceId, occurredAt, channel, categoryId, " +
                "dupGroupId, isDuplicate, userVerified, excludedFromExpense, note, tags, receiptUri) " +
                "SELECT id, rawSmsId, amountMinor, currency, amountBaseMinor, direction, accountKey, " +
                "counterparty, balanceMinor, referenceId, occurredAt, channel, categoryId, " +
                "dupGroupId, isDuplicate, userVerified, excludedFromExpense, note, tags, receiptUri " +
                "FROM transactions",
            "DROP TABLE transactions",
            "ALTER TABLE transactions_new RENAME TO transactions",
            "CREATE INDEX index_transactions_rawSmsId ON transactions(rawSmsId)",
            "CREATE INDEX index_transactions_amountMinor_accountKey_direction " +
                "ON transactions(amountMinor, accountKey, direction)",
            "CREATE INDEX index_transactions_occurredAt ON transactions(occurredAt)",
            "CREATE INDEX index_transactions_dupGroupId ON transactions(dupGroupId)",
        )

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_7_8_SQL.forEach(db::execSQL)
            }
        }

        /** v8 → v9: add logoEmoji column to merchant_aliases cache table. */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE merchant_aliases ADD COLUMN logoEmoji TEXT")
            }
        }

        /** v9 → v10: remember a per-merchant expense-exclusion flag, applied to future transactions. */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE merchant_aliases ADD COLUMN excludedFromExpense INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /** v10 → v11: per-budget rollover toggle — carry unspent limit into next month. Additive. */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE budgets ADD COLUMN rolloverEnabled INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * v11 → v12: split a transaction across categories (issue #11). Adds the isSplit flag and
         * the transaction_splits child table. Additive — existing rows keep their data.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN isSplit INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS transaction_splits (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, parentId INTEGER NOT NULL, " +
                        "categoryId INTEGER, amountMinor INTEGER NOT NULL, amountBaseMinor INTEGER NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transaction_splits_parentId ON transaction_splits(parentId)")
            }
        }

        /** v12 → v13: add the savings_goals table (issue #12). Additive. */
        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS savings_goals (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                        "targetAmountMinor INTEGER NOT NULL, deadline INTEGER, linkedAccountKey TEXT, " +
                        "createdAt INTEGER NOT NULL, savedManualMinor INTEGER NOT NULL DEFAULT 0, " +
                        "notifiedReached INTEGER NOT NULL DEFAULT 0)",
                )
            }
        }

        /**
         * v13 → v14: add the aiCategorizeAttempted flag — guards the AI auto-categoriser so an
         * uncategorised transaction the AI couldn't classify is not re-sent on every sync. Additive.
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN aiCategorizeAttempted INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * v14 → v15: add the sender_classifications cache so SMS senders are classified as
         * financial or non-financial once (via static filter or AI) and the result is reused on
         * every subsequent SMS from that sender, avoiding repeat AI calls.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sender_classifications (" +
                        "sender TEXT NOT NULL PRIMARY KEY, " +
                        "isFinancial INTEGER NOT NULL, " +
                        "source TEXT NOT NULL, " +
                        "classifiedAt INTEGER NOT NULL)",
                )
            }
        }

        /**
         * v15 → v16: add the promotional_exclusions table. Body-regex patterns saved when the AI
         * confirms a message is promotional (e.g. a loan offer). Future messages matching these
         * patterns are discarded without a network call.
         */
        /**
         * v16 → v17: add promoChecked flag to raw_sms. Set to 0 (false) for all existing rows
         * so PromotionalCheckWorker re-evaluates the backlog once on upgrade.
         */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE raw_sms ADD COLUMN promoChecked INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

        /**
         * v17 → v18: add statement-cycle, payment tracking to card_bills; add balance_snapshots
         * table for standalone balance-notification SMS.
         */
        private val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE card_bills ADD COLUMN statementCycleDay INTEGER")
                db.execSQL("ALTER TABLE card_bills ADD COLUMN paidAt INTEGER")
                db.execSQL("ALTER TABLE card_bills ADD COLUMN paidAmountMinor INTEGER")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS balance_snapshots (" +
                        "accountKey TEXT NOT NULL PRIMARY KEY, " +
                        "balanceMinor INTEGER NOT NULL, " +
                        "isCard INTEGER NOT NULL DEFAULT 0, " +
                        "currency TEXT NOT NULL, " +
                        "observedAt INTEGER NOT NULL)",
                )
            }
        }

        private val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS promotional_exclusions (" +
                        "id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT, " +
                        "bodyRegex TEXT NOT NULL, " +
                        "sampleSms TEXT, " +
                        "createdAt INTEGER NOT NULL)",
                )
            }
        }

        fun create(context: Context, keyManager: DatabaseKeyManager): AppDatabase {
            // Load SQLCipher native libs. The SupportOpenHelperFactory also triggers this;
            // guarded so a double-load (or already-linked lib) can't crash startup.
            runCatching { System.loadLibrary("sqlcipher") }
            val factory = SupportOpenHelperFactory(keyManager.getOrCreatePassphrase())
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(
                    MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7,
                    MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12,
                    MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17,
                    MIGRATION_17_18,
                )
                .fallbackToDestructiveMigration() // safety net for older dev builds
                .build()
        }
    }
}
