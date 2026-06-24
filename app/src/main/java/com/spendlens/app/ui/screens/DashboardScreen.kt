package com.spendlens.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.ui.components.GlassCard
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.BudgetsViewModel
import com.spendlens.app.ui.viewmodel.DashboardViewModel

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    budgetVm: BudgetsViewModel,
    onTransactionClick: (TransactionEntity) -> Unit = {},
    onOpenBills: () -> Unit = {},
    onViewAll: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val budgetState by budgetVm.state.collectAsState()

    val budgeted = budgetState.rows.filter { it.limitMinor > 0 }
    val totalLimit = budgeted.sumOf { it.limitMinor }
    val totalSpent = budgeted.sumOf { it.spentMinor }
    val budgetPct = if (totalLimit > 0) (totalSpent.toFloat() / totalLimit.toFloat()).coerceIn(0f, 1f) else 0f
    val budgetLeft = (totalLimit - totalSpent).coerceAtLeast(0L)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { Spacer(Modifier.height(8.dp)) }

        // ── Total Spent card ─────────────────────────────────────────────────
        item {
            GlassCard {
                Box {
                    // Subtle decorative glow
                    Box(
                        Modifier
                            .size(120.dp)
                            .align(Alignment.TopEnd)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.04f))
                    )
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "TOTAL SPENT THIS MONTH",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            Money.format(state.spendMinor, state.currency),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(10.dp))
                        // Trend chip
                        val net = state.incomeMinor - state.spendMinor
                        val isSaving = net >= 0
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = if (isSaving) Icons.Filled.TrendingDown else Icons.Filled.TrendingUp,
                                    contentDescription = null,
                                    tint = if (isSaving) MaterialTheme.colorScheme.primary else SpendLensTheme.colors.debit,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (isSaving) "On track · net ${Money.format(net, state.currency)}"
                                    else "Over income by ${Money.format(-net, state.currency)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isSaving) MaterialTheme.colorScheme.primary else SpendLensTheme.colors.debit,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Smart Insight card ───────────────────────────────────────────────
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            ) {
                Row(modifier = Modifier.padding(16.dp)) {
                    // Gradient left accent bar
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(60.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary,
                                    )
                                )
                            )
                    )
                    Spacer(Modifier.width(12.dp))
                    // Icon avatar
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
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
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Smart Insight",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        val hasIncome = state.incomeMinor > 0
                        val insightText = when {
                            !hasIncome -> "Import your SMS messages to unlock personalised spending insights."
                            state.spendMinor == 0L -> "No spending recorded yet this month. Tap the bell to review pending SMS."
                            else -> {
                                val savePct = ((state.incomeMinor - state.spendMinor) * 100 / state.incomeMinor).coerceAtLeast(0L)
                                "You're saving $savePct% of income this month. " +
                                    if (state.slices.isNotEmpty()) "Top category: ${state.slices.maxByOrNull { it.amountMinor }?.name}." else ""
                            }
                        }
                        Text(
                            insightText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Monthly Budget progress ──────────────────────────────────────────
        item {
            GlassCard {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Monthly Budget", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                        Text(
                            "${(budgetPct * 100).toInt()}% Used",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // Progress bar
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        val barColor = if (budgetPct > 0.9f) SpendLensTheme.colors.debit else MaterialTheme.colorScheme.primary
                        Box(
                            Modifier
                                .fillMaxWidth(budgetPct)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(barColor, barColor.copy(alpha = 0.8f))
                                    )
                                )
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            "Spent: ${Money.format(totalSpent, state.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            "Left: ${Money.format(budgetLeft, state.currency)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Recent transactions ──────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Recent", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface)
                TextButton(onClick = onViewAll) {
                    Text("View All", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        if (state.recent.isEmpty()) {
            item { EmptyHint("No transactions yet. Grant SMS access and import to get started.") }
        } else {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                ) {
                    Column {
                        state.recent.take(5).forEachIndexed { index, txn ->
                            TransactionRow(
                                txn = txn,
                                categories = state.categories,
                                merchantEmojis = state.merchantEmojis,
                                onClick = { onTransactionClick(txn) },
                            )
                            if (index < (state.recent.size - 1).coerceAtMost(4)) {
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

        // ── Bills shortcut ───────────────────────────────────────────────────
        item {
            Button(
                onClick = onOpenBills,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
            ) {
                Text("🧾  Upcoming bills & reminders")
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
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
    )
}
