package online.velozen.spendvault.ui.nav

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import online.velozen.spendvault.data.db.RawStatus
import online.velozen.spendvault.data.db.TransactionEntity
import online.velozen.spendvault.di.AppContainer
import online.velozen.spendvault.SpendVaultApp
import online.velozen.spendvault.ui.components.LocalPrimaryCurrency
import online.velozen.spendvault.ui.components.TransactionDetailSheet
import online.velozen.spendvault.ui.screens.AccountsScreen
import online.velozen.spendvault.ui.screens.AnalyticsScreen
import online.velozen.spendvault.ui.screens.BillsScreen
import online.velozen.spendvault.ui.screens.BudgetsScreen
import online.velozen.spendvault.ui.screens.CategoriesScreen
import online.velozen.spendvault.ui.screens.DashboardScreen
import online.velozen.spendvault.ui.screens.DebugScreen
import online.velozen.spendvault.ui.screens.GoalsScreen
import online.velozen.spendvault.ui.screens.ManualEntryScreen
import online.velozen.spendvault.ui.screens.MerchantDetailScreen
import online.velozen.spendvault.ui.screens.MerchantsScreen
import online.velozen.spendvault.ui.screens.PatternsScreen
import online.velozen.spendvault.ui.screens.SendersScreen
import online.velozen.spendvault.ui.screens.OnboardingScreen
import online.velozen.spendvault.ui.screens.OnboardingFlowScreen
import online.velozen.spendvault.ui.screens.ReviewScreen
import online.velozen.spendvault.ui.screens.SettingsScreen
import online.velozen.spendvault.ui.screens.SubscriptionsScreen
import online.velozen.spendvault.ui.screens.TransactionsScreen
import online.velozen.spendvault.ui.viewmodel.AccountsViewModel
import online.velozen.spendvault.ui.viewmodel.AnalyticsViewModel
import online.velozen.spendvault.ui.viewmodel.BillsViewModel
import online.velozen.spendvault.ui.viewmodel.BudgetsViewModel
import online.velozen.spendvault.ui.viewmodel.CategoriesViewModel
import online.velozen.spendvault.ui.viewmodel.DashboardViewModel
import online.velozen.spendvault.ui.viewmodel.GoalsViewModel
import online.velozen.spendvault.ui.viewmodel.ManualEntryViewModel
import online.velozen.spendvault.ui.viewmodel.MerchantsViewModel
import online.velozen.spendvault.ui.viewmodel.ReviewViewModel
import online.velozen.spendvault.ui.viewmodel.SenderClassificationsViewModel
import online.velozen.spendvault.ui.viewmodel.SettingsViewModel
import online.velozen.spendvault.ui.viewmodel.SpendVaultViewModelFactory
import online.velozen.spendvault.ui.viewmodel.SubscriptionsViewModel
import online.velozen.spendvault.ui.viewmodel.TransactionDetailViewModel
import online.velozen.spendvault.ui.viewmodel.TransactionsViewModel
import online.velozen.spendvault.work.SmsSyncWorker

// Bottom nav destinations (5 primary sections matching the design mock:
// Home · History · Accounts · Insights · Budgets)
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Home("dashboard",     "Home",     Icons.Filled.Home),
    History("transactions","History", Icons.AutoMirrored.Filled.ReceiptLong),
    Accounts("accounts",  "Accounts", Icons.Filled.AccountBalanceWallet),
    Insights("analytics", "Insights", Icons.Filled.Analytics),
    Budgets("budgets",    "Budgets",  Icons.Filled.Category),
}

private const val ROUTE_SETTINGS     = "settings"
private const val ROUTE_MERCHANTS    = "merchants"
private const val ROUTE_SENDERS      = "senders"
private const val ROUTE_REVIEW       = "review"
private const val ROUTE_BILLS        = "bills"
private const val ROUTE_SUBSCRIPTIONS = "subscriptions"
private const val ROUTE_CATEGORIES   = "categories"
private const val ROUTE_GOALS        = "goals"
private const val ROUTE_PATTERNS     = "patterns"
private const val ROUTE_DEBUG        = "debug"
private const val ROUTE_MERCHANT     = "merchant"
private const val ARG_MERCHANT       = "name"
private const val ROUTE_ENTRY        = "entry"
private const val ARG_TXN_ID         = "txnId"

