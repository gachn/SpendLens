package com.spendlens.app.ai

import com.spendlens.app.data.db.PatternSource
import com.spendlens.app.data.db.RawSmsEntity
import com.spendlens.app.data.db.RawStatus
import com.spendlens.app.data.db.SmsPatternEntity
import com.spendlens.app.di.AppContainer
import com.spendlens.app.util.AppLog
import com.spendlens.app.work.PatternApplyWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Orchestrates the AI "Teach" flow that turns unparsed SMS into regex parsing patterns.
 *
 * **Responsibility split:**
 * - AI is responsible for: bodyRegex, senderRegex. Nothing else.
 * - The system (MerchantExtractor → MerchantResolver → pattern `party` group → Categoriser) is
 *   responsible for determining the merchant name, category, and emoji from the matched SMS body.
 *   The AI's `cleanMerchant`, `categoryName`, `logoEmoji` fields are intentionally ignored.
 *
 * **Execution phases:**
 * 1. **Fast (inline):** OpenRouter API call + regex normalisation + [saveAiPattern] DB write.
 *    Returns to the caller immediately so the UI can unblock.
 * 2. **Immediate reparse:** The specific SMS from the teaching session are re-parsed synchronously
 *    with the new pattern so the user sees the updated transaction card right away.
 * 3. **Background (WorkManager [PatternApplyWorker]):** [reprocessAllSms] over the full SMS
 *    backlog. Continues even after the user closes the screen.
 */
class AiPatternTeacher(private val container: AppContainer) {

    /** Outcome of the flag-gated AI "Teach" action. */
    sealed interface TeachResult {
        /** AI is off or no API key is configured — the caller must use the clipboard flow. */
        data object Fallback : TeachResult

        /**
         * AI ran and saved [patternCount] patterns. The specific teaching SMS are re-parsed
         * immediately; the full inbox reprocess runs in the background via [PatternApplyWorker].
         */
        data class Applied(val patternCount: Int) : TeachResult

        /** AI was attempted but failed (network/model error). */
        data class Error(val message: String) : TeachResult
    }

    /** True when the AI flag is on and a usable key exists (so the direct API path can run). */
    fun aiUsable(): Boolean = container.aiConfigStore.isUsable()

    /** Build the "Teach with AI" prompt for [smsList]. */
    fun generatePrompt(smsList: List<RawSmsEntity>): String = PromptGenerator.generate(smsList)

    /**
     * Flag-gated AI pattern generation.
     *
     * When AI is enabled with a key, sends the prompt to OpenRouter, saves each valid pattern
     * (regex only — merchant/category is determined by the system, not the AI), immediately
     * re-parses the teaching SMS, and enqueues [PatternApplyWorker] for the full inbox.
     *
     * Otherwise returns [TeachResult.Fallback].
     */
    suspend fun teach(smsList: List<RawSmsEntity>): TeachResult {
        val store = container.aiConfigStore
        if (!store.isUsable()) {
            AppLog.aiSkipped("pattern_teach", "ai_disabled_or_no_key_or_not_premium")
            return TeachResult.Fallback
        }
        val key = store.effectiveKey() ?: return TeachResult.Fallback
        AppLog.aiProcessing("pattern_teach", "generating_prompt", mapOf("sms_count" to smsList.size))
        val prompt = generatePrompt(smsList)
        return when (
            val r = container.openRouterClient.complete(
                key,
                store.effectiveModel(),
                prompt,
                operation = "pattern_teach",
            )
        ) {
            is OpenRouterClient.Result.Success -> {
                AppLog.aiProcessing("pattern_teach", "saving_patterns")
                smsList.forEach { container.rawSmsDao.updateAiDebug(it.id, prompt, r.content) }
                // Pass the specific teaching SMS so they are re-parsed immediately.
                val patternCount = applyAiPatterns(r.content, teachingSmsList = smsList)
                AppLog.aiApplied("pattern_teach", "patterns_saved=$patternCount reprocess_enqueued=true")
                TeachResult.Applied(patternCount)
            }
            is OpenRouterClient.Result.Failure -> {
                smsList.forEach { container.rawSmsDao.updateAiDebug(it.id, prompt, "ERROR: ${r.message}") }
                TeachResult.Error(r.message)
            }
        }
    }

