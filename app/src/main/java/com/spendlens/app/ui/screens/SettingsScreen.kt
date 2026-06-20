package com.spendlens.app.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.spendlens.app.data.prefs.ThemeMode
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.ui.viewmodel.SettingsViewModel

/** Grace-period choices (seconds → label) for the app-lock re-lock delay. */
private val GRACE_OPTIONS = listOf(
    0 to "Instant",
    30 to "30s",
    60 to "1 min",
    300 to "5 min",
)

@Composable
fun SettingsScreen(vm: SettingsViewModel) {
    val patterns by vm.patterns.collectAsState()
    val exportState by vm.exportState.collectAsState()
    val appearance by vm.appearance.collectAsState()
    val security by vm.security.collectAsState()
    val context = LocalContext.current
    var showClearDataDialog by remember { mutableStateOf(false) }
    var showClearPatternsDialog by remember { mutableStateOf(false) }

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

    if (showClearPatternsDialog) {
        AlertDialog(
            onDismissRequest = { showClearPatternsDialog = false },
            title = { Text("Delete all patterns?") },
            text = {
                Text(
                    "This removes all SMS parsing patterns including built-ins and learned ones. " +
                        "They will be re-seeded on the next re-scan. This cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearPatternsDialog = false
                        vm.clearAllPatterns()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text("Delete all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearPatternsDialog = false }) { Text("Cancel") }
            },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item { SectionHeader("Appearance") }
        item {
            ElevatedSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    SegmentedChoice(
                        options = ThemeMode.entries,
                        selected = appearance.themeMode,
                        label = { it.label },
                        onSelect = { vm.setThemeMode(it) },
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("Dynamic colour", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "Match your wallpaper (Material You) instead of the SpendLens palette.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Switch(
                                checked = appearance.dynamicColor,
                                onCheckedChange = { vm.setDynamicColor(it) },
                            )
                        }
                    }
                }
            }
        }

        item { SectionHeader("Security") }
        item {
            ElevatedSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("App lock", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Require fingerprint, face or device PIN to open SpendLens.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = security.appLockEnabled,
                            onCheckedChange = { vm.setAppLockEnabled(it) },
                        )
                    }
                    if (security.appLockEnabled) {
                        Text("Re-lock after", style = MaterialTheme.typography.titleSmall)
                        SegmentedChoice(
                            options = GRACE_OPTIONS,
                            selected = GRACE_OPTIONS.firstOrNull { it.first == security.gracePeriodSec } ?: GRACE_OPTIONS[1],
                            label = { it.second },
                            onSelect = { vm.setGracePeriod(it.first) },
                        )
                    }
                }
            }
        }

        item { SectionHeader("Data") }
        item {
            ElevatedSurfaceCard {
                Column {
                    Button(onClick = { vm.reimport(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Re-scan SMS inbox")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { vm.clearTransactions() },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Clear parsed transactions")
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

        item { SectionHeader("Privacy") }
        item {
            ElevatedSurfaceCard {
                Text(
                    "SMS are parsed on this device and stored in an encrypted database. " +
                        "The only data that leaves your phone is the cleaned merchant name " +
                        "(e.g. \"swiggy\"), sent to look up its proper brand name — never amounts, " +
                        "account numbers or full messages. Looked-up names are cached locally.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            SectionHeader("Patterns (${patterns.size})")
        }
        item {
            Column {
                Text(
                    "These are the rules used to read your SMS. Tap one to see its regex and the " +
                        "sample message it was learned from. Learned patterns sit above the built-ins.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                if (patterns.isNotEmpty()) {
                    OutlinedButton(
                        onClick = { showClearPatternsDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text("Delete all patterns")
                    }
                }
            }
        }
        items(patterns) { p ->
            var expanded by remember(p.id) { mutableStateOf(false) }
            ElevatedSurfaceCard {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(
                            Modifier.weight(1f).clickable { expanded = !expanded },
                        ) {
                            Text(p.name, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${p.source} · matched ${p.matchCount}× · ${if (p.enabled) "on" else "off"}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.clickable { expanded = !expanded },
                        )
                        Switch(checked = p.enabled, onCheckedChange = { vm.setPatternEnabled(p.id, it) })
                        IconButton(onClick = { vm.deletePattern(p.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete pattern")
                        }
                    }
                    if (expanded) {
                        Spacer(Modifier.height(8.dp))
                        PatternDetail("Matches body", p.bodyRegex)
                        p.senderRegex?.let { PatternDetail("Matches sender", it) }
                        p.sampleSms?.let { PatternDetail("Learned from", it) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/**
 * A small, dependency-stable segmented control (pill row) for a single choice.
 * Avoids the experimental SegmentedButton API while matching its look.
 */
@Composable
private fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(4.dp)) {
            options.forEach { option ->
                val isSelected = option == selected
                Surface(
                    onClick = { onSelect(option) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        label(option),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PatternDetail(label: String, value: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(8.dp),
        )
    }
}
