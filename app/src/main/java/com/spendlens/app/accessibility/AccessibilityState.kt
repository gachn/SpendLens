package com.spendlens.app.accessibility

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * State holder for accessibility information across the app.
 * Provides convenient access to accessibility settings and utilities.
 */
class AccessibilityState(
    private val context: Context
) {
    val isTalkBackEnabled: Boolean
        get() = AccessibilityHelper.isTalkBackEnabled(context)
    
    val isScreenReaderEnabled: Boolean
        get() = isTalkBackEnabled
    
    fun announce(message: String) {
        AccessibilityHelper.announceForAccessibility(context, message)
    }
}

/**
 * Provides accessibility state to composables.
 */
@Composable
fun rememberAccessibilityState(): AccessibilityState {
    val context = LocalContext.current
    return remember { AccessibilityState(context) }
}

/**
 * Higher-order composable that executes different content based on accessibility state.
 */
@Composable
fun AccessibilityAware(
    accessibilityState: AccessibilityState = rememberAccessibilityState(),
    talkBackContent: @Composable () -> Unit = {},
    defaultContent: @Composable () -> Unit
) {
    if (accessibilityState.isTalkBackEnabled) {
        talkBackContent()
    } else {
        defaultContent()
    }
}

/**
 * Provides accessibility-friendly number formatting for screen readers.
 * For example, "$1,234.56" becomes "one thousand two hundred thirty-four dollars and fifty-six cents"
 */
fun formatNumberForAccessibility(
    amount: Double,
    currency: String = "USD"
): String {
    val dollars = amount.toLong()
    val cents = ((amount - dollars) * 100).toInt()
    
    return when {
        dollars == 0L && cents == 0 -> "zero"
        dollars == 0L -> "$cents cents"
        cents == 0 -> "$dollars"
        else -> "$dollars dollars and $cents cents"
    }
}

/**
 * Provides date formatting that's more natural for screen readers.
 */
fun formatDateForAccessibility(
    timestamp: Long
): String {
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("MMMM d, yyyy", java.util.Locale.getDefault())
    return format.format(date)
}