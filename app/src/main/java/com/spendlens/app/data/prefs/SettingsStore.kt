package com.spendlens.app.data.prefs

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** How the app decides between light and dark colours. */
enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

/** User-facing appearance choices, surfaced in Settings → Appearance. */
data class AppearancePrefs(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Use Android 12+ wallpaper-derived "Material You" colours instead of the brand palette. */
    val dynamicColor: Boolean = false,
    /** Show the small "AI is analysing…" banner while auto-categorisation runs. On by default. */
    val aiBannerEnabled: Boolean = true,
    /**
     * Show the per-transaction AI debug section (whether it was AI-analysed and how it was
     * categorised). Off by default — a developer aid that stays hidden in real use.
     */
    val debugInfoEnabled: Boolean = false,
)

/** App-lock choices, surfaced in Settings → Security. */
data class SecurityPrefs(
    /** Require biometric / device-credential auth to open the app. */
    val appLockEnabled: Boolean = false,
    /** Seconds the app may sit in the background before it re-locks (avoids re-prompt on rotation). */
    val gracePeriodSec: Int = 30,
)

/** SMS-parsing choices, surfaced in Settings → SMS Filtering. */
data class SmsFilterPrefs(
    /**
     * When true (default), only SMS from recognised financial-institution senders are processed.
     * Unknown senders are IGNORED pending AI classification by [SenderClassifyWorker]; once
     * confirmed financial they are recovered. Set false only to permit all senders (debug mode).
     */
    val financialSendersOnly: Boolean = true,
    /**
     * When true, unknown merchant tokens are looked up against an online company directory
     * (Clearbit) to predict a canonical brand name. Off by default: the autocomplete guesses
     * brands with no reference in the SMS (e.g. "gaurav" → "Gaurav Photography").
     */
    val merchantPredictionEnabled: Boolean = false,
)

