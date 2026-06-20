package com.spendlens.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.BudgetRow
import com.spendlens.app.ui.viewmodel.BudgetsViewModel

@Composable
fun BudgetsScreen(vm: BudgetsViewModel) {
    val state by vm.state.collectAsState()
    var editing by remember { mutableStateOf<BudgetRow?>(null) }

    val budgeted = state.rows.filter { it.limitMinor > 0 }
    val totalLimit = budgeted.sumOf { it.limitMinor }
    val totalSpent = budgeted.sumOf { it.spentMinor }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            ElevatedSurfaceCard {
                Column {
                    Text(state.monthLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "${Money.format(totalSpent, state.currency)} of ${Money.format(totalLimit, state.currency)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("Spent against your monthly budgets", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { SectionHeader("Tap a category to set its monthly limit") }
        items(state.rows) { row ->
            BudgetRowCard(row, state.currency, onClick = { editing = row })
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    editing?.let { row ->
        SetBudgetDialog(
            row = row,
            currency = state.currency,
            onDismiss = { editing = null },
            onSave = { limitMinor ->
                vm.setBudget(row.category.id, limitMinor)
                editing = null
            },
        )
    }
}

@Composable
private fun BudgetRowCard(row: BudgetRow, currency: String, onClick: () -> Unit) {
    val hasBudget = row.limitMinor > 0
    val color = Color(row.category.color)
    val fraction = if (hasBudget) (row.spentMinor.toFloat() / row.limitMinor.toFloat()).coerceIn(0f, 1f) else 0f
    val overBudget = hasBudget && row.spentMinor > row.limitMinor
    val barColor = if (overBudget) SpendLensTheme.colors.debit else color

    ElevatedSurfaceCard(modifier = Modifier.clickable(onClick = onClick)) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(36.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("  ${row.category.icon}  ", style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(row.category.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (hasBudget) {
                            "${Money.format(row.spentMinor, currency)} of ${Money.format(row.limitMinor, currency)}"
                        } else {
                            "${Money.format(row.spentMinor, currency)} spent · no budget"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = if (overBudget) SpendLensTheme.colors.debit else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (overBudget) {
                    Text("over", style = MaterialTheme.typography.labelMedium, color = SpendLensTheme.colors.debit, fontWeight = FontWeight.Bold)
                }
            }
            if (hasBudget) {
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Box(Modifier.fillMaxWidth(fraction).height(8.dp).clip(RoundedCornerShape(4.dp)).background(barColor))
                }
            }
        }
    }
}

@Composable
private fun SetBudgetDialog(
    row: BudgetRow,
    currency: String,
    onDismiss: () -> Unit,
    onSave: (limitMinor: Long) -> Unit,
) {
    var text by remember { mutableStateOf(if (row.limitMinor > 0) (row.limitMinor / 100).toString() else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val amount = text.trim().toDoubleOrNull() ?: 0.0
                onSave((amount * 100).toLong())
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("${row.category.icon} ${row.category.name} budget") },
        text = {
            Column {
                Text(
                    "Set a monthly limit. Enter 0 to remove the budget.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { new -> text = new.filter { it.isDigit() || it == '.' } },
                    singleLine = true,
                    label = { Text("Monthly limit ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
