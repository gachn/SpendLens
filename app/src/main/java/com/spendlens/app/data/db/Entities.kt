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
    /** True once PromotionalCheckWorker has evaluated this row so it is never re-sent to the AI. */
    val promoChecked: Boolean = false,
    /** Exact prompt sent / response received for the AI call that classified/parsed this SMS, if any. */
    val aiPrompt: String? = null,
    val aiResponse: String? = null,
)

object RawStatus {
    const val PENDING = "PENDING"
    const val PARSED = "PARSED"
    const val UNPARSED = "UNPARSED"   // financial but no pattern matched → Review queue
    const val IGNORED = "IGNORED"     // not a financial SMS
    const val PENDING_AI = "PENDING_AI" // Premium: queued for the debounced batch AI call
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
    /**
     * True once this transaction has been run through the AI auto-categoriser (see
     * [com.spendlens.app.ai.AiCategorizer]). Set whether or not the AI managed to assign a
     * category, so an SMS the AI couldn't classify is not sent off-device again on every sync.
     * Cleared in bulk only when the user explicitly asks for a re-run.
     */
    val aiCategorizeAttempted: Boolean = false,
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
    /** Day of month (1-31) on which this card's statement is generated, derived from [statementAt]. */
    val statementCycleDay: Int? = null,
    /** Epoch millis when a payment was auto-detected from an SMS, null if none detected yet. */
    val paidAt: Long? = null,
    /** Amount paid in minor units. */
    val paidAmountMinor: Long? = null,
)

/**
 * Latest balance snapshot per account/card from a standalone balance-notification SMS
 * (no transaction amount). Upserted so only the most recent observation is kept.
 * Backed by [BalanceUpdateParser].
 */
@Entity(tableName = "balance_snapshots")
data class BalanceSnapshotEntity(
    @PrimaryKey val accountKey: String,
    val balanceMinor: Long,
    val isCard: Boolean = false,
    val currency: String = "INR",
    val observedAt: Long,
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
    /**
     * Epoch millis when this merchant was last considered by [com.spendlens.app.work.MerchantConsolidationWorker],
     * or null if it has never been checked. Null rows are the "new merchants" the periodic Premium
     * consolidation pass sends to the AI; every row is stamped once a pass completes, so unchanged
     * merchants are never re-sent.
     */
    val consolidationCheckedAt: Long? = null,
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
 * Cached result of AI or static-filter sender classification.
 * Keyed by raw sender address (e.g. "VK-HDFCBK"). Both financial and non-financial senders are
 * stored so we never repeat an AI call for a known sender.
 */
@Entity(tableName = "sender_classifications")
data class SenderClassificationEntity(
    @PrimaryKey val sender: String,
    /** True = bank / payment / NBFC that sends transaction alerts; false = marketing / govt / info. */
    val isFinancial: Boolean,
    val source: String,        // SenderSource.STATIC | SenderSource.AI
    val classifiedAt: Long,
)

object SenderSource {
    const val STATIC = "STATIC"   // matched the built-in FinancialSenderFilter list
    const val AI = "AI"           // classified by the language model
}

/**
 * A body-regex exclusion pattern learned when the AI confirms a message is promotional
 * (e.g. a loan offer) rather than a real transaction. Future messages matching this regex
 * are ignored without any AI call.
 */
@Entity(tableName = "promotional_exclusions")
data class PromotionalExclusionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bodyRegex: String,
    val sampleSms: String? = null,
    val createdAt: Long,
)

/**
 * A user savings goal (issue #12) — e.g. a vacation or emergency fund. SpendLens never moves money;
 * progress is the sum of [savedManualMinor] (manual contributions) plus, when [linkedAccountKey] is
 * set, every CREDIT into that account since [createdAt]. [notifiedReached] guards the one-shot
 * "goal reached" notification.
 */
@Entity(tableName = "savings_goals")
data class SavingsGoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val targetAmountMinor: Long,
    /** Target date (epoch millis), if the user set one. */
    val deadline: Long? = null,
    /** Account whose CREDIT transactions auto-increment progress, or null for a manual-only goal. */
    val linkedAccountKey: String? = null,
    val createdAt: Long,
    /** Manual contributions in base-currency minor units. */
    val savedManualMinor: Long = 0,
    val notifiedReached: Boolean = false,
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
