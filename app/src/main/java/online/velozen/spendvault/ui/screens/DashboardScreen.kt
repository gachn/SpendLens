package online.velozen.spendvault.ui.screens

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import online.velozen.spendvault.data.db.TransactionEntity
import online.velozen.spendvault.ui.components.GlassCard
import online.velozen.spendvault.ui.components.ProChip
import online.velozen.spendvault.ui.components.ProProgressBar
import online.velozen.spendvault.ui.components.TransactionRow
import online.velozen.spendvault.ui.theme.SpendVaultTheme
import online.velozen.spendvault.ui.util.Money
import online.velozen.spendvault.ui.viewmodel.BudgetsViewModel
import online.velozen.spendvault.ui.viewmodel.DashboardViewModel
import online.velozen.spendvault.ui.viewmodel.DashboardUiState

@Composable
fun DashboardScreen(
    vm: DashboardViewModel,
    budgetVm: BudgetsViewModel,
    onTransactionClick: (TransactionEntity) -> Unit = {},
    onOpenBills: () -> Unit = {},
    onOpenSubscriptions: () -> Unit = {},
    onViewAll: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val budgetState by budgetVm.state.collectAsState()
    val recap by vm.recap.collectAsState()

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

        // ── Hero Spend Card ──────────────────────────────────────────────────
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
            ) {
                Box(
                    Modifier
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer,
                                )
                            )
                        )
                ) {
                    // Decorative faded icon in top-right corner
                    Icon(
                        Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.08f),
                        modifier = Modifier
                            .size(120.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 20.dp, y = (-20).dp),
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "TOTAL SPENT THIS MONTH",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f),
                            letterSpacing = 1.2.sp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            Money.format(state.spendMinor, state.currency),
                            style = MaterialTheme.typography.displayLarge.copy(fontSize = 40.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                        Spacer(Modifier.height(16.dp))
                        // White/20 divider
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.2f),
                            thickness = 0.5.dp,
                        )
                        Spacer(Modifier.height(12.dp))
                        // 2-column grid: Total Income & Outstanding
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Total Income",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    Money.format(state.incomeMinor, state.currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Outstanding",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                )
                                Spacer(Modifier.height(4.dp))
                                val outstanding = (state.spendMinor - state.incomeMinor).coerceAtLeast(0L)
                                Text(
                                    Money.format(outstanding, state.currency),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Smart Insight Card (includes AI Recap) ───────────────────────────────────────
        item { SmartInsightCard(recap, onGenerateRecap = vm::generateRecap, state = state) }

        // ── Monthly Budget progress ──────────────────────────────────────────
        item {
            GlassCard {
                Column {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Monthly Budget",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        ProChip(
                            text = "${(budgetPct * 100).toInt()}% Used",
                            color = if (budgetPct > 0.9f)
                                MaterialTheme.colorScheme.errorContainer
                            else
                                MaterialTheme.colorScheme.primaryContainer,
                            textColor = if (budgetPct > 0.9f)
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    // 4dp progress bar with rounded full shape
                    val barColor = if (budgetPct > 0.9f) SpendVaultTheme.colors.debit else MaterialTheme.colorScheme.primary
                    ProProgressBar(
                        progress = budgetPct,
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
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

        // ── Recent Transactions ──────────────────────────────────────────────
        item {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Recent",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                TextButton(onClick = onViewAll) {
                    Text(
                        "View All",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelMedium,
                    )
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
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
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
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Bills & Subscriptions shortcuts ──────────────────────────────────
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onOpenBills,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("🧾  Bills")
                }
                Button(
                    onClick = onOpenSubscriptions,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text("🔁  Subscriptions")
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Consolidated Smart Insight Card (includes AI Recap for Premium) */
@Composable
private fun SmartInsightCard(
    recap: DashboardViewModel.RecapState,
    onGenerateRecap: () -> Unit,
    state: DashboardUiState
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Left accent bar (4dp wide)
                    Box(
                        Modifier
                            .width(4.dp)
                            .height(40.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(12.dp))
                    // 40dp icon container
                    Surface(
                        shape = RoundedCornerShape(10.dp),
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
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "Smart Insight",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                
                // Show refresh button for AI recap when available
                if (recap is DashboardViewModel.RecapState.Ready || recap is DashboardViewModel.RecapState.Error) {
                    IconButton(onClick = onGenerateRecap, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Regenerate", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            
            // Show AI recap content when available, otherwise show basic insights
            when (recap) {
                is DashboardViewModel.RecapState.Ready -> {
                    Text(
                        recap.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                is DashboardViewModel.RecapState.Loading -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Generating AI insight…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                is DashboardViewModel.RecapState.Error -> {
                    Column {
                        Text(
                            "Couldn't generate AI insight (${recap.message}).",
                            style = MaterialTheme.typography.bodySmall,
                            color = SpendVaultTheme.colors.debit,
                        )
                        Spacer(Modifier.height(4.dp))
                        TextButton(onClick = onGenerateRecap, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("Retry", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
                is DashboardViewModel.RecapState.Unavailable -> {
                    // Show basic insight when AI is unavailable
                    BasicInsight(state)
                }
                is DashboardViewModel.RecapState.NoData -> {
                    Text(
                        "Not enough transactions this month yet to generate insights.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                is DashboardViewModel.RecapState.Idle -> {
                    Column {
                        BasicInsight(state)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onGenerateRecap, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                            Text("Generate AI insight", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BasicInsight(state: DashboardUiState) {
    val hasIncome = state.incomeMinor > 0
    val insightText = when {
        !hasIncome -> "Import your SMS messages to unlock personalised spending insights."
        state.spendMinor == 0L -> "No spending recorded yet this month. Tap the bell to review pending SMS."
        else -> {
            val savingsRate = if (state.incomeMinor > 0) {
                ((state.incomeMinor - state.spendMinor) * 100L) / state.incomeMinor
            } else 0L
            when {
                savingsRate > 20 -> "Great job! You're saving ${savingsRate.toInt()}% of your income this month."
                savingsRate > 0 -> "You're saving ${savingsRate.toInt()}% of your income this month."
                else -> "Your spending matches your income this month. Consider reviewing your expenses."
            }
        }
    }
    Text(
        insightText,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = MaterialTheme.typography.bodyMedium.lineHeight,
    )
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
