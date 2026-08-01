package online.velozen.spendvault.ui.screens

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import online.velozen.spendvault.ui.components.ElevatedSurfaceCard
import online.velozen.spendvault.ui.components.SectionHeader
import online.velozen.spendvault.ui.viewmodel.SettingsViewModel
import online.velozen.spendvault.config.RemoteConfigManager

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
    val counts by vm.debugCounts.collectAsState()
    val stats by vm.processingStats.collectAsState()
    val context = LocalContext.current
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showResetOnboardingDialog by remember { mutableStateOf(false) }
    val remoteConfig = RemoteConfigManager.getInstance()

    LaunchedEffect(Unit) { vm.loadDebugCounts() }
    LaunchedEffect(Unit) { remoteConfig.fetchAndActivate() }

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

    if (showResetOnboardingDialog) {
        AlertDialog(
            onDismissRequest = { showResetOnboardingDialog = false },
            title = { Text("Reset onboarding?") },
            text = {
                Text(
                    "This will reset the onboarding completion flag. The next time you launch the app, " +
                        "you'll see the onboarding flow again including currency selection. This is useful " +
                        "for testing the onboarding experience.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showResetOnboardingDialog = false
                        vm.resetOnboarding()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ),
                ) { Text("Reset onboarding") }
            },
            dismissButton = {
                TextButton(onClick = { showResetOnboardingDialog = false }) { Text("Cancel") }
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

        item { SectionHeader("Pipeline metrics") }
        item {
            ElevatedSurfaceCard {
                Column(Modifier.padding(4.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Session timing resets on app restart. Counts are live from the DB.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f).padding(end = 8.dp),
                        )
                        OutlinedButton(onClick = { vm.loadDebugCounts() }) { Text("Refresh") }
                    }

                    MetricGroupHeader("SMS rows (${counts.totalRawSms})")
                    StatRow("Parsed", counts.parsedCount.toString())
                    StatRow("Unparsed (review queue)", counts.unparsedCount.toString())
                    StatRow("Ignored (non-financial)", counts.ignoredCount.toString())
                    StatRow("Pending AI batch", counts.pendingAiCount.toString())

                    MetricGroupHeader("AI usage")
                    StatRow("Parsed by AI (direct call)", counts.aiParsedCount.toString())
                    StatRow(
                        "Parsed via AI-generated pattern",
                        counts.aiPatternParsedCount.toString(),
                    )

                    MetricGroupHeader("Patterns")
                    StatRow("Built-in", counts.patternBuiltin.toString())
                    StatRow("AI-learned", counts.patternAi.toString())
                    StatRow("Heuristic-learned", counts.patternHeuristic.toString())
                    StatRow("User-authored", counts.patternUser.toString())
                    StatRow("Firebase-synced", counts.patternFirebase.toString())

                    MetricGroupHeader("Firebase Sync")
                    StatRow("Last sync", counts.firebaseSyncLastRun)
                    StatRow("Uploadable patterns", counts.patternFirebase.toString())
                    StatRow("Sync threshold", "50 (Remote Config)")
                    Text(
                        "Auto-syncs on app startup when uploadable patterns ≥ threshold",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp)
                    )

                    MetricGroupHeader("Transactions")
                    StatRow("Total", counts.totalTransactions.toString())
                    StatRow("Flagged duplicates", counts.duplicateTransactions.toString())

                    MetricGroupHeader("AI batch timing (this session)")
                    StatRow("Batch runs", stats.aiBatchCount.toString())
                    StatRow("Last batch", "${stats.aiBatchLastSmsCount} SMS in ${fmtDur(stats.aiBatchLastMs)}")
                    StatRow("Cumulative", "${fmtDur(stats.aiBatchTotalMs)} over ${stats.aiBatchCount} run(s)")
                    if (stats.aiBatchCount > 0) {
                        StatRow("Avg per batch", fmtDur(stats.aiBatchTotalMs / stats.aiBatchCount))
                    }
                    if (stats.aiBatchLastSmsCount > 0) {
                        StatRow("Last batch per SMS", fmtDur(stats.aiBatchLastMs / stats.aiBatchLastSmsCount))
                    }

                    MetricGroupHeader("Regex pipeline timing (this session)")
                    StatRow("Reprocess runs", stats.regexRunCount.toString())
                    StatRow("Last run", "${stats.regexLastSmsCount} SMS in ${fmtDur(stats.regexLastMs)}")
                    StatRow("Cumulative", "${fmtDur(stats.regexTotalMs)} over ${stats.regexRunCount} run(s)")
                    if (stats.regexRunCount > 0) {
                        StatRow("Avg per run", fmtDur(stats.regexTotalMs / stats.regexRunCount))
                    }
                    if (stats.regexLastSmsCount > 0) {
                        StatRow("Last run per SMS", fmtDur(stats.regexLastMs / stats.regexLastSmsCount))
                    }
                }
            }
        }

        item { SectionHeader("Data") }
        item {
            ElevatedSurfaceCard {
                Column {
                    OutlinedButton(
                        onClick = { showResetOnboardingDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Text("Reset onboarding flag")
                    }
                    Spacer(Modifier.height(8.dp))
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

        item { SectionHeader("AI request tuning (Remote Config)") }
        item {
            ElevatedSurfaceCard {
                Column {
                    var maxTokens by remember { mutableStateOf(remoteConfig.getAiMaxTokensPerRequest().toString()) }
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { new ->
                            maxTokens = new.filter { it.isDigit() }
                        },
                        singleLine = true,
                        readOnly = true,
                        label = { Text("Max tokens per AI request (from Remote Config)") },
                        supportingText = {
                            Text(
                                "SMS are sent to AI in batches instead of one at a time. This caps how " +
                                    "much text goes into a single request; multiple pending SMS are " +
                                    "packed together up to this budget. Clamped between 200 and 16,000 " +
                                    "— most free-tier models can't reliably handle more in one call. " +
                                    "Manage this value in Firebase Remote Config.",
                            )
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    var concurrentRequests by remember { mutableStateOf(remoteConfig.getAiConcurrentRequests().toString()) }
                    OutlinedTextField(
                        value = concurrentRequests,
                        onValueChange = { new ->
                            concurrentRequests = new.filter { it.isDigit() }
                        },
                        singleLine = true,
                        readOnly = true,
                        label = { Text("Parallel AI requests (from Remote Config)") },
                        supportingText = {
                            Text(
                                "How many batch calls the Premium AI pipeline may have in flight at " +
                                    "once. Higher means a large backlog finishes faster, since each " +
                                    "call can take tens of seconds; too high risks a free-tier model's " +
                                    "rate limit rejecting requests instead. Clamped between 1 and 8. " +
                                    "Manage this value in Firebase Remote Config.",
                            )
                        },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))
                    var maxItemsPerBatch by remember { mutableStateOf(remoteConfig.getAiMaxItemsPerBatch().toString()) }
                    OutlinedTextField(
                        value = maxItemsPerBatch,
                        onValueChange = { new ->
                            maxItemsPerBatch = new.filter { it.isDigit() }
                        },
                        singleLine = true,
                        readOnly = true,
                        label = { Text("Max SMS per batch (from Remote Config)") },
                        supportingText = {
                            Text(
                                "Maximum number of SMS messages per AI batch request. Balances prompt " +
                                    "size against output generation time — each SMS generates one JSON object. " +
                                    "Higher values reduce total API calls but may cause timeouts. Clamped between 5 and 50. " +
                                    "Manage this value in Firebase Remote Config.",
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

        item { SectionHeader("Diagnostics (Remote Config)") }
        item {
            ElevatedSurfaceCard {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Show debug info (from Remote Config)", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Add an AI-categorisation debug section to each transaction. Manage this setting in Firebase Remote Config.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = remoteConfig.isDebugInfoEnabled(),
                        onCheckedChange = { },
                        enabled = false,
                    )
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Sub-group label inside the Pipeline metrics card. */
@Composable
private fun MetricGroupHeader(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp),
    )
}

/** A "label : value" row used throughout the Pipeline metrics card. */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Formats a duration in ms as a compact human string (e.g. "320 ms", "4.2 s", "1 m 12 s"). */
private fun fmtDur(ms: Long): String = when {
    ms < 1_000 -> "$ms ms"
    ms < 60_000 -> "%.1f s".format(ms / 1_000.0)
    else -> {
        val s = ms / 1_000
        "${s / 60} m ${s % 60} s"
    }
}