    private fun extractJson(input: String): String? {
        val firstBracket = input.indexOf('[')
        val firstBrace = input.indexOf('{')

        if (firstBracket == -1 && firstBrace == -1) return null

        return if (firstBracket != -1 && (firstBrace == -1 || firstBracket < firstBrace)) {
            val lastBracket = input.lastIndexOf(']')
            if (lastBracket != -1 && lastBracket > firstBracket) {
                input.substring(firstBracket, lastBracket + 1)
            } else null
        } else {
            val lastBrace = input.lastIndexOf('}')
            if (lastBrace != -1 && lastBrace > firstBrace) {
                input.substring(firstBrace, lastBrace + 1)
            } else null
        }
    }

    private fun cleanSenderRegex(raw: String?): String? {
        if (raw == null || raw.isBlank() || raw == "null") return null
        val trimmed = raw.trim()
        val match = Regex("^[A-Za-z]{2}-(.+)$").find(trimmed)
        val extracted = if (match != null) {
            match.groupValues[1].replace(Regex("-[A-Za-z]$"), "")
        } else {
            trimmed.replace(Regex("-[A-Za-z]$"), "")
        }
        return "(?i)$extracted"
    }

    /**
     * Save a single AI-generated regex pattern to the database.
     *
     * Only [bodyRegex] and [senderRegex] are used. The AI's merchant name, category, and emoji
     * are intentionally ignored — the system determines those from the matched SMS content when
     * the pattern is applied via [reprocessSpecificSms] or [reprocessForPatterns].
     *
     * The body regex is normalised before saving: Python-style named groups are converted to Java
     * syntax, leading `^` anchors are stripped if they prevent matching, and exact digit
     * quantifiers are relaxed when the pattern doesn't match the sample SMS.
     *
     * Returns the new pattern's database ID if the pattern was saved successfully, or null if
     * the regex is invalid. Callers collect these IDs and pass them to [PatternApplyWorker] so
     * only SMS previously matched by the affected patterns need to be reprocessed.
     */
    suspend fun saveAiPattern(
        name: String,
        bodyRegex: String,
        senderRegex: String?,
        cachedRawSmsList: List<RawSmsEntity>? = null,
    ): Long? {
        val cleanedSender = cleanSenderRegex(senderRegex)
        val compiledSender = cleanedSender?.let { runCatching { Regex(it) }.getOrNull() }

        val sampleSenderBodies = cachedRawSmsList?.map { it.sender to it.body } ?: emptyList()
        val normalizedBodyRegex = normalizeBodyRegex(bodyRegex, compiledSender, sampleSenderBodies)

        if (normalizedBodyRegex != bodyRegex) {
            AppLog.aiProcessing("saveAiPattern", "regex_normalized")
        } else if (sampleSenderBodies.isNotEmpty()) {
            val compiled = runCatching { Regex(normalizedBodyRegex, RegexOption.IGNORE_CASE) }.getOrNull()
            val matched = compiled != null && sampleSenderBodies.any { (s, b) ->
                (compiledSender == null || compiledSender.containsMatchIn(s)) && compiled.containsMatchIn(b)
            }
            if (!matched) AppLog.aiProcessing("saveAiPattern", "regex_no_sample_match — will still be saved for future SMS")
        }

        // Reject patterns whose regex doesn't compile even after normalisation.
        runCatching { Regex(normalizedBodyRegex) }.getOrNull() ?: return null

        return container.patternRepository.savePattern(
            SmsPatternEntity(
                name = name,
                senderRegex = cleanedSender,
                bodyRegex = normalizedBodyRegex,
                priority = 60, // LEARNED_PRIORITY
                source = PatternSource.USER,
            ),
        )
    }

