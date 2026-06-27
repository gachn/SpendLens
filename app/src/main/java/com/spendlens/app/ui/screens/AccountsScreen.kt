package com.spendlens.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
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
        )
        return
    }

    val banks = if (showEmpty) state.bankAccounts else state.bankAccounts.filter { it.hasActivity }
    val cards = if (showEmpty) state.cards else state.cards.filter { it.hasActivity }
    val hiddenCount = (state.bankAccounts.count { !it.hasActivity }) + (state.cards.count { !it.hasActivity })

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Debit by month", style = MaterialTheme.typography.titleMedium)
            MonthDropdown(state.selectedMonth, state.months, vm::setMonth)
        }
        if (hiddenCount > 0) {
            TextButton(
                onClick = { showEmpty = !showEmpty },
                modifier = Modifier.padding(bottom = 4.dp),
            ) {
                Text(if (showEmpty) "Hide $hiddenCount inactive" else "Show $hiddenCount inactive")
            }
        }

        if (banks.isEmpty() && cards.isEmpty()) {
            EmptyHint(
                if (state.bankAccounts.isEmpty() && state.cards.isEmpty()) {
                    "No accounts yet. They appear once transactions are parsed."
                } else {
                    "No account activity in ${Dates.label(state.selectedMonth)}."
                },
            )
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item { NetLiquidBalanceCard(bankAccounts = state.bankAccounts, cards = state.cards) }
                if (banks.isNotEmpty()) {
                    item { SectionHeader("Bank accounts") }
                    items(banks) { acct ->
                        BankAccountRow(
                            acct = acct,
                            onClick = { openKey = acct.accountKey },
                            onRename = { vm.setAccountName(acct.accountKey, it) },
                        )
                    }
                }
                if (cards.isNotEmpty()) {
                    item { SectionHeader("Credit cards") }
                    items(cards) { acct ->
                        CreditCardRow(
                            acct = acct,
                            onClick = { openKey = acct.accountKey },
                            onRename = { vm.setAccountName(acct.accountKey, it) },
                        )
                    }
                }
                smartInsight(state.cards)?.let { insight ->
                    item { SmartInsightModule(insight) }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Net Liquid Balance — hero summary card (mock: "Net Liquid Balance")
// ---------------------------------------------------------------------------

@Composable
private fun NetLiquidBalanceCard(bankAccounts: List<AccountSummary>, cards: List<AccountSummary>) {
    val cashMinor = bankAccounts.mapNotNull { it.balanceMinor }.sum()
    val outstandingMinor = cards.mapNotNull { it.billTotalDueMinor }.sum()
    val netMinor = cashMinor - outstandingMinor
    val hasCash = bankAccounts.any { it.balanceMinor != null }
    val hasOutstanding = cards.any { it.billTotalDueMinor != null }
    if (!hasCash && !hasOutstanding) return

    GlassCard {
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
private fun BankAccountRow(acct: AccountSummary, onClick: () -> Unit, onRename: (String?) -> Unit) {
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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, brand.primary.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Bank-branded avatar — initials from display name or accountKey
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(brand.primary, brand.secondary))),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    displayName.filter { it.isLetter() }.take(2).uppercase()
                        .ifEmpty { acct.accountKey.take(2).uppercase() },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = brand.onCard,
                )
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    acct.accountKey,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    "${acct.channel.ifBlank { "BANK" }} · ${acct.txnCount} txns",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    acct.balanceMinor?.let { Money.format(it, "INR") }
                        ?: ("-" + Money.format(acct.totalDebitMinor, "INR")),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    if (acct.balanceMinor != null) {
                        acct.balanceUpdatedAt?.let { "Balance · ${Dates.date(it)}" } ?: "Balance"
                    } else "Debit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            androidx.compose.material3.IconButton(
                onClick = { showRename = true },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "Rename",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Credit card row — full bank-branded card visual
// ---------------------------------------------------------------------------

@Composable
private fun CreditCardRow(acct: AccountSummary, onClick: () -> Unit, onRename: (String?) -> Unit) {
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
        modifier = Modifier
            .fillMaxWidth()
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

            // Outstanding amount (hero number)
            Text(
                outstanding?.let { Money.format(it, "INR") }
                    ?: ("-" + Money.format(acct.totalDebitMinor, "INR")),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = brand.onCard,
            )

            // Card number — padded to look like a physical card number
            Text(
                run {
                    val digits = acct.accountKey.filter { it.isDigit() }
                    if (digits.isNotEmpty()) "•••• •••• •••• $digits" else acct.accountKey
                },
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = brand.onCard.copy(alpha = 0.65f),
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
                        if (outstanding != null) "OUTSTANDING" else "DEBIT",
                        style = MaterialTheme.typography.labelSmall,
                        color = brand.onCard.copy(alpha = 0.6f),
                        letterSpacing = 0.8.sp,
                    )
                    acct.billMinDueMinor?.let {
                        Text(
                            "Min: ${Money.format(it, "INR")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = brand.onCard.copy(alpha = 0.75f),
                        )
                    }
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
private fun SmartInsightModule(insight: Insight) {
    Box(
        Modifier
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
) {
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
        Spacer(Modifier.height(4.dp))
        ElevatedSurfaceCard { AccountStats(account) }
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
private fun AccountStats(acct: AccountSummary) {
    Column {
        if (acct.isCard && acct.billTotalDueMinor != null) {
            // Latest credit-card statement.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryStat(
                    label = "Total due",
                    value = Money.format(acct.billTotalDueMinor, "INR"),
                    accent = SpendLensTheme.colors.debit,
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
                    label = "Debit (this month)",
                    value = "-" + Money.format(acct.totalDebitMinor, "INR"),
                    accent = SpendLensTheme.colors.debit,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SummaryStat(
                    label = if (acct.isCard) "Avl. balance" else "Current balance",
                    value = acct.balanceMinor?.let { Money.format(it, "INR") } ?: "—",
                    accent = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                SummaryStat(
                    label = "Total debit",
                    value = "-" + Money.format(acct.totalDebitMinor, "INR"),
                    accent = SpendLensTheme.colors.debit,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
