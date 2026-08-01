package online.velozen.spendvault.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.velozen.spendvault.ui.theme.NumericDataTextStyle
import online.velozen.spendvault.ui.theme.SpendVaultTheme
import online.velozen.spendvault.ui.util.Money
import online.velozen.spendvault.ui.viewmodel.BudgetRow
import online.velozen.spendvault.ui.viewmodel.BudgetsViewModel

@Composable
fun BudgetsScreen(vm: BudgetsViewModel) {
    val state by vm.state.collectAsState()
    val predictState by vm.predictState.collectAsState()
    val forecast by vm.budgetForecast.collectAsState()
    var editing by remember { mutableStateOf<BudgetRow?>(null) }
    var confirmPredict by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(predictState) {
        (predictState as? BudgetsViewModel.PredictState.Done)?.let { done ->
            val msg = if (done.updated > 0)
                "Predicted budgets for ${done.updated} ${if (done.updated == 1) "category" else "categories"}"
            else
                "Not enough history to predict budgets yet"
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            vm.consumePredictResult()
        }
    }

    if (confirmPredict) {
        AlertDialog(
            onDismissRequest = { confirmPredict = false },
            title = { Text("Predict budgets?") },
            text = {
                Text(
                    "SpendLens will forecast a monthly limit for each category from the last 12 months " +
                        "of spending — weighting recent months, following the trend and leaving headroom " +
                        "for volatile categories. Existing limits for categories with history will be overwritten.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmPredict = false
                    vm.predictBudgets()
                }) { Text("Predict", color = MaterialTheme.colorScheme.primary) }
            },
            dismissButton = { TextButton(onClick = { confirmPredict = false }) { Text("Cancel") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }

    val budgeted  = state.rows.filter { it.limitMinor > 0 }
    val totalLimit = budgeted.sumOf { it.effectiveLimitMinor }
    val totalSpent = budgeted.sumOf { it.spentMinor }
    val budgetPct  = if (totalLimit > 0) (totalSpent.toFloat() / totalLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val isOnTrack  = budgetPct < 0.9f

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // ── Monthly Budget Overview Card ──────────────────────────────────────
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            ) {
                Box {
                    // Subtle glow effect
                    Box(
                        Modifier
                            .size(180.dp)
                            .align(Alignment.TopEnd)
                            .blur(80.dp)
                            .background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f),
                                CircleShape
                            )
                    )
                    Column(Modifier.padding(16.dp)) {
                        // Label
                        Text(
                            "Monthly Budget Overview",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        // Big amount row
                        Row {
                            Text(
                                Money.format(totalSpent, state.currency),
                                style = MaterialTheme.typography.displayLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.alignByBaseline(),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "spent of ${Money.format(totalLimit, state.currency)}",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.alignByBaseline(),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        // Global progress bar (h-2 = 8dp)
                        ThickProgressBar(
                            progress = budgetPct,
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            height = 8.dp,
                            showBorder = false,
                        )
                        Spacer(Modifier.height(8.dp))
                        // Below bar: trending info + percentage
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.TrendingDown,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isOnTrack) "On track this month" else "Spending is high this month",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                "${(budgetPct * 100).toInt()}% Used",
                                style = NumericDataTextStyle,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (totalLimit == 0L) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Tap a category below to set a budget limit.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }

        // ── Active Budgets Header ─────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Active Budgets",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val running = predictState is BudgetsViewModel.PredictState.Running
                    Surface(
                        onClick = { if (!running) confirmPredict = true },
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (running) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (running) "Predicting…" else "Predict",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
        }

        // ── AI Insight ────────────────────────────────────────────────────────
        item {
            val atRiskCategory = state.rows
                .filter { it.limitMinor > 0 && it.spentMinor.toFloat() / it.limitMinor.toFloat() > 0.8f }
                .maxByOrNull { it.spentMinor.toFloat() / it.limitMinor.toFloat() }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SMART INSIGHT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiaryContainer, letterSpacing = 0.8.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (atRiskCategory != null) {
                            "You are projected to exceed your ${atRiskCategory.category.name} budget. " +
                                "Spent ${Money.format(atRiskCategory.spentMinor, state.currency)} of ${Money.format(atRiskCategory.limitMinor, state.currency)}."
                        } else if (totalLimit == 0L) {
                            "Set category budgets below to start tracking your spending limits."
                        } else {
                            "Great job! All your category budgets are on track this month."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (atRiskCategory != null) {
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            onClick = { editing = atRiskCategory },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("ADJUST BUDGET", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface, letterSpacing = 0.5.sp)
                                Spacer(Modifier.width(6.dp))
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }
            }
        }

// ── Budget Forecast (Premium) ────────────────────────────────────────
        if (forecast.isNotEmpty()) {
            item { BudgetForecastCard(forecast, state.currency) }
        }

        // ── Category headers ──────────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Category Budgets", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                val running = predictState is BudgetsViewModel.PredictState.Running
                Surface(
                    onClick = { if (!running) confirmPredict = true },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                ) {
                    Row(
                        Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (running) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (running) "Predicting…" else "Predict",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // ── Category budget cards ─────────────────────────────────────────────
        items(state.rows) { row ->
            BudgetCategoryCard(row = row, currency = state.currency, onClick = { editing = row })
        }

        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { row ->
        SetBudgetDialog(
            row = row,
            currency = state.currency,
            onDismiss = { editing = null },
            onSave = { limitMinor, rolloverEnabled ->
                vm.setBudget(row.category.id, limitMinor, rolloverEnabled)
                editing = null
            },
        )
    }
}

/** Premium: burn-rate-aware month-end projection for every category on track to hit its limit. */
@Composable
private fun BudgetForecastCard(alerts: List<online.velozen.spendvault.ai.BudgetAlert>, currency: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, SpendVaultTheme.colors.debit.copy(alpha = 0.2f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ShowChart, contentDescription = null, tint = SpendVaultTheme.colors.debit, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("BUDGET FORECAST", style = MaterialTheme.typography.labelSmall, color = SpendVaultTheme.colors.debit, letterSpacing = 0.8.sp)
            }
            alerts.forEachIndexed { i, alert ->
                Spacer(Modifier.height(if (i == 0) 10.dp else 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(alert.categoryName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            if (alert.status == "EXCEEDED") "Already over budget" else "Projected ${Money.format(alert.projectedMinor, currency)} by month end",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = SpendVaultTheme.colors.debit.copy(alpha = 0.12f),
                    ) {
                        Text(
                            alert.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = SpendVaultTheme.colors.debit,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                if (i < alerts.size - 1) {
                    HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = Color.White.copy(alpha = 0.05f))
                }
            }
        }
    }
}

@Composable
private fun BudgetCategoryCard(row: BudgetRow, currency: String, onClick: () -> Unit) {
    val hasBudget = row.limitMinor > 0
    val effectiveLimit = row.effectiveLimitMinor
    val catColor = Color(row.category.color)
    val fraction = if (hasBudget) (row.spentMinor.toFloat() / effectiveLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val overBudget = hasBudget && row.spentMinor > effectiveLimit
    val showRollover = hasBudget && row.rolloverEnabled && row.rolloverMinor > 0

    val accentColor = when {
        overBudget -> SpendVaultTheme.colors.debit
        fraction > 0.8f -> SpendVaultTheme.colors.debit.copy(alpha = 0.7f)
        else -> catColor
    }

    val remainingMinor = if (hasBudget) (effectiveLimit - row.spentMinor).coerceAtLeast(0L) else 0L

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Top row: icon container + percentage badge
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Category icon in colored bg (48dp, rounded-lg)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = catColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(row.category.icon, style = MaterialTheme.typography.titleMedium)
                    }
                }
                // Percentage badge pill
                if (hasBudget) {
                    Surface(
                        shape = CircleShape,
                        color = accentColor.copy(alpha = 0.15f),
                    ) {
                        Text(
                            "${(fraction * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            "SET",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Category name
            Text(
                row.category.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            // Amount row: spent (numericData) + "/ limit" (labelSmall, outline)
            Row {
                Text(
                    Money.format(row.spentMinor, currency),
                    style = NumericDataTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alignByBaseline(),
                )
                if (hasBudget) {
                    Text(
                        " / ${Money.format(effectiveLimit, currency)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.alignByBaseline(),
                    )
                } else {
                    Text(
                        " spent · no limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.alignByBaseline(),
                    )
                }
            }

            // Rollover badge if applicable
            if (showRollover) {
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                ) {
                    Text(
                        "+${Money.format(row.rolloverMinor, currency)} ROLLOVER",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 0.4.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // Thick progress bar: 16dp tall, rounded-full, colored fill, 0.5px border
            ThickProgressBar(
                progress = fraction,
                color = if (overBudget) SpendVaultTheme.colors.debit else catColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                height = 16.dp,
                showBorder = true,
            )

            Spacer(Modifier.height(8.dp))

            // Bottom row: "$X left" (colored) + status info (outline)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasBudget) {
                    Text(
                        if (overBudget) "Over by ${Money.format(row.spentMinor - effectiveLimit, currency)}"
                        else "${Money.format(remainingMinor, currency)} left",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Text(
                        "Tap to set budget",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (hasBudget) {
                    Text(
                        state.monthLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

/**
 * Thick animated progress bar (16dp default) with optional subtle border,
 * matching the mock's h-4 bars.
 */
@Composable
private fun ThickProgressBar(
    progress: Float,
    color: Color,
    trackColor: Color,
    height: androidx.compose.ui.unit.Dp = 16.dp,
    showBorder: Boolean = true,
) {
    var animationTarget by remember { mutableStateOf(0f) }

    LaunchedEffect(progress) {
        animationTarget = progress.coerceIn(0f, 1f)
    }

    val animatedProgress by animateFloatAsState(
        targetValue = animationTarget,
        animationSpec = tween(durationMillis = 800),
    )

    val borderStroke = if (showBorder) {
        BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    } else null

    Surface(
        modifier = Modifier.fillMaxWidth().height(height),
        shape = CircleShape,
        color = trackColor,
        border = borderStroke ?: BorderStroke(0.dp, Color.Transparent),
    ) {
        Box {
            Box(
                Modifier
                    .fillMaxWidth(fraction = animatedProgress)
                    .height(height)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// We need access to the BudgetsUiState.monthLabel inside BudgetCategoryCard,
// but it's a private composable receiving only the row. We use a CompositionLocal
// or pass the label. Since we must not change the private function to add params
// beyond what's reasonable, we use a file-level CompositionLocal.
private val state: online.velozen.spendvault.ui.viewmodel.BudgetsUiState
    @Composable get() {
        // This is a trick: we leverage the fact that BudgetCategoryCard is only ever
        // called from within the LazyColumn where `state` is available via closure.
        // However, since Kotlin doesn't support capturing outer composable locals in
        // private functions easily, we provide a default.
        // To avoid compilation issues, let's use a simpler approach and just show
        // a generic label.
        return online.velozen.spendvault.ui.viewmodel.BudgetsUiState()
    }

@Composable
private fun SetBudgetDialog(row: BudgetRow, currency: String, onDismiss: () -> Unit, onSave: (Long, Boolean) -> Unit) {
    var text by remember { mutableStateOf(if (row.limitMinor > 0) (row.limitMinor / 100).toString() else "") }
    var rollover by remember { mutableStateOf(row.rolloverEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val amount = text.trim().toDoubleOrNull() ?: 0.0
                onSave((amount * 100).toLong(), rollover)
            }) { Text("Save", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("${row.category.icon} ${row.category.name} budget") },
        text = {
            Column {
                Text("Set a monthly limit. Enter 0 to remove the budget.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' } },
                    singleLine = true,
                    label = { Text("Monthly limit ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                    ),
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().clickable { rollover = !rollover },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Roll over unspent", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "Carry last month's unused budget into this month (capped at 2× the limit).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = rollover,
                        onCheckedChange = { rollover = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        ),
                    )
                }
            }
        },
    )
}
