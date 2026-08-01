package com.spendlens.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendlens.app.SpendLensApp
import com.spendlens.app.ai.MerchantConsolidation
import com.spendlens.app.ai.OpenRouterClient
import com.spendlens.app.util.AppLog
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Premium's periodic merchant-name cleanup: unlike the manual "Consolidate with AI" button on the
 * Merchants screen (which always re-sends every merchant), this runs daily and only calls the AI
 * when at least one merchant has never been checked (see [com.spendlens.app.data.db.MerchantAliasEntity.consolidationCheckedAt]),
 * keeping token usage proportional to how many new merchants actually showed up since last time.
 * The full merchant list is still sent as context on each call so the model can group a new alias
 * ("AMZN*456") under an existing canonical brand ("Amazon"), not just among the new ones.
 */
class MerchantConsolidationWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container
        val store = container.aiConfigStore
        val key = store.effectiveKey()
        if (!store.isUsable() || key == null) {
            AppLog.aiSkipped("merchant_consolidation_periodic", "ai_disabled_or_no_key")
            return Result.success()
        }

        val merchantRepo = container.merchantRepository
        if (merchantRepo.countUnconsolidated() == 0) {
            AppLog.aiSkipped("merchant_consolidation_periodic", "no_new_merchants")
            return Result.success()
        }

        val names = merchantRepo.observeDisplayNames().first()
        val now = System.currentTimeMillis()
        if (names.size < 2) {
            merchantRepo.markUnconsolidatedAsChecked(now)
            return Result.success()
        }

        val prompt = MerchantConsolidation.buildPrompt(names)
        when (val response = container.openRouterClient.complete(key, store.effectiveModel(), prompt, operation = "merchant_consolidation_periodic")) {
            is OpenRouterClient.Result.Failure -> {
                // Leave unchecked rows as-is so the same new merchants are retried on the next run.
                AppLog.aiSkipped("merchant_consolidation_periodic", "call_failed: ${response.message}")
            }
            is OpenRouterClient.Result.Success -> {
                val groups = MerchantConsolidation.parse(response.content)
                val known = names.toMutableSet()
                var merged = 0
                var appliedGroups = 0
                for (group in groups) {
                    var groupMerged = false
                    for (alias in group.aliases) {
                        if (alias == group.canonical || alias !in known) continue
                        merchantRepo.renameDisplay(alias, group.canonical)
                        container.transactionRepository.renameCounterparty(alias, group.canonical)
                        known.remove(alias)
                        merged++
                        groupMerged = true
                    }
                    if (groupMerged) appliedGroups++
                }
                merchantRepo.markUnconsolidatedAsChecked(now)
                AppLog.aiApplied(
                    "merchant_consolidation_periodic",
                    "merged=$merged groups=$appliedGroups parsed_groups=${groups.size}",
                )
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "merchant_consolidation_periodic"

        /** Daily check (idempotent — keeps any existing schedule). Gating happens inside [doWork]. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MerchantConsolidationWorker>(1, TimeUnit.DAYS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request,
            )
        }
    }
}
