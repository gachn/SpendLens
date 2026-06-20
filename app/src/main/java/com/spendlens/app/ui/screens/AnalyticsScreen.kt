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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.GroupedBarChart
import com.spendlens.app.ui.components.LegendDot
import com.spendlens.app.ui.components.MonthDropdown
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.AnalyticsViewModel

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel) {
    val state by vm.state.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { SectionHeader("Last 6 months") }
        item {
            ElevatedSurfaceCard {
                Column {
                    GroupedBarChart(
                        labels = state.months.map { it.label },
                        series1 = state.months.map { it.debitMinor.toFloat() },
                        series2 = state.months.map { it.creditMinor.toFloat() },
                        color1 = SpendLensTheme.colors.debit,
                        color2 = SpendLensTheme.colors.credit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth()) {
                        state.months.forEach {
                            Text(
                                it.label,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendDot(SpendLensTheme.colors.debit, "Spent", "")
                        LegendDot(SpendLensTheme.colors.credit, "Received", "")
                    }
                }
            }
        }

        item {
            SectionHeader("Spending by category") {
                MonthDropdown(state.selectedMonth, state.monthOptions, vm::setMonth)
            }
        }
        item {
            ElevatedSurfaceCard {
                Column {
                    if (state.slices.isEmpty()) {
                        Text("No spending recorded yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.slices.forEach {
                            LegendDot(it.color, it.name, Money.format(it.amountMinor, state.currency))
                        }
                    }
                }
            }
        }

        item { SectionHeader("Top merchants · ${com.spendlens.app.ui.util.Dates.label(state.selectedMonth)}") }
        item {
            ElevatedSurfaceCard {
                Column {
                    if (state.topMerchants.isEmpty()) {
                        Text("No merchants yet.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        state.topMerchants.forEach {
                            LegendDot(it.color, it.name, Money.format(it.amountMinor, state.currency))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
