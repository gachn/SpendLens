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
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun rawSmsDao(): RawSmsDao
    abstract fun transactionDao(): TransactionDao
    abstract fun patternDao(): PatternDao
    abstract fun categoryDao(): CategoryDao
    abstract fun merchantDao(): MerchantDao
    abstract fun budgetDao(): BudgetDao
    abstract fun billDao(): BillDao
    abstract fun cardBillDao(): CardBillDao

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

        fun create(context: Context, keyManager: DatabaseKeyManager): AppDatabase {
            // Load SQLCipher native libs. The SupportOpenHelperFactory also triggers this;
            // guarded so a double-load (or already-linked lib) can't crash startup.
            runCatching { System.loadLibrary("sqlcipher") }
            val factory = SupportOpenHelperFactory(keyManager.getOrCreatePassphrase())
            return Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DB_NAME)
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration() // safety net for older dev builds
                .build()
        }
    }
}
