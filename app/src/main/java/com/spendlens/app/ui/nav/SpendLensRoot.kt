package com.spendlens.app.ui.nav

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.shouldShowRationale
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.di.AppContainer
import com.spendlens.app.ui.components.TransactionDetailSheet
import com.spendlens.app.ui.screens.AccountsScreen
import com.spendlens.app.ui.screens.AnalyticsScreen
import com.spendlens.app.ui.screens.BillsScreen
import com.spendlens.app.ui.screens.BudgetsScreen
import com.spendlens.app.ui.screens.CategoriesScreen
import com.spendlens.app.ui.screens.DashboardScreen
import com.spendlens.app.ui.screens.OnboardingScreen
import com.spendlens.app.ui.screens.ReviewScreen
import com.spendlens.app.ui.screens.SettingsScreen
import com.spendlens.app.ui.screens.TransactionsScreen
import com.spendlens.app.ui.viewmodel.AccountsViewModel
import com.spendlens.app.ui.viewmodel.AnalyticsViewModel
import com.spendlens.app.ui.viewmodel.BillsViewModel
import com.spendlens.app.ui.viewmodel.BudgetsViewModel
import com.spendlens.app.ui.viewmodel.CategoriesViewModel
import com.spendlens.app.ui.viewmodel.DashboardViewModel
import com.spendlens.app.ui.viewmodel.ReviewViewModel
import com.spendlens.app.ui.viewmodel.SettingsViewModel
import com.spendlens.app.ui.viewmodel.SpendLensViewModelFactory
import com.spendlens.app.ui.viewmodel.TransactionDetailViewModel
import com.spendlens.app.ui.viewmodel.TransactionsViewModel
import com.spendlens.app.work.SmsSyncWorker

enum class Dest(val route: String, val title: String, val icon: ImageVector) {
    Dashboard("dashboard", "SpendLens", Icons.Filled.Home),
    Transactions("transactions", "Transactions", Icons.AutoMirrored.Filled.ReceiptLong),
    Accounts("accounts", "Accounts", Icons.Filled.AccountBalanceWallet),
    Analytics("analytics", "Analytics", Icons.Filled.BarChart),
    Categories("categories", "Categories", Icons.Filled.Category),
}

private const val ROUTE_SETTINGS = "settings"

private const val ROUTE_REVIEW = "review"
private const val ROUTE_BUDGETS = "budgets"
private const val ROUTE_BILLS = "bills"

