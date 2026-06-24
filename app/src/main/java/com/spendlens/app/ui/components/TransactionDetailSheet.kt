package com.spendlens.app.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import com.spendlens.app.ai.AiBridgeHelper
import com.spendlens.app.ai.PromptGenerator
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.data.db.TransactionSplitEntity
import com.spendlens.app.data.db.RawSmsEntity
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.TransactionDetailViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionDetailSheet(
    txn: TransactionEntity,
    vm: TransactionDetailViewModel,
    onDismiss: () -> Unit,
    onMerchantHistory: (String) -> Unit = {},
) {
    val categories by vm.categories.collectAsState()
    val merchantNames by vm.merchantNames.collectAsState()
    var current by remember(txn.id) { mutableStateOf(txn) }
    var smsBody by remember(txn.id) { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showSplit by remember { mutableStateOf(false) }
    var pendingScope by remember { mutableStateOf<PendingScopeEdit?>(null) }
    val coroutineScope = rememberCoroutineScope()
    val splits by remember(current.id) { vm.splitsFlow(current.id) }
        .collectAsState(initial = emptyList<TransactionSplitEntity>())
    LaunchedEffect(txn.id) { smsBody = vm.smsBody(txn.rawSmsId) }

    val pickReceipt = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.attachReceipt(current, uri) { current = it }
    }

    val isDebit = current.direction == "DEBIT"

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    current.counterparty,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { showRename = true }) { Text("Rename") }
            }
            Text(
                (if (isDebit) "-" else "+") + Money.format(current.amountMinor, current.currency),
                style = MaterialTheme.typography.headlineMedium,
                color = if (isDebit) SpendLensTheme.colors.debit else SpendLensTheme.colors.credit,
                fontWeight = FontWeight.Bold,
            )
            Text(
                Dates.dateTime(current.occurredAt),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { onMerchantHistory(current.counterparty) }) {
                Text("📈 View merchant history")
            }
            Spacer(Modifier.height(12.dp))
            if (current.currency != "INR" && current.amountBaseMinor > 0) {
                DetailLine("In INR", Money.format(current.amountBaseMinor, "INR"))
            }
            DetailLine("Account", current.accountKey)
            DetailLine("Channel", current.channel)
            current.referenceId?.let { DetailLine("Reference", it) }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            // Count-as-expense toggle.
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Count as expense", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Turn off for transfers, salary or refunds you don't want in spending.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = !current.excludedFromExpense,
                    onCheckedChange = { checked ->
                        current = current.copy(excludedFromExpense = !checked)
                        vm.update(current)
                    },
                )
            }
            Spacer(Modifier.height(12.dp))

            Text("Category", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { cat ->
                    FilterChip(
                        selected = current.categoryId == cat.id,
                        onClick = { pendingScope = PendingScopeEdit.Category(cat.id) },
                        label = { Text("${cat.icon} ${cat.name}") },
                    )
                }
                AssistChip(
                    onClick = { showCreate = true },
                    label = { Text("➕ New") },
                    colors = AssistChipDefaults.assistChipColors(),
                )
            }

            if (isDebit) {
                Spacer(Modifier.height(16.dp))
                SplitSection(
                    splits = splits,
                    currency = current.currency,
                    categories = categories,
                    onEdit = { showSplit = true },
                    onClear = { vm.clearSplit(current) { current = it } },
                )
            }

            Spacer(Modifier.height(16.dp))
            NotesAndTags(
                note = current.note.orEmpty(),
                tags = current.tags.orEmpty(),
                onSave = { newNote, newTags ->
                    val normNote = newNote.trim().ifBlank { null }
                    val normTags = newTags.split(",").map { it.trim().lowercase() }
                        .filter { it.isNotEmpty() }.distinct().joinToString(",").ifBlank { null }
                    pendingScope = PendingScopeEdit.Tags(normNote, normTags)
                },
            )

            Spacer(Modifier.height(16.dp))
            ReceiptSection(
                receiptPath = current.receiptUri,
                loadBitmap = { vm.loadReceipt(it) },
                onPick = { pickReceipt.launch("image/*") },
                onRemove = { vm.removeReceipt(current) { current = it } },
            )

            smsBody?.let { body ->
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Original SMS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val context = androidx.compose.ui.platform.LocalContext.current
                    TextButton(onClick = {
                        coroutineScope.launch {
                            val raw = vm.rawSms(current.rawSmsId)
                            if (raw != null) {
                                val prompt = vm.generatePrompt(listOf(raw))
                                AiBridgeHelper.copyAndLaunch(context, prompt)
                            }
                        }
                    }) {
                        Text("🤖 Teach with AI", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                }
            }
        }
    }

    if (showCreate) {
        CategoryCreateDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, icon, color ->
                showCreate = false
                vm.createCategory(name, icon, color) { newId ->
                    current = current.copy(categoryId = newId)
                    vm.update(current)
                }
            },
        )
    }

    if (showSplit) {
        SplitDialog(
            txn = current,
            existing = splits,
            categories = categories,
            onDismiss = { showSplit = false },
            onSave = { parts ->
                showSplit = false
                vm.splitTransaction(current, parts) { current = it }
            },
        )
    }

    if (showRename) {
        RenameMerchantDialog(
            initial = current.counterparty,
            suggestions = merchantNames,
            onDismiss = { showRename = false },
            onRename = { newName ->
                showRename = false
                pendingScope = PendingScopeEdit.Rename(newName)
            },
        )
    }

    pendingScope?.let { edit ->
        ApplyScopeDialog(
            merchant = current.counterparty,
            onDismiss = { pendingScope = null },
            onChoice = { applyToAll ->
                when (edit) {
                    is PendingScopeEdit.Rename -> {
                        vm.renameMerchant(current, edit.newName, applyToAll) { match ->
                            current = current.copy(
                                counterparty = edit.newName.trim(),
                                categoryId = match?.categoryId ?: current.categoryId,
                                excludedFromExpense = match?.excluded ?: current.excludedFromExpense,
                            )
                        }
                    }
                    is PendingScopeEdit.Category -> {
                        current = current.copy(categoryId = edit.categoryId)
                        vm.setCategory(current, edit.categoryId, applyToAll)
                    }
                    is PendingScopeEdit.Tags -> {
                        current = current.copy(note = edit.note, tags = edit.tags)
                        vm.setTags(current, applyToAll)
                    }
                }
                pendingScope = null
            },
        )
    }
}

