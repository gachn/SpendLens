package com.spendlens.app.data.prefs

import android.content.Context
import com.spendlens.app.sync.SyncResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SyncStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)

    private val _syncResult = MutableStateFlow(loadSyncResult())
    val syncResult: StateFlow<SyncResult> = _syncResult.asStateFlow()

    private companion object {
        const val KEY_LAST_SYNC_TIME = "last_sync_time"
        const val KEY_PATTERNS_DOWNLOADED = "patterns_downloaded"
        const val KEY_SENDERS_SCANNED = "senders_scanned"
        const val KEY_PATTERNS_UPLOADED = "patterns_uploaded"
        const val KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp"
        const val KEY_DURATION_MS = "duration_ms"
        const val KEY_ERROR = "error"
    }

    suspend fun saveSyncResult(result: SyncResult) {
        prefs.edit().apply {
            putLong(KEY_LAST_SYNC_TIMESTAMP, System.currentTimeMillis())
            putString(KEY_LAST_SYNC_TIME, formatTimestamp(System.currentTimeMillis()))
            putInt(KEY_PATTERNS_DOWNLOADED, result.patternsDownloaded)
            putInt(KEY_SENDERS_SCANNED, result.sendersScanned)
            putInt(KEY_PATTERNS_UPLOADED, result.patternsUploaded)
            putLong(KEY_DURATION_MS, result.durationMs)
            putString(KEY_ERROR, result.error)
        }.apply()
        
        _syncResult.value = loadSyncResult()
    }

    fun getLastSyncTime(): String {
        return prefs.getString(KEY_LAST_SYNC_TIME, "Never") ?: "Never"
    }

    fun getFirebasePatternsDownloaded(): Int {
        return prefs.getInt(KEY_PATTERNS_DOWNLOADED, 0)
    }

    fun getFirebaseSendersScanned(): Int {
        return prefs.getInt(KEY_SENDERS_SCANNED, 0)
    }

    fun getFirebasePatternsUploaded(): Int {
        return prefs.getInt(KEY_PATTERNS_UPLOADED, 0)
    }

    fun getLastSyncTimestamp(): Long {
        return prefs.getLong(KEY_LAST_SYNC_TIMESTAMP, 0L)
    }

    private fun loadSyncResult(): SyncResult {
        return SyncResult(
            sendersScanned = prefs.getInt(KEY_SENDERS_SCANNED, 0),
            patternsDownloaded = prefs.getInt(KEY_PATTERNS_DOWNLOADED, 0),
            patternsMerged = 0,
            patternsSkipped = 0,
            firebasePatternsParsed = 0,
            patternsUploaded = prefs.getInt(KEY_PATTERNS_UPLOADED, 0),
            durationMs = prefs.getLong(KEY_DURATION_MS, 0L),
            error = prefs.getString(KEY_ERROR, null)
        )
    }

    private fun formatTimestamp(timestamp: Long): String {
        return if (timestamp == 0L) {
            "Never"
        } else {
            val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}