/**
 * Lightweight, synchronous preference store for non-sensitive UI settings.
 * Backed by plain [android.content.SharedPreferences] (no encryption needed —
 * these are cosmetic) and exposed as a [StateFlow] for Compose to observe.
 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("spendlens_settings", Context.MODE_PRIVATE)

    private val _appearance = MutableStateFlow(load())
    val appearance: StateFlow<AppearancePrefs> = _appearance.asStateFlow()

    private val _security = MutableStateFlow(loadSecurity())
    val security: StateFlow<SecurityPrefs> = _security.asStateFlow()

    private val _smsFilter = MutableStateFlow(loadSmsFilter())
    val smsFilter: StateFlow<SmsFilterPrefs> = _smsFilter.asStateFlow()

    private fun load(): AppearancePrefs {
        val mode = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        return AppearancePrefs(
            themeMode = mode,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
            aiBannerEnabled = prefs.getBoolean(KEY_AI_BANNER, true),
            debugInfoEnabled = prefs.getBoolean(KEY_DEBUG_INFO, false),
        )
    }

    private fun loadSecurity(): SecurityPrefs = SecurityPrefs(
        appLockEnabled = prefs.getBoolean(KEY_APP_LOCK, false),
        gracePeriodSec = prefs.getInt(KEY_GRACE_SEC, 30),
    )

    private fun loadSmsFilter(): SmsFilterPrefs = SmsFilterPrefs(
        financialSendersOnly = prefs.getBoolean(KEY_FINANCIAL_SENDERS_ONLY, true),
        merchantPredictionEnabled = prefs.getBoolean(KEY_MERCHANT_PREDICTION, false),
    )

    /** Synchronous read for app-launch gating (before any flow is collected). */
    fun isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK, false)
    fun gracePeriodSec(): Int = prefs.getInt(KEY_GRACE_SEC, 30)

    /** Synchronous read used by [SmsProcessor] on the IO thread during SMS processing. */
    fun financialSendersOnly(): Boolean = prefs.getBoolean(KEY_FINANCIAL_SENDERS_ONLY, true)

    /** Synchronous read used by the gated merchant resolver on the IO thread. */
    fun merchantPredictionEnabled(): Boolean = prefs.getBoolean(KEY_MERCHANT_PREDICTION, false)

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _appearance.value = _appearance.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _appearance.value = _appearance.value.copy(dynamicColor = enabled)
    }

    fun setAiBannerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AI_BANNER, enabled).apply()
        _appearance.value = _appearance.value.copy(aiBannerEnabled = enabled)
    }

    fun setDebugInfoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DEBUG_INFO, enabled).apply()
        _appearance.value = _appearance.value.copy(debugInfoEnabled = enabled)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK, enabled).apply()
        _security.value = _security.value.copy(appLockEnabled = enabled)
    }

    fun setGracePeriodSec(seconds: Int) {
        prefs.edit().putInt(KEY_GRACE_SEC, seconds).apply()
        _security.value = _security.value.copy(gracePeriodSec = seconds)
    }

    fun setFinancialSendersOnly(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FINANCIAL_SENDERS_ONLY, enabled).apply()
        _smsFilter.value = _smsFilter.value.copy(financialSendersOnly = enabled)
    }

    fun setMerchantPredictionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MERCHANT_PREDICTION, enabled).apply()
        _smsFilter.value = _smsFilter.value.copy(merchantPredictionEnabled = enabled)
    }

    // Account display-name overrides — user can rename "••••9496" to "HDFC Credit" etc.
    private fun loadAccountNames(): Map<String, String> =
        prefs.all.entries
            .filter { it.key.startsWith(KEY_ACCT_NAME_PREFIX) }
            .mapNotNull { e -> (e.value as? String)?.let { e.key.removePrefix(KEY_ACCT_NAME_PREFIX) to it } }
            .toMap()

    private val _accountNames = MutableStateFlow(loadAccountNames())
    val accountNames: StateFlow<Map<String, String>> = _accountNames.asStateFlow()

    fun setAccountName(accountKey: String, name: String?) {
        val prefKey = KEY_ACCT_NAME_PREFIX + accountKey
        if (name.isNullOrBlank()) {
            prefs.edit().remove(prefKey).apply()
            _accountNames.value = _accountNames.value - accountKey
        } else {
            prefs.edit().putString(prefKey, name.trim()).apply()
            _accountNames.value = _accountNames.value + (accountKey to name.trim())
        }
    }

    // Statement cycle-day overrides — persisted so the picker is never shown twice for the same card.
    private fun loadCycleDays(): Map<String, Int> =
        prefs.all.entries
            .filter { it.key.startsWith(KEY_CYCLE_DAY_PREFIX) }
            .mapNotNull { e -> (e.value as? Int)?.let { e.key.removePrefix(KEY_CYCLE_DAY_PREFIX) to it } }
            .toMap()

    private val _cycleDays = MutableStateFlow(loadCycleDays())
    val cycleDays: StateFlow<Map<String, Int>> = _cycleDays.asStateFlow()

    fun setStatementCycleDay(accountKey: String, day: Int) {
        prefs.edit().putInt(KEY_CYCLE_DAY_PREFIX + accountKey, day).apply()
        _cycleDays.value = _cycleDays.value + (accountKey to day)
    }

    // Backup tracking (issue #13) — drives the "last backup" label and the 30-day reminder.
    private val _lastBackupAt = MutableStateFlow(prefs.getLong(KEY_LAST_BACKUP, 0L).takeIf { it > 0L })
    val lastBackupAt: StateFlow<Long?> = _lastBackupAt.asStateFlow()

    /** Epoch millis of the last successful export, or 0 if never. */
    fun lastBackupAtMillis(): Long = prefs.getLong(KEY_LAST_BACKUP, 0L)

    fun setLastBackupAt(epochMillis: Long) {
        prefs.edit().putLong(KEY_LAST_BACKUP, epochMillis).apply()
        _lastBackupAt.value = epochMillis
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_AI_BANNER = "ai_banner_enabled"
        const val KEY_DEBUG_INFO = "debug_info_enabled"
        const val KEY_APP_LOCK = "app_lock_enabled"
        const val KEY_GRACE_SEC = "app_lock_grace_sec"
        const val KEY_LAST_BACKUP = "last_backup_at"
        const val KEY_FINANCIAL_SENDERS_ONLY = "financial_senders_only"
        const val KEY_MERCHANT_PREDICTION = "merchant_prediction_enabled"
        const val KEY_ACCT_NAME_PREFIX = "acct_name_"
        const val KEY_CYCLE_DAY_PREFIX = "cycle_day_"
    }
}
