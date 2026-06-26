package com.spendlens.app.ai

import com.spendlens.app.data.db.PatternSource
import com.spendlens.app.data.db.RawSmsEntity
import com.spendlens.app.data.db.RawStatus
import com.spendlens.app.data.db.SmsPatternEntity
import com.spendlens.app.di.AppContainer
import com.spendlens.app.util.AppLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Orchestrates the AI "Teach" flow that turns an unparsed SMS into a parsing pattern plus a
 * merchant/category/emoji mapping. Shared by the Review screen and the transaction detail sheet so
 * both honor the AI flag identically.
 *
 * When the flag is on and a key is configured, [teach] sends the prompt directly to OpenRouter and
 * applies the JSON reply; otherwise it returns [TeachResult.Fallback] so callers keep the existing
 * copy-prompt-to-clipboard flow (parsed later by the clipboard watcher in SpendLensRoot via
 * [applyAiPatterns]).
 */
class AiPatternTeacher(private val container: AppContainer) {

    /** Outcome of the flag-gated AI "Teach" action. */
    sealed interface TeachResult {
        /** AI is off or no API key is configured — the caller must use the clipboard flow. */
        data object Fallback : TeachResult
        /** AI ran and applied patterns; [updated] existing transactions were re-stamped. */
        data class Applied(val updated: Int) : TeachResult
        /** AI was attempted but failed (network/model error). */
        data class Error(val message: String) : TeachResult
    }

    /** True when the AI flag is on and a usable key exists (so the direct API path can run). */
    fun aiUsable(): Boolean = container.aiConfigStore.isUsable()

    /** Build the "Teach with AI" prompt for [smsList], seeded with known categories/merchants. */
    suspend fun generatePrompt(smsList: List<RawSmsEntity>): String {
        val categories = container.categoryRepository.all()
        val categorizer = container.categoryRepository.categorizer()
        val merchants = container.merchantRepository.observeAll().first()
        val knownMerchants = merchants.take(150).map { m ->
            val catId = categorizer.categorize(m.displayName)
            val catName = catId?.let { id -> categories.firstOrNull { it.id == id }?.name }
            PromptGenerator.KnownMerchant(
                name = m.displayName,
                emoji = m.logoEmoji,
                categoryName = catName,
            )
        }
        return PromptGenerator.generate(smsList, categories, knownMerchants)
    }

    /**
     * Flag-gated AI pattern generation. When AI is enabled with a key, send the prompt directly to
     * OpenRouter and apply the JSON reply; otherwise return [TeachResult.Fallback].
     */
    suspend fun teach(smsList: List<RawSmsEntity>): TeachResult {
        val store = container.aiConfigStore
        val key = store.effectiveKey()
        if (!store.isEnabled() || key == null) {
            AppLog.aiSkipped("pattern_teach", "ai_disabled_or_no_key")
            return TeachResult.Fallback
        }
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
                AppLog.aiProcessing("pattern_teach", "applying_patterns")
                val updated = applyAiPatterns(r.content)
                AppLog.aiApplied("pattern_teach", "patterns_saved transactions_updated=$updated")
                TeachResult.Applied(updated)
            }
            is OpenRouterClient.Result.Failure -> TeachResult.Error(r.message)
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

    suspend fun saveAiPattern(
        name: String,
        bodyRegex: String,
        senderRegex: String?,
        cleanMerchant: String,
        logoEmoji: String?,
        categoryName: String,
        cachedRawSmsList: List<RawSmsEntity>? = null,
    ): Boolean {
        // Validate regex compiles
        val cleanedSender = cleanSenderRegex(senderRegex)
        val compiledBody = runCatching { Regex(bodyRegex) }.getOrNull() ?: return false
        val compiledSender = cleanedSender?.let { runCatching { Regex(it) }.getOrNull() }

        // Find the category ID from the category name
        val categories = container.categoryRepository.all()
        val categoryId = categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }?.id
            ?: categories.firstOrNull()?.id // fallback to first category if name mismatch

        // Save SMS pattern
        container.patternRepository.savePattern(
            SmsPatternEntity(
                name = name,
                senderRegex = cleanedSender,
                bodyRegex = bodyRegex,
                priority = 60, // LEARNED_PRIORITY
                source = PatternSource.USER,
            ),
        )

        // Map raw captured party tokens in matched SMS to clean merchant
        val rawSmsList = cachedRawSmsList
            ?: (container.rawSmsDao.listByStatus(RawStatus.UNPARSED) + container.rawSmsDao.listByStatus(RawStatus.PARSED))
        val matchedParties = mutableSetOf<String>()
        for (raw in rawSmsList) {
            if (compiledSender != null && !compiledSender.containsMatchIn(raw.sender)) {
                continue
            }
            val match = compiledBody.find(raw.body)
            if (match != null) {
                val party = runCatching { match.groups["party"]?.value }.getOrNull()?.trim()
                if (!party.isNullOrBlank()) {
                    matchedParties.add(party)
                }
            }
        }
        for (party in matchedParties) {
            container.merchantRepository.setUserName(party, cleanMerchant)
        }

