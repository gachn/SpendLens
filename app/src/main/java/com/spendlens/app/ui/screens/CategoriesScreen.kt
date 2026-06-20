package com.spendlens.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendlens.app.ui.components.CategoryCreateDialog
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.MonthDropdown
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.CategoriesViewModel
import com.spendlens.app.ui.viewmodel.CategorySpend

@Composable
fun CategoriesScreen(vm: CategoriesViewModel, onOpenBudgets: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    var showCreate by remember { mutableStateOf(false) }
    val maxAmount = state.items.maxOfOrNull { it.amountMinor }?.takeIf { it > 0 } ?: 1L

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            ElevatedSurfaceCard {
                Column {
                    MonthDropdown(state.selectedMonth, state.monthOptions, vm::setMonth)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        Money.format(state.totalMinor, state.currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Total spent in ${state.monthLabel}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { showCreate = true }, modifier = Modifier.weight(1f)) {
                    Text("➕ New category")
                }
                OutlinedButton(onClick = onOpenBudgets, modifier = Modifier.weight(1f)) {
                    Text("📊 Budgets")
                }
            }
        }
        item { SectionHeader("Spending by category") }
        items(state.items) { item ->
            CategoryRow(item, maxAmount, state.currency)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    if (showCreate) {
        CategoryCreateDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, icon, color ->
                showCreate = false
                vm.createCategory(name, icon, color)
            },
        )
    }
}

@Composable
private fun CategoryRow(item: CategorySpend, maxAmount: Long, currency: String) {
    val fraction = (item.amountMinor.toFloat() / maxAmount.toFloat()).coerceIn(0f, 1f)
    val color = Color(item.category.color)
    ElevatedSurfaceCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(36.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("  ${item.category.icon}  ", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    item.category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                )
                Text(
                    Money.format(item.amountMinor, currency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(8.dp))
            // Proportion bar.
            Box(
                Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Box(
                    Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color),
                )
            }
        }
    }
}
