package com.spendlens.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Switch
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
import com.spendlens.app.ui.components.DonutChart
import com.spendlens.app.ui.components.GlassCard
import com.spendlens.app.ui.components.GroupedBarChart
import com.spendlens.app.ui.components.MonthDropdown
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.AnalyticsTab
import com.spendlens.app.ui.viewmodel.AnalyticsViewModel
import com.spendlens.app.ui.viewmodel.CategoryComparisonRow
import com.spendlens.app.ui.viewmodel.CategorySlice
import kotlin.math.absoluteValue

@Composable
fun AnalyticsScreen(
    vm: AnalyticsViewModel,
    onViewAllMerchants: () -> Unit = {},
    onMerchantClick: (String) -> Unit = {},
    onCategoryClick: (Long) -> Unit = {},
) {
    val state by vm.state.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // ── Tab selector ─────────────────────────────────────────────────────
        item {
            AnalyticsTabRow(
                activeTab = state.activeTab,
                onTabSelected = vm::setTab,
            )
        }

        if (state.activeTab == AnalyticsTab.Spend) {
            // ── Cash Flow chart ───────────────────────────────────────────────
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
                        GroupedBarChart(
                            labels = state.months.map { it.label },
                            series1 = state.months.map { it.debitMinor.toFloat() },
                            series2 = state.months.map { it.creditMinor.toFloat() },
                            color1 = SpendLensTheme.colors.debit,
                            color2 = SpendLensTheme.colors.credit,
                            modifier = Modifier.fillMaxWidth(),
                            height = 160.dp,
                            series1Label = "Spent",
                            series2Label = "Received",
                            formatValue = { Money.format(it.toLong(), state.currency) },
                        )
                        Spacer(Modifier.height(8.dp))
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
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            LegendPill(color = SpendLensTheme.colors.debit, label = "Spent")
                            LegendPill(color = SpendLensTheme.colors.credit, label = "Received")
                        }
                    }
                }
            }

            // ── Month-over-month comparison (issue #15) ───────────────────────
            item {
                GlassCard {
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text("Compare Months", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("See where spending changed by category", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = state.compareMode, onCheckedChange = vm::setCompareMode)
                        }
                        if (state.compareMode) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text("From", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    MonthDropdown(state.compareMonthA, state.monthOptions, vm::setCompareMonthA)
                                }
                                Column(Modifier.weight(1f)) {
                                    Text("To", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    MonthDropdown(state.compareMonthB, state.monthOptions, vm::setCompareMonthB)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            ComparisonTable(
                                rows = state.comparisonRows,
                                currency = state.currency,
                                onCategoryClick = onCategoryClick,
                            )
                        }
                    }
                }
            }

            // ── Spending by category bento ────────────────────────────────────
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
                        topSlices.chunked(2).forEach { pair ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { slice ->
                                    CategoryBentoCard(
                                        slice,
                                        maxAmount,
                                        Modifier.weight(1f),
                                        onClick = slice.categoryId?.let { id -> { onCategoryClick(id) } },
                                    )
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // ── Top Merchants ─────────────────────────────────────────────────
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Top Merchants · ${Dates.label(state.selectedMonth)}", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onViewAllMerchants) {
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
                                MerchantRow(merchant, state.currency, onClick = { onMerchantClick(merchant.name) })
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
        } else {
            // ── Income tab ────────────────────────────────────────────────────

            // 6-month income bar chart
            item {
                GlassCard {
                    Column {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Column {
                                Text("Income", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                                Text("Last 6 months", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("TOTAL INCOME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 0.5.sp)
                                val totalIncome = state.months.sumOf { it.creditMinor }
                                Text(
                                    Money.format(totalIncome, state.currency),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = SpendLensTheme.colors.credit,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        GroupedBarChart(
                            labels = state.months.map { it.label },
                            series1 = state.months.map { it.creditMinor.toFloat() },
                            series2 = state.months.map { 0f },
                            color1 = SpendLensTheme.colors.credit,
                            color2 = Color.Transparent,
                            modifier = Modifier.fillMaxWidth(),
                            height = 160.dp,
                            series1Label = "Income",
                            series2Label = "",
                            formatValue = { Money.format(it.toLong(), state.currency) },
                        )
                        Spacer(Modifier.height(8.dp))
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
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            LegendPill(color = SpendLensTheme.colors.credit, label = "Income")
                        }
                    }
                }
            }

            // Income sources donut chart + breakdown
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Income Sources", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                    MonthDropdown(state.selectedMonth, state.monthOptions, vm::setMonth)
                }
            }

            item {
                if (state.incomeSlices.isEmpty()) {
                    GlassCard {
                        Text(
                            "No income recorded for ${Dates.label(state.selectedMonth)}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    GlassCard {
                        Column {
                            val totalIncome = state.incomeSlices.sumOf { it.amountMinor }
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(24.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                DonutChart(
                                    values = state.incomeSlices.map { it.amountMinor.toFloat() },
                                    colors = state.incomeSlices.map { it.color },
                                    diameter = 140.dp,
                                    strokeWidth = 24.dp,
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            Money.format(totalIncome, state.currency),
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center,
                                        )
                                        Text(
                                            "total",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    state.incomeSlices.take(5).forEach { slice ->
                                        val pct = if (totalIncome > 0) (slice.amountMinor * 100f / totalIncome) else 0f
                                        IncomeSourceRow(slice, pct, state.currency)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Savings rate per month
            item {
                Text("Savings Rate", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
            }

            item {
                if (state.months.all { it.creditMinor == 0L }) {
                    GlassCard {
                        Text(
                            "No income data to compute savings rate.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                    ) {
                        Column {
                            state.months.zip(state.monthlySavingsRates).forEachIndexed { i, (month, rate) ->
                                SavingsRateRow(
                                    month = month.label,
                                    incomeMinor = month.creditMinor,
                                    spendMinor = month.debitMinor,
                                    savingsRate = rate,
                                    currency = state.currency,
                                )
                                if (i < state.months.size - 1) {
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
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ── Tab row ──────────────────────────────────────────────────────────────────

@Composable
private fun AnalyticsTabRow(
    activeTab: AnalyticsTab,
    onTabSelected: (AnalyticsTab) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnalyticsTab.entries.forEach { tab ->
                val selected = tab == activeTab
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTabSelected(tab) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                ) {
                    Text(
                        text = tab.name,
                        modifier = Modifier.padding(vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ── Income tab sub-components ────────────────────────────────────────────────

@Composable
private fun IncomeSourceRow(slice: CategorySlice, percentage: Float, currency: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(shape = CircleShape, color = slice.color, modifier = Modifier.size(8.dp)) {}
        Text(
            slice.name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${percentage.toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SavingsRateRow(
    month: String,
    incomeMinor: Long,
    spendMinor: Long,
    savingsRate: Float,
    currency: String,
) {
    val hasIncome = incomeMinor > 0L
    val rateColor = when {
        !hasIncome -> MaterialTheme.colorScheme.onSurfaceVariant
        savingsRate >= 20f -> SpendLensTheme.colors.credit
        savingsRate >= 0f -> MaterialTheme.colorScheme.onSurface
        else -> SpendLensTheme.colors.debit
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            month,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            if (hasIncome) {
                Text(
                    "Income ${Money.format(incomeMinor, currency)}  ·  Spend ${Money.format(spendMinor, currency)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Text("No income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            if (hasIncome) "${if (savingsRate >= 0) "+" else ""}${savingsRate.toInt()}%" else "—",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = rateColor,
        )
    }
}

// ── Spend tab sub-components ─────────────────────────────────────────────────

@Composable
private fun CategoryBentoCard(
    slice: CategorySlice,
    maxAmount: Float,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val fillFraction = (slice.amountMinor.toFloat() / maxAmount).coerceIn(0f, 1f)
    Surface(
        modifier = modifier
            .aspectRatio(1f)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).padding(0.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().fillMaxSize(fillFraction),
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
private fun MerchantRow(merchant: CategorySlice, currency: String, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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

// ── Month-over-month comparison table (issue #15) ────────────────────────────

@Composable
private fun ComparisonTable(
    rows: List<CategoryComparisonRow>,
    currency: String,
    onCategoryClick: (Long) -> Unit,
) {
    if (rows.isEmpty()) {
        Text(
            "No spending in either month.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Column {
        // Header row.
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text("Category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1.4f))
            Text("From", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("To", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            Text("Δ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.weight(1.1f))
        }
        HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
        rows.forEach { row ->
            ComparisonRow(row, currency, onCategoryClick)
            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
        }
    }
}

@Composable
private fun ComparisonRow(
    row: CategoryComparisonRow,
    currency: String,
    onCategoryClick: (Long) -> Unit,
) {
    // Spending going down is good → green; going up → red. Zero delta is neutral.
    val deltaColor = when {
        row.deltaMinor < 0 -> SpendLensTheme.colors.credit
        row.deltaMinor > 0 -> SpendLensTheme.colors.debit
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val deltaPrefix = if (row.deltaMinor > 0) "+" else ""
    val pctText = row.deltaPercent?.let { " (${if (it >= 0) "+" else ""}${it.toInt()}%)" }
        ?: if (row.amountBMinor > 0) " (new)" else ""
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(row.categoryId?.let { id -> Modifier.clickable { onCategoryClick(id) } } ?: Modifier)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1.4f), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = row.color, modifier = Modifier.size(8.dp)) {}
            Spacer(Modifier.width(6.dp))
            Text(
                row.name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            Money.format(row.amountAMinor, currency),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Text(
            Money.format(row.amountBMinor, currency),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        Text(
            "$deltaPrefix${Money.format(row.deltaMinor, currency)}$pctText",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = deltaColor,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1.1f),
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
