package com.spendlens.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Luminous Ledger is a dark-only design system.
// The darkTheme / dynamicColor params are accepted for call-site compatibility
// but ignored — we always render the dark scheme.
private val LuminousLedgerColors = darkColorScheme(
    primary                 = LLPrimary,
    onPrimary               = LLOnPrimary,
    primaryContainer        = LLPrimaryContainer,
    onPrimaryContainer      = LLOnPrimaryContainer,
    secondary               = LLSecondary,
    onSecondary             = LLOnSecondary,
    secondaryContainer      = LLSecondaryContainer,
    onSecondaryContainer    = LLOnSecondaryContainer,
    tertiary                = LLTertiary,
    onTertiary              = LLOnTertiary,
    tertiaryContainer       = LLTertiaryContainer,
    onTertiaryContainer     = LLOnTertiaryContainer,
    error                   = LLError,
    onError                 = LLOnError,
    errorContainer          = LLErrorContainer,
    onErrorContainer        = LLOnErrorContainer,
    background              = LLBackground,
    onBackground            = LLOnBackground,
    surface                 = LLSurface,
    onSurface               = LLOnSurface,
    surfaceVariant          = LLSurfaceContainerHighest,
    onSurfaceVariant        = LLOnSurfaceVariant,
    surfaceContainerLowest  = LLSurfaceContainerLowest,
    surfaceContainerLow     = LLSurfaceContainerLow,
    surfaceContainer        = LLSurfaceContainer,
    surfaceContainerHigh    = LLSurfaceContainerHigh,
    surfaceContainerHighest = LLSurfaceContainerHighest,
    outline                 = LLOutline,
    outlineVariant          = LLOutlineVariant,
    inverseSurface          = LLInverseSurface,
    inverseOnSurface        = LLInverseOnSurface,
    inversePrimary          = LLInversePrimary,
)

@Immutable
data class ExtendedColors(val debit: Color, val credit: Color)

private val LuminousExtendedColors = ExtendedColors(debit = Expense, credit = Income)

val LocalExtendedColors = staticCompositionLocalOf { LuminousExtendedColors }

object SpendLensTheme {
    val colors: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

@Composable
fun SpendLensTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            (view.context as? Activity)?.window?.let { window ->
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars  = false
                controller.isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides LuminousExtendedColors) {
        MaterialTheme(
            colorScheme = LuminousLedgerColors,
            typography  = SpendLensTypography,
            shapes      = SpendLensShapes,
            content     = content,
        )
    }
}
