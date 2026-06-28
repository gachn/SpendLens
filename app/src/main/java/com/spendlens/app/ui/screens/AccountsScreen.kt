package com.spendlens.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.CreditCard
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spendlens.app.data.db.CategoryEntity
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.GlassCard
import com.spendlens.app.ui.components.MonthDropdown
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.ui.components.SummaryStat
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.theme.BankBranding
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.ui.util.Dates
import com.spendlens.app.ui.util.Money
import com.spendlens.app.ui.viewmodel.AccountSummary
import com.spendlens.app.ui.viewmodel.AccountsViewModel

@Composable
fun AccountsScreen(vm: AccountsViewModel, onTransactionClick: (TransactionEntity) -> Unit = {}) {
    val state by vm.state.collectAsState()
    var openKey by remember { mutableStateOf<String?>(null) }
    var showEmpty by remember { mutableStateOf(false) }
    val open = openKey?.let { key ->
        (state.bankAccounts + state.cards).firstOrNull { it.accountKey == key }
    }

    if (open != null) {
        BackHandler { openKey = null }
        AccountDetail(
            account = open,
            monthLabel = Dates.label(state.selectedMonth),
            categories = state.categories,
            onBack = { openKey = null },
            onTransactionClick = onTransactionClick,
            onSetStatementCycleDay = { day -> vm.setStatementCycleDay(open.accountKey, day) },
            onSetBalance = { minor -> vm.setManualBalance(open.accountKey, minor, open.isCard) },
        )
        return
    }

    val banks = if (showEmpty) state.bankAccounts else state.bankAccounts.filter { it.hasActivity }
    val cards = if (showEmpty) state.cards else state.cards.filter { it.hasActivity }
    val hiddenCount = (state.bankAccounts.count { !it.hasActivity }) + (state.cards.count { !it.hasActivity })

    var showAllBanks by remember { mutableStateOf(false) }
    var showAllCards by remember { mutableStateOf(false) }

    if (banks.isEmpty() && cards.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Debit by month", style = MaterialTheme.typography.titleMedium)
                MonthDropdown(state.selectedMonth, state.months, vm::setMonth)
            }
            EmptyHint(
                if (state.bankAccounts.isEmpty() && state.cards.isEmpty()) {
                    "No accounts yet. They appear once transactions are parsed."
                } else {
                    "No account activity in ${Dates.label(state.selectedMonth)}."
                },
            )
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Debit by month", style = MaterialTheme.typography.titleMedium)
                MonthDropdown(state.selectedMonth, state.months, vm::setMonth)
            }
        }
        if (hiddenCount > 0) {
            item {
                TextButton(
                    onClick = { showEmpty = !showEmpty },
                    modifier = Modifier.padding(horizontal = 12.dp),
                ) {
                    Text(if (showEmpty) "Hide $hiddenCount inactive" else "Show $hiddenCount inactive")
                }
            }
        }
        item {
            NetLiquidBalanceCard(
                bankAccounts = state.bankAccounts,
                cards = state.cards,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        if (banks.isNotEmpty()) {
            item {
                AccountCarouselSection(
                    title = "Bank accounts",
                    count = banks.size,
                    expanded = showAllBanks,
                    onToggle = { showAllBanks = !showAllBanks },
                    cardWidth = 252.dp,
                    items = banks,
                ) { acct, mod ->
                    BankAccountRow(
                        acct = acct,
                        modifier = mod,
                        onClick = { openKey = acct.accountKey },
                        onRename = { vm.setAccountName(acct.accountKey, it) },
                    )
                }
            }
        }
        if (cards.isNotEmpty()) {
            item {
                AccountCarouselSection(
                    title = "Credit cards",
                    count = cards.size,
                    expanded = showAllCards,
                    onToggle = { showAllCards = !showAllCards },
                    cardWidth = 264.dp,
                    items = cards,
                ) { acct, mod ->
                    CreditCardRow(
                        acct = acct,
                        modifier = mod,
                        onClick = { openKey = acct.accountKey },
                        onRename = { vm.setAccountName(acct.accountKey, it) },
                    )
                }
            }
        }
        smartInsight(state.cards)?.let { insight ->
            item { SmartInsightModule(insight, modifier = Modifier.padding(horizontal = 16.dp)) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun AccountCarouselSection(
    title: String,
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    cardWidth: Dp,
    items: List<AccountSummary>,
    itemContent: @Composable (AccountSummary, Modifier) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onToggle) {
            Text(if (expanded) "Show less" else "View all ($count)")
        }
    }
    if (expanded) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items.forEach { acct -> itemContent(acct, Modifier.fillMaxWidth()) }
        }
    } else {
        val listState = rememberLazyListState()
        LaunchedEffect(items) { listState.scrollToItem(0) }
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(items, key = { it.accountKey }) { acct ->
                itemContent(acct, Modifier.width(cardWidth).wrapContentHeight())
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Net Liquid Balance — hero summary card (mock: "Net Liquid Balance")
// ---------------------------------------------------------------------------

@Composable
private fun NetLiquidBalanceCard(
    bankAccounts: List<AccountSummary>,
    cards: List<AccountSummary>,
    modifier: Modifier = Modifier,
) {
    val cashMinor = bankAccounts.mapNotNull { it.effectiveBalanceMinor }.sum()
    val outstandingMinor = cards.mapNotNull { it.billTotalDueMinor }.sum()
    val netMinor = cashMinor - outstandingMinor
    val hasCash = bankAccounts.any { it.effectiveBalanceMinor != null }
    val hasOutstanding = cards.any { it.billTotalDueMinor != null }
    if (!hasCash && !hasOutstanding) return

    GlassCard(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "NET LIQUID BALANCE",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
            Text(
                Money.format(netMinor, "INR"),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (hasCash) {
                    SummaryStat(
                        label = "Total cash",
                        value = Money.format(cashMinor, "INR"),
                        accent = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (hasOutstanding) {
                    SummaryStat(
                        label = "Outstanding",
                        value = Money.format(outstandingMinor, "INR"),
                        accent = SpendLensTheme.colors.debit,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rename dialog — shared by bank rows and card rows
// ---------------------------------------------------------------------------

@Composable
private fun RenameDialog(
    current: String,
    placeholder: String,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename account") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Display name") },
                placeholder = { Text(placeholder) },
                singleLine = true,
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onConfirm(text.takeIf { it.isNotBlank() }) }) {
                Text("Save")
            }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------
// Bank account row — branded icon tile + name + balance
// ---------------------------------------------------------------------------

@Composable
private fun BankAccountRow(
    acct: AccountSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRename: (String?) -> Unit,
) {
    val brand = BankBranding.forAccount(acct.accountKey, acct.topSender)
    val displayName = acct.customName ?: acct.detectedBankName ?: acct.accountKey
    var showRename by remember { mutableStateOf(false) }

    if (showRename) {
        RenameDialog(
            current = acct.customName ?: "",
            placeholder = acct.detectedBankName ?: acct.accountKey,
            onDismiss = { showRename = false },
            onConfirm = { onRename(it); showRename = false },
        )
    }

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, brand.primary.copy(alpha = 0.25f)),
    ) {
        Column(Modifier.padding(12.dp)) {
            // ── Top row: avatar + name + edit ──────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.linearGradient(listOf(brand.primary, brand.secondary))),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        displayName.filter { it.isLetter() }.take(2).uppercase()
                            .ifEmpty { acct.accountKey.filter { it.isLetterOrDigit() }.take(2).uppercase() },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = brand.onCard,
                    )
                }
                Column(
                    Modifier.weight(1f).padding(start = 10.dp, end = 4.dp),
                ) {
                    Text(
                        displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        acct.accountKey,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.material3.IconButton(
                    onClick = { showRename = true },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Rename",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            // ── Bottom row: balance + channel/txns ─────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column {
                    Text(
                        acct.effectiveBalanceMinor?.let { Money.format(it, "INR") }
                            ?: ("-" + Money.format(acct.totalDebitMinor, "INR")),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        if (acct.effectiveBalanceMinor != null) "Balance" else "Debit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    "${acct.txnCount} txns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Credit card row — full bank-branded card visual
// ---------------------------------------------------------------------------

@Composable
private fun CreditCardRow(
    acct: AccountSummary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRename: (String?) -> Unit,
) {
    val brand = BankBranding.forAccount(acct.accountKey, acct.topSender)
    val outstanding = acct.billTotalDueMinor
    val displayName = acct.customName ?: acct.detectedBankName ?: acct.accountKey
    var showRename by remember { mutableStateOf(false) }

    if (showRename) {
        RenameDialog(
            current = acct.customName ?: "",
            placeholder = acct.detectedBankName ?: acct.accountKey,
            onDismiss = { showRename = false },
            onConfirm = { onRename(it); showRename = false },
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        brand.primary,
                        brand.secondary.copy(alpha = 0.85f),
                        brand.primary.copy(alpha = 0.9f),
                    ),
                    start = Offset(0f, 0f),
                    end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                )
            ),
    ) {
        // Decorative translucent circles for card texture
        Box(
            Modifier
                .size(140.dp)
                .offset(x = (-30).dp, y = (-40).dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.06f))
        )
        Box(
            Modifier
                .size(100.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 30.dp, y = 30.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.05f))
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Top row: bank name + edit + card icon
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        displayName.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = brand.onCard,
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "CREDIT",
                        style = MaterialTheme.typography.labelSmall,
                        color = brand.onCard.copy(alpha = 0.7f),
                        letterSpacing = 0.8.sp,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.IconButton(
                        onClick = { showRename = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "Rename",
                            tint = brand.onCard.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Icon(
                        Icons.Filled.CreditCard,
                        contentDescription = null,
                        tint = brand.onCard.copy(alpha = 0.6f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            // Outstanding amount (hero number) — estimated when no statement SMS was parsed
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    outstanding?.let { Money.format(it, "INR") }
                        ?: ("-" + Money.format(acct.totalDebitMinor, "INR")),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = brand.onCard,
                )
                if (acct.isEstimatedBill) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = brand.onCard.copy(alpha = 0.2f),
                    ) {
                        Text(
                            "EST",
                            style = MaterialTheme.typography.labelSmall,
                            color = brand.onCard.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }

            // Card number formatted like a physical card
            Text(
                run {
                    val digits = acct.accountKey.filter { it.isDigit() }.takeLast(4)
                    if (digits.isNotEmpty()) "•••• •••• •••• $digits" else acct.accountKey
                },
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = brand.onCard.copy(alpha = 0.6f),
                letterSpacing = 1.5.sp,
            )

            // Bottom row: label + due date + txn count
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        when {
                            outstanding == null -> "DEBIT"
                            acct.isEstimatedBill -> "EST. OUTSTANDING"
                            else -> "OUTSTANDING"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = brand.onCard.copy(alpha = 0.6f),
                        letterSpacing = 0.8.sp,
                    )
                    // Always render this line (empty when no min due) so all cards have same height
                    Text(
                        acct.billMinDueMinor?.let { "Min: ${Money.format(it, "INR")}" } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = brand.onCard.copy(alpha = 0.75f),
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    acct.billDueDate?.let {
                        Text(
                            "DUE ${Dates.date(it).uppercase()}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = brand.onCard.copy(alpha = 0.85f),
                            letterSpacing = 0.5.sp,
                        )
                    }
                    Text(
                        "${acct.txnCount} txns this month",
                        style = MaterialTheme.typography.labelSmall,
                        color = brand.onCard.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Smart Insight — gradient-border AI module (mock: Smart Suggestion)
// ---------------------------------------------------------------------------

private data class Insight(val text: String)

private fun smartInsight(cards: List<AccountSummary>): Insight? {
    val due = cards.filter { it.billTotalDueMinor != null && it.billDueDate != null }
        .maxByOrNull { it.billTotalDueMinor!! } ?: return null
    return Insight(
        "Pay off the ${due.accountKey} balance of " +
            "${Money.format(due.billTotalDueMinor!!, "INR")} before " +
            "${Dates.date(due.billDueDate!!)} to avoid interest charges.",
    )
}

@Composable
private fun SmartInsightModule(insight: Insight, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.primary,
                    ),
                ),
            )
            .padding(1.5.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.5.dp))
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                shape = androidx.compose.foundation.shape.CircleShape,
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
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "SMART SUGGESTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.5.sp,
                )
                Text(
                    insight.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun AccountDetail(
    account: AccountSummary,
    monthLabel: String,
    categories: Map<Long, CategoryEntity>,
    onBack: () -> Unit,
    onTransactionClick: (TransactionEntity) -> Unit,
    onSetStatementCycleDay: (Int) -> Unit = {},
    onSetBalance: (Long) -> Unit = {},
) {
    var showCyclePicker by remember { mutableStateOf(false) }
    var showBalanceEditor by remember { mutableStateOf(false) }

    if (showBalanceEditor) {
        BalanceEditDialog(
            isCard = account.isCard,
            currentMinor = account.effectiveBalanceMinor,
            onDismiss = { showBalanceEditor = false },
            onConfirm = { minor -> onSetBalance(minor); showBalanceEditor = false },
        )
    }

    // Prompt the user to set a statement date the first time a card with no detected cycle is opened.
    LaunchedEffect(account.accountKey) {
        if (account.isCard && account.statementCycleDay == null) showCyclePicker = true
    }

    if (showCyclePicker) {
        StatementDayPickerDialog(
            currentDay = account.statementCycleDay,
            onDismiss = { showCyclePicker = false },
            onConfirm = { day -> onSetStatementCycleDay(day); showCyclePicker = false },
        )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to accounts")
            }
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    "${if (account.isCard) "💳" else "🏦"}  ${account.accountKey}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "${account.channel.ifBlank { if (account.isCard) "CARD" else "BANK" }} · $monthLabel",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Payment reminder banner — shown when due within 7 days and not yet paid
        if (account.isCard && !account.isStatementPaid) {
            val dueDate = account.billDueDate
            val daysUntilDue = dueDate?.let { (it - System.currentTimeMillis()) / 86_400_000L }
            if (daysUntilDue != null && daysUntilDue in 0L..7L) {
                Spacer(Modifier.height(4.dp))
                PaymentReminderBanner(
                    daysUntilDue = daysUntilDue,
                    dueDate = dueDate,
                    totalDue = account.billTotalDueMinor,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        ElevatedSurfaceCard {
            AccountStats(
                acct = account,
                onEditCycleDay = { showCyclePicker = true },
                onEditBalance = { showBalanceEditor = true },
            )
        }
        Spacer(Modifier.height(8.dp))
        SectionHeader("Transactions (${account.txnCount})")
        if (account.transactions.isEmpty()) {
            EmptyHint("No transactions on this account in $monthLabel.")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(account.transactions) { txn ->
                    TransactionRow(txn, categories, onClick = { onTransactionClick(txn) })
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun AccountStats(
    acct: AccountSummary,
    onEditCycleDay: () -> Unit = {},
    onEditBalance: () -> Unit = {},
) {
    Column {
        if (acct.isCard && acct.billTotalDueMinor != null) {
            // Latest credit-card statement summary.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryStat(
                    label = when {
                        acct.isStatementPaid -> "Total due (PAID ✓)"
                        acct.isEstimatedBill -> "Est. outstanding"
                        else -> "Total due"
                    },
                    value = Money.format(acct.billTotalDueMinor, "INR"),
                    accent = if (acct.isStatementPaid) SpendLensTheme.colors.credit else SpendLensTheme.colors.debit,
                    modifier = Modifier.weight(1f),
                )
                SummaryStat(
                    label = "Due date",
                    value = acct.billDueDate?.let { Dates.date(it) } ?: "—",
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryStat(
                    label = "Min due",
                    value = acct.billMinDueMinor?.let { Money.format(it, "INR") } ?: "—",
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                SummaryStat(
                    label = "Cycle spend",
                    value = "-" + Money.format(acct.cycleSpendMinor, "INR"),
                    accent = SpendLensTheme.colors.debit,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        "Statement date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        acct.statementCycleDay?.let { "${it}th of each month" } ?: "Not detected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                androidx.compose.material3.IconButton(onClick = onEditCycleDay) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Set statement date",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    SummaryStat(
                        label = if (acct.isCard) "Avl. balance" else "Current balance",
                        value = acct.effectiveBalanceMinor?.let { Money.format(it, "INR") } ?: "—",
                        accent = MaterialTheme.colorScheme.onSurface,
                    )
                }
                SummaryStat(
                    label = "Total debit",
                    value = "-" + Money.format(acct.totalDebitMinor, "INR"),
                    accent = SpendLensTheme.colors.debit,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onEditBalance) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Edit balance",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            // Both sources present and differing → show the other one (live txn vs. periodic SMS).
            acct.secondaryBalance?.let { (amountMinor, observedAt) ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Also ${Money.format(amountMinor, "INR")} as on ${Dates.date(observedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Payment reminder banner
// ---------------------------------------------------------------------------

@Composable
private fun PaymentReminderBanner(daysUntilDue: Long, dueDate: Long, totalDue: Long?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("⚠️", style = MaterialTheme.typography.titleMedium)
            Column {
                Text(
                    if (daysUntilDue == 0L) "Payment due TODAY" else "Payment due in $daysUntilDue day${if (daysUntilDue == 1L) "" else "s"}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                if (totalDue != null) {
                    Text(
                        "Pay ${Money.format(totalDue, "INR")} by ${Dates.date(dueDate)} to avoid interest.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Statement day picker dialog (1-28)
// ---------------------------------------------------------------------------

@Composable
private fun StatementDayPickerDialog(
    currentDay: Int?,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var selected by remember { mutableStateOf(currentDay ?: 1) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Statement date") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Select the day of month when your credit card statement is generated.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = selected.toString(),
                    onValueChange = { v -> v.toIntOrNull()?.takeIf { it in 1..28 }?.let { selected = it } },
                    label = { Text("Day (1–28)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ---------------------------------------------------------------------------
// Manual balance editor dialog
// ---------------------------------------------------------------------------

@Composable
private fun BalanceEditDialog(
    isCard: Boolean,
    currentMinor: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    var text by remember {
        mutableStateOf(currentMinor?.let { (it / 100.0).toString() } ?: "")
    }
    val parsedMinor: Long? = text.trim().replace(",", "").toBigDecimalOrNull()
        ?.movePointRight(2)?.toLong()?.takeIf { it >= 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isCard) "Update available balance" else "Update current balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Enter the latest balance shown in your bank/card app. This overrides the " +
                        "balance read from SMS.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Balance (₹)") },
                    singleLine = true,
                    isError = text.isNotBlank() && parsedMinor == null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { parsedMinor?.let(onConfirm) },
                enabled = parsedMinor != null,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