        // Save merchant alias and emoji
        container.merchantRepository.setUserName(cleanMerchant, cleanMerchant)
        if (logoEmoji != null) {
            container.merchantRepository.setMerchantEmoji(cleanMerchant, logoEmoji)
        }

        // Add category rule
        if (categoryId != null) {
            container.categoryRepository.addUserRule(cleanMerchant, categoryId)
        }

        return true
    }

    /** A merchant/category mapping taught by the AI flow, used to update existing transactions. */
    private data class TaughtMapping(
        val compiledBody: Regex,
        val compiledSender: Regex?,
        val cleanMerchant: String,
        val categoryId: Long?,
    )

    suspend fun applyAiPatterns(jsonString: String): Int = withContext(Dispatchers.IO) {
        var count = 0
        var updatedCount = 0
        val mappings = mutableListOf<TaughtMapping>()
        try {
            val extracted = extractJson(jsonString) ?: run {
                AppLog.aiFailure("pattern_teach", "n/a", null, "No JSON found in model response", 0)
                return@withContext 0
            }
            val rawSmsList = container.rawSmsDao.listByStatus(RawStatus.UNPARSED) + container.rawSmsDao.listByStatus(RawStatus.PARSED)
            val objects = when {
                extracted.startsWith("[") -> {
                    val array = org.json.JSONArray(extracted)
                    (0 until array.length()).map { array.getJSONObject(it) }
                }
                extracted.startsWith("{") -> listOf(org.json.JSONObject(extracted))
                else -> emptyList()
            }
            val categories = container.categoryRepository.all()
            for (obj in objects) {
                val bodyRegex = obj.optString("bodyRegex")
                if (bodyRegex.isNullOrBlank()) continue
                val cleanMerchant = obj.optString("cleanMerchant", obj.optString("merchant", "Unknown"))
                val name = obj.optString("name").takeIf { it.isNotBlank() } ?: "Pattern for $cleanMerchant"
                val categoryName = obj.optString("categoryName", obj.optString("category", "Uncategorized"))
                val logoEmoji = obj.optString("logoEmoji").takeIf { it != "null" && it.isNotBlank() }
                val senderRegex = obj.optString("senderRegex").takeIf { it != "null" && it.isNotBlank() }

                val success = saveAiPattern(
                    name = name,
                    bodyRegex = bodyRegex,
                    senderRegex = senderRegex,
                    cleanMerchant = cleanMerchant,
                    logoEmoji = logoEmoji,
                    categoryName = categoryName,
                    cachedRawSmsList = rawSmsList,
                )
                if (!success) continue
                count++

                val compiledBody = runCatching { Regex(bodyRegex) }.getOrNull() ?: continue
                val compiledSender = cleanSenderRegex(senderRegex)?.let { runCatching { Regex(it) }.getOrNull() }
                val categoryId = categories.firstOrNull { it.name.equals(categoryName, ignoreCase = true) }?.id
                mappings.add(TaughtMapping(compiledBody, compiledSender, cleanMerchant, categoryId))
            }
            if (count > 0) {
                // First let the SMS pipeline (re)parse so newly-matched UNPARSED messages become
                // transactions. Then directly stamp the taught merchant/category onto every
                // transaction whose raw SMS matches — this overrides the generic merchant/category
                // enrichment so the user's correction actually sticks (and applies to all matches).
                container.smsProcessor.reprocessAllSms()
                updatedCount = applyTaughtMappings(mappings)
            }
        } catch (e: Exception) {
            AppLog.e(AppLog.TAG_AI, "applyAiPatterns failed", e)
        }
        return@withContext updatedCount
    }

    /**
     * Stamp each taught merchant/category directly onto existing transactions whose raw SMS
     * matches the pattern, marking them user-verified. Returns the number of rows updated.
     */
    private suspend fun applyTaughtMappings(mappings: List<TaughtMapping>): Int {
        if (mappings.isEmpty()) return 0
        val rawById = (container.rawSmsDao.listByStatus(RawStatus.PARSED) +
            container.rawSmsDao.listByStatus(RawStatus.UNPARSED)).associateBy { it.id }
        var updated = 0
        for (txn in container.transactionRepository.getAllTransactions()) {
            val rawId = txn.rawSmsId ?: continue
            val raw = rawById[rawId] ?: continue
            val mapping = mappings.firstOrNull { m ->
                (m.compiledSender == null || m.compiledSender.containsMatchIn(raw.sender)) &&
                    m.compiledBody.containsMatchIn(raw.body)
            } ?: continue
            val tags = container.merchantRepository.tagsFor(mapping.cleanMerchant)
            container.transactionRepository.update(
                txn.copy(
                    counterparty = mapping.cleanMerchant,
                    categoryId = mapping.categoryId ?: txn.categoryId,
                    tags = tags ?: txn.tags,
                    userVerified = true,
                ),
            )
            updated++
        }
        return updated
    }
}
