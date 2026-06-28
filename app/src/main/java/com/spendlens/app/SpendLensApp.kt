package com.spendlens.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.spendlens.app.di.AppContainer
import com.spendlens.app.util.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SpendLensApp : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppLog.i("SpendLensApp starting version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        container = AppContainer(this)
        createNotificationChannels()
        com.spendlens.app.work.BillReminderWorker.schedule(this)
        com.spendlens.app.work.CardPaymentReminderWorker.schedule(this)
        com.spendlens.app.work.VelocityAlertWorker.schedule(this)
        com.spendlens.app.work.WidgetRefreshWorker.schedule(this)
        appScope.launch {
            container.seed()
            runCatching { container.fxRepository.refresh() } // refresh FX rates best-effort
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
