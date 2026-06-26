package com.spendlens.app.work

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.spendlens.app.MainActivity
import com.spendlens.app.R
import com.spendlens.app.SpendLensApp
import com.spendlens.app.util.AppLog
import com.spendlens.app.util.NotificationHelper

/**
 * WorkManager job for the slow half of the "Teach with AI" / pattern-update flow.
 *
 * When [KEY_PATTERN_IDS] is present in the input data the worker runs the targeted
 * [SmsProcessor.reprocessForPatterns] — only SMS that were previously matched by those exact
 * patterns (plus all UNPARSED rows) are touched. This is far cheaper than a full inbox scan
 * and is the default path whenever a pattern is created or modified.
 *
 * When no pattern IDs are supplied (e.g. a manual "Reprocess all" from the Review screen) the
 * worker falls back to [SmsProcessor.reprocessAllSms].
 *
 * Enqueued as a unique job ([WORK_NAME]) with [ExistingWorkPolicy.REPLACE] so rapid successive
 * "Teach with AI" taps coalesce into one reprocess run.
 *
 * Survives screen dismissal, activity recreation, and process restarts because it runs inside
 * WorkManager rather than a coroutine scope tied to the UI lifecycle.
 */
class PatternApplyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as SpendLensApp).container
        val patternIds = patternIdsFromData(inputData.getString(KEY_PATTERN_IDS))

        val smsChanged = if (patternIds.isNotEmpty()) {
            AppLog.i("PatternApplyWorker: targeted reprocess for patternIds=$patternIds")
            runCatching { container.smsProcessor.reprocessForPatterns(patternIds) }
                .onFailure { AppLog.e("reprocessForPatterns failed in PatternApplyWorker", "worker", it) }
                .getOrDefault(0)
        } else {
            AppLog.i("PatternApplyWorker: full SMS reprocess (no pattern IDs provided)")
            runCatching { container.smsProcessor.reprocessAllSms() }
                .onFailure { AppLog.e("reprocessAllSms failed in PatternApplyWorker", "worker", it) }
                .getOrDefault(0)
        }

        AppLog.i("PatternApplyWorker: done smsChanged=$smsChanged")

        WidgetRefreshWorker.enqueue(applicationContext)
        notifyDone(applicationContext, smsChanged)
        return Result.success()
    }

    private fun notifyDone(context: Context, smsChanged: Int) {
        val contentText = if (smsChanged > 0) {
            "Patterns applied — $smsChanged transaction(s) updated."
        } else {
            "Patterns applied. No new matches found in existing SMS."
        }

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, SpendLensApp.CHANNEL_AI_PATTERNS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("AI Patterns Applied")
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationHelper.notify(context, NOTIFICATION_ID, notification)
    }

    companion object {
        const val WORK_NAME = "pattern_apply"
        private const val NOTIFICATION_ID = 9001
        const val KEY_PATTERN_IDS = "pattern_ids"

        /**
         * Serialise a list of pattern IDs into the work-data string. Returns null (omit the key)
         * when the list is empty so the worker falls back to a full reprocess.
         */
        fun patternIdsToString(ids: List<Long>): String? =
            if (ids.isEmpty()) null else ids.joinToString(",")

        /**
         * Deserialise the comma-joined pattern-ID string from WorkManager input data. Returns an
         * empty list when the string is null or blank; non-numeric tokens are silently skipped.
         */
        fun patternIdsFromData(idsString: String?): List<Long> =
            idsString?.split(",")
                ?.mapNotNull { it.trim().toLongOrNull() }
                ?.filter { it > 0 }
                ?: emptyList()

        /**
         * Enqueue (or replace any already-pending) pattern-apply job.
         *
         * @param patternIds IDs of the patterns that were just created/modified. When non-empty
         *   only SMS previously matched by those patterns are reprocessed (plus all UNPARSED rows).
         *   When empty a full inbox reprocess is performed.
         */
        fun enqueue(context: Context, patternIds: List<Long> = emptyList()) {
            val inputData = androidx.work.workDataOf(
                KEY_PATTERN_IDS to patternIdsToString(patternIds),
            )
            val request = OneTimeWorkRequestBuilder<PatternApplyWorker>()
                .setInputData(inputData)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