@Composable
fun SpendVaultRoot(
    container: AppContainer,
    initialTxnId: Long? = null,
    onTxnIdConsumed: () -> Unit = {},
) {
    val factory = remember { SpendVaultViewModelFactory(container) }
    var selected by remember { mutableStateOf<TransactionEntity?>(null) }

    var lastProcessedClip by remember { mutableStateOf("") }
    var detectedPatternSingle by remember { mutableStateOf<DetectedPatternSingle?>(null) }
    var detectedPatternBulk by remember { mutableStateOf<List<DetectedPatternSingle>?>(null) }

    val processingProgress by container.smsProcessor.progress.collectAsState()
    var showProgressPopup by remember { mutableStateOf(false) }

    // Overall SMS progress: total PARSED vs total SMS count (persists across screen switches)
    val parsedCountFlow = remember { container.rawSmsDao.observeParsedCount() }
    val parsedCount by parsedCountFlow.collectAsState(initial = 0)
    val totalCountFlow = remember { container.rawSmsDao.observeTotalCount() }
    val totalCount by totalCountFlow.collectAsState(initial = 0)

    // SMS queued for the Premium AI batch (PENDING_AI) but not yet picked up by
    // AiSmsBatchWorker — e.g. still inside its debounce window, or waiting for WorkManager to run
    // it. Without this, the progress popup only appears once the worker is actually mid-call,
    // leaving a silent gap where messages are visibly stuck with no feedback.
    val pendingAiCountFlow = remember { container.rawSmsDao.observeCountByStatus(RawStatus.PENDING_AI) }
    val pendingAiCount by pendingAiCountFlow.collectAsState(initial = 0)

    // "AI is analysing…" banner: shown while auto-categorisation runs, unless the user turned it off.
    val aiRunning by container.aiCategorizer.running.collectAsState()
    val appearance by container.settingsStore.appearance.collectAsState()
    val showAiBanner = aiRunning && appearance.aiBannerEnabled
    val showSmsQueueSection = appearance.smsQueueSectionEnabled

    // Recomputed whenever the override is set/cleared OR auto-detect resolves differently (e.g.
    // once enough transactions exist to outvote an initial locale guess) so every screen's
    // "≈ converted amount" badge tracks the current primary currency without a restart.
    val currencyPrefs by container.settingsStore.currency.collectAsState()
    val detectedCurrency by container.settingsStore.detected.collectAsState()
    val primaryCurrency = currencyPrefs.primaryCurrencyOverride ?: detectedCurrency

    LaunchedEffect(processingProgress.isProcessing) {
        if (processingProgress.isProcessing) {
            showProgressPopup = true
        } else {
            // A batch just finished (or none has run yet this session) — re-check whether the
            // resolved primary currency should change now that more transactions may exist, and
            // keep amountBaseMinor consistent with it.
            container.refreshPrimaryCurrency()
        }
    }

    // Surface the popup as soon as SMS are queued for the AI batch, even before the debounced
    // worker actually starts running (isProcessing is false the whole time it's just waiting).
    LaunchedEffect(pendingAiCount) {
        if (pendingAiCount > 0) showProgressPopup = true
    }

    // Show progress popup when there are unprocessed SMS (parsed count < total count)
    LaunchedEffect(parsedCount, totalCount) {
        if (parsedCount < totalCount && totalCount > 0) {
            showProgressPopup = true
        }
    }


    val context = LocalContext.current
    val hasCompletedOnboarding by container.settingsStore.onboarding.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipText = if (clipboard.hasPrimaryClip()) {
                    val data = clipboard.primaryClip
                    if (data != null && data.itemCount > 0) {
                        data.getItemAt(0).text?.toString()
                    } else null
                } else null

                if (clipText != null && clipText != lastProcessedClip &&
                    clipText != online.velozen.spendvault.ai.AiBridgeHelper.lastCopiedPrompt) {
                    val extracted = extractJson(clipText)
                    if (extracted != null) {
                        try {
                            if (extracted.startsWith("[")) {
                                val array = org.json.JSONArray(extracted)
                                val list = mutableListOf<DetectedPatternSingle>()
                                for (i in 0 until array.length()) {
                                    val obj = array.getJSONObject(i)
                                    val bodyRegex = obj.optString("bodyRegex")
                                    if (bodyRegex.isNullOrBlank()) continue
                                    val cleanMerchant = obj.optString("cleanMerchant", obj.optString("merchant", "Unknown"))
                                    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Pattern for $cleanMerchant"
                                    val categoryName = obj.optString("categoryName", obj.optString("category", "Uncategorized"))
                                    val logoEmoji = obj.optString("logoEmoji").takeIf { it != "null" && it.isNotBlank() }
                                    val senderRegex = obj.optString("senderRegex").takeIf { it != "null" && it.isNotBlank() }

                                    list.add(
                                        DetectedPatternSingle(
                                            originalText = "Bulk Pattern #${i+1}",
                                            name = name,
                                            cleanMerchant = cleanMerchant,
                                            logoEmoji = logoEmoji,
                                            categoryName = categoryName,
                                            bodyRegex = bodyRegex,
                                            senderRegex = senderRegex,
                                            rawJson = clipText
                                        )
                                    )
                                }
                                if (list.isNotEmpty()) {
                                    detectedPatternBulk = list
                                }
                            } else if (extracted.startsWith("{")) {
                                val obj = org.json.JSONObject(extracted)
                                val bodyRegex = obj.optString("bodyRegex")
                                if (!bodyRegex.isNullOrBlank()) {
                                    val cleanMerchant = obj.optString("cleanMerchant", obj.optString("merchant", "Unknown"))
                                    val name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Pattern for $cleanMerchant"
                                    val categoryName = obj.optString("categoryName", obj.optString("category", "Uncategorized"))
                                    val logoEmoji = obj.optString("logoEmoji").takeIf { it != "null" && it.isNotBlank() }
                                    val senderRegex = obj.optString("senderRegex").takeIf { it != "null" && it.isNotBlank() }

                                    detectedPatternSingle = DetectedPatternSingle(
                                        originalText = "",
                                        name = name,
                                        cleanMerchant = cleanMerchant,
                                        logoEmoji = logoEmoji,
                                        categoryName = categoryName,
                                        bodyRegex = bodyRegex,
                                        senderRegex = senderRegex,
                                        rawJson = clipText
                                    )
                                }
                            }
                        } catch (_: Exception) {
                            // ignore silently if it's not our JSON format
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Single Pattern Dialog
    detectedPatternSingle?.let { pat ->
        AlertDialog(
            onDismissRequest = {
                detectedPatternSingle = null
                lastProcessedClip = pat.rawJson
            },
            title = { Text("🤖 AI Pattern Detected") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("We found a parsing pattern in your clipboard:", style = MaterialTheme.typography.bodyMedium)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Name: ${pat.name}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text("Merchant: ${pat.logoEmoji ?: ""} ${pat.cleanMerchant}", style = MaterialTheme.typography.bodyMedium)
                            Text("Category: ${pat.categoryName}", style = MaterialTheme.typography.bodyMedium)
                            Text("Regex: ${pat.bodyRegex.take(40)}...", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                val reviewVm = viewModel<ReviewViewModel>(factory = factory)
                val scope = rememberCoroutineScope()
                TextButton(onClick = {
                    val rawJson = pat.rawJson
                    detectedPatternSingle = null
                    lastProcessedClip = rawJson
                    scope.launch {
                        val patternCount = reviewVm.applyAiPatterns(rawJson)
                        if (patternCount > 0) {
                            Toast.makeText(context, "🤖 Pattern saved! Applying to all SMS in background…", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "⚠️ No valid patterns found in the AI response.", Toast.LENGTH_LONG).show()
                        }
                    }
                }) {
                    Text("Approve & Save")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    detectedPatternSingle = null
                    lastProcessedClip = pat.rawJson
                }) {
                    Text("Discard")
                }
            }
        )
    }

    // Bulk Pattern Dialog
    detectedPatternBulk?.let { list ->
        AlertDialog(
            onDismissRequest = {
                detectedPatternBulk = null
                lastProcessedClip = list.first().rawJson
            },
            title = { Text("🤖 AI Bulk Mappings Detected") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("We found ${list.size} parsing patterns in your clipboard:", style = MaterialTheme.typography.bodyMedium)
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(list) { pat ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(Modifier.padding(8.dp)) {
                                    Text("${pat.logoEmoji ?: "📝"} ${pat.cleanMerchant} (${pat.categoryName})", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                    Text(pat.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                val reviewVm = viewModel<ReviewViewModel>(factory = factory)
                val scope = rememberCoroutineScope()
                TextButton(onClick = {
                    val rawJson = list.first().rawJson
                    detectedPatternBulk = null
                    lastProcessedClip = rawJson
                    scope.launch {
                        val patternCount = reviewVm.applyAiPatterns(rawJson)
                        if (patternCount > 0) {
                            Toast.makeText(context, "🤖 $patternCount pattern(s) saved! Applying to all SMS in background…", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "⚠️ No valid patterns found in the AI response.", Toast.LENGTH_LONG).show()
                        }
                    }
                }) {
                    Text("Approve All (${list.size})")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    detectedPatternBulk = null
                    lastProcessedClip = list.first().rawJson
                }) {
                    Text("Discard")
                }
            }
        )
    }

    LaunchedEffect(initialTxnId) {
        initialTxnId ?: return@LaunchedEffect
        selected = container.transactionRepository.getById(initialTxnId)
        onTxnIdConsumed()
    }

    PermissionGate {
        Box(modifier = Modifier.fillMaxSize()) {
            CompositionLocalProvider(LocalPrimaryCurrency provides primaryCurrency) {
                MainScaffold(
                    container = container,
                    factory = factory,
                    selected = selected,
                    onSelectedChanged = { selected = it },
                    showAiBanner = showAiBanner,
                    showSmsQueueSection = showSmsQueueSection,
                    onToggleSmsQueueSection = {
                        container.settingsStore.setSmsQueueSectionEnabled(!showSmsQueueSection)
                    },
                )
            }

            // Processing progress — shows overall SMS parsing progress (PARSED vs total SMS count)
            // Persists across screen switches and shows the complete picture of SMS processing.
            // Only visible when there's active processing or pending SMS to process.
            val isQueuedOnly = !processingProgress.isProcessing && pendingAiCount > 0
            val hasUnprocessed = (parsedCount < totalCount) || isQueuedOnly
            if (showProgressPopup && hasUnprocessed) {
                val displayCurrent = parsedCount
                val displayTotal = totalCount
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 90.dp, start = 16.dp, end = 16.dp)
                        .fillMaxWidth(0.9f),
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 6.dp,
                    shadowElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isQueuedOnly) "SMS queued for AI analysis…" else "Processing SMS…",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$displayCurrent / $displayTotal parsed",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            val fraction = if (displayTotal > 0) {
                                displayCurrent.toFloat() / displayTotal.toFloat()
                            } else 0f
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = { showProgressPopup = false },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val container = (context.applicationContext as SpendVaultApp).container
    val hasCompletedOnboarding by container.settingsStore.onboarding.collectAsState()
    
    val required = buildList {
        add(Manifest.permission.READ_SMS)
        add(Manifest.permission.RECEIVE_SMS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val permState = rememberMultiplePermissionsState(required)
    val smsGranted = permState.permissions
        .filter { it.permission == Manifest.permission.READ_SMS || it.permission == Manifest.permission.RECEIVE_SMS }
        .all { it.status.isGranted }
    val notifGranted = permState.permissions
        .firstOrNull { it.permission == Manifest.permission.POST_NOTIFICATIONS }
        ?.status?.isGranted ?: true
    var requested by rememberSaveable { mutableStateOf(false) }
    var showPermissionOnboarding by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(smsGranted) {
        if (smsGranted) {
            SmsSyncWorker.enqueueImport(context)
            online.velozen.spendvault.work.BackupReminderWorker.schedule(context)
        }
    }
    LaunchedEffect(smsGranted, notifGranted) {
        if (smsGranted && !notifGranted) permState.launchMultiplePermissionRequest()
    }

    when {
        !hasCompletedOnboarding.hasCompletedOnboarding -> {
            OnboardingFlowScreen(
                onGrantPermissions = {
                    showPermissionOnboarding = true
                },
                onComplete = { currencyCode ->
                    container.settingsStore.setPrimaryCurrency(currencyCode)
                    container.settingsStore.setOnboardingCompleted(true)
                }
            )
        }
        showPermissionOnboarding -> {
            val permanentlyDenied = requested && !permState.shouldShowRationale
            OnboardingScreen(
                permanentlyDenied = permanentlyDenied,
                onGrant = {
                    if (permanentlyDenied) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } else {
                        requested = true
                        permState.launchMultiplePermissionRequest()
                    }
                },
            )
        }
        smsGranted -> {
            content()
        }
        else -> {
            val permanentlyDenied = requested && !permState.shouldShowRationale
            OnboardingScreen(
                permanentlyDenied = permanentlyDenied,
                onGrant = {
                    if (permanentlyDenied) {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    } else {
                        requested = true
                        permState.launchMultiplePermissionRequest()
                    }
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    container: AppContainer,
    factory: SpendVaultViewModelFactory,
    selected: TransactionEntity?,
    onSelectedChanged: (TransactionEntity?) -> Unit,
    showAiBanner: Boolean = false,
    showSmsQueueSection: Boolean = true,
    onToggleSmsQueueSection: () -> Unit = {},
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    // One-shot category filter handed to the History screen when a category is tapped elsewhere.
    var pendingCategoryFilter by remember { mutableStateOf<Long?>(null) }

    val detailVm = viewModel<TransactionDetailViewModel>(factory = factory)
    val reviewVm = viewModel<ReviewViewModel>(factory = factory)
    val reviewState by reviewVm.state.collectAsState()
    val pending = reviewState.unparsed.size + reviewState.duplicates.size

    val context = LocalContext.current
    val workInfos by remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(SmsSyncWorker.IMPORT_WORK)
    }.collectAsState(initial = emptyList())
    val scanProgress: Float? = workInfos.firstOrNull()?.let { info ->
        if (info.state == WorkInfo.State.RUNNING) {
            val done  = info.progress.getInt(SmsSyncWorker.KEY_DONE, 0)
            val total = info.progress.getInt(SmsSyncWorker.KEY_TOTAL, 0)
            if (total > 0) done.toFloat() / total else 0f
        } else null
    }

    // Hide bottom nav on secondary screens
    val showBottomNav = Dest.entries.any { it.route == currentRoute }

    Scaffold(
        topBar = {
            Column {
                LuminousTopBar(
                    pending = pending,
                    onNotificationsClick = {
                        if (pending > 0) nav.navigate(ROUTE_REVIEW) { launchSingleTop = true }
                    },
                    onSettingsClick = { nav.navigate(ROUTE_SETTINGS) { launchSingleTop = true } },
                    onToggleSmsQueueSection = onToggleSmsQueueSection,
                    showSmsQueueSection = showSmsQueueSection,
                )
                
                // AI auto-categorisation banner (shown below top bar when enabled)
                if (showAiBanner && showSmsQueueSection) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Text(
                                text = "AI is analysing your transactions…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            IconButton(
                                onClick = onToggleSmsQueueSection,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Hide",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
                
                if (scanProgress != null) {
                    if (scanProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { scanProgress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    } else {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    }
                }
            }
        },
        bottomBar = {
            if (showBottomNav) {
                LuminousNavBar(currentRoute = currentRoute) { dest ->
                    nav.navigate(dest.route) {
                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
        },
        floatingActionButton = {
            // Add manual transaction — reachable from Home and History (FR-A).
            if (currentRoute == Dest.Home.route || currentRoute == Dest.History.route) {
                FloatingActionButton(
                    onClick = { nav.navigate(ROUTE_ENTRY) { launchSingleTop = true } },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Add transaction")
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.Home.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.Home.route) {
                DashboardScreen(
                    vm = viewModel<DashboardViewModel>(factory = factory),
                    budgetVm = viewModel<BudgetsViewModel>(factory = factory),
                    onTransactionClick = { onSelectedChanged(it) },
                    onOpenBills = { nav.navigate(ROUTE_BILLS) { launchSingleTop = true } },
                    onOpenSubscriptions = { nav.navigate(ROUTE_SUBSCRIPTIONS) { launchSingleTop = true } },
                    onViewAll = { navigateToTab(nav, Dest.History) },
                )
            }
            composable(Dest.History.route) {
                TransactionsScreen(
                    vm = viewModel<TransactionsViewModel>(factory = factory),
                    pendingReviewCount = pending,
                    onOpenReview = { nav.navigate(ROUTE_REVIEW) { launchSingleTop = true } },
                    onTransactionClick = { onSelectedChanged(it) },
                    onEditTransaction = { nav.navigate("$ROUTE_ENTRY/${it.id}") { launchSingleTop = true } },
                    initialCategoryId = pendingCategoryFilter,
                    onInitialCategoryConsumed = { pendingCategoryFilter = null },
                )
            }
            composable(Dest.Insights.route) {
                AnalyticsScreen(
                    vm = viewModel<AnalyticsViewModel>(factory = factory),
                    onViewAllMerchants = { navigateToTab(nav, Dest.History) },
                    onMerchantClick = { name ->
                        nav.navigate("$ROUTE_MERCHANT/${Uri.encode(name)}") { launchSingleTop = true }
                    },
                    onCategoryClick = { categoryId ->
                        pendingCategoryFilter = categoryId
                        navigateToTab(nav, Dest.History)
                    },
                    onTransactionClick = { onSelectedChanged(it) },
                )
            }
            composable(Dest.Budgets.route) {
                BudgetsScreen(viewModel<BudgetsViewModel>(factory = factory))
            }
            composable(ROUTE_BILLS) {
                BillsScreen(viewModel<BillsViewModel>(factory = factory))
            }
            composable(ROUTE_SUBSCRIPTIONS) {
                SubscriptionsScreen(viewModel<SubscriptionsViewModel>(factory = factory))
            }
            composable(Dest.Accounts.route) {
                AccountsScreen(viewModel<AccountsViewModel>(factory = factory), onTransactionClick = { onSelectedChanged(it) })
            }
            composable(ROUTE_CATEGORIES) {
                CategoriesScreen(
                    viewModel<CategoriesViewModel>(factory = factory),
                    onOpenBudgets = { nav.navigate(Dest.Budgets.route) { launchSingleTop = true } },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(
                    vm = viewModel<SettingsViewModel>(factory = factory),
                    onBack = { nav.popBackStack() },
                    onOpenMerchants = { nav.navigate(ROUTE_MERCHANTS) { launchSingleTop = true } },
                    onOpenGoals = { nav.navigate(ROUTE_GOALS) { launchSingleTop = true } },
                    onOpenPatterns = { nav.navigate(ROUTE_PATTERNS) { launchSingleTop = true } },
                    onOpenSenders = { nav.navigate(ROUTE_SENDERS) { launchSingleTop = true } },
                    onOpenDebug = { nav.navigate(ROUTE_DEBUG) { launchSingleTop = true } },
                )
            }
            composable(ROUTE_DEBUG) {
                DebugScreen(
                    vm = viewModel<SettingsViewModel>(factory = factory),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(ROUTE_SENDERS) {
                SendersScreen(
                    vm = viewModel<SenderClassificationsViewModel>(factory = factory),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(ROUTE_PATTERNS) {
                PatternsScreen(
                    vm = viewModel<SettingsViewModel>(factory = factory),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(ROUTE_GOALS) {
                GoalsScreen(
                    vm = viewModel<GoalsViewModel>(factory = factory),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(ROUTE_MERCHANTS) {
                MerchantsScreen(
                    vm = viewModel<MerchantsViewModel>(factory = factory),
                    onBack = { nav.popBackStack() },
                )
            }
            composable(ROUTE_REVIEW) {
                ReviewScreen(viewModel<ReviewViewModel>(factory = factory), onTransactionClick = { onSelectedChanged(it) })
            }
            composable(ROUTE_ENTRY) {
                ManualEntryScreen(
                    vm = viewModel<ManualEntryViewModel>(factory = factory),
                    editId = null,
                    onClose = { nav.popBackStack() },
                )
            }
            composable(
                route = "$ROUTE_ENTRY/{$ARG_TXN_ID}",
                arguments = listOf(navArgument(ARG_TXN_ID) { type = NavType.LongType }),
            ) { entry ->
                ManualEntryScreen(
                    vm = viewModel<ManualEntryViewModel>(factory = factory),
                    editId = entry.arguments?.getLong(ARG_TXN_ID),
                    onClose = { nav.popBackStack() },
                )
            }
            composable(
                route = "$ROUTE_MERCHANT/{$ARG_MERCHANT}",
                arguments = listOf(navArgument(ARG_MERCHANT) { type = NavType.StringType }),
            ) { entry ->
                val name = entry.arguments?.getString(ARG_MERCHANT).orEmpty()
                MerchantDetailScreen(
                    counterparty = name,
                    container = container,
                    onBack = { nav.popBackStack() },
                    onTransactionClick = { onSelectedChanged(it) },
                )
            }
        }
    }

    selected?.let { txn ->
        TransactionDetailSheet(
            txn = txn,
            vm = detailVm,
            onDismiss = { onSelectedChanged(null) },
            onMerchantHistory = { name ->
                onSelectedChanged(null)
                nav.navigate("$ROUTE_MERCHANT/${Uri.encode(name)}") { launchSingleTop = true }
            },
        )
    }
}

/** Navigate to a primary bottom-nav tab, matching the nav bar's behavior (single-top, state-preserving). */
private fun navigateToTab(nav: androidx.navigation.NavController, dest: Dest) {
    nav.navigate(dest.route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LuminousTopBar(
    pending: Int,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onToggleSmsQueueSection: () -> Unit = {},
    showSmsQueueSection: Boolean = true,
) {
    TopAppBar(
        navigationIcon = {
            Box(modifier = Modifier.padding(start = 8.dp)) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                    modifier = Modifier.size(40.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Text(
                    "SpendLens",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        actions = {
            IconButton(onClick = onNotificationsClick) {
                BadgedBox(badge = {
                    if (pending > 0) Badge(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ) { Text("$pending") }
                }) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = "Notifications",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            IconButton(onClick = onToggleSmsQueueSection) {
                Icon(
                    if (showSmsQueueSection) Icons.Filled.Notifications else Icons.Filled.Close,
                    contentDescription = if (showSmsQueueSection) "Hide SMS Queue" else "Show SMS Queue",
                    tint = if (showSmsQueueSection) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.9f),
        ),
    )
}

@Composable
private fun LuminousNavBar(currentRoute: String?, onNavigate: (Dest) -> Unit) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.95f),
        tonalElevation = 0.dp,
    ) {
        Dest.entries.forEach { dest ->
            val isSelected = currentRoute == dest.route
            NavigationBarItem(
                selected = isSelected,
                onClick = { onNavigate(dest) },
                icon = {
                    Icon(
                        dest.icon,
                        contentDescription = dest.label,
                        modifier = Modifier.size(24.dp),
                    )
                },
                label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            )
        }
    }
}

private fun extractJson(input: String): String? {
    val firstBracket = input.indexOf('[')
    val firstBrace = input.indexOf('{')
    
    if (firstBracket == -1 && firstBrace == -1) return null
    
    return if (firstBracket != -1 && (firstBrace == -1 || firstBracket < firstBrace)) {
        val lastBracket = input.lastIndexOf(']')
        if (lastBracket != -1 && lastBracket > firstBracket) {
            input.substring(firstBracket, lastBracket + 1)
        } else null
    } else {
        val lastBrace = input.lastIndexOf('}')
        if (lastBrace != -1 && lastBrace > firstBrace) {
            input.substring(firstBrace, lastBrace + 1)
        } else null
    }
}

private data class DetectedPatternSingle(
    val originalText: String,
    val name: String,
    val cleanMerchant: String,
    val logoEmoji: String?,
    val categoryName: String,
    val bodyRegex: String,
    val senderRegex: String?,
    val rawJson: String
)
