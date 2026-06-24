package com.spendlens.app.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entities for the encrypted database. Money is stored as integer minor
 * units (e.g. paise/cents) — never floating point. See docs/DESIGN.md §2.
 */

/** Raw inbound SMS. [contentHash] is unique → idempotent import + exact-dup suppression. */
@Entity(
    tableName = "raw_sms",
    indices = [Index(value = ["contentHash"], unique = true), Index("status")],
)
data class RawSmsEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val contentHash: String,
    val status: String,        // RawStatus
    val patternId: Long? = null,
)

object RawStatus {
    const val PENDING = "PENDING"
    const val PARSED = "PARSED"
    const val UNPARSED = "UNPARSED"   // financial but no pattern matched → Review queue
    const val IGNORED = "IGNORED"     // not a financial SMS
}

/** Discriminator stored in [TransactionEntity.channel] for transactions the user entered by hand. */
object TransactionChannel {
    const val MANUAL = "MANUAL"
}

/** A parsed financial transaction. */
@Entity(
    tableName = "transactions",
    indices = [
        Index("rawSmsId"),
        Index(value = ["amountMinor", "accountKey", "direction"]),
        Index("occurredAt"),
        Index("dupGroupId"),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Source SMS row, or null for manually-entered transactions ([TransactionChannel.MANUAL]). */
    val rawSmsId: Long?,
    val amountMinor: Long,
    val currency: String,
    /** Amount converted to the base currency (INR) minor units at ingest time. Totals use this. */
    val amountBaseMinor: Long = 0,
    val direction: String,         // DEBIT | CREDIT
    val accountKey: String,
    val counterparty: String,
    val balanceMinor: Long? = null,
    val referenceId: String? = null,
    val occurredAt: Long,
    val channel: String,
    val categoryId: Long? = null,
    val dupGroupId: String? = null,
    val isDuplicate: Boolean = false,
    val userVerified: Boolean = false,
    /** True for self-transfers, salary credits and user-excluded rows — kept out of spend/income totals. */
    val excludedFromExpense: Boolean = false,
    /** Free-text note the user attached to this transaction. */
    val note: String? = null,
    /** User tags, comma-joined (e.g. "work,reimbursable"). Stored lowercase, trimmed. */
    val tags: String? = null,
    /** Local file path (app-private storage) of an attached receipt image, if any. */
    val receiptUri: String? = null,
    /**
     * True when this transaction has been split across multiple categories. The split children
     * live in [TransactionSplitEntity]; a split parent is excluded from category totals (its
     * children are counted instead), but still appears in lists and account/spend totals.
     */
    val isSplit: Boolean = false,
)

/**
 * One slice of a transaction that the user split across categories (issue #11). The slices of a
 * parent always sum to the parent's amount. [amountMinor] is in the parent's display currency;
 * [amountBaseMinor] is the base-currency (INR) equivalent and is what category totals aggregate.
 */
@Entity(tableName = "transaction_splits", indices = [Index("parentId")])
data class TransactionSplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long,
    val categoryId: Long? = null,
    val amountMinor: Long,
    val amountBaseMinor: Long,
)

/**
 * Latest credit-card statement/bill parsed from an SMS. One row per card ([cardKey] matches
 * a transaction's [TransactionEntity.accountKey], e.g. "••••1234"), upserted to the newest bill.
 */
@Entity(tableName = "card_bills")
data class CardBillEntity(
    @PrimaryKey val cardKey: String,
    val totalDueMinor: Long,
    val minDueMinor: Long? = null,
    val currency: String = "INR",
    /** Payment due date (epoch millis, start of day), if the SMS stated one. */
    val dueDate: Long? = null,
    /** When the statement SMS was received — used to keep only the most recent bill. */
    val statementAt: Long,
    val rawSmsId: Long,
    val updatedAt: Long,
)

/** A learnable SMS pattern (built-in seed, AI/heuristic-generated, or user-defined). */
@Entity(tableName = "sms_patterns", indices = [Index("priority"), Index("enabled")])
data class SmsPatternEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val senderRegex: String? = null,
    val bodyRegex: String,
    val priority: Int,
    val source: String,            // PatternSource
    val enabled: Boolean = true,
    val matchCount: Int = 0,
    val lastMatchedAt: Long? = null,
    val sampleSms: String? = null,
)

object PatternSource {
    const val BUILTIN = "BUILTIN"
    const val AI = "AI"
    const val HEURISTIC = "HEURISTIC"
    const val USER = "USER"
}

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String,   // emoji glyph for a zero-asset, recognisable icon
    val color: Long,    // ARGB
)

@Entity(tableName = "category_rules", indices = [Index(value = ["matcher"], unique = true)])
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matcher: String,   // lowercase keyword matched against counterparty
    val categoryId: Long,
    val source: String,    // BUILTIN | USER
)

/** Cached merchant-name resolution: canonical key → display name. The "metadata" the parser reuses. */
@Entity(tableName = "merchant_aliases")
data class MerchantAliasEntity(
    @PrimaryKey val rawKey: String,
    val displayName: String,
    val source: String,    // DICTIONARY | WEB | NORMALIZED | USER
    /** User tags remembered for this merchant; applied to future parsed transactions. Comma-joined. */
    val tags: String? = null,
    val logoEmoji: String? = null,
    /** User excluded this merchant from spend/income totals; applied to future parsed transactions. */
    val excludedFromExpense: Boolean = false,
)

/** A user-set monthly spending limit for one category. One row per category (categoryId = PK). */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val categoryId: Long,
    val monthlyLimitMinor: Long,
    /** When set, unspent budget from the previous month is carried into this month's effective limit. */
    val rolloverEnabled: Boolean = false,
)

/**
 * A recurring bill/subscription inferred from transactions (e.g. rent, electricity, Netflix).
 * Reminders only — SpendLens never moves money. [dayOfMonth] is the predicted due day (1–28).
 */
@Entity(tableName = "bills", indices = [Index(value = ["counterparty"], unique = true)])
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val counterparty: String,
    val typicalAmountMinor: Long,
    val dayOfMonth: Int,
    val categoryId: Long? = null,
    val lastPaidAt: Long,
    val reminderEnabled: Boolean = true,
)
