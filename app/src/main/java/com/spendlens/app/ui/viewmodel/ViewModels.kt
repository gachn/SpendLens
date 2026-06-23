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
import com.spendlens.app.data.prefs.AppearancePrefs
import com.spendlens.app.data.prefs.ThemeMode
import com.spendlens.app.di.AppContainer
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.spendlens.app.ui.util.Dates
import androidx.work.WorkManager
import com.spendlens.app.work.SmsSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

// ---------- UI state models ----------

data class CategorySlice(val name: String, val color: Color, val amountMinor: Long)

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
)

data class MonthlyBar(val label: String, val debitMinor: Long, val creditMinor: Long)

data class AnalyticsUiState(
    val months: List<MonthlyBar> = emptyList(),
    val slices: List<CategorySlice> = emptyList(),
    val topMerchants: List<CategorySlice> = emptyList(),
    val currency: String = "INR",
    val selectedMonth: YearMonth = YearMonth.now(),
    val monthOptions: List<YearMonth> = emptyList(),
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
)

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

class DashboardViewModel(container: AppContainer) : ViewModel() {
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
        ) { spend, income, monthTxns, cats, categories ->
            val map = categories.associateBy { it.id }
            val slices = cats.mapNotNull { ct ->
                if (ct.categoryId == null) {
                    CategorySlice("Uncategorized", Color(0xFF9E9E9EL), ct.total)
                } else {
                    map[ct.categoryId]?.let { CategorySlice(it.name, Color(it.color), ct.total) }
                }
            }
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
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardUiState(monthOptions = monthOptions))
}

class TransactionsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.transactionRepository
    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(TxnFilters())

    fun setQuery(q: String) { query.value = q }
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
        query,
        filters,
    ) { all, categories, q, f ->
        val (from, to) = dateBounds(f.dateRange)
        val filtered = all.filter { txn ->
            (when (f.direction) {
                TxnFilter.ALL -> true
                TxnFilter.DEBIT -> txn.direction == "DEBIT"
                TxnFilter.CREDIT -> txn.direction == "CREDIT"
            }) &&
                (f.categoryId == null || txn.categoryId == f.categoryId) &&
                (f.accountKey == null || txn.accountKey == f.accountKey) &&
                (from == null || txn.occurredAt >= from) &&
                (to == null || txn.occurredAt <= to) &&
                (f.minAmountMinor == null || txn.amountMinor >= f.minAmountMinor) &&
                (f.maxAmountMinor == null || txn.amountMinor <= f.maxAmountMinor) &&
                matchesQuery(txn, q)
        }
        val accounts = all.map { it.accountKey }.distinct().sorted()
        TransactionsUiState(filtered, categories.associateBy { it.id }, q, f, accounts)
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
    val totalDebitMinor: Long,
    val totalCreditMinor: Long,
    val txnCount: Int,
    val lastActivityAt: Long,
    val transactions: List<TransactionEntity>,
    /** Latest credit-card statement, if this is a card and a bill SMS was parsed. */
    val billTotalDueMinor: Long? = null,
    val billMinDueMinor: Long? = null,
    val billDueDate: Long? = null,
) {
    /** Active in the selected month, or a card carrying an outstanding bill. */
    val hasActivity: Boolean get() = txnCount > 0 || billTotalDueMinor != null
}

data class AccountsUiState(
    val bankAccounts: List<AccountSummary> = emptyList(),
    val cards: List<AccountSummary> = emptyList(),
    val categories: Map<Long, CategoryEntity> = emptyMap(),
    val selectedMonth: YearMonth = YearMonth.now(),
    val months: List<YearMonth> = emptyList(),
)

class AccountsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.transactionRepository
    private val monthOptions = Dates.recentMonths(12)
    private val selectedMonth = MutableStateFlow(monthOptions.first())

    fun setMonth(ym: YearMonth) { selectedMonth.value = ym }

    val state: StateFlow<AccountsUiState> = combine(
        repo.observeAll(),
        repo.observeAccountBalances(),
        container.categoryRepository.observeCategories(),
        container.cardBillDao.observeAll(),
        selectedMonth,
    ) { txns, balances, cats, bills, month ->
        val (start, end) = Dates.monthRange(month)
        val balanceByKey = balances.associate { it.accountKey to it.balanceMinor }
        val billByCard = bills.associateBy { it.cardKey }
        // Group across all history so every account stays visible; scope totals to the month.
        val summaries = txns
            .filter { !it.isDuplicate }
            .groupBy { it.accountKey }
            .map { (key, all) ->
                // Representative channel = the one most transactions used.
                val topChannel = all.groupingBy { it.channel }.eachCount()
                    .maxByOrNull { it.value }?.key.orEmpty()
                val monthTxns = all
                    .filter { it.occurredAt in start until end }
                    .sortedByDescending { it.occurredAt }
                val bill = billByCard[key]
                AccountSummary(
                    accountKey = key,
                    channel = topChannel,
                    isCard = topChannel.equals("CARD", ignoreCase = true),
                    balanceMinor = balanceByKey[key],
                    totalDebitMinor = monthTxns.filter { it.direction == "DEBIT" }.sumOf { it.amountBaseMinor },
                    totalCreditMinor = monthTxns.filter { it.direction == "CREDIT" }.sumOf { it.amountBaseMinor },
                    txnCount = monthTxns.size,
                    lastActivityAt = all.maxOf { it.occurredAt },
                    transactions = monthTxns,
                    billTotalDueMinor = bill?.totalDueMinor,
                    billMinDueMinor = bill?.minDueMinor,
                    billDueDate = bill?.dueDate,
                )
            }
            .sortedByDescending { it.lastActivityAt }
        AccountsUiState(
            bankAccounts = summaries.filter { !it.isCard },
            cards = summaries.filter { it.isCard },
            categories = cats.associateBy { it.id },
            selectedMonth = month,
            months = monthOptions,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountsUiState())
}

