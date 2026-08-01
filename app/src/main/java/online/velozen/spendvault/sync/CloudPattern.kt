package com.spendlens.app.sync

data class CloudPattern(
    val id: String = "",
    val name: String = "",
    val senderName: String = "",
    val senderRegex: String? = null,
    val bodyRegex: String = "",
    val priority: Int = 50,
    val source: String = "",
    val countryCode: String = "",
    val channel: String = "",
    val qualityScore: Double = 0.0,
    val globalMatchCount: Int = 0,
    val avgSuccessRate: Double = 0.0,
    val createdAt: Long = 0L,
    val lastUpdated: Long = 0L,
    val deviceId: String = ""
) {
    companion object {
        const val MIN_QUALITY_SCORE = 0.8
        const val COLLECTION = "public_patterns"
    }
}
