package online.velozen.spendvault

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.ktx.Firebase
import online.velozen.spendvault.config.RemoteConfigManager
import online.velozen.spendvault.di.AppContainer
import online.velozen.spendvault.sms.SmsProcessingStats
import online.velozen.spendvault.ui.viewmodel.DebugCounts
import online.velozen.spendvault.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpendVaultApp : Application() {

    lateinit var container: AppContainer
        private set

    lateinit var analytics: FirebaseAnalytics
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        analytics = Firebase.analytics
        AppLog.i("SpendVaultApp starting version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        container = AppContainer(this)
        createNotificationChannels()
        com.spendvault.app.work.BillReminderWorker.schedule(this)
        com.spendvault.app.work.CardPaymentReminderWorker.schedule(this)
        com.spendvault.app.work.VelocityAlertWorker.schedule(this)
        com.spendvault.app.work.WidgetRefreshWorker.schedule(this)
        com.spendvault.app.work.MerchantConsolidationWorker.schedule(this)
        
        setupDebugAnalyticsSync()
        
        appScope.launch {
            container.seed()
            runCatching { container.fxRepository.refresh() }
            val stranded = container.rawSmsDao.listByStatus(com.spendvault.app.data.db.RawStatus.PENDING_AI)
            if (stranded.isNotEmpty()) {
                AppLog.i("SpendVaultApp: resuming ${stranded.size} AI-batch rows stranded from a prior run")
                com.spendvault.app.work.AiSmsBatchWorker.enqueue(this@SpendVaultApp)
            }
        }
    }
    
    private fun setupDebugAnalyticsSync() {
        appScope.launch(Dispatchers.IO) {
            try {
                RemoteConfigManager.getInstance().fetchAndActivate()
                
                val raw = container.rawSmsDao
                val txn = container.database.transactionDao()
                val pat = container.patternRepository
                val stats = container.smsProcessor.stats.value
                
                val counts = DebugCounts(
                    totalRawSms = raw.count(),
                    parsedCount = raw.countByStatus(com.spendvault.app.data.db.RawStatus.PARSED),
                    unparsedCount = raw.countByStatus(com.spendvault.app.data.db.RawStatus.UNPARSED),
                    ignoredCount = raw.countByStatus(com.spendvault.app.data.db.RawStatus.IGNORED),
                    pendingAiCount = raw.countByStatus(com.spendvault.app.data.db.RawStatus.PENDING_AI),
                    aiParsedCount = raw.countAiParsed(),
                    aiPatternParsedCount = raw.countAiPatternParsed(),
                    totalTransactions = txn.count(),
                    duplicateTransactions = txn.countDuplicates(),
                    patternBuiltin = pat.countBySource(com.spendvault.app.data.db.PatternSource.BUILTIN),
                    patternAi = pat.countBySource(com.spendvault.app.data.db.PatternSource.AI),
                    patternHeuristic = pat.countBySource(com.spendvault.app.data.db.PatternSource.HEURISTIC),
                    patternUser = pat.countBySource(com.spendvault.app.data.db.PatternSource.USER),
                    patternFirebase = 0,
                    firebaseSyncLastRun = "",
                    firebaseSyncPatternsDownloaded = 0,
                    firebaseSyncSendersScanned = 0,
                    firebaseSyncPatternsUploaded = 0,
                )
                
                container.debugAnalyticsManager.syncDebugAnalytics(counts, stats)
                
            } catch (e: Exception) {
                AppLog.e("SpendVaultApp", "Failed to sync debug analytics: ${e.message}", e)
            }
        }
    }
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            // Migrate: delete old low-importance channel so it's recreated with HIGH.
            manager.deleteNotificationChannel(CHANNEL_TRANSACTIONS_V1)
            val transactions = NotificationChannel(
                CHANNEL_TRANSACTIONS,
                getString(R.string.sms_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = getString(R.string.sms_channel_desc) }
            val bills = NotificationChannel(
                CHANNEL_BILLS,
                getString(R.string.bills_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = getString(R.string.bills_channel_desc) }
            val budgets = NotificationChannel(
                CHANNEL_BUDGETS,
                getString(R.string.budgets_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = getString(R.string.budgets_channel_desc) }
            val aiPatterns = NotificationChannel(
                CHANNEL_AI_PATTERNS,
                "AI Pattern Updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Notifies when AI-taught patterns finish applying to your SMS history" }
            manager.createNotificationChannel(transactions)
            manager.createNotificationChannel(bills)
            manager.createNotificationChannel(budgets)
            manager.createNotificationChannel(aiPatterns)
        }
    }

    companion object {
        private const val CHANNEL_TRANSACTIONS_V1 = "transactions" // old low-importance channel
        const val CHANNEL_TRANSACTIONS = "transactions_v2"
        const val CHANNEL_BILLS = "bills"
        const val CHANNEL_BUDGETS = "budgets"
        const val CHANNEL_AI_PATTERNS = "ai_patterns"
    }
}
