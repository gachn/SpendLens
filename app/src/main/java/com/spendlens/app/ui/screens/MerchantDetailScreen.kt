package com.spendlens.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.di.AppContainer
import com.spendlens.app.ui.components.GlassCard
import com.spendlens.app.ui.components.GroupedBarChart
import com.spendlens.app.ui.components.SummaryStat
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.util.Money
import kotlinx.coroutines.launch

private const val MONTHS_BACK = 6

/**
 * Drill-down for a single merchant/counterparty: total spend, transaction count, a 6-month
 * spend trend, and the full (lazily paginated) list of transactions with that merchant.
 * Reuses [GroupedBarChart] and [TransactionRow]; no schema changes. See issue #4.
 */
@Composable
fun MerchantDetailScreen(
    counterparty: String,
    container: AppContainer,
    onBack: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
) {
    val txns by remember(counterparty) {
        container.transactionRepository.observeByCounterparty(counterparty)
    }.collectAsState(initial = emptyList())
    val categoryList by remember {
        container.categoryRepository.observeCategories()
    }.collectAsState(initial = emptyList())
    val categories = remember(categoryList) { categoryList.associateBy { it.id } }
    val scope = rememberCoroutineScope()
    val excluded by remember(counterparty) {
        container.merchantRepository.observeExcluded(counterparty)
    }.collectAsState(initial = false)

    // Spend totals use base-currency (INR) minor units and ignore non-expense rows (transfers etc.).
    val spendable = txns.filter { it.direction == "DEBIT" && !it.excludedFromExpense }
    val totalSpentMinor = spendable.sumOf { it.amountBaseMinor }

    // 6-month spend buckets, oldest → newest for the bar chart.
    val months = remember { Dates.recentMonths(MONTHS_BACK).reversed() }
    val monthly = remember(spendable) {
        val byMonth = spendable.groupBy { Dates.monthOf(it.occurredAt) }
        months.map { ym -> (byMonth[ym]?.sumOf { it.amountBaseMinor } ?: 0L) }
    }

    Column(Modifier.fillMaxSize()) {
        // ── Header with back ─────────────────────────────────────────────────
        Row(
            Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.onSurface)
            }
            Text(
                counterparty,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }

        LazyColumn(
            Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Summary ──────────────────────────────────────────────────────
            item {
                GlassCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        SummaryStat(
                            label = "Total spent",
                            value = Money.format(totalSpentMinor, "INR"),
                            accent = SpendLensTheme.colors.debit,
                        )
                        SummaryStat(
                            label = "Transactions",
                            value = txns.size.toString(),
                            accent = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 16.dp),
                        )
                    }
                }
            }

            // ── Exclude-from-expense toggle ──────────────────────────────────
            item {
                GlassCard {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                "Count in spending",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                if (excluded) {
                                    "Excluded — these transactions are kept out of all totals."
                                } else {
                                    "Included in spend, income and budget totals."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = !excluded,
                            onCheckedChange = { include ->
                                scope.launch {
                                    container.merchantRepository.setExcluded(counterparty, !include)
                                    container.transactionRepository
                                        .setExcludedForCounterparty(counterparty, !include)
                                }
                            },
                        )
                    }
                }
            }

            // ── 6-month trend ────────────────────────────────────────────────
            item {
                GlassCard {
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            "LAST $MONTHS_BACK MONTHS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        GroupedBarChart(
                            labels = months.map { Dates.shortMonth(it) },
                            series1 = monthly.map { it / 100f },
                            series2 = List(months.size) { 0f },
                            color1 = MaterialTheme.colorScheme.primary,
                            color2 = androidx.compose.ui.graphics.Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                            height = 140.dp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            months.forEach { ym ->
                                Text(
                                    Dates.shortMonth(ym),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            // ── Transaction list ─────────────────────────────────────────────
            item {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (txns.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "No transactions with this merchant.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f)),
                    ) {
                        Column {
                            txns.forEachIndexed { i, txn ->
                                TransactionRow(txn, categories, onClick = { onTransactionClick(txn) })
                                if (i < txns.size - 1) {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.05f),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}
