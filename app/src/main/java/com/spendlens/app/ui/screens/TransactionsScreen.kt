package com.spendlens.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.viewmodel.DateRangeFilter
import com.spendlens.app.ui.viewmodel.TransactionsViewModel
import com.spendlens.app.ui.viewmodel.TxnFilter

@Composable
fun TransactionsScreen(
    vm: TransactionsViewModel,
    pendingReviewCount: Int = 0,
    onOpenReview: () -> Unit = {},
    onTransactionClick: (TransactionEntity) -> Unit = {},
    onEditTransaction: (TransactionEntity) -> Unit = {},
    initialCategoryId: Long? = null,
    onInitialCategoryConsumed: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    var showFilters by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Apply a category filter passed in from another screen (e.g. tapping a category in Insights).
    LaunchedEffect(initialCategoryId) {
        if (initialCategoryId != null) {
            vm.setCategory(initialCategoryId)
            onInitialCategoryConsumed()
        }
    }

    val exportMessage by vm.exportMessage.collectAsState()
    LaunchedEffect(exportMessage) {
        exportMessage?.let {
            android.widget.Toast.makeText(context, it, android.widget.Toast.LENGTH_SHORT).show()
            vm.consumeExportMessage()
        }
    }

    // Group by display-day label
    val grouped = state.items
        .groupBy { Dates.date(it.occurredAt) }
        .entries.toList()

    Column(Modifier.fillMaxSize()) {

        // ── Search + filter row ──────────────────────────────────────────────
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    TextField(
                        value = state.query,
                        onValueChange = vm::setQuery,
                        singleLine = true,
                        placeholder = {
                            Text(
                                "Search merchant, account, note…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor        = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor      = MaterialTheme.colorScheme.onSurface,
                        ),
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (state.filters.activeCount > 0)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        if (state.filters.activeCount > 0)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        else
                            Color.White.copy(alpha = 0.06f),
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    IconButton(onClick = { showFilters = !showFilters }) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = "Filter",
                            tint = if (state.filters.activeCount > 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                    modifier = Modifier.size(48.dp),
                ) {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Export as CSV") },
                            onClick = { showMenu = false; vm.exportCsv(context) },
                        )
                        DropdownMenuItem(
                            text = { Text("Export as PDF") },
                            onClick = { showMenu = false; vm.exportPdf(context) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Direction chips
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TxnFilter.entries.forEach { f ->
                    FilterChip(
                        selected = state.filters.direction == f,
                        onClick = { vm.setDirection(f) },
                        label = { Text(f.label, style = MaterialTheme.typography.labelMedium) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor     = MaterialTheme.colorScheme.primary,
                            containerColor         = MaterialTheme.colorScheme.surfaceContainerHigh,
                            labelColor             = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = state.filters.direction == f,
                            selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                            borderColor = Color.White.copy(alpha = 0.06f),
                        ),
                    )
                }
                if (state.filters.activeCount > 0) {
                    TextButton(onClick = { vm.clearFilters() }) {
                        Text(
                            "Clear (${state.filters.activeCount})",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Expanded filter panel
            if (showFilters) {
                FilterPanel(vm, state.filters.activeCount)
            }
        }

        // ── Feed ─────────────────────────────────────────────────────────────
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            // Pending banner
            if (pendingReviewCount > 0) {
                item {
                    Surface(
                        onClick = onOpenReview,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                modifier = Modifier.size(40.dp),
                            ) {
                                Icon(
                                    Icons.Filled.Sms, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Unparsed Messages", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                                Text("Tap to categorise raw SMS data", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Surface(
                                shape = RoundedCornerShape(24.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
                            ) {
                                Text(
                                    "$pendingReviewCount PENDING",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    letterSpacing = 0.5.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            if (grouped.isEmpty()) {
                item { EmptyHint("No matching transactions.") }
            } else {
                grouped.forEach { (dateLabel, txns) ->
                    item {
                        Text(
                            dateLabel.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                        )
                    }
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                        ) {
                            Column {
                                txns.forEachIndexed { i, txn ->
                                    // Manual rows open the editable entry form; parsed rows open the detail sheet.
                                    val isManual = txn.channel == com.spendlens.app.data.db.TransactionChannel.MANUAL
                                    TransactionRow(
                                        txn,
                                        state.categories,
                                        merchantEmojis = state.merchantEmojis,
                                        onClick = { if (isManual) onEditTransaction(txn) else onTransactionClick(txn) },
                                    )
                                    if (i < txns.size - 1) {
                                        HorizontalDivider(
                                            modifier = Modifier.padding(horizontal = 16.dp),
                                            color = Color.White.copy(alpha = 0.05f),
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun FilterPanel(vm: TransactionsViewModel, activeCount: Int) {
    val state by vm.state.collectAsState()
    val f = state.filters
    var minText by remember { mutableStateOf("") }
    var maxText by remember { mutableStateOf("") }

    fun applyAmount() {
        val min = minText.toDoubleOrNull()?.let { (it * 100).toLong() }
        val max = maxText.toDoubleOrNull()?.let { (it * 100).toLong() }
        vm.setAmountRange(min, max)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FilterLabel("Date range")
        ChipRow {
            DateRangeFilter.entries.forEach { r ->
                FilterChip(
                    selected = f.dateRange == r,
                    onClick = { vm.setDateRange(r) },
                    label = { Text(r.label, style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor     = MaterialTheme.colorScheme.primary,
                    ),
                )
            }
        }

        if (state.categories.isNotEmpty()) {
            FilterLabel("Category")
            ChipRow {
                FilterChip(
                    selected = f.categoryId == null,
                    onClick = { vm.setCategory(null) },
                    label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor     = MaterialTheme.colorScheme.primary,
                    ),
                )
                FilterChip(
                    selected = f.categoryId == TransactionsViewModel.UNCATEGORIZED_CATEGORY_ID,
                    onClick = { vm.setCategory(TransactionsViewModel.UNCATEGORIZED_CATEGORY_ID) },
                    label = { Text("Uncategorized", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor     = MaterialTheme.colorScheme.primary,
                    ),
                )
                state.categories.values.forEach { cat ->
                    FilterChip(
                        selected = f.categoryId == cat.id,
                        onClick = { vm.setCategory(cat.id) },
                        label = { Text(cat.name, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor     = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }

        if (state.accounts.isNotEmpty()) {
            FilterLabel("Account")
            ChipRow {
                FilterChip(
                    selected = f.accountKey == null,
                    onClick = { vm.setAccount(null) },
                    label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        selectedLabelColor     = MaterialTheme.colorScheme.primary,
                    ),
                )
                state.accounts.forEach { acct ->
                    FilterChip(
                        selected = f.accountKey == acct,
                        onClick = { vm.setAccount(acct) },
                        label = { Text(acct, style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            selectedLabelColor     = MaterialTheme.colorScheme.primary,
                        ),
                    )
                }
            }
        }

        FilterLabel("Amount range")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = minText,
                onValueChange = { minText = it; applyAmount() },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Min", style = MaterialTheme.typography.labelSmall) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor  = MaterialTheme.colorScheme.primary,
                ),
            )
            OutlinedTextField(
                value = maxText,
                onValueChange = { maxText = it; applyAmount() },
                modifier = Modifier.weight(1f),
                singleLine = true,
                label = { Text("Max", style = MaterialTheme.typography.labelSmall) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor  = MaterialTheme.colorScheme.primary,
                ),
            )
        }
    }
}

@Composable
private fun FilterLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}
