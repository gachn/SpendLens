package com.spendlens.app.ui.viewmodel

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.RawSmsEntity
import com.spendlens.app.data.db.RawStatus
import com.spendlens.app.data.db.SmsPatternEntity
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.parser.Categorizer
import com.spendlens.app.data.repository.MerchantRepository
import com.spendlens.app.data.repository.CategoryRepository
import com.spendlens.app.data.repository.TransactionRepository
import com.spendlens.app.data.repository.PatternRepository
import com.spendlens.app.data.repository.BudgetRepository
import com.spendlens.app.data.repository.BillRepository
import com.spendlens.app.data.prefs.AppearancePrefs
import com.spendlens.app.data.prefs.Plan
import com.spendlens.app.data.prefs.ThemeMode
import com.spendlens.app.ui.theme.BankBranding
import com.spendlens.app.di.AppContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.spendlens.app.ui.util.Dates
import androidx.work.WorkManager
import com.spendlens.app.work.SmsSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import kotlin.math.absoluteValue

// ---------- UI state models ----------

/** Remembered metadata for a known merchant, applied when its name is picked/typed. */
data class MerchantMatch(val categoryId: Long?, val excluded: Boolean)

/** One merchant in the Settings → Merchants manager, aggregated across its alias rows. */
data class MerchantRow(
    val displayName: String,
    val emoji: String?,
    val categoryId: Long?,
    val excluded: Boolean,
    val tags: String?,
    /** Raw SMS tokens (alias keys) that resolve to this merchant — its "patterns". */
    val tokens: List<String>,
)

data class MerchantsUiState(
    val items: List<MerchantRow> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val query: String = "",
)

data class CategorySlice(val name: String, val color: Color, val amountMinor: Long, val categoryId: Long? = null)

/**
 * One category's spend in two months being compared (issue #15). [deltaMinor] is B − A;
 * [deltaPercent] is null when month A had no spend (an entirely new category), so the UI can show
 * "new" instead of a divide-by-zero.
 */
data class CategoryComparisonRow(
    val name: String,
    val color: Color,
    val categoryId: Long?,
    val amountAMinor: Long,
    val amountBMinor: Long,
) {
    val deltaMinor: Long get() = amountBMinor - amountAMinor
    val deltaPercent: Float? get() = if (amountAMinor == 0L) null else deltaMinor * 100f / amountAMinor
}

data class DashboardUiState(
    val spendMinor: Long = 0,
    val incomeMinor: Long = 0,
    val currency: String = "INR",
    val monthLabel: String = Dates.monthLabel(),
    val recent: List<TransactionEntity> = emptyList(),
    val slices: List<CategorySlice> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val monthOptions: List<YearMonth> = emptyList(),
    val merchantEmojis: Map<String, String> = emptyMap(),
)

enum class TxnFilter(val label: String) {
    ALL("All"),
    DEBIT("Debit"),
    CREDIT("Credit"),
}

enum class DateRangeFilter(val label: String) {
    ANY("Any time"),
    THIS_MONTH("This month"),
    LAST_30("30 days"),
    LAST_90("90 days"),
    THIS_YEAR("This year"),
}

/**
 * Active transaction filters. A null id/key or an ALL/ANY enum means "no constraint".
 * Held in the ViewModel so the selection persists for the session.
 */
data class TxnFilters(
    val direction: TxnFilter = TxnFilter.ALL,
    val categoryId: Long? = null,
    val accountKey: String? = null,
    val dateRange: DateRangeFilter = DateRangeFilter.ANY,
    val minAmountMinor: Long? = null,
    val maxAmountMinor: Long? = null,
) {
    /** Number of constraints currently narrowing the list — shown on the Filters toggle. */
    val activeCount: Int
        get() = listOf(
            direction != TxnFilter.ALL,
            categoryId != null,
            accountKey != null,
            dateRange != DateRangeFilter.ANY,
            minAmountMinor != null || maxAmountMinor != null,
        ).count { it }
}

data class TransactionsUiState(
    val items: List<TransactionEntity> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val query: String = "",
    val filters: TxnFilters = TxnFilters(),
    /** Distinct account keys present in the data, for the account filter chips. */
    val accounts: List<String> = emptyList(),
    val merchantEmojis: Map<String, String> = emptyMap(),
)

data class MonthlyBar(val label: String, val debitMinor: Long, val creditMinor: Long)

enum class AnalyticsTab { Spend, Income }

data class AnalyticsUiState(
    val months: List<MonthlyBar> = emptyList(),
    val slices: List<CategorySlice> = emptyList(),
    val topMerchants: List<CategorySlice> = emptyList(),
    val currency: String = "INR",
    val selectedMonth: YearMonth = YearMonth.now(),
    val monthOptions: List<YearMonth> = emptyList(),
    val activeTab: AnalyticsTab = AnalyticsTab.Spend,
    val incomeSlices: List<CategorySlice> = emptyList(),
    val monthlySavingsRates: List<Float> = emptyList(),
    // Month-over-month comparison (issue #15).
    val compareMode: Boolean = false,
    val compareMonthA: YearMonth = YearMonth.now().minusMonths(1),
    val compareMonthB: YearMonth = YearMonth.now(),
    val comparisonRows: List<CategoryComparisonRow> = emptyList(),
)

data class ReviewUiState(
    val unparsed: List<RawSmsEntity> = emptyList(),
    val duplicates: List<TransactionEntity> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
)

data class CategorySpend(val category: CategoryEntity, val amountMinor: Long)

