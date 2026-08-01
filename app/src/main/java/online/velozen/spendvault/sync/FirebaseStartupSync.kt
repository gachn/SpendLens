package online.velozen.spendvault.sync

import android.content.Context
import online.velozen.spendvault.config.RemoteConfigManager
import online.velozen.spendvault.data.db.PatternDao
import online.velozen.spendvault.data.db.PatternSource
import online.velozen.spendvault.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FirebaseStartupSync {

    suspend fun checkAndSyncIfNeeded(
        context: Context,
        patternDao: PatternDao
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val remoteConfig = RemoteConfigManager.getInstance()
            remoteConfig.fetchAndActivate()
            
            val syncThreshold = remoteConfig.getFirebaseSyncThreshold()
            val uploadableCount = countUploadablePatterns(patternDao)
            
            AppLog.d("FirebaseStartupSync", "Sync threshold: $syncThreshold, Uploadable patterns: $uploadableCount")
            
            if (uploadableCount >= syncThreshold) {
                AppLog.d("FirebaseStartupSync", "Threshold reached, starting sync")
                val syncService = PatternSyncService(context, patternDao)
                val result = syncService.syncPatterns()
                
                if (result.isSuccess) {
                    AppLog.d("FirebaseStartupSync", "Sync completed: ${result.patternsUploaded} uploaded, ${result.patternsDownloaded} downloaded")
                    true
                } else {
                    AppLog.e("FirebaseStartupSync", "Sync failed: ${result.error}", null)
                    false
                }
            } else {
                AppLog.d("FirebaseStartupSync", "Threshold not reached, skipping sync")
                false
            }
        } catch (e: Exception) {
            AppLog.e("FirebaseStartupSync", "Sync check failed", e)
            false
        }
    }

    private suspend fun countUploadablePatterns(patternDao: PatternDao): Int {
        return try {
            val allPatterns = patternDao.enabledOrdered()
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            
            allPatterns.count { pattern ->
                pattern.source in listOf(PatternSource.AI, PatternSource.HEURISTIC, PatternSource.USER) &&
                pattern.matchCount >= 5 &&
                (pattern.lastMatchedAt ?: 0) > thirtyDaysAgo
            }
        } catch (e: Exception) {
            AppLog.e("FirebaseStartupSync", "Failed to count uploadable patterns", e)
            0
        }
    }
}
