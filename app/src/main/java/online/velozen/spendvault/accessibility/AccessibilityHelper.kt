package online.velozen.spendvault.accessibility

import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

object AccessibilityHelper {
    
    fun isTalkBackEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return am?.isEnabled == true && am.isTouchExplorationEnabled
    }
    
    fun announceForAccessibility(context: Context, message: String) {
        val view = View(context)
        view.announceForAccessibility(message)
    }
}

/**
 * Ensures that a composable has proper content descriptions for screen readers.
 */
fun Modifier.accessibilityDescription(
    description: String
): Modifier {
    return this.then(
        Modifier.semantics {
            this.contentDescription = description
        }
    )
}

/**
 * Creates a modifier that makes an element heading-level for accessibility navigation.
 */
fun Modifier.heading(): Modifier {
    return this.then(
        Modifier.semantics {
            this.heading()
        }
    )
}