data class CategoriesUiState(
    val items: List<CategorySpend> = emptyList(),
    val totalMinor: Long = 0,
    val currency: String = "INR",
    val monthLabel: String = Dates.monthLabel(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val monthOptions: List<YearMonth> = emptyList(),
)

data class BudgetRow(
    val category: CategoryEntity,
    val limitMinor: Long,   // 0 = no budget set
    val spentMinor: Long,
    val rolloverEnabled: Boolean = false,
    val rolloverMinor: Long = 0L,   // unspent amount carried from the previous month
) {
    /** Base limit plus any carried-over rollover. */
    val effectiveLimitMinor: Long get() = limitMinor + rolloverMinor
}

data class BudgetsUiState(
    val rows: List<BudgetRow> = emptyList(),
    val currency: String = "INR",
    val monthLabel: String = Dates.monthLabel(),
)

data class BillItem(
    val bill: com.spendlens.app.data.db.BillEntity,
    val category: CategoryEntity?,
    val dueLabel: String,
    val daysUntil: Long,
)

data class BillsUiState(
    val items: List<BillItem> = emptyList(),
    val currency: String = "INR",
)

// ---------- ViewModels ----------

class DashboardViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.transactionRepository
    private val monthOptions = Dates.recentMonths(12)
    private val selectedMonth = MutableStateFlow(monthOptions.first())

    fun setMonth(ym: YearMonth) { selectedMonth.value = ym }

    /** Latest known balance per account, for the Accounts card. */
    val accounts: StateFlow<List<com.spendlens.app.data.db.AccountBalance>> =
        repo.observeAccountBalances()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<DashboardUiState> = selectedMonth.flatMapLatest { month ->
        val (from, to) = Dates.monthRange(month)
        combine(
            repo.observeTotal("DEBIT", from, to - 1),
            repo.observeTotal("CREDIT", from, to - 1),
            repo.observeBetween(from, to - 1),
            repo.observeCategoryTotals(from, to - 1),
            container.categoryRepository.observeCategories(),
            container.merchantRepository.observeAll(),
        ) { array: Array<Any?> ->
            val spend = array[0] as Long
            val income = array[1] as Long
            val monthTxns = array[2] as List<TransactionEntity>
            val cats = array[3] as List<com.spendlens.app.data.db.CategoryTotal>
            val categories = array[4] as List<CategoryEntity>
            val merchants = array[5] as List<com.spendlens.app.data.db.MerchantAliasEntity>

            val map = categories.associateBy { it.id }
            val slices = cats.mapNotNull { ct ->
                if (ct.categoryId == null) {
                    CategorySlice("Uncategorized", Color(0xFF9E9E9EL), ct.total)
                } else {
                    map[ct.categoryId]?.let { CategorySlice(it.name, Color(it.color), ct.total) }
                }
            }
            val merchantEmojis = merchants.mapNotNull { m ->
                m.logoEmoji?.let { m.displayName to it }
            }.toMap()
            DashboardUiState(
                spendMinor = spend,
                incomeMinor = income,
                currency = "INR",
                monthLabel = Dates.label(month),
                recent = monthTxns.take(15),
                slices = slices,
                categories = map,
                selectedMonth = month,
                monthOptions = monthOptions,
                merchantEmojis = merchantEmojis,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState(monthOptions = monthOptions))

    /**
     * Generate a spending summary for the selected month.
     * Returns natural language description of spending patterns.
     */
    suspend fun generateSpendingSummary(): String? {
        val allTxns = repo.observeAll().first()
        val cats = container.categoryRepository.all()
        val metrics = com.spendlens.app.ai.SpendingSummaryGenerator.calculateMetrics(
            allTxns,
            cats,
            daysBack = 30,
        )
        return metrics.takeIf { it.transactionCount > 0 }?.let {
            com.spendlens.app.ai.SpendingSummaryGenerator.buildPrompt(it, cats, "this month")
        }
    }

    /**
     * Detect recurring transactions in the current data.
     * Returns subscriptions and regular payments sorted by confidence.
     */
    suspend fun detectRecurringTransactions(): List<com.spendlens.app.ai.RecurringPattern> {
        val allTxns = repo.observeAll().first()
        return com.spendlens.app.ai.RecurringDetector.detectRecurring(allTxns, minOccurrences = 3)
    }
}

@OptIn(FlowPreview::class)
class TransactionsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.transactionRepository
    private val _rawQuery = MutableStateFlow("")

    /** Immediate value — used by the TextField so the UI stays responsive on each keystroke. */
    val displayQuery: StateFlow<String> = _rawQuery.asStateFlow()

    /** Debounced value — drives the list filter so the list only recomputes after the user pauses typing. */
    private val query: StateFlow<String> = _rawQuery
        .debounce(300L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val filters = MutableStateFlow(TxnFilters())

    fun generatePrompt(smsList: List<RawSmsEntity>): String =
        com.spendlens.app.ai.PromptGenerator.generate(smsList)

    /**
     * Score a transaction for anomalies compared to user's baseline.
     * Returns score info or null if not anomalous.
     */
    suspend fun scoreTransactionAnomaly(transactionId: Long): com.spendlens.app.ai.AnomalyScore? {
        val txn = repo.getById(transactionId) ?: return null
        val allTxns = repo.observeAll().first()
        val cats = container.categoryRepository.all()
        return com.spendlens.app.ai.AnomalyDetector.scoreAnomaly(txn, allTxns, cats)
    }

    /**
     * Detect all anomalous transactions in user's data.
     * Returns sorted by anomaly score (highest first).
     */
    suspend fun detectAnomalies(): List<com.spendlens.app.ai.AnomalyScore> {
        val allTxns = repo.observeAll().first()
        val cats = container.categoryRepository.all()
        return com.spendlens.app.ai.AnomalyDetector.detectAnomalies(allTxns, cats)
    }

    companion object {
        /** Sentinel category id meaning "transactions with no category" (txn.categoryId == null). */
        const val UNCATEGORIZED_CATEGORY_ID = -1L
    }

    fun setQuery(q: String) { _rawQuery.value = q }
    fun setDirection(d: TxnFilter) { filters.update { it.copy(direction = d) } }
    fun setCategory(id: Long?) { filters.update { it.copy(categoryId = id) } }
    fun setAccount(key: String?) { filters.update { it.copy(accountKey = key) } }
    fun setDateRange(r: DateRangeFilter) { filters.update { it.copy(dateRange = r) } }
    fun setAmountRange(minMinor: Long?, maxMinor: Long?) {
        filters.update { it.copy(minAmountMinor = minMinor, maxAmountMinor = maxMinor) }
    }
    fun clearFilters() { filters.value = TxnFilters() }

    val state: StateFlow<TransactionsUiState> = combine(
        repo.observeAll(),
        container.categoryRepository.observeCategories(),
        container.merchantRepository.observeAll(),
        query,
        filters,
    ) { all, categories, merchants, q, f ->
        val (from, to) = dateBounds(f.dateRange)
        val filtered = all.filter { txn ->
            (when (f.direction) {
                TxnFilter.ALL -> true
                TxnFilter.DEBIT -> txn.direction == "DEBIT"
                TxnFilter.CREDIT -> txn.direction == "CREDIT"
            }) &&
                (f.categoryId == null ||
                    (f.categoryId == UNCATEGORIZED_CATEGORY_ID && txn.categoryId == null) ||
                    txn.categoryId == f.categoryId) &&
                (f.accountKey == null || txn.accountKey == f.accountKey) &&
                (from == null || txn.occurredAt >= from) &&
                (to == null || txn.occurredAt <= to) &&
                (f.minAmountMinor == null || txn.amountMinor >= f.minAmountMinor) &&
                (f.maxAmountMinor == null || txn.amountMinor <= f.maxAmountMinor) &&
                matchesQuery(txn, q)
        }
        val accounts = all.map { it.accountKey }.distinct().sorted()
        val merchantEmojis = merchants.mapNotNull { m ->
            m.logoEmoji?.let { m.displayName to it }
        }.toMap()
        TransactionsUiState(filtered, categories.associateBy { it.id }, q, f, accounts, merchantEmojis)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TransactionsUiState())

    private fun matchesQuery(txn: TransactionEntity, q: String): Boolean {
        if (q.isBlank()) return true
        return txn.counterparty.contains(q, ignoreCase = true) ||
            txn.accountKey.contains(q, ignoreCase = true) ||
            (txn.note?.contains(q, ignoreCase = true) == true) ||
            (txn.tags?.contains(q, ignoreCase = true) == true)
    }

    /** Resolve a [DateRangeFilter] to inclusive epoch-millis bounds; null means unbounded on that side. */
    private fun dateBounds(range: DateRangeFilter): Pair<Long?, Long?> {
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        return when (range) {
            DateRangeFilter.ANY -> null to null
            DateRangeFilter.THIS_MONTH -> Dates.currentMonth()
            DateRangeFilter.LAST_30 -> (now - 30L * day) to null
            DateRangeFilter.LAST_90 -> (now - 90L * day) to null
            DateRangeFilter.THIS_YEAR -> Dates.yearStart() to null
        }
    }

    // ----- Export (CSV / PDF), respecting the current filters -----

    /** One-shot user-facing message after an export attempt (null = nothing to show). */
    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage
    fun consumeExportMessage() { _exportMessage.value = null }

    fun exportCsv(context: android.content.Context) =
        export(context, "text/csv", "Export transactions (CSV)") { snapshot ->
            com.spendlens.app.data.export.StatementExporter
                .writeCsv(context, snapshot.items, snapshot.categories)
        }

    fun exportPdf(context: android.content.Context) =
        export(context, "application/pdf", "Export statement (PDF)") { snapshot ->
            com.spendlens.app.data.export.StatementExporter
                .writePdf(context, snapshot.items, snapshot.categories)
        }

    private fun export(
        context: android.content.Context,
        mime: String,
        label: String,
        build: suspend (TransactionsUiState) -> java.io.File,
    ) = viewModelScope.launch {
        val snapshot = state.value
        if (snapshot.items.isEmpty()) {
            _exportMessage.value = "Nothing to export."
            return@launch
        }
        runCatching {
            val file = withContext(Dispatchers.IO) { build(snapshot) }
            // Launch the share-sheet on the main thread.
            com.spendlens.app.data.export.StatementExporter.share(context, file, mime, label)
        }.onFailure { _exportMessage.value = "Export failed: ${it.message}" }
    }
}

/** One bank account or card, aggregated from its transactions. */
data class AccountSummary(
    val accountKey: String,
    val channel: String,
    val isCard: Boolean,
    /** Latest balance reported by an SMS for this account, if any. */
    val balanceMinor: Long?,
    /** When that balance was last reported (occurredAt of the latest balance-bearing SMS). */
    val balanceUpdatedAt: Long? = null,
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    val txnCount: Int,
    val lastActivityAt: Long,
    val transactions: List<TransactionEntity>,
    /** Latest credit-card statement, if this is a card and a bill SMS was parsed. */
    val billTotalDueMinor: Long? = null,
    val billMinDueMinor: Long? = null,
    val billDueDate: Long? = null,
    /** SMS sender address for the most common sender on this account, e.g. "VK-HDFCBK". */
    val topSender: String? = null,
    /** Human-readable bank name auto-detected from [topSender], e.g. "Axis Bank". */
    val detectedBankName: String? = null,
    /** User-set display name overriding the raw accountKey in the UI. */
    val customName: String? = null,
    /** Day of month (1-31) on which this card's statement generates (derived from parsed bill or user setting). */
    val statementCycleDay: Int? = null,
    /** True when bill amounts are estimated from transactions (no statement SMS parsed). */
    val isEstimatedBill: Boolean = false,
    /** True when a payment SMS was detected for the current statement. */
    val isStatementPaid: Boolean = false,
    /** Amount paid in minor units if a payment was auto-detected. */
    val paidAmountMinor: Long? = null,
    /** Total DEBIT spend from the last statement date to now (current cycle). */
    val cycleSpendMinor: Long = 0L,
    /** Balance from a standalone balance-notification SMS ([BalanceSnapshotEntity]). */
    val snapshotBalanceMinor: Long? = null,
    /** When that snapshot balance was observed (epoch millis), for the freshness tie-break. */
    val snapshotObservedAt: Long? = null,
) {
    /** Active in the selected month, or a card carrying an outstanding bill. */
    val hasActivity: Boolean get() = txnCount > 0 || billTotalDueMinor != null

    /** Effective balance: most-recent of transaction-reported vs. snapshot balance. */
    val effectiveBalanceMinor: Long?
        get() = when {
            balanceMinor != null && snapshotBalanceMinor != null ->
                if ((balanceUpdatedAt ?: 0L) >= (snapshotObservedAt ?: 0L)) balanceMinor else snapshotBalanceMinor
            else -> balanceMinor ?: snapshotBalanceMinor
        }

    /**
     * When both a transaction-reported balance and a standalone snapshot balance exist and differ,
     * this is the *other* source (the one not shown as [effectiveBalanceMinor]) paired with when it
     * was observed — so the UI can surface both values instead of silently dropping one.
     */
    val secondaryBalance: Pair<Long, Long>?
        get() {
            val live = balanceMinor ?: return null
            val snap = snapshotBalanceMinor ?: return null
            if (live == snap) return null
            val liveAt = balanceUpdatedAt ?: 0L
            val snapAt = snapshotObservedAt ?: 0L
            return if (liveAt >= snapAt) snap to snapAt else live to liveAt
        }
}

data class AccountsUiState(
    val bankAccounts: List<AccountSummary> = emptyList(),
    val cards: List<AccountSummary> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val months: List<YearMonth> = emptyList(),
)

class AccountsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.transactionRepository
    private val monthOptions = Dates.recentMonths(12)
    private val selectedMonth = MutableStateFlow(monthOptions.first())

    // Top SMS sender per accountKey — loaded once; stable (bank doesn't change per account).
    private val _senders = MutableStateFlow<Map<String, String>>(emptyMap())

    init {
        viewModelScope.launch {
            _senders.value = repo.topSenderPerAccount()
                .distinctBy { it.accountKey }
                .associate { it.accountKey to it.sender }
        }
    }

    fun setMonth(ym: YearMonth) { selectedMonth.value = ym }

    fun setAccountName(accountKey: String, name: String?) =
        container.settingsStore.setAccountName(accountKey, name)

    fun setStatementCycleDay(accountKey: String, day: Int) = viewModelScope.launch {
        container.settingsStore.setStatementCycleDay(accountKey, day)
        // Also update the DB row if a parsed bill exists for this card.
        container.cardBillDao.setStatementCycleDay(accountKey, day)
    }

    /**
     * Manually set/override an account's balance. Stored as a [BalanceSnapshotEntity] observed now,
     * so it wins the freshness tie-break in [AccountSummary.effectiveBalanceMinor] over older
     * SMS-derived balances. [isCard] picks the avl-limit vs avl-balance label in the UI.
     */
    fun setManualBalance(accountKey: String, balanceMinor: Long, isCard: Boolean) = viewModelScope.launch {
        container.balanceSnapshotDao.upsert(
            com.spendlens.app.data.db.BalanceSnapshotEntity(
                accountKey = accountKey,
                balanceMinor = balanceMinor,
                isCard = isCard,
                currency = "INR",
                observedAt = System.currentTimeMillis(),
            ),
        )
    }

    val state: StateFlow<AccountsUiState> = combine(
        repo.observeAll(),
        repo.observeAccountBalances(),
        container.categoryRepository.observeCategories(),
        combine(
            container.cardBillDao.observeAll(),
            selectedMonth,
            _senders,
            container.settingsStore.accountNames,
            container.balanceSnapshotDao.observeAll(),
            container.settingsStore.cycleDays,
        ) { array ->
            @Suppress("UNCHECKED_CAST")
            object {
                val bills = array[0] as List<com.spendlens.app.data.db.CardBillEntity>
                val month = array[1] as YearMonth
                val senders = array[2] as Map<String, String>
                val names = array[3] as Map<String, String?>
                val snapshots = array[4] as List<com.spendlens.app.data.db.BalanceSnapshotEntity>
                val cycleDays = array[5] as Map<String, Int>
            }
        },
    ) { txns, balances, cats, bag ->
        val (start, end) = Dates.monthRange(bag.month)
        val balanceByKey = balances.associateBy { it.accountKey }
        // Bills keyed by both cardKey (e.g. "••••1234") and by sender (fallback for
        // statement SMS that had no card number → cardKey = sender address).
        val billByCard = bag.bills.associateBy { it.cardKey }
        val snapshotByKey = bag.snapshots.associateBy { it.accountKey }
        val now = System.currentTimeMillis()
        // Group across all history so every account stays visible; scope totals to the month.
        val summaries = txns
            .filter { !it.isDuplicate }
            // Accounts with no detected number ("Unknown") are not surfaced as cards — the user
            // tags those transactions with a known account from the transaction detail sheet.
            .filter { it.accountKey != "Unknown" }
            .groupBy { it.accountKey }
            .map { (key, all) ->
                // Representative channel = the one most transactions used.
                val topChannel = all.groupingBy { it.channel }.eachCount()
                    .maxByOrNull { it.value }?.key.orEmpty()
                val monthTxns = all
                    .filter { it.occurredAt in start until end }
                    .sortedByDescending { it.occurredAt }
                // Try card-number key first; fall back to matching via the account's top sender.
                val bill = billByCard[key] ?: bag.senders[key]?.let { billByCard[it] }
                // Cycle day: prefer parsed SMS value, fall back to user's stored preference.
                val resolvedCycleDay = bill?.statementCycleDay ?: bag.cycleDays[key]
                val cycleFrom = bill?.statementAt ?: start
                val cycleSpend = all
                    .filter { it.direction == "DEBIT" && !it.isDuplicate && it.occurredAt >= cycleFrom }
                    .sumOf { it.amountBaseMinor }
                val isCard = topChannel.equals("CARD", ignoreCase = true)
                // When no statement SMS was parsed but a cycle day is known, estimate the bill
                // from the current cycle's spend so the card is never shown with a blank amount.
                val estimatedBill = bill == null && isCard && resolvedCycleDay != null && cycleSpend > 0
                val estimatedDueDate: Long? = if (estimatedBill) {
                    estimateNextDueDate(resolvedCycleDay!!, now)
                } else null
                AccountSummary(
                    accountKey = key,
                    channel = topChannel,
                    isCard = isCard,
                    balanceMinor = balanceByKey[key]?.balanceMinor,
                    balanceUpdatedAt = balanceByKey[key]?.updatedAt,
                    totalDebitMinor = monthTxns.filter { it.direction == "DEBIT" }.sumOf { it.amountBaseMinor },
                    totalCreditMinor = monthTxns.filter { it.direction == "CREDIT" }.sumOf { it.amountBaseMinor },
                    txnCount = monthTxns.size,
                    lastActivityAt = all.maxOf { it.occurredAt },
                    transactions = monthTxns,
                    billTotalDueMinor = bill?.totalDueMinor ?: if (estimatedBill) cycleSpend else null,
                    billMinDueMinor = bill?.minDueMinor,
                    billDueDate = bill?.dueDate ?: estimatedDueDate,
                    topSender = bag.senders[key],
                    detectedBankName = BankBranding.detectedBankName(bag.senders[key]),
                    customName = bag.names[key],
                    statementCycleDay = resolvedCycleDay,
                    isEstimatedBill = estimatedBill,
                    isStatementPaid = bill?.paidAt != null,
                    paidAmountMinor = bill?.paidAmountMinor,
                    cycleSpendMinor = cycleSpend,
                    snapshotBalanceMinor = snapshotByKey[key]?.balanceMinor,
                    snapshotObservedAt = snapshotByKey[key]?.observedAt,
                )
            }
            .sortedByDescending { it.lastActivityAt }
        AccountsUiState(
            bankAccounts = summaries.filter { !it.isCard },
            cards = summaries.filter { it.isCard },
            categories = cats.associateBy { it.id },
            selectedMonth = bag.month,
            months = monthOptions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())

    private fun estimateNextDueDate(cycleDay: Int, nowMillis: Long): Long {
        val zone = ZoneId.systemDefault()
        val today = java.time.Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        // Statement generates on cycleDay; due date is 20 days later (Indian bank standard).
        val statementThisMonth = runCatching {
            today.withDayOfMonth(cycleDay.coerceAtMost(today.lengthOfMonth()))
        }.getOrNull() ?: today
        val statementDate = if (statementThisMonth <= today) statementThisMonth
        else statementThisMonth.minusMonths(1).withDayOfMonth(
            cycleDay.coerceAtMost(statementThisMonth.minusMonths(1).lengthOfMonth())
        )
        return statementDate.plusDays(20).atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

class AnalyticsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.transactionRepository
    private val zone: ZoneId = ZoneId.systemDefault()
    private val monthOptions = Dates.recentMonths(12)
    private val selectedMonth = MutableStateFlow(monthOptions.first())
    private val activeTab = MutableStateFlow(AnalyticsTab.Spend)

    /** Comparison-mode controls (issue #15): on/off plus the two months being diffed. */
    private data class CompareControls(val enabled: Boolean, val monthA: YearMonth, val monthB: YearMonth)
    private val compare = MutableStateFlow(
        CompareControls(
            enabled = false,
            monthA = monthOptions.getOrElse(1) { monthOptions.first() },
            monthB = monthOptions.first(),
        ),
    )

    fun setMonth(ym: YearMonth) { selectedMonth.value = ym }
    fun setTab(tab: AnalyticsTab) { activeTab.value = tab }
    fun setCompareMode(enabled: Boolean) { compare.value = compare.value.copy(enabled = enabled) }
    fun setCompareMonthA(ym: YearMonth) { compare.value = compare.value.copy(monthA = ym) }
    fun setCompareMonthB(ym: YearMonth) { compare.value = compare.value.copy(monthB = ym) }

    private val controls = combine(selectedMonth, activeTab, compare) { month, tab, cmp -> Triple(month, tab, cmp) }

    val state: StateFlow<AnalyticsUiState> = combine(
        repo.observeAll(),
        container.categoryRepository.observeCategories(),
        repo.observeAllSplits(),
        controls,
    ) { txns, categories, splits, ctrl ->
        val (breakdownMonth, tab, cmp) = ctrl
        val map = categories.associateBy { it.id }
        val splitsByParent = splits.groupBy { it.parentId }
        val currency = "INR" // base currency for all totals

        // Last 6 months, oldest → newest.
        val now = YearMonth.now(zone)
        val spendable = txns.filter { !it.excludedFromExpense }
        val months = (5 downTo 0).map { back ->
            val ym = now.minusMonths(back.toLong())
            val inMonth = spendable.filter { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) == ym }
            MonthlyBar(
                label = ym.month.name.take(3),
                debitMinor = inMonth.filter { it.direction == "DEBIT" }.sumOf { it.amountBaseMinor },
                creditMinor = inMonth.filter { it.direction == "CREDIT" }.sumOf { it.amountBaseMinor },
            )
        }

        // Per-month DEBIT totals by category. Split parents contribute via their children's
        // categories, not their own. Reused for the breakdown slices and the A/B comparison.
        fun debitsIn(month: YearMonth) = spendable.filter {
            YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) == month && it.direction == "DEBIT"
        }
        fun categoryTotals(month: YearMonth): Map<Long?, Long> {
            val totals = HashMap<Long?, Long>()
            debitsIn(month).forEach { t ->
                if (t.isSplit) {
                    splitsByParent[t.id].orEmpty().forEach { s ->
                        totals[s.categoryId] = (totals[s.categoryId] ?: 0L) + s.amountBaseMinor
                    }
                } else {
                    totals[t.categoryId] = (totals[t.categoryId] ?: 0L) + t.amountBaseMinor
                }
            }
            return totals
        }

        // Selected-month spend: category slices + top merchants.
        val inMonthDebits = debitsIn(breakdownMonth)
        val totalByCat = categoryTotals(breakdownMonth)
        val slices = totalByCat
            .mapNotNull { (catId, total) ->
                if (catId == null) {
                    CategorySlice("Uncategorized", Color(0xFF9E9E9EL), total, categoryId = TransactionsViewModel.UNCATEGORIZED_CATEGORY_ID)
                } else {
                    map[catId]?.let { CategorySlice(it.name, Color(it.color), total, categoryId = catId) }
                }
            }.sortedByDescending { it.amountMinor }
        val palette = listOf(0xFF0E7C66L, 0xFF42A5F5L, 0xFFEC407AL, 0xFFFFB300L, 0xFFAB47BCL, 0xFF26C6DAL, 0xFFEF5350L, 0xFF66BB6AL)
        val merchants = inMonthDebits.groupBy { it.counterparty }
            .map { (name, list) -> name to list.sumOf { it.amountBaseMinor } }
            .sortedByDescending { it.second }.take(5)
            .mapIndexed { i, (name, total) -> CategorySlice(name, Color(palette[i % palette.size]), total) }

        // Income tab: sources breakdown + savings rates.
        val incomeData = IncomeAnalytics.compute(
            transactions = txns,
            breakdownMonth = breakdownMonth,
            months = months,
            zone = zone,
            categories = map.mapValues { it.value.name },
        )
        val incomeSlices = incomeData.sources.mapIndexed { i, src ->
            CategorySlice(src.name, Color(palette[i % palette.size]), src.amountMinor)
        }

        // Month-over-month comparison (issue #15): diff category spend between the two chosen months.
        val comparisonRows = if (cmp.enabled) {
            val totalsA = categoryTotals(cmp.monthA)
            val totalsB = categoryTotals(cmp.monthB)
            (totalsA.keys + totalsB.keys).map { catId ->
                val name: String
                val color: Color
                if (catId == null) {
                    name = "Uncategorized"; color = Color(0xFF9E9E9EL)
                } else {
                    val cat = map[catId]
                    name = cat?.name ?: "Unknown"; color = Color(cat?.color ?: 0xFF9E9E9EL)
                }
                CategoryComparisonRow(
                    name = name,
                    color = color,
                    categoryId = catId ?: TransactionsViewModel.UNCATEGORIZED_CATEGORY_ID,
                    amountAMinor = totalsA[catId] ?: 0L,
                    amountBMinor = totalsB[catId] ?: 0L,
                )
            }.sortedByDescending { it.deltaMinor.absoluteValue }
        } else {
            emptyList()
        }

        AnalyticsUiState(
            months = months,
            slices = slices,
            topMerchants = merchants,
            currency = currency,
            selectedMonth = breakdownMonth,
            monthOptions = monthOptions,
            activeTab = tab,
            incomeSlices = incomeSlices,
            monthlySavingsRates = incomeData.monthlySavingsRates,
            compareMode = cmp.enabled,
            compareMonthA = cmp.monthA,
            compareMonthB = cmp.monthB,
            comparisonRows = comparisonRows,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalyticsUiState(monthOptions = monthOptions))
}

class ReviewViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.transactionRepository

    val state: StateFlow<ReviewUiState> = combine(
        container.rawSmsDao.observeByStatus(RawStatus.UNPARSED),
        repo.observeFlaggedDuplicates(),
        container.categoryRepository.observeCategories(),
    ) { unparsed, dups, categories ->
        ReviewUiState(unparsed, dups, categories.associateBy { it.id })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReviewUiState())

    /** Confirm a flagged row really is a duplicate: hide it and mark the group resolved. */
    fun confirmDuplicate(txn: TransactionEntity) = viewModelScope.launch {
        repo.update(txn.copy(isDuplicate = true, userVerified = true))
    }

    /** Keep a flagged row as a genuine separate transaction. */
    fun keepAsUnique(txn: TransactionEntity) = viewModelScope.launch {
        repo.update(txn.copy(dupGroupId = null, isDuplicate = false, userVerified = true))
    }

    fun ignoreSms(raw: RawSmsEntity) = viewModelScope.launch {
        container.rawSmsDao.updateStatus(raw.id, RawStatus.IGNORED, null)
    }

    /** Re-run filter + patterns over the UNPARSED and PARSED backlog to apply newly added patterns. */
    fun reprocessUnparsed() = viewModelScope.launch {
        container.smsProcessor.reprocessAllSms()
    }

    /** Shared AI orchestration (flag check, OpenRouter call, pattern apply). */
    private val teacher = com.spendlens.app.ai.AiPatternTeacher(container)

    suspend fun generatePrompt(smsList: List<RawSmsEntity>): String = teacher.generatePrompt(smsList)

    /** True when the AI flag is on and a usable key exists (so the direct API path can run). */
    fun aiUsable(): Boolean = teacher.aiUsable()

    /**
     * Flag-gated AI pattern generation. Delegates to [AiPatternTeacher]: when AI is enabled with a
     * key it calls OpenRouter and applies the reply; otherwise it returns
     * [AiPatternTeacher.TeachResult.Fallback] so the UI keeps the copy-to-clipboard flow.
     */
    suspend fun teachWithAi(smsList: List<RawSmsEntity>): com.spendlens.app.ai.AiPatternTeacher.TeachResult =
        teacher.teach(smsList)

    /** Apply AI-produced pattern JSON (used by the clipboard-reply watcher in SpendLensRoot). */
    suspend fun applyAiPatterns(jsonString: String): Int = teacher.applyAiPatterns(jsonString)
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val patterns: StateFlow<List<SmsPatternEntity>> =
        container.patternRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ----- Appearance -----

    val appearance: StateFlow<AppearancePrefs> = container.settingsStore.appearance

    // ----- Plan -----

    val plan: StateFlow<Plan> = container.planStore.plan

    fun setPlan(plan: Plan) = container.planStore.setPlan(plan)

    fun setThemeMode(mode: ThemeMode) = container.settingsStore.setThemeMode(mode)

    fun setDynamicColor(enabled: Boolean) = container.settingsStore.setDynamicColor(enabled)

    fun setAiBannerEnabled(enabled: Boolean) = container.settingsStore.setAiBannerEnabled(enabled)

    fun setDebugInfoEnabled(enabled: Boolean) = container.settingsStore.setDebugInfoEnabled(enabled)

    val security: StateFlow<com.spendlens.app.data.prefs.SecurityPrefs> = container.settingsStore.security

    fun setAppLockEnabled(enabled: Boolean) = container.settingsStore.setAppLockEnabled(enabled)

    fun setGracePeriod(seconds: Int) = container.settingsStore.setGracePeriodSec(seconds)

    // ----- SMS Filtering -----

    val smsFilter: StateFlow<com.spendlens.app.data.prefs.SmsFilterPrefs> = container.settingsStore.smsFilter

    fun setFinancialSendersOnly(enabled: Boolean) = container.settingsStore.setFinancialSendersOnly(enabled)

    fun setMerchantPrediction(enabled: Boolean) = container.settingsStore.setMerchantPredictionEnabled(enabled)

    // ----- AI (OpenRouter) -----

    val aiPrefs: StateFlow<com.spendlens.app.data.prefs.AiPrefs> = container.aiConfigStore.prefsFlow

    fun setAiEnabled(enabled: Boolean) = container.aiConfigStore.setEnabled(enabled)

    fun setAiModel(model: String) = container.aiConfigStore.setModel(model)

    fun setAiApiKey(key: String?) = container.aiConfigStore.setApiKey(key)

    fun setAiMaxTokens(tokens: Int) = container.aiConfigStore.setMaxTokens(tokens)

    /** OpenRouter model slugs for autocompleting the Model field; empty until [loadAiModels] runs. */
    private val _aiModels = MutableStateFlow<List<String>>(emptyList())
    val aiModels: StateFlow<List<String>> = _aiModels.asStateFlow()

    /** Fetch the model catalogue once (no-op if already loaded). Failures leave the list empty. */
    fun loadAiModels() {
        if (_aiModels.value.isNotEmpty()) return
        viewModelScope.launch {
            val models = container.openRouterClient.listModels(container.aiConfigStore.effectiveKey())
            if (models.isNotEmpty()) _aiModels.value = models
        }
    }

    fun setPatternEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        container.patternRepository.setEnabled(id, enabled)
    }

    fun deletePattern(id: Long) = viewModelScope.launch { container.patternRepository.delete(id) }

    fun clearAllPatterns() = viewModelScope.launch { container.patternRepository.clearAll() }

    /**
     * Persist a user-authored SMS pattern (issue #14). User patterns get a high priority so they
     * sit above the built-ins, mirroring how learned patterns are ranked. Blank fields are coerced
     * to null/empty; the caller is expected to have validated the regexes via [testPattern].
     */
    fun savePattern(name: String, senderRegex: String?, bodyRegex: String, sampleSms: String?) =
        viewModelScope.launch {
            container.patternRepository.savePattern(
                SmsPatternEntity(
                    name = name.trim(),
                    senderRegex = senderRegex?.trim()?.takeIf { it.isNotEmpty() },
                    bodyRegex = bodyRegex.trim(),
                    priority = 1000, // above built-ins (their priorities are well under this)
                    source = com.spendlens.app.data.db.PatternSource.USER,
                    sampleSms = sampleSms?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }

    fun reimport(context: android.content.Context) = SmsSyncWorker.enqueueImport(context)

    /** True when AI is on with a usable key — gates the "auto-categorise" controls. */
    fun aiUsable(): Boolean = container.aiConfigStore.isUsable()

    /**
     * User-requested AI re-categorisation. Clears the attempted flag on still-uncategorised rows so
     * transactions the AI previously couldn't classify are reconsidered, then runs the categoriser.
     */
    fun recategorizeWithAi(context: android.content.Context) = viewModelScope.launch {
        container.aiCategorizer.resetAttempts()
        com.spendlens.app.work.AiCategorizeWorker.enqueueReplace(context)
    }

    /** Clears parsed transactions, raw SMS and derived bills, leaving learned patterns/categories. */
    fun clearTransactions() = viewModelScope.launch {
        container.database.transactionDao().clear()
        container.rawSmsDao.clear()
        container.billRepository.clear()
        container.cardBillDao.clear()
    }

    /** Wipes all transactions + raw SMS + derived bills then triggers a fresh full rescan. */
    fun clearAllDataAndRescan(context: android.content.Context) = viewModelScope.launch {
        WorkManager.getInstance(context).cancelUniqueWork(SmsSyncWorker.IMPORT_WORK)
        container.database.transactionDao().clear()
        container.rawSmsDao.clear()
        container.billRepository.clear()
        container.cardBillDao.clear()
        SmsSyncWorker.enqueueImport(context)
    }

    // ----- Encrypted backup / restore (issue #13) -----

    /** Epoch millis of the last successful export, or null if never backed up. */
    val lastBackupAt: StateFlow<Long?> = container.settingsStore.lastBackupAt

    sealed interface BackupState {
        data object Idle : BackupState
        data object Working : BackupState
        data class Done(val message: String) : BackupState
        data class Failed(val message: String) : BackupState
    }

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState
    fun consumeBackupState() { _backupState.value = BackupState.Idle }

    /** Encrypt all data under [password] and write the blob to the user-chosen [uri]. */
    fun exportBackup(context: android.content.Context, uri: android.net.Uri, password: CharArray) =
        viewModelScope.launch {
            if (_backupState.value is BackupState.Working) return@launch
            _backupState.value = BackupState.Working
            _backupState.value = runCatching {
                val blob = container.backupManager.export(password)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(blob) }
                        ?: error("Could not open the destination file")
                }
                container.settingsStore.setLastBackupAt(System.currentTimeMillis())
                BackupState.Done("Backup saved")
            }.getOrElse { e -> BackupState.Failed(e.message ?: "Export failed") }
                .also { password.fill(' ') }
        }

    /** Read the blob at [uri], decrypt with [password], and replace current data on success. */
    fun importBackup(context: android.content.Context, uri: android.net.Uri, password: CharArray) =
        viewModelScope.launch {
            if (_backupState.value is BackupState.Working) return@launch
            _backupState.value = BackupState.Working
            _backupState.value = runCatching {
                val blob = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: error("Could not open the backup file")
                }
                container.backupManager.import(blob, password)
                BackupState.Done("Backup restored")
            }.getOrElse { e ->
                val msg = if (e is com.spendlens.app.data.backup.BackupManager.BadPasswordException)
                    "Wrong password or corrupt file" else (e.message ?: "Restore failed")
                BackupState.Failed(msg)
            }.also { password.fill(' ') }
        }

    // ----- Debug export -----

    sealed class ExportState {
        data object Idle : ExportState()
        data object InProgress : ExportState()
        data class Success(val path: String) : ExportState()
        data class Failed(val message: String) : ExportState()
    }

    private val _exportState = MutableStateFlow<ExportState>(ExportState.Idle)
    val exportState: StateFlow<ExportState> = _exportState

    fun exportDebugCsv(context: android.content.Context) = viewModelScope.launch {
        fun escape(v: String) = if (v.contains(',') || v.contains('"') || v.contains('\n'))
            "\"${v.replace("\"", "\"\"")}\"" else v
        if (_exportState.value is ExportState.InProgress) return@launch
        _exportState.value = ExportState.InProgress
        _exportState.value = runCatching {
            val file = withContext(Dispatchers.IO) {
                val txns = container.transactionRepository.allTransactions()
                val categories = container.categoryRepository.all().associateBy { it.id }
                val dir = context.getExternalFilesDir(null) ?: error("External storage unavailable")
                val ts = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US).format(java.util.Date())
                val out = java.io.File(dir, "spendlens_debug_$ts.csv")
                val unparsed = container.rawSmsDao.listByStatus(RawStatus.UNPARSED)
                out.bufferedWriter().use { w ->
                    w.write("id,amount_inr,currency,direction,counterparty,category,occurred_at,channel,account_key,excluded,note,tags\n")
                    txns.forEach { t ->
                        val cat = categories[t.categoryId]?.name ?: ""
                        val date = java.time.Instant.ofEpochMilli(t.occurredAt).toString()
                        w.write("${t.id},${t.amountBaseMinor / 100.0},${escape(t.currency)},${escape(t.direction)},${escape(t.counterparty)},${escape(cat)},${date},${escape(t.channel)},${escape(t.accountKey)},${t.excludedFromExpense},${escape(t.note ?: "")},${escape(t.tags ?: "")}\n")
                    }
                    // Second section: messages that passed the financial filter but matched no pattern
                    // (status UNPARSED). Dumped so the raw bodies can be inspected to find missing patterns.
                    w.write("\n# UNPARSED (${unparsed.size})\n")
                    w.write("id,sender,received_at,body\n")
                    unparsed.forEach { r ->
                        val date = java.time.Instant.ofEpochMilli(r.receivedAt).toString()
                        w.write("${r.id},${escape(r.sender)},${date},${escape(r.body)}\n")
                    }
                }
                out
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(
                android.content.Intent.createChooser(shareIntent, "Share debug export").apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
            file.absolutePath
        }.fold(
            onSuccess = { ExportState.Success(it) },
            onFailure = { ExportState.Failed(it.message ?: "Unknown error") },
        )
    }
}

