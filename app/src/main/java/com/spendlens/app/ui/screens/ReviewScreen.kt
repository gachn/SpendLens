package com.spendlens.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.ui.components.TransactionRow
import com.spendlens.app.ui.viewmodel.ReviewViewModel
import com.spendlens.app.ai.AiBridgeHelper
import kotlinx.coroutines.launch

@Composable
fun ReviewScreen(vm: ReviewViewModel, onTransactionClick: (TransactionEntity) -> Unit = {}) {
    val state by vm.state.collectAsState()
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { SectionHeader("Possible duplicates (${state.duplicates.size})") }
        if (state.duplicates.isEmpty()) {
            item { EmptyHint("No duplicates flagged. ") }
        } else {
            items(state.duplicates) { txn ->
                ElevatedSurfaceCard {
                    Column {
                        TransactionRow(txn, state.categories, onClick = { onTransactionClick(txn) })
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { vm.keepAsUnique(txn) }) { Text("Keep") }
                            Spacer(Modifier.height(0.dp))
                            OutlinedButton(onClick = { vm.confirmDuplicate(txn) }) { Text("It's a duplicate") }
                        }
                    }
                }
            }
        }

        item {
            SectionHeader("Unparsed messages (${state.unparsed.size})") {
                if (state.unparsed.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val selectedCount = selectedIds.size
                        OutlinedButton(onClick = {
                            coroutineScope.launch {
                                val toTeach = if (selectedIds.isEmpty()) {
                                    state.unparsed
                                } else {
                                    state.unparsed.filter { it.id in selectedIds }
                                }
                                val prompt = vm.generatePrompt(toTeach)
                                AiBridgeHelper.copyAndLaunch(context, prompt)
                            }
                        }) {
                            Text(if (selectedCount > 0) "🤖 Teach Selected ($selectedCount)" else "🤖 Teach All")
                        }
                        OutlinedButton(onClick = { vm.reprocessUnparsed() }) { Text("Reprocess") }
                    }
                }
            }
        }
        if (state.unparsed.isEmpty()) {
            item { EmptyHint("Every financial SMS was parsed. ") }
        } else {
            items(state.unparsed) { raw ->
                ElevatedSurfaceCard {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(raw.id),
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + raw.id
                                    } else {
                                        selectedIds - raw.id
                                    }
                                }
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(raw.sender, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(4.dp))
                                Text(raw.body, style = MaterialTheme.typography.bodyMedium, maxLines = 4)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                coroutineScope.launch {
                                    val prompt = vm.generatePrompt(listOf(raw))
                                    AiBridgeHelper.copyAndLaunch(context, prompt)
                                }
                            }) {
                                Text("🤖 Teach with AI")
                            }
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = { vm.ignoreSms(raw) }) { Text("Not a transaction") }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
