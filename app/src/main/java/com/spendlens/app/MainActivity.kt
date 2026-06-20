package com.spendlens.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.spendlens.app.data.prefs.ThemeMode
import com.spendlens.app.ui.nav.SpendLensRoot
import com.spendlens.app.ui.theme.SpendLensTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingTxnId = MutableStateFlow<Long?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingTxnId.value = intent.getLongExtra(EXTRA_TXN_ID, -1L).takeIf { it != -1L }
        val container = (application as SpendLensApp).container
        setContent {
            val appearance by container.settingsStore.appearance.collectAsState()
            val darkTheme = when (appearance.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            val txnId by pendingTxnId.collectAsState()
            SpendLensTheme(darkTheme = darkTheme, dynamicColor = appearance.dynamicColor) {
                SpendLensRoot(
                    container = container,
                    initialTxnId = txnId,
                    onTxnIdConsumed = { pendingTxnId.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingTxnId.value = intent.getLongExtra(EXTRA_TXN_ID, -1L).takeIf { it != -1L }
    }

    companion object {
        const val EXTRA_TXN_ID = "txn_id"
    }
}
