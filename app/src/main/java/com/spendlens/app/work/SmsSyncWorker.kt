package com.spendlens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.spendlens.app.SpendLensApp
import com.spendlens.app.parser.model.SmsMessage
import com.spendlens.app.sms.TransactionNotifier

/**
 * Background parser. Two modes: parse a single freshly-received SMS, or import the
 * whole inbox. Idempotent and crash-safe (WorkManager retry). docs/DESIGN.md §6.
 */
class SmsSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container
        container.seed() // no-op once seeded; guarantees patterns/categories exist
        runCatching { container.fxRepository.refresh() } // best-effort; keeps cached rates on failure

        return when (inputData.getString(KEY_MODE)) {
            MODE_IMPORT -> {
                runCatching {
                    container.smsImporter.importAll { done, total ->
                        setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total))
                    }
                    // Refresh recurring-bill detection over the freshly-imported history.
                    val detected = com.spendlens.app.parser.BillDetector.detect(
                        container.transactionRepository.allDebits(),
                    )
                    container.billRepository.syncDetected(detected)
                }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
            }
            else -> {
                val sender = inputData.getString(KEY_SENDER) ?: return Result.success()
                val body = inputData.getString(KEY_BODY) ?: return Result.success()
                val ts = inputData.getLong(KEY_TS, System.currentTimeMillis())
                runCatching { container.smsProcessor.process(SmsMessage(sender, body, ts)) }
                    .onSuccess { txn ->
                        txn?.let { TransactionNotifier.notify(applicationContext, it) }
                        WidgetRefreshWorker.enqueue(applicationContext)
                    }
                    .fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
            }
        }
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_SENDER = "sender"
        private const val KEY_BODY = "body"
        private const val KEY_TS = "ts"
        private const val MODE_IMPORT = "import"
        const val IMPORT_WORK = "sms_import"

        fun enqueueSingle(context: Context, sender: String, body: String, ts: Long) {
            val data = workDataOf(KEY_SENDER to sender, KEY_BODY to body, KEY_TS to ts)
            val request = OneTimeWorkRequestBuilder<SmsSyncWorker>().setInputData(data).build()
            WorkManager.getInstance(context).enqueue(request)
        }

        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"

        fun enqueueImport(context: Context) {
            val data = workDataOf(KEY_MODE to MODE_IMPORT)
            val request = OneTimeWorkRequestBuilder<SmsSyncWorker>().setInputData(data).build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMPORT_WORK,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
