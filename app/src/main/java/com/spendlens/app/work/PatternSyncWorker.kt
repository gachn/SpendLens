package com.spendlens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.spendlens.app.di.AppContainer
import com.spendlens.app.sync.PatternSyncService
import com.spendlens.app.sync.SyncResult
import com.spendlens.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PatternSyncWorker(
    context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return withContext(Dispatchers.IO) {
            try {
                val container = AppContainer(applicationContext)
                val syncService = PatternSyncService(
                    context = applicationContext,
                    patternDao = container.database.patternDao()
                )

                AppLog.d("PatternSyncWorker", "Starting pattern sync worker")

                val result = syncService.syncPatterns()

                if (result.isSuccess) {
                    AppLog.d("PatternSyncWorker", "Sync completed: ${result.patternsDownloaded} patterns downloaded, ${result.patternsMerged} merged, ${result.firebasePatternsParsed} Firebase patterns parsed")
                    Result.success()
                } else {
                    AppLog.e("Pattern sync failed: ${result.error}", "PatternSyncWorker", null)
                    Result.retry()
                }
            } catch (e: Exception) {
                AppLog.e("Pattern sync worker crashed", "PatternSyncWorker", e)
                Result.retry()
            }
        }
    }

    companion object {
        const val WORK_NAME = "pattern_sync_worker"

        suspend fun enqueue(context: Context, syncResult: SyncResult) {
            androidx.work.WorkManager.getInstance(context).enqueue(
                androidx.work.OneTimeWorkRequestBuilder<PatternSyncWorker>()
                    .setInitialDelay(5, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
            )
        }

        suspend fun enqueuePeriodic(context: Context) {
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                androidx.work.PeriodicWorkRequestBuilder<PatternSyncWorker>(
                    24, java.util.concurrent.TimeUnit.HOURS
                ).build()
            )
        }
    }
}