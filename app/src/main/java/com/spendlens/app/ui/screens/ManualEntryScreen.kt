package com.spendlens.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.spendlens.app.ai.CategorySuggestion
import com.spendlens.app.ai.MerchantWarning
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.ui.components.CategoryCreateDialog
import com.spendlens.app.ui.components.MerchantSuggestField
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.util.ManualAmount
import com.spendlens.app.ui.viewmodel.ManualEntryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

/**
 * Add / edit a manual (cash / non-SMS) transaction. [editId] non-null = edit that row; null = add.
 * Save is blocked until the amount is a valid positive number and a category is chosen (PRD AC-5).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ManualEntryScreen(
    vm: ManualEntryViewModel,
    editId: Long?,
    onClose: () -> Unit,
) {
    val categories by vm.categories.collectAsState()
    val accounts by vm.accounts.collectAsState()
    val merchantNames by vm.merchantNames.collectAsState()
    val isPremium by vm.isPremium.collectAsState()
    val scope = rememberCoroutineScope()

    var categorySuggestions by remember { mutableStateOf<List<CategorySuggestion>>(emptyList()) }
    var duplicateWarning by remember { mutableStateOf<MerchantWarning?>(null) }

    var editing by remember { mutableStateOf<TransactionEntity?>(null) }
    var amountText by remember { mutableStateOf("") }
    var amountTouched by remember { mutableStateOf(false) }
    var currency by remember { mutableStateOf(vm.baseCurrency) }
    var isDebit by remember { mutableStateOf(true) }
    var categoryId by remember { mutableStateOf<Long?>(null) }
    var categoryTouched by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf(ManualEntryViewModel.CASH_ACCOUNT) }
    var occurredAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var counterparty by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var excluded by remember { mutableStateOf(false) }

    var showCreateCategory by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Prefill in edit mode.
    LaunchedEffect(editId) {
        val id = editId ?: return@LaunchedEffect
        vm.loadById(id)?.let { txn ->
            editing = txn
            amountText = (txn.amountMinor / 100.0).toString()
            currency = txn.currency
            isDebit = txn.direction == "DEBIT"
            categoryId = txn.categoryId
            account = txn.accountKey
            occurredAt = txn.occurredAt
            counterparty = txn.counterparty
            note = txn.note.orEmpty()
            tags = txn.tags.orEmpty()
            excluded = txn.excludedFromExpense
        }
    }

    // Premium: suggest a category and warn about likely-duplicate merchants as the user types,
    // debounced so it doesn't fire on every keystroke.
    LaunchedEffect(counterparty, isPremium) {
        if (!isPremium || counterparty.trim().length < 3) {
            categorySuggestions = emptyList()
            duplicateWarning = null
            return@LaunchedEffect
        }
        delay(400)
        categorySuggestions = if (categoryId == null) vm.suggestCategoriesForMerchant(counterparty) else emptyList()
        duplicateWarning = vm.checkDuplicateMerchant(counterparty)
    }

    val amountMinor = ManualAmount.parseMinor(amountText)
    val amountError = amountTouched && amountMinor == null
    val categoryError = categoryTouched && categoryId == null
    val canSave = amountMinor != null && categoryId != null

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("Cancel") }
            Text(
                if (editing == null) "Add transaction" else "Edit transaction",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = amountText,
            onValueChange = { amountText = it; amountTouched = true },
            label = { Text("Amount") },
            singleLine = true,
            isError = amountError,
            supportingText = if (amountError) {
                { Text("Enter a positive amount") }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        // Direction
        Text("Type", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = isDebit, onClick = { isDebit = true }, label = { Text("Expense") })
            FilterChip(selected = !isDebit, onClick = { isDebit = false }, label = { Text("Income") })
        }
        Spacer(Modifier.height(12.dp))

        // Currency
        var currencyExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = currencyExpanded,
            onExpandedChange = { currencyExpanded = it },
        ) {
            OutlinedTextField(
                value = currency,
                onValueChange = {},
                readOnly = true,
                label = { Text("Currency") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = currencyExpanded, onDismissRequest = { currencyExpanded = false }) {
                vm.currencies.forEach { code ->
                    DropdownMenuItem(
                        text = { Text(code) },
                        onClick = { currency = code; currencyExpanded = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Category
        Text("Category", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { cat ->
                FilterChip(
                    selected = categoryId == cat.id,
                    onClick = { categoryId = cat.id; categoryTouched = true; categorySuggestions = emptyList() },
                    label = { Text("${cat.icon} ${cat.name}") },
                )
            }
            AssistChip(onClick = { showCreateCategory = true }, label = { Text("➕ New") })
        }
        if (categoryError) {
            Text(
                "Pick a category",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        Spacer(Modifier.height(12.dp))

        // Account
        Text("Account", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEach { key ->
                FilterChip(
                    selected = account == key,
                    onClick = { account = key },
                    label = { Text(key) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        // Date-time
        Text("When", style = MaterialTheme.typography.labelMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Dates.dateTime(occurredAt), modifier = Modifier.weight(1f))
            TextButton(onClick = { showDatePicker = true }) { Text("Date") }
            TextButton(onClick = { showTimePicker = true }) { Text("Time") }
        }
        Spacer(Modifier.height(12.dp))

        MerchantSuggestField(
            value = counterparty,
            onValueChange = { counterparty = it },
            suggestions = merchantNames,
            onPick = { name ->
                // Known merchant chosen → auto-fill its remembered category + expense flag.
                scope.launch {
                    vm.resolveMerchant(name)?.let { match ->
                        match.categoryId?.let { categoryId = it; categoryTouched = true }
                        excluded = match.excluded
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        duplicateWarning?.let { warning ->
            Spacer(Modifier.height(8.dp))
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Did you mean \"${warning.existingMerchant}\"?",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Used ${warning.transactionCount} time${if (warning.transactionCount == 1) "" else "s"} before",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = {
                        counterparty = warning.existingMerchant
                        duplicateWarning = null
                        scope.launch {
                            vm.resolveMerchant(warning.existingMerchant)?.let { match ->
                                match.categoryId?.let { categoryId = it; categoryTouched = true }
                                excluded = match.excluded
                            }
                        }
                    }) { Text("Use it") }
                }
            }
        }

        if (categorySuggestions.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Suggested category", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categorySuggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = {
                            categoryId = suggestion.categoryId
                            categoryTouched = true
                            categorySuggestions = emptyList()
                        },
                        label = { Text("${suggestion.icon} ${suggestion.name}") },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Count-as-expense toggle (auto-set when a known merchant is picked).
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Count as expense", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Turn off for transfers, salary or refunds you don't want in spending.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = !excluded, onCheckedChange = { excluded = !it })
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Note") },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = tags,
            onValueChange = { tags = it },
            label = { Text("Tags (comma separated)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))

        Button(
            onClick = {
                amountTouched = true
                categoryTouched = true
                val minor = amountMinor ?: return@Button
                val cat = categoryId ?: return@Button
                vm.save(
                    editing = editing,
                    amountMinor = minor,
                    currency = currency,
                    direction = if (isDebit) "DEBIT" else "CREDIT",
                    accountKey = account.ifBlank { ManualEntryViewModel.CASH_ACCOUNT },
                    counterparty = counterparty.trim().ifBlank { "Manual entry" },
                    occurredAt = occurredAt,
                    categoryId = cat,
                    note = note.trim().ifBlank { null },
                    tags = tags.split(",").map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() }.distinct().joinToString(",").ifBlank { null },
                    excludedFromExpense = excluded,
                    onDone = onClose,
                )
            },
            enabled = canSave,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (editing == null) "Save transaction" else "Save changes") }

        // Delete is only offered when editing an existing manual entry (FR-C lifecycle).
        editing?.let { existing ->
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete", color = MaterialTheme.colorScheme.error) }

            if (showDeleteConfirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    confirmButton = {
                        TextButton(onClick = {
                            showDeleteConfirm = false
                            vm.delete(existing.id, onClose)
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") } },
                    title = { Text("Delete transaction?") },
                    text = { Text("This permanently removes the manual entry and updates your totals.") },
                )
            }
        }
    }

    if (showCreateCategory) {
        CategoryCreateDialog(
            onDismiss = { showCreateCategory = false },
            onCreate = { name, icon, color ->
                showCreateCategory = false
                vm.createCategory(name, icon, color) { newId ->
                    categoryId = newId
                    categoryTouched = true
                }
            },
        )
    }

    if (showDatePicker) {
        val zone = ZoneId.systemDefault()
        val state = rememberDatePickerState(initialSelectedDateMillis = occurredAt)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { picked ->
                        val newDate = Instant.ofEpochMilli(picked).atZone(ZoneId.of("UTC")).toLocalDate()
                        val time = Instant.ofEpochMilli(occurredAt).atZone(zone).toLocalTime()
                        occurredAt = newDate.atTime(time).atZone(zone).toInstant().toEpochMilli()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }

    if (showTimePicker) {
        val zone = ZoneId.systemDefault()
        val current = Instant.ofEpochMilli(occurredAt).atZone(zone)
        val state = rememberTimePickerState(initialHour = current.hour, initialMinute = current.minute)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTimePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val date = current.toLocalDate()
                    occurredAt = date.atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli()
                    showTimePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
