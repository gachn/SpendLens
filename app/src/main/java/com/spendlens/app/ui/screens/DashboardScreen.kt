package com.spendlens.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendlens.app.ui.components.DonutChart
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.LegendDot
import com.spendlens.app.ui.components.MonthDropdown
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.ui.components.SummaryStat
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.util.Money
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.ui.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    onTransactionClick: (TransactionEntity) -> Unit = {},
    onOpenBills: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val accounts by vm.accounts.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Overview", style = MaterialTheme.typography.titleMedium)
                MonthDropdown(state.selectedMonth, state.monthOptions, vm::setMonth)
            }
        }
        item {
            ElevatedSurfaceCard {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            SummaryStat("Spent", Money.format(state.spendMinor, state.currency), SpendLensTheme.colors.debit)
                            Spacer(Modifier.height(12.dp))
                            SummaryStat("Received", Money.format(state.incomeMinor, state.currency), SpendLensTheme.colors.credit)
                            Spacer(Modifier.height(12.dp))
                            val net = state.incomeMinor - state.spendMinor
                            SummaryStat(
                                "Net",
                                Money.format(net, state.currency),
                                if (net >= 0) SpendLensTheme.colors.credit else SpendLensTheme.colors.debit,
                            )
                        }
                        DonutChart(
                            values = state.slices.map { it.amountMinor.toFloat() },
                            colors = state.slices.map { it.color },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Spent", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    Money.compact(state.spendMinor, state.currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            OutlinedButton(onClick = onOpenBills, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("🧾 Upcoming bills & reminders")
            }
        }

        if (accounts.isNotEmpty()) {
            item { SectionHeader("Accounts") }
            item {
                ElevatedSurfaceCard {
                    Column {
                        accounts.forEachIndexed { i, acc ->
                            if (i > 0) Spacer(Modifier.height(10.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(acc.accountKey, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "Updated ${Dates.day(acc.updatedAt)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text(
                                    Money.format(acc.balanceMinor, state.currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (state.slices.isNotEmpty()) {
            item { SectionHeader("Top categories") }
            item {
                ElevatedSurfaceCard {
                    Column {
                        state.slices.sortedByDescending { it.amountMinor }.take(5).forEach { slice ->
                            LegendDot(slice.color, slice.name, Money.format(slice.amountMinor, state.currency))
                        }
                    }
                }
            }
        }

        item { SectionHeader("Recent transactions") }
        if (state.recent.isEmpty()) {
            item { EmptyHint("No transactions yet. Grant SMS access and import to get started.") }
        } else {
            items(state.recent) { txn ->
                TransactionRow(txn, state.categories, onClick = { onTransactionClick(txn) })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
    )
}