class CategoriesViewModel(private val container: AppContainer) : ViewModel() {
    private val monthOptions = Dates.recentMonths(12)
    private val selectedMonth = MutableStateFlow(monthOptions.first())

    fun setMonth(ym: YearMonth) { selectedMonth.value = ym }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<CategoriesUiState> = selectedMonth.flatMapLatest { month ->
        val (from, to) = Dates.monthRange(month)
        combine(
            container.transactionRepository.observeCategoryTotals(from, to - 1),
            container.categoryRepository.observeCategories(),
        ) { totals, categories ->
            val totalByCat = totals.associate { it.categoryId to it.total }
            val uncategorizedTotal = totalByCat[null] ?: 0L
            val items = buildList {
                addAll(categories.map { CategorySpend(it, totalByCat[it.id] ?: 0L) })
                if (uncategorizedTotal > 0L) add(
                    CategorySpend(CategoryEntity(-1L, "Uncategorized", "❓", 0xFF9E9E9EL), uncategorizedTotal)
                )
            }.sortedByDescending { it.amountMinor }
            CategoriesUiState(
                items = items,
                totalMinor = totals.sumOf { it.total },
                currency = "INR", // base currency
                monthLabel = Dates.label(month),
                selectedMonth = month,
                monthOptions = monthOptions,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategoriesUiState(monthOptions = monthOptions))

    fun createCategory(name: String, icon: String, color: Long) = viewModelScope.launch {
        container.categoryRepository.createCategory(name, icon, color)
    }
}

class BudgetsViewModel(private val container: AppContainer) : ViewModel() {
    private val range = Dates.currentMonth()
    private val prevRange = Dates.monthRange(YearMonth.now(ZoneId.systemDefault()).minusMonths(1))
    private val zone: ZoneId = ZoneId.systemDefault()

    val state: StateFlow<BudgetsUiState> = combine(
        container.budgetRepository.observeAll(),
        container.categoryRepository.observeCategories(),
        container.transactionRepository.observeCategoryTotals(range.first, range.second),
        container.transactionRepository.observeCategoryTotals(prevRange.first, prevRange.second),
    ) { budgets, categories, totals, prevTotals ->
        val spentByCat = totals.associate { it.categoryId to it.total }
        val prevSpentByCat = prevTotals.associate { it.categoryId to it.total }
        val budgetByCat = budgets.associate { it.categoryId to it }
        val rows = categories
            .map { cat ->
                val budget = budgetByCat[cat.id]
                val limit = budget?.monthlyLimitMinor ?: 0L
                val rolloverEnabled = budget?.rolloverEnabled == true
                val rollover = if (rolloverEnabled && limit > 0L) {
                    container.budgetRepository.rolloverAmount(limit, prevSpentByCat[cat.id] ?: 0L)
                } else 0L
                BudgetRow(cat, limit, spentByCat[cat.id] ?: 0L, rolloverEnabled, rollover)
            }
            // Budgeted categories first, then by spend.
            .sortedWith(compareByDescending<BudgetRow> { it.limitMinor > 0 }.thenByDescending { it.spentMinor })
        BudgetsUiState(rows = rows, currency = "INR")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun setBudget(categoryId: Long, limitMinor: Long, rolloverEnabled: Boolean? = null) = viewModelScope.launch {
        container.budgetRepository.setBudget(categoryId, limitMinor, rolloverEnabled)
    }

    /** Tracks the predict-budgets action so the screen can show progress / a result. */
    sealed interface PredictState {
        data object Idle : PredictState
        data object Running : PredictState
        data class Done(val updated: Int) : PredictState
    }

    private val _predictState = MutableStateFlow<PredictState>(PredictState.Idle)
    val predictState: StateFlow<PredictState> = _predictState
    fun consumePredictResult() { _predictState.value = PredictState.Idle }

    /**
     * Forecast a monthly limit for every category from the last 12 months of debit history and
     * write the results. Uses [BudgetPredictor], which weights recent months, follows the trend
     * and adds a volatility buffer instead of a flat average. Categories with no usable history
     * are left untouched.
     */
    fun predictBudgets() = viewModelScope.launch {
        if (_predictState.value is PredictState.Running) return@launch
        _predictState.value = PredictState.Running

        val months = Dates.recentMonths(12) // newest → oldest
        val orderedMonths = months.reversed() // oldest → newest, matching BudgetPredictor's input

        val debits = withContext(Dispatchers.IO) { container.transactionRepository.allDebits() }

        // Per-category, per-month base-minor totals.
        val byCategory: Map<Long, Map<YearMonth, Long>> = debits
            .filter { it.categoryId != null }
            .groupBy { it.categoryId!! }
            .mapValues { (_, list) ->
                list.groupBy { YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) }
                    .mapValues { (_, txns) -> txns.sumOf { t -> t.amountBaseMinor } }
            }

        var updated = 0
        byCategory.forEach { (categoryId, monthly) ->
            val series = orderedMonths.map { ym -> monthly[ym] ?: 0L }
            val predicted = com.spendlens.app.ui.util.BudgetPredictor.predict(series)
            if (predicted > 0L) {
                container.budgetRepository.setBudget(categoryId, predicted)
                updated++
            }
        }
        _predictState.value = PredictState.Done(updated)
    }

    /**
     * Generate AI-based budget alerts for all categories.
     * Shows current status and whether user is on track to exceed budget.
     */
    suspend fun generateBudgetAlerts(): List<com.spendlens.app.ai.BudgetAlert> {
        val allTxns = container.transactionRepository.observeAll().first()
        val categories = container.categoryRepository.all().associateBy { it.id }
        val budgets = container.budgetRepository.all().associate { budget ->
            val catName = categories[budget.categoryId]?.name ?: "Unknown"
            budget.categoryId to (catName to budget.monthlyLimitMinor)
        }
        return com.spendlens.app.ai.BudgetPredictor.generateAllAlerts(budgets, allTxns)
    }

    /**
     * Predict whether a category will exceed budget before month ends.
     */
    suspend fun predictCategoryBudgetStatus(categoryId: Long, categoryName: String, budgetMinor: Long): com.spendlens.app.ai.BudgetPrediction {
        val allTxns = container.transactionRepository.observeAll().first()
        return com.spendlens.app.ai.BudgetPredictor.predictBudgetStatus(categoryId, categoryName, budgetMinor, allTxns)
    }
}

class BillsViewModel(private val container: AppContainer) : ViewModel() {
    private val zone = ZoneId.systemDefault()
    private val dueFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())

    val state: StateFlow<BillsUiState> = combine(
        container.billRepository.observeAll(),
        container.categoryRepository.observeCategories(),
    ) { bills, categories ->
        val map = categories.associateBy { it.id }
        val now = System.currentTimeMillis()
        val items = bills.map { b ->
            val due = com.spendlens.app.parser.BillReminders.nextDueDate(b.dayOfMonth, now, zone)
            BillItem(
                bill = b,
                category = b.categoryId?.let { map[it] },
                dueLabel = due.format(dueFmt),
                daysUntil = com.spendlens.app.parser.BillReminders.daysUntilDue(b.dayOfMonth, now, zone),
            )
        }.sortedBy { it.daysUntil }
        BillsUiState(items, "INR")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BillsUiState())

    fun setReminder(id: Long, enabled: Boolean) = viewModelScope.launch {
        container.billRepository.setReminderEnabled(id, enabled)
    }

    fun delete(id: Long) = viewModelScope.launch { container.billRepository.delete(id) }

    /** Re-run recurring-bill detection over the full debit history. */
    fun rescan() = viewModelScope.launch {
        val debits = container.transactionRepository.allDebits()
        val detected = com.spendlens.app.parser.BillDetector.detect(debits)
        container.billRepository.syncDetected(detected)
    }
}

/** Backs the transaction detail sheet: edit category, toggle expense, view source SMS. */
class TransactionDetailViewModel(private val container: AppContainer) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> =
        container.categoryRepository.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _senderMap = MutableStateFlow<Map<String, String>>(emptyMap())
    val senderMap: StateFlow<Map<String, String>> = _senderMap.asStateFlow()

