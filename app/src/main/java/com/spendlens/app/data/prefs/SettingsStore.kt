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
)

/** App-lock choices, surfaced in Settings → Security. */
data class SecurityPrefs(
    /** Require biometric / device-credential auth to open the app. */
    val appLockEnabled: Boolean = false,
    /** Seconds the app may sit in the background before it re-locks (avoids re-prompt on rotation). */
    val gracePeriodSec: Int = 30,
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

    private fun load(): AppearancePrefs {
        val mode = prefs.getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
            ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
            ?: ThemeMode.SYSTEM
        return AppearancePrefs(
            themeMode = mode,
            dynamicColor = prefs.getBoolean(KEY_DYNAMIC_COLOR, false),
        )
    }

    private fun loadSecurity(): SecurityPrefs = SecurityPrefs(
        appLockEnabled = prefs.getBoolean(KEY_APP_LOCK, false),
        gracePeriodSec = prefs.getInt(KEY_GRACE_SEC, 30),
    )

    /** Synchronous read for app-launch gating (before any flow is collected). */
    fun isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK, false)
    fun gracePeriodSec(): Int = prefs.getInt(KEY_GRACE_SEC, 30)

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _appearance.value = _appearance.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DYNAMIC_COLOR, enabled).apply()
        _appearance.value = _appearance.value.copy(dynamicColor = enabled)
    }

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK, enabled).apply()
        _security.value = _security.value.copy(appLockEnabled = enabled)
    }

    fun setGracePeriodSec(seconds: Int) {
        prefs.edit().putInt(KEY_GRACE_SEC, seconds).apply()
        _security.value = _security.value.copy(gracePeriodSec = seconds)
    }

    private companion object {
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_DYNAMIC_COLOR = "dynamic_color"
        const val KEY_APP_LOCK = "app_lock_enabled"
        const val KEY_GRACE_SEC = "app_lock_grace_sec"
    }
}
