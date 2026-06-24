package com.spendlens.app.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendlens.app.ui.components.CircularProgressRing
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.BudgetRow
import com.spendlens.app.ui.viewmodel.BudgetsViewModel

@Composable
fun BudgetsScreen(vm: BudgetsViewModel) {
    val state by vm.state.collectAsState()
    val predictState by vm.predictState.collectAsState()
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

        // ── Global Budget overview ────────────────────────────────────────────
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            ) {
                Box {
                    // Gradient tint
                    Box(
                        Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.04f),
                                        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.02f),
                                    )
                                )
                            )
                    )
                    Column(Modifier.padding(20.dp)) {
                        // Header row
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ShowChart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "GLOBAL BUDGET",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    letterSpacing = 0.8.sp,
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                            ) {
                                Text(
                                    if (isOnTrack) "ON TRACK" else "AT RISK",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOnTrack) MaterialTheme.colorScheme.onSurfaceVariant else SpendLensTheme.colors.debit,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        // Big number
                        Text(
                            Money.format(totalSpent, state.currency),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            "of ${Money.format(totalLimit, state.currency)} total allocated",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        // Progress bar
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("0%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${(budgetPct * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("100%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(4.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        ) {
                            val barColor = if (isOnTrack) MaterialTheme.colorScheme.primary else SpendLensTheme.colors.debit
                            Box(
                                Modifier
                                    .fillMaxWidth(budgetPct)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(listOf(barColor, barColor.copy(alpha = 0.7f)))
                                    )
                            )
                        }
                        if (totalLimit == 0L) {
                            Spacer(Modifier.height(8.dp))
                            Text("Tap a category below to set a budget limit.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
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
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
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

@Composable
private fun BudgetCategoryCard(row: BudgetRow, currency: String, onClick: () -> Unit) {
    val hasBudget = row.limitMinor > 0
    val effectiveLimit = row.effectiveLimitMinor
    val catColor = Color(row.category.color)
    val fraction = if (hasBudget) (row.spentMinor.toFloat() / effectiveLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val overBudget = hasBudget && row.spentMinor > effectiveLimit
    val showRollover = hasBudget && row.rolloverEnabled && row.rolloverMinor > 0
    val ringColor = when {
        overBudget -> SpendLensTheme.colors.debit
        fraction > 0.8f -> SpendLensTheme.colors.debit.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (overBudget) SpendLensTheme.colors.debit.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Circular progress ring with icon/emoji center
            CircularProgressRing(
                progress = fraction,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                progressColor = ringColor,
                diameter = 56.dp,
                strokeWidth = 5.dp,
            ) {
                Text(row.category.icon, style = MaterialTheme.typography.titleSmall)
            }

            Spacer(Modifier.width(14.dp))

            // Category info
            Column(Modifier.weight(1f)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        row.category.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (hasBudget) "${(fraction * 100).toInt()}%" else "–",
                        style = MaterialTheme.typography.bodySmall,
                        color = ringColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (hasBudget)
                            "${Money.format(row.spentMinor, currency)} / ${Money.format(effectiveLimit, currency)}"
                        else
                            "${Money.format(row.spentMinor, currency)} spent · no limit",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (overBudget) SpendLensTheme.colors.debit else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (showRollover) {
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
                    } else if (!hasBudget) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                        ) {
                            Text(
                                "SET BUDGET",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.4.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    } else if (overBudget) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SpendLensTheme.colors.debit.copy(alpha = 0.12f),
                        ) {
                            Text(
                                "OVER BUDGET",
                                style = MaterialTheme.typography.labelSmall,
                                color = SpendLensTheme.colors.debit,
                                letterSpacing = 0.4.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
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
