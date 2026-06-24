package com.spendlens.app.ui.screens

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spendlens.app.data.db.PatternSource
import com.spendlens.app.data.db.SmsPatternEntity
import com.spendlens.app.ui.components.ElevatedSurfaceCard
import com.spendlens.app.ui.components.SectionHeader
import com.spendlens.app.ui.viewmodel.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Dedicated editor for the SMS parsing rules (issue #14). Lists every pattern with a source badge,
 * match stats and an enable toggle, lets the user expand to inspect the regex, and provides an
 * "add custom pattern" form that can be tested inline against a sample message before saving.
 * Only USER-authored patterns can be deleted — built-ins and learned ones are toggled off instead.
 */
@Composable
fun PatternsScreen(vm: SettingsViewModel, onBack: () -> Unit = {}) {
    val patterns by vm.patterns.collectAsState()
    var showClearDialog by remember { mutableStateOf(false) }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Delete all patterns?") },
            text = {
                Text(
                    "This removes all SMS parsing patterns including built-ins and learned ones. " +
                        "They will be re-seeded on the next re-scan. This cannot be undone.",
                )
            },
            confirmButton = {
                Button(
                    onClick = { showClearDialog = false; vm.clearAllPatterns() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete all") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("Cancel") } },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Text("SMS patterns", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
        }

        item {
            Text(
                "These rules read your bank and payment SMS. Add your own to capture a format the " +
                    "built-ins miss, or turn one off if it's matching the wrong messages.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        item { AddPatternCard(onSave = vm::savePattern) }

        item { SectionHeader("All patterns (${patterns.size})") }

        items(patterns, key = { it.id }) { p -> PatternRow(p, vm) }

        item {
            if (patterns.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showClearDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete all patterns") }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PatternRow(p: SmsPatternEntity, vm: SettingsViewModel) {
    var expanded by remember(p.id) { mutableStateOf(false) }
    val canDelete = p.source == PatternSource.USER
    ElevatedSurfaceCard {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { expanded = !expanded }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SourceBadge(p.source)
                        Spacer(Modifier.height(0.dp))
                        Text(
                            p.name,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Text(
                        patternSubtitle(p),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.clickable { expanded = !expanded },
                )
                Switch(checked = p.enabled, onCheckedChange = { vm.setPatternEnabled(p.id, it) })
                if (canDelete) {
                    IconButton(onClick = { vm.deletePattern(p.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete pattern")
                    }
                }
            }
            if (expanded) {
                Spacer(Modifier.height(8.dp))
                Mono("Matches body", p.bodyRegex)
                p.senderRegex?.let { Mono("Matches sender", it) }
                p.sampleSms?.let { Mono(if (p.source == PatternSource.USER) "Sample" else "Learned from", it) }
            }
        }
    }
}

/** The add-custom-pattern form. Collapsed by default; expands into a labelled regex editor. */
@Composable
private fun AddPatternCard(onSave: (name: String, senderRegex: String?, bodyRegex: String, sampleSms: String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var senderRegex by remember { mutableStateOf("") }
    var bodyRegex by remember { mutableStateOf("") }
    var sample by remember { mutableStateOf("") }
    var testResult by remember { mutableStateOf<TestResult?>(null) }

    fun reset() {
        name = ""; senderRegex = ""; bodyRegex = ""; sample = ""; testResult = null
    }

    ElevatedSurfaceCard {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "Add custom pattern",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                )
                Icon(
                    if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (open) "Collapse" else "Expand",
                )
            }
            if (open) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = bodyRegex,
                    onValueChange = { bodyRegex = it; testResult = null },
                    label = { Text("Body regex") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = senderRegex,
                    onValueChange = { senderRegex = it; testResult = null },
                    label = { Text("Sender regex (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = sample,
                    onValueChange = { sample = it; testResult = null },
                    label = { Text("Sample SMS to test against") },
                    modifier = Modifier.fillMaxWidth(),
                )

                testResult?.let { r ->
                    Spacer(Modifier.height(8.dp))
                    Text(
                        r.message,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (r.ok) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { testResult = runTest(bodyRegex, senderRegex, sample) },
                        modifier = Modifier.weight(1f),
                    ) { Text("Test") }
                    Button(
                        onClick = {
                            onSave(
                                name,
                                senderRegex.ifBlank { null },
                                bodyRegex,
                                sample.ifBlank { null },
                            )
                            reset()
                            open = false
                        },
                        enabled = name.isNotBlank() && bodyRegex.isNotBlank() && regexValid(bodyRegex) &&
                            (senderRegex.isBlank() || regexValid(senderRegex)),
                        modifier = Modifier.weight(1f),
                    ) { Text("Save") }
                }
            }
        }
    }
}

private data class TestResult(val ok: Boolean, val message: String)

private fun regexValid(pattern: String): Boolean = runCatching { Regex(pattern) }.isSuccess

private fun runTest(bodyRegex: String, senderRegex: String, sample: String): TestResult {
    if (bodyRegex.isBlank()) return TestResult(false, "Enter a body regex first.")
    val body = runCatching { Regex(bodyRegex) }.getOrElse {
        return TestResult(false, "Body regex is invalid: ${it.message}")
    }
    if (senderRegex.isNotBlank() && runCatching { Regex(senderRegex) }.isFailure) {
        return TestResult(false, "Sender regex is invalid.")
    }
    if (sample.isBlank()) return TestResult(true, "Regex compiles. Add a sample message to test a match.")
    return if (body.containsMatchIn(sample)) {
        TestResult(true, "✓ Matches the sample message.")
    } else {
        TestResult(false, "✗ Does not match the sample message.")
    }
}

@Composable
private fun SourceBadge(source: String) {
    val color = when (source) {
        PatternSource.BUILTIN -> MaterialTheme.colorScheme.secondary
        PatternSource.AI -> MaterialTheme.colorScheme.tertiary
        PatternSource.USER -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outline // HEURISTIC + any future source
    }
    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
        Text(
            source,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private val TS_FMT = SimpleDateFormat("d MMM yyyy", Locale.getDefault())

private fun patternSubtitle(p: SmsPatternEntity): String {
    val last = p.lastMatchedAt?.let { "last ${TS_FMT.format(it)}" } ?: "never matched"
    val state = if (p.enabled) "on" else "off"
    return "matched ${p.matchCount}× · $last · $state"
}

@Composable
private fun Mono(label: String, value: String) {
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
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(8.dp),
        )
    }
}
