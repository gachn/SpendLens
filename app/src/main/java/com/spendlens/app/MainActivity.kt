package com.spendlens.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.newrelic.agent.android.NewRelic
import com.spendlens.app.data.prefs.ThemeMode
import com.spendlens.app.ui.nav.SpendLensRoot
import com.spendlens.app.ui.theme.SpendLensTheme
import com.spendlens.app.util.AppLog
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FragmentActivity() {

    private val pendingTxnId = MutableStateFlow<Long?>(null)
    private val locked = MutableStateFlow(false)

    /** Guards against firing a second prompt while one is already on screen. */
    private var authInProgress = false

    private val settingsStore by lazy { (application as SpendLensApp).container.settingsStore }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (BuildConfig.NEW_RELIC_APP_TOKEN.isNotBlank()) {
            NewRelic.withApplicationToken(BuildConfig.NEW_RELIC_APP_TOKEN)
                .start(applicationContext)
            AppLog.i("New Relic agent started build=${BuildConfig.BUILD_TYPE} version=${BuildConfig.VERSION_NAME}")
        } else {
            AppLog.w("New Relic agent skipped — NEW_RELIC_APP_TOKEN not configured")
        }
        enableEdgeToEdge()
        pendingTxnId.value = intent.getLongExtra(EXTRA_TXN_ID, -1L).takeIf { it != -1L }
        // Cold start: lock immediately if the feature is on.
        locked.value = settingsStore.isAppLockEnabled()
        val container = (application as SpendLensApp).container
        setContent {
            val appearance by container.settingsStore.appearance.collectAsState()
            val darkTheme = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val txnId by pendingTxnId.collectAsState()
            val isLocked by locked.collectAsState()
            SpendLensTheme(darkTheme = darkTheme, dynamicColor = appearance.dynamicColor) {
                if (isLocked) {
                    LockScreen(onUnlock = ::promptUnlock)
                } else {
                    SpendLensRoot(
                        container = container,
                        initialTxnId = txnId,
                        onTxnIdConsumed = { pendingTxnId.value = null },
                    )
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        lastBackgroundElapsed = SystemClock.elapsedRealtime()
    }

    /**
     * Auto-prompt only once the window actually has focus. BiometricPrompt rejects
     * authenticate() with "Caller is not foreground" if fired during composition/onResume
     * on a cold start (window not yet focused), which silently swallowed the prompt.
     */
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && locked.value && !authInProgress) promptUnlock()
    }

    override fun onStart() {
        super.onStart()
        if (!settingsStore.isAppLockEnabled()) return
        // Re-lock only if backgrounded longer than the grace period (ignores rotation re-creates).
        val graceMs = settingsStore.gracePeriodSec() * 1000L
        if (lastBackgroundElapsed != 0L &&
            SystemClock.elapsedRealtime() - lastBackgroundElapsed > graceMs
        ) {
            locked.value = true
        }
    }

    /** Prompt for biometric / device-credential auth. Clears [locked] on success. */
    private fun promptUnlock() {
        if (authInProgress) return
        val authenticators =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) BIOMETRIC_STRONG or DEVICE_CREDENTIAL
            else BIOMETRIC_WEAK
        // No secure lock configured at all — don't trap the user out of their own app.
        val canAuth = BiometricManager.from(this).canAuthenticate(authenticators)
        if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
            AppLog.w("canAuthenticate($authenticators) returned $canAuth — unlocking without prompt", TAG)
            locked.value = false
            return
        }
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authInProgress = false
                    locked.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // User cancelled / hardware error / lockout: keep locked so the
                    // LockScreen's Unlock button lets them retry.
                    authInProgress = false
                    AppLog.w("auth error $errorCode: $errString", TAG)
                }
            },
        )
        authInProgress = true
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock SpendLens")
            .setSubtitle("Authenticate to view your finances")
            .setAllowedAuthenticators(authenticators)
            .apply {
                // A negative button is required (and only allowed) when device credential is not an option.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) setNegativeButtonText("Cancel")
            }
            .build()
        prompt.authenticate(info)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingTxnId.value = intent.getLongExtra(EXTRA_TXN_ID, -1L).takeIf { it != -1L }
    }

    companion object {
        private const val TAG = "SpendLensLock"
        const val EXTRA_TXN_ID = "txn_id"

        /**
         * Process-level timestamp ([SystemClock.elapsedRealtime]) of when the activity last stopped.
         * Static so it survives activity re-creation (e.g. rotation) and the grace check stays correct.
         */
        @Volatile
        private var lastBackgroundElapsed = 0L
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit) {
    // Auto-prompt is driven by Activity.onWindowFocusChanged (the window must have focus
    // before BiometricPrompt will show). The button below is the manual retry path.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
            Text(
                "SpendLens is locked",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp),
            )
            Text(
                "Authenticate to view your finances.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 24.dp),
            )
            Button(onClick = onUnlock) { Text("Unlock") }
        }
    }
}