    init {
        viewModelScope.launch {
            _senderMap.value = container.transactionRepository.topSenderPerAccount()
                .distinctBy { it.accountKey }
                .associate { it.accountKey to it.sender }
        }
    }

    /** Known merchant names for the rename type-ahead. */
    val merchantNames: StateFlow<List<String>> =
        container.merchantRepository.observeDisplayNames()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Distinct known account keys for the "tag with account" picker. Excludes "Unknown". */
    val knownAccountKeys: StateFlow<List<String>> =
        container.transactionRepository.observeAll()
            .map { txns ->
                txns.map { it.accountKey }
                    .filter { it != "Unknown" && it.isNotBlank() }
                    .distinct()
                    .sorted()
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Update the account key for a single transaction. */
    fun updateAccountKey(txn: TransactionEntity, newKey: String) = viewModelScope.launch {
        container.transactionRepository.update(txn.copy(accountKey = newKey))
    }

    /** Gates the per-transaction AI debug section. Off by default (developer aid). */
    val debugInfoEnabled: StateFlow<Boolean> =
        container.settingsStore.appearance
            .map { it.debugInfoEnabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Snapshot of how a transaction relates to the AI auto-categoriser, for the debug section. */
    data class AiDebugInfo(
        val analysed: Boolean,
        val outcome: String,
        val categoryName: String?,
        val viaAiRule: Boolean,
        val model: String,
        /** Exact prompt/response for the AI call that classified/parsed this transaction's SMS, if any. */
        val parsePrompt: String?,
        val parseResponse: String?,
    )

    /**
     * Derive the AI debug state from the transaction's stored flags. The auto-categoriser only ever
     * runs on rows with no category, so an attempted row that now has a category was set by AI.
     */
    suspend fun aiDebug(txn: TransactionEntity): AiDebugInfo {
        val cats = container.categoryRepository.all()
        val categoryName = txn.categoryId?.let { id -> cats.firstOrNull { it.id == id }?.name }
        val viaAiRule = container.categoryRepository.aiRuleCategory(txn.counterparty) != null
        val outcome = when {
            !txn.aiCategorizeAttempted -> "Not analysed by AI (rule/manual or still pending)"
            txn.categoryId != null -> "AI assigned a category"
            else -> "AI analysed but could not categorise"
        }
        val raw = txn.rawSmsId?.let { container.rawSmsDao.getById(it) }
        return AiDebugInfo(
            analysed = txn.aiCategorizeAttempted,
            outcome = outcome,
            categoryName = categoryName,
            viaAiRule = viaAiRule,
            model = container.aiConfigStore.effectiveModel(),
            parsePrompt = raw?.aiPrompt,
            parseResponse = raw?.aiResponse,
        )
    }

    /** Remembered category + expense flag for [name], or null if it isn't a known merchant. */
    private suspend fun matchFor(name: String): MerchantMatch? {
        if (!container.merchantRepository.isKnownMerchant(name)) return null
        val categorizer = container.categoryRepository.categorizer()
        return MerchantMatch(categorizer.categorize(name.trim()), container.merchantRepository.isExcluded(name))
    }

    fun update(txn: TransactionEntity) = viewModelScope.launch {
        container.transactionRepository.update(txn)
    }

    fun createCategory(name: String, icon: String, color: Long, onCreated: (Long) -> Unit) =
        viewModelScope.launch {
            val id = container.categoryRepository.createCategory(name, icon, color)
            onCreated(id)
        }

    /**
     * Rename a merchant. [applyToAll] = true renames every transaction from this merchant and
     * remembers it for future ones; false touches only this transaction. When [newName] is an
     * already-known merchant, its remembered category and expense flag are applied too. [onApplied]
     * reports the match (if any) so the sheet can reflect the auto-changed category/expense flag.
     */
    fun renameMerchant(
        txn: TransactionEntity,
        newName: String,
        applyToAll: Boolean,
        onApplied: (MerchantMatch?) -> Unit = {},
    ) = viewModelScope.launch {
        val trimmed = newName.trim()
        val match = matchFor(trimmed)
        if (applyToAll) {
            container.merchantRepository.setUserName(txn.counterparty, trimmed)
            container.transactionRepository.renameCounterparty(txn.counterparty, trimmed)
            match?.categoryId?.let { container.transactionRepository.setCategoryForCounterparty(trimmed, it) }
            match?.let { container.transactionRepository.setExcludedForCounterparty(trimmed, it.excluded) }
        } else {
            container.transactionRepository.update(
                txn.copy(
                    counterparty = trimmed,
                    categoryId = match?.categoryId ?: txn.categoryId,
                    excludedFromExpense = match?.excluded ?: txn.excludedFromExpense,
                ),
            )
        }
        onApplied(match)
    }

    /**
     * Set the category. [txn] already carries the new categoryId. [applyToAll] = true also
     * re-categorises every other transaction from this merchant and remembers it for future ones.
     */
    fun setCategory(txn: TransactionEntity, categoryId: Long, applyToAll: Boolean) = viewModelScope.launch {
        container.transactionRepository.update(txn)
        if (applyToAll) {
            container.transactionRepository.setCategoryForCounterparty(txn.counterparty, categoryId)
            container.categoryRepository.addUserRule(txn.counterparty, categoryId)
        }
    }

    /**
     * Save the note/tags carried by [txn]. The note is always per-transaction; [applyToAll] = true
     * additionally copies the tags to every transaction from this merchant and remembers them.
     */
    fun setTags(txn: TransactionEntity, applyToAll: Boolean) = viewModelScope.launch {
        container.transactionRepository.update(txn)
        if (applyToAll) {
            container.transactionRepository.setTagsForCounterparty(txn.counterparty, txn.tags)
            container.merchantRepository.setUserTags(txn.counterparty, txn.tags)
        }
    }

    /** Observe the split children of [parentId] (issue #11). Empty when the txn isn't split. */
    fun splitsFlow(parentId: Long): kotlinx.coroutines.flow.Flow<List<com.spendlens.app.data.db.TransactionSplitEntity>> =
        container.transactionRepository.observeSplits(parentId)

    /**
     * Split [parent] across categories. [parts] are (categoryId, displayAmountMinor) pairs summing
     * to the parent total. [onDone] receives the parent with [isSplit] set so the sheet can refresh.
     */
    fun splitTransaction(
        parent: TransactionEntity,
        parts: List<Pair<Long?, Long>>,
        onDone: (TransactionEntity) -> Unit = {},
    ) = viewModelScope.launch {
        container.transactionRepository.splitTransaction(parent, parts)
        onDone(parent.copy(isSplit = true))
    }

    fun clearSplit(parent: TransactionEntity, onDone: (TransactionEntity) -> Unit = {}) = viewModelScope.launch {
        container.transactionRepository.clearSplit(parent)
        onDone(parent.copy(isSplit = false))
    }

    suspend fun smsBody(rawSmsId: Long?): String? =
        rawSmsId?.let { container.rawSmsDao.getById(it)?.body }

    suspend fun rawSms(rawSmsId: Long?): RawSmsEntity? =
        rawSmsId?.let { container.rawSmsDao.getById(it) }

    /** Shared AI orchestration (flag check, OpenRouter call, pattern apply). */
    private val teacher = com.spendlens.app.ai.AiPatternTeacher(container)

    /** Build the "Teach with AI" prompt for a raw SMS, seeded with known categories/merchants. */
    suspend fun generatePrompt(smsList: List<RawSmsEntity>): String = teacher.generatePrompt(smsList)

    /**
     * Flag-gated AI teach for a single SMS. Returns [AiPatternTeacher.TeachResult.Fallback] when AI
     * is off or unconfigured so the caller keeps the copy-to-clipboard flow.
     */
    suspend fun teachWithAi(smsList: List<RawSmsEntity>): com.spendlens.app.ai.AiPatternTeacher.TeachResult =
        teacher.teach(smsList)

    /** Copy a picked image into encrypted storage and link it to the transaction. */
    fun attachReceipt(txn: TransactionEntity, uri: android.net.Uri, onDone: (TransactionEntity) -> Unit) =
        viewModelScope.launch {
            val path = container.receiptStore.save(txn.id, uri)
            val updated = txn.copy(receiptUri = path)
            container.transactionRepository.update(updated)
            onDone(updated)
        }

    fun removeReceipt(txn: TransactionEntity, onDone: (TransactionEntity) -> Unit) = viewModelScope.launch {
        txn.receiptUri?.let { container.receiptStore.delete(it) }
        val updated = txn.copy(receiptUri = null)
        container.transactionRepository.update(updated)
        onDone(updated)
    }

    suspend fun loadReceipt(path: String): android.graphics.Bitmap? = container.receiptStore.loadBitmap(path)
}

/**
 * Backs the manual (cash / non-SMS) transaction entry & edit form. Persists through
 * [TransactionRepository.addManual]/[updateManual] so manual rows skip the SMS pipeline and
 * count in totals identically to parsed rows (PRD FR-A/FR-B).
 */
class ManualEntryViewModel(private val container: AppContainer) : ViewModel() {

    val categories: StateFlow<List<CategoryEntity>> =
        container.categoryRepository.observeCategories()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Known merchant names for the description/merchant type-ahead. */
    val merchantNames: StateFlow<List<String>> =
        container.merchantRepository.observeDisplayNames()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Remembered category + expense flag for [name], or null if it isn't a known merchant — used
     * to auto-fill the form when the user picks/types a known merchant.
     */
    suspend fun resolveMerchant(name: String): MerchantMatch? {
        if (!container.merchantRepository.isKnownMerchant(name)) return null
        val categorizer = container.categoryRepository.categorizer()
        return MerchantMatch(categorizer.categorize(name.trim()), container.merchantRepository.isExcluded(name))
    }

    /** Accounts seen so far plus a "Cash" default, for the account picker. */
    val accounts: StateFlow<List<String>> =
        container.transactionRepository.observeAll()
            .map { txns -> (listOf(CASH_ACCOUNT) + txns.map { it.accountKey }).distinct().sorted() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf(CASH_ACCOUNT))

    /** Currencies the app can convert to the base currency. Base ("INR") first. */
    val currencies: List<String> =
        listOf(com.spendlens.app.parser.CurrencyConverter.BASE) +
            (com.spendlens.app.data.fx.FxRepository.BUNDLED.keys - com.spendlens.app.parser.CurrencyConverter.BASE).sorted()

    val baseCurrency: String get() = com.spendlens.app.parser.CurrencyConverter.BASE

    suspend fun loadById(id: Long): TransactionEntity? = container.transactionRepository.getById(id)

    fun createCategory(name: String, icon: String, color: Long, onCreated: (Long) -> Unit) =
        viewModelScope.launch {
            onCreated(container.categoryRepository.createCategory(name, icon, color))
        }

    /**
     * Save a manual entry. [editing] = null adds a new row; otherwise the existing row is updated
     * in place (same id, no duplicate). [amountBaseMinor] is recomputed from the live FX rates.
     */
    fun save(
        editing: TransactionEntity?,
        amountMinor: Long,
        currency: String,
        direction: String,
        accountKey: String,
        counterparty: String,
        occurredAt: Long,
        categoryId: Long,
        note: String?,
        tags: String?,
        excludedFromExpense: Boolean,
        onDone: () -> Unit,
    ) = viewModelScope.launch {
        val rates = container.fxRepository.ratesToBase()
        if (editing == null) {
            container.transactionRepository.addManual(
                amountMinor = amountMinor,
                currency = currency,
                direction = direction,
                accountKey = accountKey,
                counterparty = counterparty,
                occurredAt = occurredAt,
                categoryId = categoryId,
                note = note,
                tags = tags,
                receiptUri = null,
                excludedFromExpense = excludedFromExpense,
                ratesToBase = rates,
            )
        } else {
            container.transactionRepository.updateManual(
                editing.copy(
                    amountMinor = amountMinor,
                    currency = currency,
                    direction = direction,
                    accountKey = accountKey,
                    counterparty = counterparty,
                    occurredAt = occurredAt,
                    categoryId = categoryId,
                    note = note,
                    tags = tags,
                    excludedFromExpense = excludedFromExpense,
                ),
                rates,
            )
        }
        onDone()
    }

    /** Delete a manual transaction by id (FR-C lifecycle). Reverts totals automatically. */
    fun delete(id: Long, onDone: () -> Unit) = viewModelScope.launch {
        container.transactionRepository.delete(id)
        onDone()
    }

    /**
     * Get category suggestions for a merchant name.
     * Returns top 3 suggestions sorted by confidence, using local pattern matching.
     */
    suspend fun suggestCategoriesForMerchant(merchantName: String): List<com.spendlens.app.ai.CategorySuggestion> {
        val recentTxns = container.transactionRepository.allTransactions().takeLast(100)
        val cats = container.categoryRepository.all()
        return com.spendlens.app.ai.ManualEntryCategorySuggester.suggestLocally(merchantName, recentTxns, cats)
    }

    companion object {
        const val CASH_ACCOUNT = "Cash"
    }
}

/**
 * Backs Settings → Merchants: lists every known merchant with its remembered category, expense
 * flag, emoji, tags and matched raw tokens, and persists edits across the alias/rule/transaction
 * tables so corrections stick and apply to past and future transactions.
 */
@OptIn(FlowPreview::class)
class MerchantsViewModel(private val container: AppContainer) : ViewModel() {
    private val _rawQuery = MutableStateFlow("")

    /** Immediate value — used by the TextField so the UI stays responsive on each keystroke. */
    val displayQuery: StateFlow<String> = _rawQuery.asStateFlow()

    /** Debounced value — drives the merchant list filter so the list only recomputes after the user pauses. */
    private val query: StateFlow<String> = _rawQuery
        .debounce(300L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun setQuery(q: String) { _rawQuery.value = q }

    /** Progress/result of the flag-gated AI "consolidate merchant names" action. */
    sealed interface ConsolidationState {
        data object Idle : ConsolidationState
        data object Running : ConsolidationState
        /** AI is off or no API key is configured. */
        data object Disabled : ConsolidationState
        /** Auto-applied [merged] renames across [groups] brand groups. */
        data class Done(val merged: Int, val groups: Int) : ConsolidationState
        data class Error(val message: String) : ConsolidationState
    }

    private val _consolidation = MutableStateFlow<ConsolidationState>(ConsolidationState.Idle)
    val consolidation: StateFlow<ConsolidationState> = _consolidation.asStateFlow()

    /** True when the AI flag is on with a usable key (controls the consolidate button). */
    fun aiUsable(): Boolean = container.aiConfigStore.isUsable()

    /** Reset the consolidation state after the UI has shown the outcome. */
    fun consumeConsolidation() { _consolidation.value = ConsolidationState.Idle }

    /**
     * Flag-gated AI merchant-name consolidation. Sends every known merchant name to OpenRouter, asks
     * it to group obvious variants of the same brand, and (per the chosen "auto-apply" behavior)
     * immediately merges each variant into its canonical name by renaming — which collapses them on
     * this screen. When AI is off/unconfigured it reports [ConsolidationState.Disabled].
     */
    fun consolidateWithAi() = viewModelScope.launch {
        val store = container.aiConfigStore
        val key = store.effectiveKey()
        if (!store.isEnabled() || key == null) {
            com.spendlens.app.util.AppLog.aiSkipped("merchant_consolidation", "ai_disabled_or_no_key")
            _consolidation.value = ConsolidationState.Disabled
            return@launch
        }
        _consolidation.value = ConsolidationState.Running
        val names = container.merchantRepository.observeDisplayNames().first()
        if (names.size < 2) {
            com.spendlens.app.util.AppLog.aiSkipped("merchant_consolidation", "fewer_than_two_merchants")
            _consolidation.value = ConsolidationState.Done(merged = 0, groups = 0)
            return@launch
        }
        val prompt = com.spendlens.app.ai.MerchantConsolidation.buildPrompt(names)
        when (
            val r = container.openRouterClient.complete(
                key,
                store.effectiveModel(),
                prompt,
                operation = "merchant_consolidation",
            )
        ) {
            is com.spendlens.app.ai.OpenRouterClient.Result.Failure ->
                _consolidation.value = ConsolidationState.Error(r.message)
            is com.spendlens.app.ai.OpenRouterClient.Result.Success -> {
                val groups = com.spendlens.app.ai.MerchantConsolidation.parse(r.content)
                val known = names.toMutableSet()
                var merged = 0
                var appliedGroups = 0
                for (g in groups) {
                    var groupMerged = false
                    for (alias in g.aliases) {
                        if (alias == g.canonical || alias !in known) continue
                        container.merchantRepository.renameDisplay(alias, g.canonical)
                        container.transactionRepository.renameCounterparty(alias, g.canonical)
                        known.remove(alias)
                        merged++
                        groupMerged = true
                    }
                    if (groupMerged) appliedGroups++
                }
                com.spendlens.app.util.AppLog.aiApplied(
                    "merchant_consolidation",
                    "merged=$merged groups=$appliedGroups parsed_groups=${groups.size}",
                )
                _consolidation.value = ConsolidationState.Done(merged = merged, groups = appliedGroups)
            }
        }
    }

    val state: StateFlow<MerchantsUiState> = combine(
        container.merchantRepository.observeAll(),
        container.categoryRepository.observeCategories(),
        container.categoryRepository.observeRules(),
        query,
    ) { aliases, categories, rules, q ->
        val categorizer = Categorizer(rules.map { Categorizer.Rule(it.matcher, it.categoryId) })
        val rows = aliases
            .groupBy { it.displayName }
            .map { (name, group) ->
                MerchantRow(
                    displayName = name,
                    emoji = group.firstNotNullOfOrNull { it.logoEmoji },
                    categoryId = categorizer.categorize(name),
                    excluded = group.any { it.excludedFromExpense },
                    tags = group.firstNotNullOfOrNull { it.tags },
                    tokens = group.map { it.rawKey }.sorted(),
                )
            }
            .filter { q.isBlank() || it.displayName.contains(q, ignoreCase = true) }
            .sortedBy { it.displayName.lowercase() }
        MerchantsUiState(rows, categories, q)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MerchantsUiState())

    /**
     * Persist edits to a merchant. Renames propagate to the alias rows and existing transactions;
     * category/expense/tags are remembered (for future parses) and stamped onto existing rows.
     */
    fun save(
        original: MerchantRow,
        newName: String,
        categoryId: Long?,
        excluded: Boolean,
        emoji: String?,
        tags: String?,
    ) = viewModelScope.launch {
        val target = newName.trim().ifBlank { original.displayName }
        if (target != original.displayName) {
            container.merchantRepository.renameDisplay(original.displayName, target)
            container.transactionRepository.renameCounterparty(original.displayName, target)
        }
        if (categoryId != null) {
            container.categoryRepository.addUserRule(target, categoryId)
            container.transactionRepository.setCategoryForCounterparty(target, categoryId)
        }
        container.merchantRepository.setExcluded(target, excluded)
        container.transactionRepository.setExcludedForCounterparty(target, excluded)
        container.merchantRepository.setMerchantEmoji(target, emoji?.trim()?.ifBlank { null })
        val normTags = tags?.split(",")?.map { it.trim().lowercase() }
            ?.filter { it.isNotEmpty() }?.distinct()?.joinToString(",")?.ifBlank { null }
        container.merchantRepository.setUserTags(target, normTags)
        container.transactionRepository.setTagsForCounterparty(target, normTags)
    }

    /** Remove one raw-token → merchant mapping. */
    fun deleteToken(rawKey: String) = viewModelScope.launch {
        container.merchantRepository.deleteAlias(rawKey)
    }
}

// ---------- Savings goals (issue #12) ----------

data class GoalItem(
    val goal: com.spendlens.app.data.db.SavingsGoalEntity,
    val savedMinor: Long,
    val progress: Float,              // 0f..1f
    val reached: Boolean,
    /** Projected completion (epoch millis) from the current savings pace, or null if not derivable. */
    val projectedCompletion: Long?,
)

data class GoalsUiState(
    val goals: List<GoalItem> = emptyList(),
    val currency: String = "INR",
    /** Account keys the user can link a goal to (auto-tracking CREDITs into them). */
    val accounts: List<String> = emptyList(),
)

class GoalsViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.savingsGoalRepository

    val state: StateFlow<GoalsUiState> = combine(
        repo.observeAll(),
        container.transactionRepository.observeAll(),
    ) { goals, txns ->
        val accounts = txns.map { it.accountKey }.filter { it.isNotBlank() }.distinct().sorted()
        val items = goals.map { g ->
            val auto = g.linkedAccountKey?.let { key ->
                txns.filter { it.direction == "CREDIT" && it.accountKey == key && it.occurredAt >= g.createdAt }
                    .sumOf { it.amountBaseMinor }
            } ?: 0L
            val saved = g.savedManualMinor + auto
            val progress = if (g.targetAmountMinor > 0) (saved.toFloat() / g.targetAmountMinor).coerceIn(0f, 1f) else 0f
            GoalItem(
                goal = g,
                savedMinor = saved,
                progress = progress,
                reached = saved >= g.targetAmountMinor,
                projectedCompletion = projectCompletion(g, saved),
            )
        }
        GoalsUiState(goals = items, currency = "INR", accounts = accounts)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GoalsUiState())

    private fun projectCompletion(goal: com.spendlens.app.data.db.SavingsGoalEntity, saved: Long): Long? {
        if (saved >= goal.targetAmountMinor || saved <= 0L) return null
        val now = System.currentTimeMillis()
        val elapsedMs = (now - goal.createdAt).coerceAtLeast(DAY_MS)
        val ratePerMs = saved.toDouble() / elapsedMs
        if (ratePerMs <= 0.0) return null
        val msLeft = ((goal.targetAmountMinor - saved) / ratePerMs).toLong()
        return now + msLeft
    }

    fun createGoal(name: String, targetAmountMinor: Long, deadline: Long?, linkedAccountKey: String?) =
        viewModelScope.launch { repo.createGoal(name, targetAmountMinor, deadline, linkedAccountKey) }

    fun deleteGoal(id: Long) = viewModelScope.launch { repo.delete(id) }

    fun addContribution(id: Long, deltaMinor: Long) = viewModelScope.launch { repo.addContribution(id, deltaMinor) }

    private companion object {
        const val DAY_MS = 86_400_000L
    }
}

// ---------- Sender classifications ----------

data class SenderRow(
    val sender: String,
    val isFinancial: Boolean,
    val source: String,
    val classifiedAt: Long,
)

data class SendersUiState(
    val items: List<SenderRow> = emptyList(),
    val query: String = "",
    val filterFinancialOnly: Boolean = false,
)

@OptIn(FlowPreview::class)
class SenderClassificationsViewModel(private val container: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val displayQuery: StateFlow<String> = _query.asStateFlow()
    private val debouncedQuery = _query
        .debounce(300L)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    private val _financialOnly = MutableStateFlow(false)

    fun setQuery(q: String) { _query.value = q }
    fun setFinancialOnly(v: Boolean) { _financialOnly.value = v }

    val state: StateFlow<SendersUiState> = combine(
        container.senderClassificationDao.observeAll(),
        debouncedQuery,
        _financialOnly,
    ) { all, q, financialOnly ->
        val items = all
            .filter { if (financialOnly) it.isFinancial else true }
            .filter { q.isBlank() || it.sender.contains(q, ignoreCase = true) }
            .map { SenderRow(it.sender, it.isFinancial, it.source, it.classifiedAt) }
        SendersUiState(items, q, financialOnly)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SendersUiState())
}

// ---------- Factory ----------

class SpendLensViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val vm: ViewModel = when {
            modelClass.isAssignableFrom(DashboardViewModel::class.java) -> DashboardViewModel(container)
            modelClass.isAssignableFrom(TransactionsViewModel::class.java) -> TransactionsViewModel(container)
            modelClass.isAssignableFrom(AccountsViewModel::class.java) -> AccountsViewModel(container)
            modelClass.isAssignableFrom(AnalyticsViewModel::class.java) -> AnalyticsViewModel(container)
            modelClass.isAssignableFrom(ReviewViewModel::class.java) -> ReviewViewModel(container)
            modelClass.isAssignableFrom(SettingsViewModel::class.java) -> SettingsViewModel(container)
            modelClass.isAssignableFrom(MerchantsViewModel::class.java) -> MerchantsViewModel(container)
            modelClass.isAssignableFrom(CategoriesViewModel::class.java) -> CategoriesViewModel(container)
            modelClass.isAssignableFrom(BudgetsViewModel::class.java) -> BudgetsViewModel(container)
            modelClass.isAssignableFrom(BillsViewModel::class.java) -> BillsViewModel(container)
            modelClass.isAssignableFrom(GoalsViewModel::class.java) -> GoalsViewModel(container)
            modelClass.isAssignableFrom(TransactionDetailViewModel::class.java) -> TransactionDetailViewModel(container)
            modelClass.isAssignableFrom(ManualEntryViewModel::class.java) -> ManualEntryViewModel(container)
            modelClass.isAssignableFrom(SenderClassificationsViewModel::class.java) -> SenderClassificationsViewModel(container)
            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
        return vm as T
    }
}
