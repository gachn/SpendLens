package com.spendlens.app.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.ui.components.ElevatedSurfaceCard
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
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (banks.isNotEmpty()) {
                    item { SectionHeader("Bank accounts") }
                    items(banks) { acct -> AccountCard(acct, onClick = { openKey = acct.accountKey }) }
                }
                if (cards.isNotEmpty()) {
                    item { SectionHeader("Cards") }
                    items(cards) { acct -> AccountCard(acct, onClick = { openKey = acct.accountKey }) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AccountCard(acct: AccountSummary, onClick: () -> Unit) {
    ElevatedSurfaceCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (acct.isCard) "💳" else "🏦", style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(
                        acct.accountKey,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "${acct.channel.ifBlank { if (acct.isCard) "CARD" else "BANK" }} · ${acct.txnCount} txns",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "View transactions",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            AccountStats(acct)
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