    /**
     * Parse the AI JSON response, save each valid pattern (regex only), immediately re-parse the
     * [teachingSmsList] SMS so the user sees updated transaction cards right away, then enqueue
     * [PatternApplyWorker] to reprocess the full inbox in the background.
     *
     * Only `name`, `senderRegex`, and `bodyRegex` are read from the AI response. Merchant name,
     * category, and emoji are determined entirely by the system pipeline (MerchantExtractor →
     * MerchantResolver → Categoriser) when the pattern is applied.
     *
     * @param teachingSmsList the specific SMS used in the teaching session; when provided, these
     *   are re-parsed synchronously before the background worker is enqueued, giving immediate
     *   feedback on the transaction card. Null in the clipboard flow (worker handles it all).
     * @return the number of patterns successfully saved.
     */
    suspend fun applyAiPatterns(
        jsonString: String,
        teachingSmsList: List<RawSmsEntity>? = null,
    ): Int = withContext(Dispatchers.IO) {
        val savedPatternIds = mutableListOf<Long>()
        try {
            val extracted = extractJson(jsonString) ?: run {
                AppLog.aiFailure("pattern_teach", "n/a", null, "No JSON found in model response", 0)
                return@withContext 0
            }
            // Load full SMS list for regex normalisation validation.
            val allSms = container.rawSmsDao.listByStatus(RawStatus.UNPARSED) +
                container.rawSmsDao.listByStatus(RawStatus.PARSED)

            val objects = when {
                extracted.startsWith("[") -> {
                    val array = org.json.JSONArray(extracted)
                    (0 until array.length()).map { array.getJSONObject(it) }
                }
                extracted.startsWith("{") -> listOf(org.json.JSONObject(extracted))
                else -> emptyList()
            }

            for (obj in objects) {
                val bodyRegex = obj.optString("bodyRegex")
                if (bodyRegex.isNullOrBlank()) continue
                val senderRegex = obj.optString("senderRegex").takeIf { it != "null" && it.isNotBlank() }
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: "AI Pattern"

                val patternId = saveAiPattern(
                    name = name,
                    bodyRegex = bodyRegex,
                    senderRegex = senderRegex,
                    cachedRawSmsList = allSms,
                )
                if (patternId != null) savedPatternIds += patternId
            }

            if (savedPatternIds.isNotEmpty()) {
                // Immediately re-parse the specific teaching SMS so the transaction card updates
                // right away — this is a small list and fast enough to run inline.
                val smsToReparseNow = teachingSmsList ?: emptyList()
                if (smsToReparseNow.isNotEmpty()) {
                    container.smsProcessor.reprocessSpecificSms(smsToReparseNow)
                }
                // Enqueue background worker. Pass the saved pattern IDs so only SMS previously
                // matched by these patterns (plus UNPARSED) are reprocessed — not the full inbox.
                PatternApplyWorker.enqueue(container.appContext, savedPatternIds)
                AppLog.aiProcessing("pattern_teach", "reprocess_enqueued patterns=${savedPatternIds.size} ids=$savedPatternIds")
            }
        } catch (e: Exception) {
            AppLog.e("applyAiPatterns failed", AppLog.TAG_AI, e)
        }
        return@withContext savedPatternIds.size
    }

    companion object {
        /**
         * Attempt to fix common AI regex mistakes so the pattern actually matches the sample SMS.
         *
         * Steps (applied in order, stops as soon as a match is found):
         * 1. Convert Python-style named groups `(?P<name>...)` → Java/Kotlin `(?<name>...)`.
         *    AI models often generate Python regex syntax; Java throws [java.util.regex.PatternSyntaxException]
         *    for `(?P<`, silently rejecting the pattern with no user-visible error.
         * 2. Strip a leading `^` anchor. AI models often add it when the SMS body has a bank-name
         *    prefix that the regex doesn't account for (e.g. "ICICI Bank Acct XX1234 debited…").
         * 3. Relax exact digit quantifiers `\d{N}` → `\d{N,}` to handle banks that use more
         *    digits than the AI assumed (e.g. `\d{3}` fails on ICICI's 4-digit account suffix).
         *
         * @param bodyRegex raw regex string from AI output
         * @param senderRe compiled sender filter (null = validate against all senders)
         * @param senderBodies list of (sender, body) pairs from the SMS inbox to test against
         * @return normalised regex string that compiles in Java and ideally matches a sample
         */
        internal fun normalizeBodyRegex(
            bodyRegex: String,
            senderRe: Regex?,
            senderBodies: List<Pair<String, String>>,
        ): String {
            fun matches(re: String): Boolean {
                val compiled = runCatching { Regex(re, RegexOption.IGNORE_CASE) }.getOrNull()
                    ?: return false
                return senderBodies.any { (sender, body) ->
                    (senderRe == null || senderRe.containsMatchIn(sender)) &&
                        compiled.containsMatchIn(body)
                }
            }

            // Step 1: Python → Java named group syntax.
            val javaCompat = bodyRegex.replace(Regex("""\(\?P<"""), "(?<")
            if (matches(javaCompat)) return javaCompat

            // Step 2: strip leading ^ anchor.
            val stripped = if (javaCompat.startsWith("^")) javaCompat.removePrefix("^") else javaCompat
            if (stripped != javaCompat && matches(stripped)) return stripped

            // Step 3: relax \d{N} → \d{N,}.
            val relaxed = stripped.replace(Regex("""\\d\{(\d+)\}"""), """\\d{$1,}""")
            if (relaxed != stripped && matches(relaxed)) return relaxed

            // Return the Java-compatible version even if no sample matched — it will at least
            // compile correctly and may match future SMS of the same format.
            return javaCompat
        }
    }
}
