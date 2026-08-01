package com.spendlens.app.sync

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import com.spendlens.app.data.db.PatternDao
import com.spendlens.app.data.db.PatternSource
import com.spendlens.app.data.db.SmsPatternEntity
import com.spendlens.app.parser.model.Channel
import com.spendlens.app.util.AppLog
import kotlinx.coroutines.tasks.await

class PatternSyncService(
    private val context: Context,
    private val patternDao: PatternDao,
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    companion object {
        private const val MIN_UPLOAD_MATCH_COUNT = 5
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }

    suspend fun syncPatterns(): SyncResult {
        val startTime = System.currentTimeMillis()
        AppLog.d("PatternSync", "Starting pattern sync")

        return try {
            // Phase 1: Upload high-quality local patterns to Firebase
            val uploadResult = uploadHighQualityPatterns()
            AppLog.d("PatternSync", "Uploaded ${uploadResult.uploaded} patterns to Firebase")

            // Phase 2: Download patterns for device senders
            val uniqueSenders = extractUniqueSenders()
            AppLog.d("PatternSync", "Found ${uniqueSenders.size} unique senders")

            if (uniqueSenders.isEmpty()) {
                return SyncResult(
                    sendersScanned = 0,
                    patternsDownloaded = 0,
                    patternsMerged = 0,
                    patternsSkipped = 0,
                    firebasePatternsParsed = 0,
                    patternsUploaded = uploadResult.uploaded,
                    durationMs = System.currentTimeMillis() - startTime
                )
            }

            val cloudPatterns = fetchPatternsForSenders(uniqueSenders)
            AppLog.d("PatternSync", "Downloaded ${cloudPatterns.size} patterns from Firebase")

            val mergeResult = mergePatterns(cloudPatterns)
            val firebaseParsedCount = countFirebaseParsedPatterns()

            SyncResult(
                sendersScanned = uniqueSenders.size,
                patternsDownloaded = cloudPatterns.size,
                patternsMerged = mergeResult.merged,
                patternsSkipped = mergeResult.skipped,
                firebasePatternsParsed = firebaseParsedCount,
                patternsUploaded = uploadResult.uploaded,
                durationMs = System.currentTimeMillis() - startTime
            )
        } catch (e: Exception) {
            AppLog.e("Pattern sync failed", "PatternSync", e)
            SyncResult(
                sendersScanned = 0,
                patternsDownloaded = 0,
                patternsMerged = 0,
                patternsSkipped = 0,
                firebasePatternsParsed = 0,
                patternsUploaded = 0,
                durationMs = System.currentTimeMillis() - startTime,
                error = e.message
            )
        }
    }

    private suspend fun uploadHighQualityPatterns(): UploadResult {
        var uploaded = 0
        var skipped = 0

        try {
            val allPatterns = patternDao.enabledOrdered()
            
            val uploadablePatterns = allPatterns.filter { pattern ->
                pattern.source in listOf(PatternSource.AI, PatternSource.HEURISTIC, PatternSource.USER) &&
                pattern.matchCount >= MIN_UPLOAD_MATCH_COUNT &&
                (pattern.lastMatchedAt ?: 0) > System.currentTimeMillis() - THIRTY_DAYS_MS
            }

            AppLog.d("PatternSync", "Found ${uploadablePatterns.size} uploadable patterns")

            for (pattern in uploadablePatterns) {
                try {
                    val existingQuery = firestore.collection(CloudPattern.COLLECTION)
                        .whereEqualTo("bodyRegex", pattern.bodyRegex)
                        .limit(1)
                        .get()
                        .await()

                    if (existingQuery.isEmpty) {
                        val senderName = pattern.senderRegex?.let { normalizeSenderName(it) } ?: "UNKNOWN"
                        val channel = detectChannel(pattern)

                        val cloudPattern = CloudPattern(
                            id = "",
                            name = pattern.name,
                            senderName = senderName,
                            senderRegex = pattern.senderRegex,
                            bodyRegex = pattern.bodyRegex,
                            priority = pattern.priority,
                            source = pattern.source,
                            countryCode = detectCountryCode(),
                            channel = channel,
                            qualityScore = calculateQualityScore(pattern),
                            globalMatchCount = pattern.matchCount,
                            avgSuccessRate = 0.9,
                            createdAt = System.currentTimeMillis(),
                            lastUpdated = System.currentTimeMillis(),
                            deviceId = getDeviceId()
                        )

                        firestore.collection(CloudPattern.COLLECTION).add(cloudPattern).await()
                        uploaded++
                        AppLog.d("PatternSync", "Uploaded pattern: ${pattern.name}")
                    } else {
                        skipped++
                        AppLog.d("PatternSync", "Pattern already exists in Firebase: ${pattern.name}")
                    }
                } catch (e: Exception) {
                    AppLog.e("Failed to upload pattern: ${pattern.name}", "PatternSync", e)
                    skipped++
                }
            }
        } catch (e: Exception) {
            AppLog.e("Failed to upload patterns", "PatternSync", e)
        }

        return UploadResult(uploaded, skipped)
    }

    private fun detectChannel(pattern: SmsPatternEntity): String {
        val name = pattern.name.lowercase()
        return when {
            name.contains("upi") -> "UPI"
            name.contains("card") -> "CARD"
            name.contains("wallet") -> "WALLET"
            name.contains("netbank") -> "NETBANKING"
            else -> "UNKNOWN"
        }
    }

    private fun detectCountryCode(): String {
        return "IN"
    }

    private fun calculateQualityScore(pattern: SmsPatternEntity): Double {
        return when {
            pattern.matchCount >= 50 -> 0.95
            pattern.matchCount >= 20 -> 0.90
            pattern.matchCount >= 10 -> 0.85
            pattern.matchCount >= 5 -> 0.80
            else -> 0.75
        }
    }

    private fun getDeviceId(): String {
        return try {
            android.provider.Settings.Secure.getString(
                context.contentResolver,
                android.provider.Settings.Secure.ANDROID_ID
            )
        } catch (e: Exception) {
            "unknown-device"
        }
    }

    private suspend fun extractUniqueSenders(): Set<String> {
        val allSenders = patternDao.getAllSenders()
        return allSenders.map { sender -> normalizeSenderName(sender) }.toSet()
    }

    private fun normalizeSenderName(sender: String): String {
        return sender.uppercase()
            .removePrefix("VM-")
            .removePrefix("+91-")
            .take(10)
    }

    private suspend fun fetchPatternsForSenders(senders: Set<String>): List<CloudPattern> {
        if (senders.isEmpty()) return emptyList()

        return try {
            val senderList = senders.toList()
            val snapshot = firestore.collection(CloudPattern.COLLECTION)
                .whereIn("senderName", senderList)
                .whereGreaterThanOrEqualTo("qualityScore", CloudPattern.MIN_QUALITY_SCORE)
                .get()
                .await()

            snapshot.documents.mapNotNull { doc ->
                try {
                    doc.toObject(CloudPattern::class.java)?.copy(id = doc.id)
                } catch (e: Exception) {
                    AppLog.e("Failed to parse cloud pattern", "PatternSync", e)
                    null
                }
            }
        } catch (e: Exception) {
            AppLog.e("Failed to fetch patterns from Firebase", "PatternSync", e)
            emptyList()
        }
    }

    private suspend fun mergePatterns(cloudPatterns: List<CloudPattern>): MergeResult {
        var merged = 0
        var skipped = 0

        cloudPatterns.forEach { cloudPattern ->
            val existingPattern = patternDao.findByBodyRegex(cloudPattern.bodyRegex)

            if (existingPattern == null) {
                patternDao.insert(
                    SmsPatternEntity(
                        name = cloudPattern.name,
                        senderRegex = cloudPattern.senderRegex,
                        bodyRegex = cloudPattern.bodyRegex,
                        priority = cloudPattern.priority,
                        source = PatternSource.BUILTIN,
                        enabled = true,
                        matchCount = 0
                    )
                )
                merged++
            } else {
                skipped++
            }
        }

        return MergeResult(merged, skipped)
    }

    private suspend fun countFirebaseParsedPatterns(): Int {
        return patternDao.countBySource(PatternSource.BUILTIN)
    }

    private data class MergeResult(
        val merged: Int,
        val skipped: Int
    )

    private data class UploadResult(
        val uploaded: Int,
        val skipped: Int
    )
}
