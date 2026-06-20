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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.BillItem
import com.spendlens.app.ui.viewmodel.BillsViewModel

@Composable
fun BillsScreen(vm: BillsViewModel) {
    val state by vm.state.collectAsState()
    // Refresh detection when the screen opens so newly-imported transactions surface bills.
    LaunchedEffect(Unit) { vm.rescan() }

    LazyColumn(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            OutlinedButton(onClick = { vm.rescan() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text("🔄 Re-scan for recurring bills")
            }
        }
        if (state.items.isEmpty()) {
            item {
                EmptyHint(
                    "No recurring bills detected yet. Once you have a few months of transactions, " +
                        "SpendLens spots subscriptions and regular payments automatically.",
                )
            }
        } else {
            item { SectionHeader("Upcoming bills") }
            items(state.items) { item ->
                BillCard(
                    item = item,
                    currency = state.currency,
                    onToggleReminder = { vm.setReminder(item.bill.id, it) },
                    onDelete = { vm.delete(item.bill.id) },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BillCard(
    item: BillItem,
    currency: String,
    onToggleReminder: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    val due = item.daysUntil
    val dueText = when {
        due <= 0L -> "Due today"
        due == 1L -> "Due tomorrow"
        else -> "Due in $due days · ${item.dueLabel}"
    }
    val urgent = due <= 3L
    ElevatedSurfaceCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.height(36.dp).clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("  ${item.category?.icon ?: "🧾"}  ", style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(item.bill.counterparty, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        dueText,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (urgent) SpendLensTheme.colors.debit else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (urgent) FontWeight.Bold else FontWeight.Normal,
                    )
                }
                Text(
                    "~${Money.format(item.bill.typicalAmountMinor, currency)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Remind me", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(checked = item.bill.reminderEnabled, onCheckedChange = onToggleReminder)
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "Remove bill")
                }
            }
        }
    }
}
