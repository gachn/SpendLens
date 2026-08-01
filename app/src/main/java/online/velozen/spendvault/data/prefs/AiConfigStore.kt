package online.velozen.spendvault.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import online.velozen.spendvault.BuildConfig
import online.velozen.spendvault.ai.AiConfig
import online.velozen.spendvault.config.RemoteConfigManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User-facing AI choices, surfaced in Settings → AI:
 * - [enabled]: master flag. When on (default) the AI-backed pattern/merchant flows run; when off
 *   the app falls back to its existing on-device / clipboard flows.
 * - [model]: OpenRouter model slug (any model is reachable by changing this string).
 * - [hasOverrideKey]: whether the user entered their own key (stored encrypted).
 * - [buildKeyPresent]: whether a build-baked default key exists (from local.properties).
 */
data class AiPrefs(
    val enabled: Boolean = true,
    val model: String = AiConfig.DEFAULT_MODEL,
    val hasOverrideKey: Boolean = false,
    val buildKeyPresent: Boolean = false,
    val maxTokensPerRequest: Int = AiConfig.DEFAULT_MAX_TOKENS_PER_REQUEST,
    val concurrentRequests: Int = AiConfig.DEFAULT_CONCURRENT_REQUESTS,
    val maxItemsPerBatch: Int = AiConfig.DEFAULT_MAX_ITEMS_PER_BATCH,
)

/**
 * Persists the AI configuration. The enabled flag and model slug are non-sensitive and live in
 * plain [android.content.SharedPreferences]; the optional API-key override is sensitive and is
 * stored in [EncryptedSharedPreferences] (Keystore-backed), mirroring [online.velozen.spendvault.data.crypto.DatabaseKeyManager].
 *
 * Resolution rules (BuildConfig default, Settings override) live in the pure, unit-tested [AiConfig].
 */
class AiConfigStore(context: Context, private val planStore: PlanStore) {

    private val appContext = context.applicationContext
    private val remoteConfig = RemoteConfigManager.getInstance()

    private val prefs = appContext.getSharedPreferences("spendlens_ai", Context.MODE_PRIVATE)

