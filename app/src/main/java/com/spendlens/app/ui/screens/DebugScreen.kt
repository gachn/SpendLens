package com.spendlens.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.ui.viewmodel.SettingsViewModel

/**
 * App-level debug/diagnostic controls — split out of [SettingsScreen] so ordinary user
 * preferences aren't cluttered with dev-only flags and destructive actions. Reuses
 * [SettingsViewModel] since every control here reads/writes the same stores Settings does
 * (mirrors how [PatternsScreen] reuses it too).
 */
@Composable
fun DebugScreen(vm: SettingsViewModel, onBack: () -> Unit = {}) {
    val appearance by vm.appearance.collectAsState()
    val ai by vm.aiPrefs.collectAsState()
    val exportState by vm.exportState.collectAsState()
    val context = LocalContext.current
    var showClearDataDialog by remember { mutableStateOf(false) }

    if (showClearDataDialog) {
        AlertDialog(
            onDismissRequest = { showClearDataDialog = false },
            title = { Text("Delete all data & re-scan?") },
            text = {
                Text(
                    "This will delete all parsed transactions and raw SMS records, then re-scan " +
                        "your inbox from scratch. Learned patterns and categories are kept. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDataDialog = false
                        vm.clearAllDataAndRescan(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete & re-scan") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDataDialog = false }) { Text("Cancel") }
            },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text("Developer options", style = MaterialTheme.typography.titleLarge)
            }
        }

        item { SectionHeader("Data") }
        item {
            ElevatedSurfaceCard {
                Column {
                    OutlinedButton(
                        onClick = { showClearDataDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Delete all data & re-scan")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.exportDebugCsv(context) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = exportState !is SettingsViewModel.ExportState.InProgress,
                    ) {
                        if (exportState is SettingsViewModel.ExportState.InProgress) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(16.dp).padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("Exporting…")
                        } else {
                            Icon(
                                Icons.Filled.Share,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp),
                            )
                            Text("Export debug CSV")
                        }
                    }
                    when (val s = exportState) {
                        is SettingsViewModel.ExportState.Failed -> Text(
                            "Export failed: ${s.message}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> {}
                    }
                }
            }
        }

        item { SectionHeader("AI request tuning") }
        item {
            ElevatedSurfaceCard {
                Column {
                    var maxTokens by remember(ai.maxTokensPerRequest) {
                        mutableStateOf(ai.maxTokensPerRequest.toString())
                    }
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { new ->
                            maxTokens = new.filter { it.isDigit() }
                            maxTokens.toIntOrNull()?.let { vm.setAiMaxTokens(it) }
                        },
                        singleLine = true,
                        label = { Text("Max tokens per AI request") },
                        supportingText = {
                            Text(
                                "SMS are sent to AI in batches instead of one at a time. This caps how " +
                                    "much text goes into a single request; multiple pending SMS are " +
                                    "packed together up to this budget. Clamped between 200 and 16,000 " +
                                    "— most free-tier models can't reliably handle more in one call.",
                            )
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    var concurrentRequests by remember(ai.concurrentRequests) {
                        mutableStateOf(ai.concurrentRequests.toString())
                    }
                    OutlinedTextField(
                        value = concurrentRequests,
                        onValueChange = { new ->
                            concurrentRequests = new.filter { it.isDigit() }
                            concurrentRequests.toIntOrNull()?.let { vm.setAiConcurrentRequests(it) }
                        },
                        singleLine = true,
                        label = { Text("Parallel AI requests") },
                        supportingText = {
                            Text(
                                "How many batch calls the Premium AI pipeline may have in flight at " +
                                    "once. Higher means a large backlog finishes faster, since each " +
                                    "call can take tens of seconds; too high risks a free-tier model's " +
                                    "rate limit rejecting requests instead. Clamped between 1 and 8.",
                            )
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item { SectionHeader("Diagnostics") }
        item {
            ElevatedSurfaceCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Show debug info", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Add an AI-categorisation debug section to each transaction.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = appearance.debugInfoEnabled,
                        onCheckedChange = { vm.setDebugInfoEnabled(it) },
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