/** A pending edit awaiting the user's "this one vs. all from this merchant" choice. */
private sealed class PendingScopeEdit {
    data class Rename(val newName: String) : PendingScopeEdit()
    data class Category(val categoryId: Long) : PendingScopeEdit()
    data class Tags(val note: String?, val tags: String?) : PendingScopeEdit()
}

@Composable
private fun ApplyScopeDialog(
    merchant: String,
    onDismiss: () -> Unit,
    onChoice: (applyToAll: Boolean) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onChoice(true) }) { Text("All from $merchant") } },
        dismissButton = { TextButton(onClick = { onChoice(false) }) { Text("Just this one") } },
        title = { Text("Apply to which transactions?") },
        text = {
            Text(
                "Update only this transaction, or every transaction from $merchant " +
                    "and remember it for future ones?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
    )
}

@Composable
private fun RenameMerchantDialog(
    initial: String,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onRename(name.trim()) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Rename merchant") },
        text = {
            Column {
                Text(
                    "Pick a known merchant to also apply its category and expense setting, or type a " +
                        "new name. You'll choose whether this applies to just this transaction or all.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                MerchantSuggestField(
                    value = name,
                    onValueChange = { name = it },
                    suggestions = suggestions,
                    onPick = { name = it },
                    label = "Merchant name",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NotesAndTags(
    note: String,
    tags: String,
    onSave: (note: String, tags: String) -> Unit,
) {
    var noteDraft by remember(note) { mutableStateOf(note) }
    var tagsDraft by remember(tags) { mutableStateOf(tags) }
    val changed = noteDraft != note || tagsDraft != tags

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Notes & tags", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (changed) {
            TextButton(onClick = { onSave(noteDraft, tagsDraft) }) { Text("Save") }
        }
    }
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = noteDraft,
        onValueChange = { noteDraft = it },
        label = { Text("Note") },
        minLines = 1,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = tagsDraft,
        onValueChange = { tagsDraft = it },
        label = { Text("Tags (comma separated)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    val chips = tagsDraft.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (chips.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            chips.forEach { tag -> AssistChip(onClick = {}, label = { Text("#$tag") }) }
        }
    }
}

@Composable
private fun ReceiptSection(
    receiptPath: String?,
    loadBitmap: suspend (String) -> android.graphics.Bitmap?,
    onPick: () -> Unit,
    onRemove: () -> Unit,
) {
    Text("Receipt", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    if (receiptPath == null) {
        OutlinedButton(onClick = onPick) { Text("📎 Attach receipt") }
    } else {
        var bmp by remember(receiptPath) { mutableStateOf<ImageBitmap?>(null) }
        LaunchedEffect(receiptPath) { bmp = loadBitmap(receiptPath)?.asImageBitmap() }
        bmp?.let { image ->
            Image(
                bitmap = image,
                contentDescription = "Attached receipt",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onPick) { Text("Replace") }
            TextButton(onClick = onRemove) { Text("Remove") }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoryCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, icon: String, color: Long) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf(EMOJIS.first()) }
    var color by remember { mutableStateOf(COLORS.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), icon, color) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("New category") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("Icon", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EMOJIS.forEach { e ->
                        val selected = e == icon
                        Surface(
                            shape = CircleShape,
                            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(40.dp).clip(CircleShape).clickable { icon = e },
                        ) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Text(e)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text("Colour", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    COLORS.forEach { c ->
                        val selected = c == color
                        Spacer(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(
                                    width = if (selected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable { color = c },
                        )
                    }
                }
            }
        },
    )
}

// ── Split transaction across categories (issue #11) ─────────────────────────

private fun splitAmountToMinor(text: String): Long =
    ((text.trim().toDoubleOrNull() ?: 0.0) * 100).toLong()

private fun minorToSplitField(minor: Long): String =
    if (minor % 100 == 0L) (minor / 100).toString() else (minor / 100.0).toString()

@Composable
private fun SplitSection(
    splits: List<TransactionSplitEntity>,
    currency: String,
    categories: List<CategoryEntity>,
    onEdit: () -> Unit,
    onClear: () -> Unit,
) {
    fun catLabel(id: Long?): String =
        categories.firstOrNull { it.id == id }?.let { "${it.icon} ${it.name}" } ?: "Uncategorized"

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Split across categories", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (splits.isEmpty()) {
            TextButton(onClick = onEdit) { Text("Split") }
        } else {
            TextButton(onClick = onEdit) { Text("Edit") }
            TextButton(onClick = onClear) { Text("Remove") }
        }
    }
    if (splits.isEmpty()) {
        Text(
            "Divide this transaction into category portions. Analytics and budgets count the portions.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Spacer(Modifier.height(6.dp))
        splits.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(catLabel(s.categoryId), style = MaterialTheme.typography.bodyMedium)
                Text(Money.format(s.amountMinor, currency), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitDialog(
    txn: TransactionEntity,
    existing: List<TransactionSplitEntity>,
    categories: List<CategoryEntity>,
    onDismiss: () -> Unit,
    onSave: (List<Pair<Long?, Long>>) -> Unit,
) {
    // Each row: selected categoryId + amount text (major units, in the txn's display currency).
    val rows = remember {
        mutableStateListOf<Pair<Long?, String>>().apply {
            if (existing.isNotEmpty()) {
                existing.forEach { add(it.categoryId to minorToSplitField(it.amountMinor)) }
            } else {
                add(txn.categoryId to "")
                add((null as Long?) to "")
            }
        }
    }
    val totalMinor = txn.amountMinor
    val enteredMinor = rows.sumOf { splitAmountToMinor(it.second) }
    val remaining = totalMinor - enteredMinor
    val valid = remaining == 0L && rows.size >= 2 && rows.all { it.second.isNotBlank() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(rows.map { it.first to splitAmountToMinor(it.second) }) },
            ) { Text("Save", color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = { Text("Split ${Money.format(totalMinor, txn.currency)}") },
        text = {
            Column {
                Text(
                    if (remaining == 0L) "Allocated in full."
                    else if (remaining > 0L) "Remaining ${Money.format(remaining, txn.currency)}"
                    else "Over by ${Money.format(-remaining, txn.currency)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (remaining == 0L) MaterialTheme.colorScheme.primary else SpendLensTheme.colors.debit,
                )
                Spacer(Modifier.height(8.dp))
                rows.forEachIndexed { i, row ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SplitCategoryPicker(
                            categories = categories,
                            selectedId = row.first,
                            onSelect = { rows[i] = it to row.second },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = row.second,
                            onValueChange = { new -> rows[i] = row.first to new.filter { it.isDigit() || it == '.' } },
                            singleLine = true,
                            label = { Text(txn.currency) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(96.dp),
                        )
                        if (rows.size > 2) {
                            TextButton(onClick = { rows.removeAt(i) }) { Text("✕") }
                        }
                    }
                }
                TextButton(onClick = { rows.add((null as Long?) to "") }) { Text("➕ Add category") }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitCategoryPicker(
    categories: List<CategoryEntity>,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = categories.firstOrNull { it.id == selectedId }?.let { "${it.icon} ${it.name}" } ?: "Uncategorized"
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Uncategorized") }, onClick = { onSelect(null); expanded = false })
            categories.forEach { cat ->
                DropdownMenuItem(
                    text = { Text("${cat.icon} ${cat.name}") },
                    onClick = { onSelect(cat.id); expanded = false },
                )
            }
        }
    }
}

private val EMOJIS = listOf(
    "📦", "🛒", "🍽️", "🚕", "🛍️", "💡", "🎬", "🩺", "✈️", "💰", "🔁", "🏠", "🎓", "🐾", "💪", "🎁",
)
private val COLORS = listOf(
    0xFFEF6C50, 0xFF66BB6A, 0xFF42A5F5, 0xFFAB47BC, 0xFFFFB300,
    0xFFEC407A, 0xFF26C6DA, 0xFF7E57C2, 0xFF8D9CA8, 0xFF90A4AE,
)
