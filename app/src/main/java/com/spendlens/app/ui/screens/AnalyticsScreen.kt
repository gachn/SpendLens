package com.spendlens.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendlens.app.ui.components.GlassCard
import com.spendlens.app.ui.components.GroupedBarChart
import com.spendlens.app.ui.components.MonthDropdown
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.AnalyticsViewModel
import com.spendlens.app.ui.viewmodel.CategorySlice

@Composable
fun AnalyticsScreen(vm: AnalyticsViewModel) {
    val state by vm.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // ── Cash Flow chart ──────────────────────────────────────────────────
        item {
            GlassCard {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Column {
                            Text("Cash Flow", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                            Text("Last 6 months", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("NET SAVINGS", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                            val netSavings = state.months.sumOf { it.creditMinor - it.debitMinor }
                            Text(
                                (if (netSavings >= 0) "+" else "") + Money.format(netSavings, state.currency),
                                style = MaterialTheme.typography.headlineMedium,
                                color = if (netSavings >= 0) MaterialTheme.colorScheme.primary else SpendLensTheme.colors.debit,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    // Chart area
                    GroupedBarChart(
                        labels = state.months.map { it.label },
                        series1 = state.months.map { it.debitMinor.toFloat() },
                        series2 = state.months.map { it.creditMinor.toFloat() },
                        color1 = SpendLensTheme.colors.debit,
                        color2 = SpendLensTheme.colors.credit,
                        modifier = Modifier.fillMaxWidth(),
                        height = 160.dp,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Month labels
                    Row(Modifier.fillMaxWidth()) {
                        state.months.forEach { m ->
                            Text(
                                m.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    // Legend
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        LegendPill(color = SpendLensTheme.colors.debit, label = "Spent")
                        LegendPill(color = SpendLensTheme.colors.credit, label = "Received")
                    }
                }
            }
        }

        // ── Spending by category bento ───────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Spending by Category", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                MonthDropdown(state.selectedMonth, state.monthOptions, vm::setMonth)
            }
        }

        item {
            if (state.slices.isEmpty()) {
                GlassCard { Text("No spending recorded yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                val topSlices = state.slices.sortedByDescending { it.amountMinor }.take(4)
                val maxAmount = topSlices.maxOfOrNull { it.amountMinor }?.toFloat() ?: 1f
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val rows = topSlices.chunked(2)
                    rows.forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            pair.forEach { slice ->
                                CategoryBentoCard(slice, maxAmount, Modifier.weight(1f))
                            }
                            // Fill empty slot if odd
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // ── Top Merchants ────────────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Top Merchants · ${Dates.label(state.selectedMonth)}", style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = {}) {
                    Text("View All", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        item {
            if (state.topMerchants.isEmpty()) {
                GlassCard { Text("No merchants yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                ) {
                    Column {
                        state.topMerchants.forEachIndexed { i, merchant ->
                            MerchantRow(merchant, state.currency)
                            if (i < state.topMerchants.size - 1) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    color = Color.White.copy(alpha = 0.05f),
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

@Composable
private fun CategoryBentoCard(slice: CategorySlice, maxAmount: Float, modifier: Modifier = Modifier) {
    val fillFraction = (slice.amountMinor.toFloat() / maxAmount).coerceIn(0f, 1f)
    Surface(
        modifier = modifier.aspectRatio(1f),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Vertical fill bar
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                // Track
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(8.dp))
                        .padding(0.dp),
                )
                // Fill
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize(fillFraction),
                    shape = RoundedCornerShape(8.dp),
                    color = slice.color.copy(alpha = 0.75f),
                ) {}
            }
            Spacer(Modifier.height(8.dp))
            Text(
                slice.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                Money.format(slice.amountMinor, "INR"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun MerchantRow(merchant: CategorySlice, currency: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = CircleShape,
            color = merchant.color.copy(alpha = 0.15f),
            border = BorderStroke(1.dp, merchant.color.copy(alpha = 0.2f)),
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("🏪", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(merchant.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            "-" + Money.format(merchant.amountMinor, currency),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LegendPill(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = color, modifier = Modifier.size(10.dp)) {}
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
