package com.spendlens.app.ui.nav

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.spendlens.app.data.db.TransactionEntity
import com.spendlens.app.di.AppContainer
import com.spendlens.app.ui.components.TransactionDetailSheet
import com.spendlens.app.ui.screens.AccountsScreen
import com.spendlens.app.ui.screens.AnalyticsScreen
import com.spendlens.app.ui.screens.BillsScreen
import com.spendlens.app.ui.screens.BudgetsScreen
import com.spendlens.app.ui.screens.CategoriesScreen
import com.spendlens.app.ui.screens.DashboardScreen
import com.spendlens.app.ui.screens.MerchantDetailScreen
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

// Bottom nav destinations (4 primary sections matching the design)
enum class Dest(val route: String, val label: String, val icon: ImageVector) {
    Home("dashboard",     "Home",     Icons.Filled.Home),
    History("transactions","History", Icons.AutoMirrored.Filled.ReceiptLong),
    Insights("analytics", "Insights", Icons.Filled.Analytics),
    Budgets("budgets",    "Budgets",  Icons.Filled.Category),
}

private const val ROUTE_SETTINGS     = "settings"
private const val ROUTE_REVIEW       = "review"
private const val ROUTE_BILLS        = "bills"
private const val ROUTE_ACCOUNTS     = "accounts"
private const val ROUTE_CATEGORIES   = "categories"
private const val ROUTE_MERCHANT     = "merchant"
private const val ARG_MERCHANT       = "name"

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
            container = container,
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
        ?.status?.isGranted ?: true
    var requested by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(smsGranted) { if (smsGranted) SmsSyncWorker.enqueueImport(context) }
    LaunchedEffect(smsGranted, notifGranted) {
        if (smsGranted && !notifGranted) permState.launchMultiplePermissionRequest()
    }

    if (smsGranted) {
        content()
    } else {
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScaffold(
    container: AppContainer,
    factory: SpendLensViewModelFactory,
    selected: TransactionEntity?,
    onSelectedChanged: (TransactionEntity?) -> Unit,
) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

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
                )
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
                )
            }
            composable(Dest.History.route) {
                TransactionsScreen(
                    vm = viewModel<TransactionsViewModel>(factory = factory),
                    pendingReviewCount = pending,
                    onOpenReview = { nav.navigate(ROUTE_REVIEW) { launchSingleTop = true } },
                    onTransactionClick = { onSelectedChanged(it) },
                )
            }
            composable(Dest.Insights.route) {
                AnalyticsScreen(viewModel<AnalyticsViewModel>(factory = factory))
            }
            composable(Dest.Budgets.route) {
                BudgetsScreen(viewModel<BudgetsViewModel>(factory = factory))
            }
            composable(ROUTE_BILLS) {
                BillsScreen(viewModel<BillsViewModel>(factory = factory))
            }
            composable(ROUTE_ACCOUNTS) {
                AccountsScreen(viewModel<AccountsViewModel>(factory = factory), onTransactionClick = { onSelectedChanged(it) })
            }
            composable(ROUTE_CATEGORIES) {
                CategoriesScreen(
                    viewModel<CategoriesViewModel>(factory = factory),
                    onOpenBudgets = { nav.navigate(Dest.Budgets.route) { launchSingleTop = true } },
                )
            }
            composable(ROUTE_SETTINGS) {
                SettingsScreen(viewModel<SettingsViewModel>(factory = factory))
            }
            composable(ROUTE_REVIEW) {
                ReviewScreen(viewModel<ReviewViewModel>(factory = factory), onTransactionClick = { onSelectedChanged(it) })
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LuminousTopBar(
    pending: Int,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit,
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