class AnalyticsViewModel(container: AppContainer) : ViewModel() {
    private val repo = container.transactionRepository
    private val zone: ZoneId = ZoneId.systemDefault()
    private val monthOptions = Dates.recentMonths(12)
    private val selectedMonth = MutableStateFlow(monthOptions.first())

    fun setMonth(ym: YearMonth) { selectedMonth.value = ym }

    val state: StateFlow<AnalyticsUiState> = combine(
        repo.observeAll(),
        container.categoryRepository.observeCategories(),
        selectedMonth,
    ) { txns, categories, breakdownMonth ->
        val map = categories.associateBy { it.id }
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

        // Selected-month category + merchant breakdown.
        val inMonth = spendable.filter {
            YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) == breakdownMonth && it.direction == "DEBIT"
        }
        val slices = inMonth.groupBy { it.categoryId }
            .mapNotNull { (catId, list) ->
                val total = list.sumOf { t -> t.amountBaseMinor }
                if (catId == null) {
                    CategorySlice("Uncategorized", Color(0xFF9E9E9EL), total)
                } else {
                    map[catId]?.let { CategorySlice(it.name, Color(it.color), total) }
                }
            }.sortedByDescending { it.amountMinor }
        val palette = listOf(0xFF0E7C66, 0xFF42A5F5, 0xFFEC407A, 0xFFFFB300, 0xFFAB47BC)
        val merchants = inMonth.groupBy { it.counterparty }
            .map { (name, list) -> name to list.sumOf { it.amountBaseMinor } }
            .sortedByDescending { it.second }.take(5)
            .mapIndexed { i, (name, total) -> CategorySlice(name, Color(palette[i % palette.size]), total) }

        AnalyticsUiState(months, slices, merchants, currency, breakdownMonth, monthOptions)
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

    /** Re-run filter + patterns over the UNPARSED backlog (e.g. after new builtin patterns ship). */
    fun reprocessUnparsed() = viewModelScope.launch {
        container.smsProcessor.reprocessUnparsed()
    }
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    val patterns: StateFlow<List<SmsPatternEntity>> =
        container.patternRepository.observeAll()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // ----- Appearance -----

    val appearance: StateFlow<AppearancePrefs> = container.settingsStore.appearance

    fun setThemeMode(mode: ThemeMode) = container.settingsStore.setThemeMode(mode)

    fun setDynamicColor(enabled: Boolean) = container.settingsStore.setDynamicColor(enabled)

    val security: StateFlow<com.spendlens.app.data.prefs.SecurityPrefs> = container.settingsStore.security

    fun setAppLockEnabled(enabled: Boolean) = container.settingsStore.setAppLockEnabled(enabled)

    fun setGracePeriod(seconds: Int) = container.settingsStore.setGracePeriodSec(seconds)

    fun setPatternEnabled(id: Long, enabled: Boolean) = viewModelScope.launch {
        container.patternRepository.setEnabled(id, enabled)
    }

    fun deletePattern(id: Long) = viewModelScope.launch { container.patternRepository.delete(id) }

    fun clearAllPatterns() = viewModelScope.launch { container.patternRepository.clearAll() }

    fun reimport(context: android.content.Context) = SmsSyncWorker.enqueueImport(context)

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

    val state: StateFlow<BudgetsUiState> = combine(
        container.budgetRepository.observeAll(),
        container.categoryRepository.observeCategories(),
        container.transactionRepository.observeCategoryTotals(range.first, range.second),
    ) { budgets, categories, totals ->
        val spentByCat = totals.associate { it.categoryId to it.total }
        val limitByCat = budgets.associate { it.categoryId to it.monthlyLimitMinor }
        val rows = categories
            .map { cat -> BudgetRow(cat, limitByCat[cat.id] ?: 0L, spentByCat[cat.id] ?: 0L) }
            // Budgeted categories first, then by spend.
            .sortedWith(compareByDescending<BudgetRow> { it.limitMinor > 0 }.thenByDescending { it.spentMinor })
        BudgetsUiState(rows = rows, currency = "INR")
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetsUiState())

    fun setBudget(categoryId: Long, limitMinor: Long) = viewModelScope.launch {
        container.budgetRepository.setBudget(categoryId, limitMinor)
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
     * remembers it for future ones; false touches only this transaction.
     */
    fun renameMerchant(txn: TransactionEntity, newName: String, applyToAll: Boolean) = viewModelScope.launch {
        if (applyToAll) {
            container.merchantRepository.setUserName(txn.counterparty, newName)
            container.transactionRepository.renameCounterparty(txn.counterparty, newName.trim())
        } else {
            container.transactionRepository.update(txn.copy(counterparty = newName.trim()))
        }
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

    suspend fun smsBody(rawSmsId: Long?): String? =
        rawSmsId?.let { container.rawSmsDao.getById(it)?.body }

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
                ),
                rates,
            )
        }
        onDone()
    }

    companion object {
        const val CASH_ACCOUNT = "Cash"
    }
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
            modelClass.isAssignableFrom(CategoriesViewModel::class.java) -> CategoriesViewModel(container)
            modelClass.isAssignableFrom(BudgetsViewModel::class.java) -> BudgetsViewModel(container)
            modelClass.isAssignableFrom(BillsViewModel::class.java) -> BillsViewModel(container)
            modelClass.isAssignableFrom(TransactionDetailViewModel::class.java) -> TransactionDetailViewModel(container)
            modelClass.isAssignableFrom(ManualEntryViewModel::class.java) -> ManualEntryViewModel(container)
            else -> error("Unknown ViewModel: ${modelClass.name}")
        }
        return vm as T
    }
}
