package com.spendlens.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.GlassCard
import com.spendlens.app.ui.components.MonthDropdown
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.ui.components.SummaryStat
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.AccountSummary
import com.spendlens.app.ui.viewmodel.AccountsViewModel

@Composable
fun AccountsScreen(vm: AccountsViewModel, onTransactionClick: (TransactionEntity) -> Unit = {}) {
    val state by vm.state.collectAsState()
    var openKey by remember { mutableStateOf<String?>(null) }
    var showEmpty by remember { mutableStateOf(false) }
    val open = openKey?.let { key ->
        (state.bankAccounts + state.cards).firstOrNull { it.accountKey == key }
    }

    if (open != null) {
        BackHandler { openKey = null }
        AccountDetail(
            account = open,
            monthLabel = Dates.label(state.selectedMonth),
            categories = state.categories,
            onBack = { openKey = null },
            onTransactionClick = onTransactionClick,
        )
        return
    }

    val banks = if (showEmpty) state.bankAccounts else state.bankAccounts.filter { it.hasActivity }
    val cards = if (showEmpty) state.cards else state.cards.filter { it.hasActivity }
    val hiddenCount = (state.bankAccounts.count { !it.hasActivity }) + (state.cards.count { !it.hasActivity })

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Debit by month", style = MaterialTheme.typography.titleMedium)
            MonthDropdown(state.selectedMonth, state.months, vm::setMonth)
        }
        if (hiddenCount > 0) {
            TextButton(
                onClick = { showEmpty = !showEmpty },
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Text(if (showEmpty) "Hide $hiddenCount inactive" else "Show $hiddenCount inactive")
            }
        }

        if (banks.isEmpty() && cards.isEmpty()) {
            EmptyHint(
                if (state.bankAccounts.isEmpty() && state.cards.isEmpty()) {
                    "No accounts yet. They appear once transactions are parsed."
                } else {
                    "No account activity in ${Dates.label(state.selectedMonth)}."
                },
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { NetLiquidBalanceCard(bankAccounts = state.bankAccounts, cards = state.cards) }
                if (banks.isNotEmpty()) {
                    item { SectionHeader("Bank accounts") }
                    items(banks) { acct -> BankAccountRow(acct, onClick = { openKey = acct.accountKey }) }
                }
                if (cards.isNotEmpty()) {
                    item { SectionHeader("Credit cards") }
                    items(cards) { acct -> CreditCardRow(acct, onClick = { openKey = acct.accountKey }) }
                }
                smartInsight(state.cards)?.let { insight ->
                    item { SmartInsightModule(insight) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Net Liquid Balance — hero summary card (mock: "Net Liquid Balance")
// ---------------------------------------------------------------------------

@Composable
private fun NetLiquidBalanceCard(bankAccounts: List<AccountSummary>, cards: List<AccountSummary>) {
    val cashMinor = bankAccounts.mapNotNull { it.balanceMinor }.sum()
    val outstandingMinor = cards.mapNotNull { it.billTotalDueMinor }.sum()
    val netMinor = cashMinor - outstandingMinor
    val hasCash = bankAccounts.any { it.balanceMinor != null }
    val hasOutstanding = cards.any { it.billTotalDueMinor != null }
    if (!hasCash && !hasOutstanding) return

    GlassCard {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "NET LIQUID BALANCE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            Text(
                Money.format(netMinor, "INR"),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (hasCash) {
                    SummaryStat(
                        label = "Total cash",
                        value = Money.format(cashMinor, "INR"),
                        accent = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (hasOutstanding) {
                    SummaryStat(
                        label = "Outstanding",
                        value = Money.format(outstandingMinor, "INR"),
                        accent = SpendLensTheme.colors.debit,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Bank account row — icon tile + name + balance (mock: Bank Accounts list)
// ---------------------------------------------------------------------------

@Composable
private fun BankAccountRow(acct: AccountSummary, onClick: () -> Unit) {
    GlassCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.size(48.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AccountBalance,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    acct.accountKey,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "${acct.channel.ifBlank { "BANK" }} · ${acct.txnCount} txns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    acct.balanceMinor?.let { Money.format(it, "INR") }
                        ?: ("-" + Money.format(acct.totalDebitMinor, "INR")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (acct.balanceMinor != null) {
                        acct.balanceUpdatedAt?.let { "Balance · ${Dates.date(it)}" } ?: "Balance"
                    } else "Debit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Credit card row — card visual + accent border + outstanding (mock: Cards)
// ---------------------------------------------------------------------------

@Composable
private fun CreditCardRow(acct: AccountSummary, onClick: () -> Unit) {
    val accent = SpendLensTheme.colors.debit
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Left accent strip
            Box(
                Modifier.width(4.dp).height(72.dp).background(accent),
            )
            Row(
                Modifier.weight(1f).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Mini card visual
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(width = 56.dp, height = 36.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.CreditCard,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        acct.accountKey,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${acct.channel.ifBlank { "CARD" }} · ${acct.txnCount} txns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    val outstanding = acct.billTotalDueMinor
                    Text(
                        outstanding?.let { Money.format(it, "INR") }
                            ?: ("-" + Money.format(acct.totalDebitMinor, "INR")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                    )
                    Text(
                        acct.billDueDate?.let { "Due ${Dates.date(it)}" }
                            ?: if (outstanding != null) "Outstanding" else "Debit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Smart Insight — gradient-border AI module (mock: Smart Suggestion)
// ---------------------------------------------------------------------------

private data class Insight(val text: String)

private fun smartInsight(cards: List<AccountSummary>): Insight? {
    val due = cards.filter { it.billTotalDueMinor != null && it.billDueDate != null }
        .maxByOrNull { it.billTotalDueMinor!! } ?: return null
    return Insight(
        "Pay off the ${due.accountKey} balance of " +
            "${Money.format(due.billTotalDueMinor!!, "INR")} before " +
            "${Dates.date(due.billDueDate!!)} to avoid interest charges.",
    )
}

@Composable
private fun SmartInsightModule(insight: Insight) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.primary,
                    ),
                ),
            )
            .padding(1.5.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.5.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "SMART SUGGESTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    insight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AccountDetail(
    account: AccountSummary,
    monthLabel: String,
    categories: Map<Long, CategoryEntity>,
    onBack: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to accounts")
            }
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    "${if (account.isCard) "💳" else "🏦"}  ${account.accountKey}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${account.channel.ifBlank { if (account.isCard) "CARD" else "BANK" }} · $monthLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        ElevatedSurfaceCard { AccountStats(account) }
        Spacer(Modifier.height(8.dp))
        SectionHeader("Transactions (${account.txnCount})")
        if (account.transactions.isEmpty()) {
            EmptyHint("No transactions on this account in $monthLabel.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(account.transactions) { txn ->
                    TransactionRow(txn, categories, onClick = { onTransactionClick(txn) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AccountStats(acct: AccountSummary) {
    Column {
        if (acct.isCard && acct.billTotalDueMinor != null) {
            // Latest credit-card statement.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryStat(
                    label = "Total due",
                    value = Money.format(acct.billTotalDueMinor, "INR"),
                    accent = SpendLensTheme.colors.debit,
                    modifier = Modifier.weight(1f),
                )
                SummaryStat(
                    label = "Due date",
                    value = acct.billDueDate?.let { Dates.date(it) } ?: "—",
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryStat(
                    label = "Min due",
                    value = acct.billMinDueMinor?.let { Money.format(it, "INR") } ?: "—",
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                SummaryStat(
                    label = "Debit (this month)",
                    value = "-" + Money.format(acct.totalDebitMinor, "INR"),
                    accent = SpendLensTheme.colors.debit,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryStat(
                    label = if (acct.isCard) "Avl. balance" else "Current balance",
                    value = acct.balanceMinor?.let { Money.format(it, "INR") } ?: "—",
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                SummaryStat(
                    label = "Total debit",
                    value = "-" + Money.format(acct.totalDebitMinor, "INR"),
                    accent = SpendLensTheme.colors.debit,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