    private val securePrefs by lazy {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            "spendlens_ai_secure",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private val _prefs = MutableStateFlow(load())
    val prefsFlow: StateFlow<AiPrefs> = _prefs.asStateFlow()

    private fun load(): AiPrefs = AiPrefs(
        enabled = remoteConfig.isAiEnabled(),
        model = remoteConfig.getAiModel(),
        hasOverrideKey = !overrideKey().isNullOrBlank(),
        buildKeyPresent = BuildConfig.OPENROUTER_API_KEY.isNotBlank(),
        maxTokensPerRequest = remoteConfig.getAiMaxTokensPerRequest(),
        concurrentRequests = remoteConfig.getAiConcurrentRequests(),
        maxItemsPerBatch = remoteConfig.getAiMaxItemsPerBatch(),
    )

    /** Synchronous read used off the main thread when deciding whether to use the AI path. */
    fun isEnabled(): Boolean = remoteConfig.isAiEnabled()

    fun effectiveModel(): String = remoteConfig.getAiModel()

    /**
     * Token budget for one batched AI request — drives how many SMS AiSmsBatchWorker packs per
     * call. Retrieved from Firebase Remote Config.
     */
    fun maxTokensPerRequest(): Int = remoteConfig.getAiMaxTokensPerRequest()

    /**
     * How many [online.velozen.spendvault.work.AiSmsBatchWorker] batch calls may be in flight at once.
     * Retrieved from Firebase Remote Config.
     */
    fun concurrentRequests(): Int = remoteConfig.getAiConcurrentRequests()

    /**
     * Maximum number of SMS messages per AI batch request — balances prompt size against
     * output generation time. Retrieved from Firebase Remote Config.
     */
    fun maxItemsPerBatch(): Int = remoteConfig.getAiMaxItemsPerBatch()

    /** The key to use for requests, or null if neither an override nor a build default is set. */
    fun effectiveKey(): String? = AiConfig.effectiveKey(overrideKey(), BuildConfig.OPENROUTER_API_KEY)

    /**
     * True only on the Premium plan, with AI enabled and a usable key — the single gate every AI
     * call site checks before reaching the network. On the Free plan this is always false, so
     * every AI-backed flow (pattern learning, categorisation, sender/promo classification) falls
     * back to its on-device heuristic — no AI call is ever made.
     */
    fun isUsable(): Boolean = planStore.isPremium() && isEnabled() && effectiveKey() != null

    private fun overrideKey(): String? = securePrefs.getString(KEY_API_KEY, null)

    /** Epoch millis of the last auto-categorise AI call attempt — backs the per-minute throttle. */
    fun lastAutoCategorizeAt(): Long = prefs.getLong(KEY_LAST_AUTO_CATEGORIZE, 0L)

    fun setLastAutoCategorizeAt(at: Long) {
        prefs.edit().putLong(KEY_LAST_AUTO_CATEGORIZE, at).apply()
    }

    fun setEnabled(enabled: Boolean) {
        // No-op - now managed by Remote Config
        _prefs.value = _prefs.value.copy(enabled = remoteConfig.isAiEnabled())
    }

    fun setModel(model: String) {
        // No-op - now managed by Remote Config
        _prefs.value = _prefs.value.copy(model = remoteConfig.getAiModel())
    }

    /** Store (or clear, when [key] is blank) the user's own API key. */
    fun setApiKey(key: String?) {
        val cleaned = key?.trim().orEmpty()
        securePrefs.edit().apply {
            if (cleaned.isBlank()) remove(KEY_API_KEY) else putString(KEY_API_KEY, cleaned)
        }.apply()
        _prefs.value = _prefs.value.copy(hasOverrideKey = cleaned.isNotBlank())
    }

    fun setMaxTokens(tokens: Int) {
        // No-op - now managed by Remote Config
        _prefs.value = _prefs.value.copy(maxTokensPerRequest = remoteConfig.getAiMaxTokensPerRequest())
    }

    fun setConcurrentRequests(count: Int) {
        // No-op - now managed by Remote Config
        _prefs.value = _prefs.value.copy(concurrentRequests = remoteConfig.getAiConcurrentRequests())
    }

    fun setMaxItemsPerBatch(count: Int) {
        // No-op - now managed by Remote Config
        _prefs.value = _prefs.value.copy(maxItemsPerBatch = remoteConfig.getAiMaxItemsPerBatch())
    }

    private companion object {
        const val KEY_ENABLED = "ai_enabled"
        const val KEY_MODEL = "ai_model"
        const val KEY_API_KEY = "ai_api_key"
        const val KEY_LAST_AUTO_CATEGORIZE = "ai_last_auto_categorize_at"
        const val KEY_MAX_TOKENS = "ai_max_tokens_per_request"
        const val MIN_TOKENS = 200 // floor: below this a single SMS often won't fit
        // Ceiling: most free-tier OpenRouter models cap context well under this; a bigger single
        // request just risks an outright context-length failure for the whole batch.
        const val MAX_TOKENS = 16_000
        const val KEY_CONCURRENT_REQUESTS = "ai_concurrent_requests"
        const val MIN_CONCURRENT_REQUESTS = 1
        // Ceiling: past this, a free-tier model's per-key rate limit is nearly guaranteed to start
        // rejecting requests rather than speed anything up further.
        const val MAX_CONCURRENT_REQUESTS = 8
        const val KEY_MAX_ITEMS_PER_BATCH = "ai_max_items_per_batch"
        const val MIN_ITEMS_PER_BATCH = 5
        // Ceiling: each SMS in the batch generates one JSON object in the response; past this the
        // output generation time dominates and requests routinely time out at 90s.
        const val MAX_ITEMS_PER_BATCH = 50
    }
}