@Composable
fun SpendLensRoot(
    container: AppContainer,
    initialTxnId: Long? = null,
    onTxnIdConsumed: () -> Unit = {},
) {
    val factory = remember { SpendLensViewModelFactory(container) }
    var selected by remember { mutableStateOf<TransactionEntity?>(null) }

    LaunchedEffect(initialTxnId) {
        initialTxnId ?: return@LaunchedEffect
        selected = container.transactionRepository.getById(initialTxnId)
        onTxnIdConsumed()
    }

    PermissionGate {
        MainScaffold(
            factory = factory,
            selected = selected,
            onSelectedChanged = { selected = it },
        )
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
private fun PermissionGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
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
        ?.status?.isGranted ?: true // below T: permission doesn't exist, treat as granted
    var requested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(smsGranted) {
        if (smsGranted) SmsSyncWorker.enqueueImport(context)
    }
    // If SMS was already granted (e.g. previous install) but notifications haven't been
    // requested yet, ask now — without this, POST_NOTIFICATIONS is never triggered when
    // the onboarding screen is skipped.
    LaunchedEffect(smsGranted, notifGranted) {
        if (smsGranted && !notifGranted) permState.launchMultiplePermissionRequest()
    }

    if (smsGranted) {
        content()
    } else {
        // After at least one request, no-rationale + not-granted means "Don't ask again".
        val permanentlyDenied = requested && !permState.shouldShowRationale
        OnboardingScreen(
            permanentlyDenied = permanentlyDenied,
            onGrant = {
                if (permanentlyDenied) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                } else {
                    requested = true
                    permState.launchMultiplePermissionRequest()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    factory: SpendLensViewModelFactory,
    selected: TransactionEntity?,
    onSelectedChanged: (TransactionEntity?) -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val title = when (currentRoute) {
        ROUTE_REVIEW -> "Review"
        ROUTE_BUDGETS -> "Budgets"
        ROUTE_BILLS -> "Bills"
        ROUTE_SETTINGS -> "Settings"
        else -> Dest.entries.firstOrNull { it.route == currentRoute }?.title ?: "SpendLens"
    }

    val detailVm = viewModel<TransactionDetailViewModel>(factory = factory)
    val reviewVm = viewModel<ReviewViewModel>(factory = factory)
    val reviewState by reviewVm.state.collectAsState()
    val pending = reviewState.unparsed.size + reviewState.duplicates.size

    val context = LocalContext.current
    val workInfos by remember(context) {
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow(SmsSyncWorker.IMPORT_WORK)
    }.collectAsState(initial = emptyList())
    val scanProgress: Float? = workInfos.firstOrNull()?.let { info ->
        if (info.state == WorkInfo.State.RUNNING) {
            val done = info.progress.getInt(SmsSyncWorker.KEY_DONE, 0)
            val total = info.progress.getInt(SmsSyncWorker.KEY_TOTAL, 0)
            if (total > 0) done.toFloat() / total else 0f
        } else null
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = { Text(title) },
                    actions = {
                        IconButton(onClick = { nav.navigate(ROUTE_REVIEW) { launchSingleTop = true } }) {
                            BadgedBox(badge = { if (pending > 0) Badge { Text("$pending") } }) {
                                Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = "Review")
                            }
                        }
                        IconButton(onClick = { nav.navigate(ROUTE_SETTINGS) { launchSingleTop = true } }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    },
                )
                if (scanProgress != null) {
                    if (scanProgress > 0f) {
                        LinearProgressIndicator(
                            progress = { scanProgress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                Dest.entries.forEach { dest ->
                    NavigationBarItem(
                        selected = currentRoute == dest.route,
                        onClick = {
                            nav.navigate(dest.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.title) },
                        label = { Text(dest.title) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Dest.Dashboard.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Dest.Dashboard.route) {
                DashboardScreen(
                    viewModel<DashboardViewModel>(factory = factory),
                    onTransactionClick = { onSelectedChanged(it) },
                    onOpenBills = { nav.navigate(ROUTE_BILLS) { launchSingleTop = true } },
                )
            }
            composable(ROUTE_BILLS) {
                BillsScreen(viewModel<BillsViewModel>(factory = factory))
            }
            composable(Dest.Transactions.route) {
                TransactionsScreen(viewModel<TransactionsViewModel>(factory = factory), onTransactionClick = { onSelectedChanged(it) })
            }
            composable(Dest.Accounts.route) {
                AccountsScreen(viewModel<AccountsViewModel>(factory = factory), onTransactionClick = { onSelectedChanged(it) })
            }
            composable(Dest.Categories.route) {
                CategoriesScreen(
                    viewModel<CategoriesViewModel>(factory = factory),
                    onOpenBudgets = { nav.navigate(ROUTE_BUDGETS) { launchSingleTop = true } },
                )
            }
            composable(ROUTE_BUDGETS) {
                BudgetsScreen(viewModel<BudgetsViewModel>(factory = factory))
            }
            composable(Dest.Analytics.route) {
                AnalyticsScreen(viewModel<AnalyticsViewModel>(factory = factory))
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(viewModel<SettingsViewModel>(factory = factory))
            }
            composable(ROUTE_REVIEW) {
                ReviewScreen(viewModel<ReviewViewModel>(factory = factory), onTransactionClick = { onSelectedChanged(it) })
            }
        }
    }

    selected?.let { txn ->
        TransactionDetailSheet(txn = txn, vm = detailVm, onDismiss = { onSelectedChanged(null) })
    }
}
