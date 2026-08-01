package com.spendlens.app.sync

import com.google.firebase.firestore.FirebaseFirestore
import com.spendlens.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

object FirebaseTestDataSeeder {

    private val firestore = FirebaseFirestore.getInstance()

    suspend fun seedSamplePatterns(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val samplePatterns = listOf(
                CloudPattern(
                    id = "",
                    name = "HDFC Bank UPI Debit",
                    senderName = "HDFCBK",
                    senderRegex = "HDFCBK",
                    bodyRegex = "debited.*Rs\\.?(\\d+).*UPI",
                    priority = 60,
                    source = "BUILTIN",
                    countryCode = "IN",
                    channel = "UPI",
                    qualityScore = 0.85,
                    globalMatchCount = 100,
                    avgSuccessRate = 0.9,
                    createdAt = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis(),
                    deviceId = "initial-setup"
                ),
                CloudPattern(
                    id = "",
                    name = "ICICI Bank Card Transaction",
                    senderName = "ICICIB",
                    senderRegex = "ICICIB",
                    bodyRegex = "spent.*Rs\\.?(\\d+).*card",
                    priority = 60,
                    source = "BUILTIN",
                    countryCode = "IN",
                    channel = "CARD",
                    qualityScore = 0.88,
                    globalMatchCount = 150,
                    avgSuccessRate = 0.92,
                    createdAt = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis(),
                    deviceId = "initial-setup"
                ),
                CloudPattern(
                    id = "",
                    name = "Google Pay Credit",
                    senderName = "GPAY",
                    senderRegex = "GPAY",
                    bodyRegex = "received.*Rs\\.?(\\d+).*Google Pay",
                    priority = 60,
                    source = "BUILTIN",
                    countryCode = "IN",
                    channel = "UPI",
                    qualityScore = 0.90,
                    globalMatchCount = 200,
                    avgSuccessRate = 0.95,
                    createdAt = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis(),
                    deviceId = "initial-setup"
                ),
                CloudPattern(
                    id = "",
                    name = "PhonePe UPI Debit",
                    senderName = "PAYTM",
                    senderRegex = "PAYTM",
                    bodyRegex = "paid.*Rs\\.?(\\d+).*PhonePe",
                    priority = 60,
                    source = "BUILTIN",
                    countryCode = "IN",
                    channel = "UPI",
                    qualityScore = 0.82,
                    globalMatchCount = 80,
                    avgSuccessRate = 0.88,
                    createdAt = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis(),
                    deviceId = "initial-setup"
                ),
                CloudPattern(
                    id = "",
                    name = "SBI Account Debit",
                    senderName = "SBIALR",
                    senderRegex = "SBIALR",
                    bodyRegex = "debited.*Rs\\.?(\\d+).*SBI",
                    priority = 60,
                    source = "BUILTIN",
                    countryCode = "IN",
                    channel = "NETBANKING",
                    qualityScore = 0.87,
                    globalMatchCount = 120,
                    avgSuccessRate = 0.91,
                    createdAt = System.currentTimeMillis(),
                    lastUpdated = System.currentTimeMillis(),
                    deviceId = "initial-setup"
                )
            )

            var successCount = 0
            for (pattern in samplePatterns) {
                try {
                    val docRef = firestore.collection(CloudPattern.COLLECTION).add(pattern).await()
                    AppLog.d("FirebaseSeeder", "Seeded pattern: ${pattern.name} with ID: ${docRef.id}")
                    successCount++
                } catch (e: Exception) {
                    AppLog.e("Failed to seed pattern: ${pattern.name}", "FirebaseSeeder", e)
                }
            }

            AppLog.d("FirebaseSeeder", "Seeding complete: $successCount/${samplePatterns.size} patterns")
            Result.success(successCount)
        } catch (e: Exception) {
            AppLog.e("Firebase seeding failed", "FirebaseSeeder", e)
            Result.failure(e)
        }
    }

    suspend fun clearAllPatterns(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val snapshot = firestore.collection(CloudPattern.COLLECTION).get().await()
            var deletedCount = 0
            
            for (document in snapshot.documents) {
                try {
                    firestore.collection(CloudPattern.COLLECTION).document(document.id).delete().await()
                    deletedCount++
                } catch (e: Exception) {
                    AppLog.e("Failed to delete document: ${document.id}", "FirebaseSeeder", e)
                }
            }
            
            AppLog.d("FirebaseSeeder", "Cleared $deletedCount patterns")
            Result.success(deletedCount)
        } catch (e: Exception) {
            AppLog.e("Failed to clear patterns", "FirebaseSeeder", e)
            Result.failure(e)
        }
    }
}
