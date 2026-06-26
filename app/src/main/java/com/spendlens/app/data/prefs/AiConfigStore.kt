package com.spendlens.app.data.prefs

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.spendlens.app.BuildConfig
import com.spendlens.app.ai.AiConfig
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
)

/**
 * Persists the AI configuration. The enabled flag and model slug are non-sensitive and live in
 * plain [android.content.SharedPreferences]; the optional API-key override is sensitive and is
 * stored in [EncryptedSharedPreferences] (Keystore-backed), mirroring [com.spendlens.app.data.crypto.DatabaseKeyManager].
 *
 * Resolution rules (BuildConfig default, Settings override) live in the pure, unit-tested [AiConfig].
 */
class AiConfigStore(context: Context) {

    private val appContext = context.applicationContext

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
        enabled = prefs.getBoolean(KEY_ENABLED, true),
        model = AiConfig.effectiveModel(prefs.getString(KEY_MODEL, null)),
        hasOverrideKey = !overrideKey().isNullOrBlank(),
        buildKeyPresent = BuildConfig.OPENROUTER_API_KEY.isNotBlank(),
    )

    /** Synchronous read used off the main thread when deciding whether to use the AI path. */
    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    fun effectiveModel(): String = AiConfig.effectiveModel(prefs.getString(KEY_MODEL, null))

    /** The key to use for requests, or null if neither an override nor a build default is set. */
    fun effectiveKey(): String? = AiConfig.effectiveKey(overrideKey(), BuildConfig.OPENROUTER_API_KEY)

    /** True when AI is enabled and a usable key exists — the only state in which AI calls run. */
    fun isUsable(): Boolean = isEnabled() && effectiveKey() != null

    private fun overrideKey(): String? = securePrefs.getString(KEY_API_KEY, null)

    /** Epoch millis of the last auto-categorise AI call attempt — backs the per-minute throttle. */
    fun lastAutoCategorizeAt(): Long = prefs.getLong(KEY_LAST_AUTO_CATEGORIZE, 0L)

    fun setLastAutoCategorizeAt(at: Long) {
        prefs.edit().putLong(KEY_LAST_AUTO_CATEGORIZE, at).apply()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _prefs.value = _prefs.value.copy(enabled = enabled)
    }

    fun setModel(model: String) {
        val cleaned = model.trim()
        prefs.edit().putString(KEY_MODEL, cleaned).apply()
        _prefs.value = _prefs.value.copy(model = AiConfig.effectiveModel(cleaned))
    }

    /** Store (or clear, when [key] is blank) the user's own API key. */
    fun setApiKey(key: String?) {
        val cleaned = key?.trim().orEmpty()
        securePrefs.edit().apply {
            if (cleaned.isBlank()) remove(KEY_API_KEY) else putString(KEY_API_KEY, cleaned)
        }.apply()
        _prefs.value = _prefs.value.copy(hasOverrideKey = cleaned.isNotBlank())
    }

    private companion object {
        const val KEY_ENABLED = "ai_enabled"
        const val KEY_MODEL = "ai_model"
        const val KEY_API_KEY = "ai_api_key"
        const val KEY_LAST_AUTO_CATEGORIZE = "ai_last_auto_categorize_at"
    }
}
