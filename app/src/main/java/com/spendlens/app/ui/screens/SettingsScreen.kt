package com.spendlens.app.ui.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
fun SettingsScreen(
    vm: SettingsViewModel,
    onBack: () -> Unit = {},
    onOpenMerchants: () -> Unit = {},
    onOpenGoals: () -> Unit = {},
    onOpenPatterns: () -> Unit = {},
) {
    val patterns by vm.patterns.collectAsState()
    val exportState by vm.exportState.collectAsState()
    val appearance by vm.appearance.collectAsState()
    val security by vm.security.collectAsState()
    val smsFilter by vm.smsFilter.collectAsState()
    val ai by vm.aiPrefs.collectAsState()
    val backupState by vm.backupState.collectAsState()
    val lastBackupAt by vm.lastBackupAt.collectAsState()
    val context = LocalContext.current
    var showClearDataDialog by remember { mutableStateOf(false) }

    // Encrypted backup/restore (issue #13). A picked file uri + mode opens the password dialog.
    var pendingExportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var pendingImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val exportPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) pendingExportUri = uri }
    val importPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingImportUri = uri }

    androidx.compose.runtime.LaunchedEffect(backupState) {
        when (val s = backupState) {
            is SettingsViewModel.BackupState.Done -> {
                android.widget.Toast.makeText(context, s.message, android.widget.Toast.LENGTH_SHORT).show()
                vm.consumeBackupState()
            }
            is SettingsViewModel.BackupState.Failed -> {
                android.widget.Toast.makeText(context, s.message, android.widget.Toast.LENGTH_LONG).show()
                vm.consumeBackupState()
            }
            else -> {}
        }
    }

    pendingExportUri?.let { uri ->
        BackupPasswordDialog(
            confirm = true,
            onDismiss = { pendingExportUri = null },
            onConfirm = { pwd -> pendingExportUri = null; vm.exportBackup(context, uri, pwd) },
        )
    }
    pendingImportUri?.let { uri ->
        BackupPasswordDialog(
            confirm = false,
            onDismiss = { pendingImportUri = null },
            onConfirm = { pwd -> pendingImportUri = null; vm.importBackup(context, uri, pwd) },
        )
    }

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
                Spacer(Modifier.width(4.dp))
                Text(
                    "Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

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
                            onCheckedChange = { enable ->
                                if (!enable) {
                                    vm.setAppLockEnabled(false)
                                    return@Switch
                                }
                                val authenticators =
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                                        BIOMETRIC_STRONG or DEVICE_CREDENTIAL
                                    else BIOMETRIC_WEAK
                                val bmResult = BiometricManager.from(context)
                                    .canAuthenticate(authenticators)
                                if (bmResult == BiometricManager.BIOMETRIC_SUCCESS) {
                                    vm.setAppLockEnabled(true)
                                } else {
                                    // No biometric / PIN enrolled — send user to system enrollment.
                                    val enrollIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                        Intent(Settings.ACTION_BIOMETRIC_ENROLL).apply {
                                            putExtra(
                                                Settings.EXTRA_BIOMETRIC_AUTHENTICATORS_ALLOWED,
                                                authenticators,
                                            )
                                        }
                                    } else {
                                        @Suppress("DEPRECATION")
                                        Intent(Settings.ACTION_SECURITY_SETTINGS)
                                    }
                                    context.startActivity(enrollIntent)
                                }
                            },
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

        item { SectionHeader("Merchants") }
        item {
            ElevatedSurfaceCard {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenMerchants() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Manage merchants", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Edit names, categories, expense setting and matched tokens.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionHeader("Savings") }
        item {
            ElevatedSurfaceCard {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenGoals() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("Savings goals", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Track progress toward a target like a vacation or emergency fund.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item { SectionHeader("Backup & restore") }
        item {
            val working = backupState is SettingsViewModel.BackupState.Working
            val tsFmt = remember { java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale.getDefault()) }
            ElevatedSurfaceCard {
                Column {
                    Text(
                        "Encrypted, offline backup. Choose a password — it's the only way to restore, " +
                            "and SpendLens can't recover it. Nothing is uploaded.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            val name = "spendlens-backup-${java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())}.slb"
                            exportPicker.launch(name)
                        },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Create encrypted backup") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { importPicker.launch(arrayOf("*/*")) },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Restore from backup") }
                    if (working) {
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.height(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Working…", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        lastBackupAt?.let { "Last backup: ${tsFmt.format(java.util.Date(it))}" } ?: "No backup yet",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

        item { SectionHeader("SMS Filtering") }
        item {
            ElevatedSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                "Financial senders only",
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                "Only treat SMS from recognised banks and financial services " +
                                    "as transactions. Messages from other senders are ignored.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = smsFilter.financialSendersOnly,
                            onCheckedChange = { vm.setFinancialSendersOnly(it) },
                        )
                    }
                    if (smsFilter.financialSendersOnly) {
                        Text(
                            "Re-scan your inbox after changing this setting to apply it to " +
                                "previously received messages.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { SectionHeader("AI") }
        item {
            ElevatedSurfaceCard {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f).padding(end = 12.dp)) {
                            Text("Use AI (OpenRouter)", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Generate SMS parsing patterns and consolidate merchant names with an " +
                                    "AI model. When off, SpendLens uses the on-device / copy-to-clipboard " +
                                    "flow. Sends SMS templates and merchant names to OpenRouter.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = ai.enabled,
                            onCheckedChange = { vm.setAiEnabled(it) },
                        )
                    }

                    if (ai.enabled) {
                        var model by remember(ai.model) { mutableStateOf(ai.model) }
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it; vm.setAiModel(it) },
                            singleLine = true,
                            label = { Text("Model") },
                            placeholder = { Text("provider/model-slug") },
                            supportingText = {
                                Text("Any OpenRouter model slug, e.g. openai/gpt-latest or a :free model.")
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )

                        var apiKey by remember { mutableStateOf("") }
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            singleLine = true,
                            label = { Text("API key") },
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                            ),
                            supportingText = {
                                Text(
                                    when {
                                        ai.hasOverrideKey -> "Using your saved key. Type a new one to replace, or clear it below."
                                        ai.buildKeyPresent -> "Using the built-in key. Enter your own to override it."
                                        else -> "No key set. Enter your OpenRouter API key to enable AI calls."
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { vm.setAiApiKey(apiKey); apiKey = "" },
                                enabled = apiKey.isNotBlank(),
                            ) { Text("Save key") }
                            if (ai.hasOverrideKey) {
                                OutlinedButton(onClick = { vm.setAiApiKey(null); apiKey = "" }) {
                                    Text("Clear key")
                                }
                            }
                        }
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

        item { SectionHeader("Patterns") }
        item {
            ElevatedSurfaceCard {
                Row(
                    Modifier.fillMaxWidth().clickable { onOpenPatterns() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("SMS patterns (${patterns.size})", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "View, toggle, test and add the rules that read your SMS.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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

/**
 * Prompts for the backup password. [confirm] = true (create) requires a second matching entry and a
 * minimum length; false (restore) accepts a single password. The password is handed back as a
 * CharArray the caller wipes after use.
 */
@Composable
private fun BackupPasswordDialog(
    confirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    val tooShort = confirm && password.length < 6
    val mismatch = confirm && repeat.isNotEmpty() && password != repeat
    val valid = password.isNotEmpty() && !tooShort && (!confirm || password == repeat)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = valid, onClick = { onConfirm(password.toCharArray()) }) {
                Text(if (confirm) "Encrypt & save" else "Restore", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (confirm) "Set backup password" else "Enter backup password") },
        text = {
            Column {
                if (confirm) {
                    Text(
                        "This password encrypts your backup. There's no recovery if you forget it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text("Password") },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    ),
                    isError = tooShort,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirm) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it },
                        singleLine = true,
                        label = { Text("Confirm password") },
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                        ),
                        isError = mismatch,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (tooShort) {
                        Text("At least 6 characters.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    } else if (mismatch) {
                        Text("Passwords don't match.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
    )
}
