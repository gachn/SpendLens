package online.velozen.spendvault.sync

data class SyncResult(
    val sendersScanned: Int = 0,
    val patternsDownloaded: Int = 0,
    val patternsMerged: Int = 0,
    val patternsSkipped: Int = 0,
    val firebasePatternsParsed: Int = 0,
    val patternsUploaded: Int = 0,
    val durationMs: Long = 0L,
    val error: String? = null
) {
    val isSuccess: Boolean
        get() = error == null
}
