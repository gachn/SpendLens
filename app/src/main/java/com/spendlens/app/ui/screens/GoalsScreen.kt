package com.spendlens.app.ui.screens

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.spendlens.app.data.db.SavingsGoalEntity
import com.spendlens.app.ui.components.GlassCard
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.GoalItem
import com.spendlens.app.ui.viewmodel.GoalsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(vm: GoalsViewModel, onBack: () -> Unit = {}) {
    val state by vm.state.collectAsState()
    var showAdd by remember { mutableStateOf(false) }
    var contributeTo by remember { mutableStateOf<GoalItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Savings Goals") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, contentDescription = "Add goal") }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        if (state.goals.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No savings goals yet. Tap + to set a target like a vacation or emergency fund.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                items(state.goals, key = { it.goal.id }) { item ->
                    GoalCard(
                        item = item,
                        currency = state.currency,
                        onContribute = { contributeTo = item },
                        onDelete = { vm.deleteGoal(item.goal.id) },
                    )
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }

    if (showAdd) {
        AddGoalDialog(
            accounts = state.accounts,
            currency = state.currency,
            onDismiss = { showAdd = false },
            onCreate = { name, targetMinor, deadline, account ->
                vm.createGoal(name, targetMinor, deadline, account)
                showAdd = false
            },
        )
    }

    contributeTo?.let { item ->
        ContributeDialog(
            item = item,
            currency = state.currency,
            onDismiss = { contributeTo = null },
            onAdd = { deltaMinor ->
                vm.addContribution(item.goal.id, deltaMinor)
                contributeTo = null
            },
        )
    }
}

@Composable
private fun GoalCard(item: GoalItem, currency: String, onContribute: () -> Unit, onDelete: () -> Unit) {
    val g = item.goal
    val barColor = if (item.reached) SpendLensTheme.colors.credit else MaterialTheme.colorScheme.primary
    GlassCard {
        Column {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(g.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (item.reached) {
                    Surface(shape = RoundedCornerShape(8.dp), color = SpendLensTheme.colors.credit.copy(alpha = 0.15f)) {
                        Text(
                            "REACHED",
                            style = MaterialTheme.typography.labelSmall,
                            color = SpendLensTheme.colors.credit,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${Money.format(item.savedMinor, currency)} of ${Money.format(g.targetAmountMinor, currency)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { item.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )
            Spacer(Modifier.height(8.dp))
            val meta = buildList {
                add("${(item.progress * 100).toInt()}%")
                g.linkedAccountKey?.let { add("Auto · $it") }
                g.deadline?.let { add("By ${Dates.date(it)}") }
                if (!item.reached) item.projectedCompletion?.let { add("Est. ${Dates.date(it)}") }
            }.joinToString("  ·  ")
            Text(meta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onContribute) { Text("Add funds") }
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private fun amountToMinor(text: String): Long = ((text.trim().toDoubleOrNull() ?: 0.0) * 100).toLong()

@Composable
private fun AddGoalDialog(
    accounts: List<String>,
    currency: String,
    onDismiss: () -> Unit,
    onCreate: (name: String, targetMinor: Long, deadline: Long?, account: String?) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    var deadlineDays by remember { mutableStateOf("") }
    var account by remember { mutableStateOf<String?>(null) }
    var accountMenu by remember { mutableStateOf(false) }
    val valid = name.isNotBlank() && amountToMinor(target) > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    val days = deadlineDays.trim().toLongOrNull()
                    val deadline = days?.takeIf { it > 0 }?.let { System.currentTimeMillis() + it * 86_400_000L }
                    onCreate(name.trim(), amountToMinor(target), deadline, account)
                },
            ) { Text("Create", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("New savings goal") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name (e.g. Vacation fund)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = target,
                    onValueChange = { new -> target = new.filter { it.isDigit() || it == '.' } },
                    singleLine = true,
                    label = { Text("Target ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = deadlineDays,
                    onValueChange = { new -> deadlineDays = new.filter { it.isDigit() } },
                    singleLine = true,
                    label = { Text("Deadline in days (optional)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Link an account (optional)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Box {
                    OutlinedButton(onClick = { accountMenu = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(account ?: "None — manual only")
                    }
                    DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                        DropdownMenuItem(text = { Text("None — manual only") }, onClick = { account = null; accountMenu = false })
                        accounts.forEach { acc ->
                            DropdownMenuItem(text = { Text(acc) }, onClick = { account = acc; accountMenu = false })
                        }
                    }
                }
                if (account != null) {
                    Text(
                        "Credits into $account will auto-increment progress.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun ContributeDialog(item: GoalItem, currency: String, onDismiss: () -> Unit, onAdd: (Long) -> Unit) {
    var amount by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = amountToMinor(amount) != 0L,
                onClick = { onAdd(amountToMinor(amount)) },
            ) { Text("Add", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Add to ${item.goal.name}") },
        text = {
            Column {
                Text(
                    "Record a manual contribution. Use a negative amount to correct an over-count.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = { new -> amount = new.filter { it.isDigit() || it == '.' || it == '-' } },
                    singleLine = true,
                    label = { Text("Amount ($currency)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}
