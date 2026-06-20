package com.spendlens.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * SpendLens brand palette, expressed as a full Material 3 tonal set for both
 * light and dark schemes. Seeded from the brand teal (#0E7C66) with a warm gold
 * accent. Roles follow the Material 3 colour-role naming so every component —
 * not just the few we style by hand — renders on-brand in either mode.
 */

// ---------- Light scheme ----------
val md_light_primary = Color(0xFF006B57)
val md_light_onPrimary = Color(0xFFFFFFFF)
val md_light_primaryContainer = Color(0xFF7FF8D5)
val md_light_onPrimaryContainer = Color(0xFF002019)

val md_light_secondary = Color(0xFF4B635B)
val md_light_onSecondary = Color(0xFFFFFFFF)
val md_light_secondaryContainer = Color(0xFFCDE9DD)
val md_light_onSecondaryContainer = Color(0xFF072019)

val md_light_tertiary = Color(0xFF7C5800)
val md_light_onTertiary = Color(0xFFFFFFFF)
val md_light_tertiaryContainer = Color(0xFFFFDEA8)
val md_light_onTertiaryContainer = Color(0xFF271900)

val md_light_error = Color(0xFFBA1A1A)
val md_light_onError = Color(0xFFFFFFFF)
val md_light_errorContainer = Color(0xFFFFDAD6)
val md_light_onErrorContainer = Color(0xFF410002)

val md_light_background = Color(0xFFF5FBF6)
val md_light_onBackground = Color(0xFF171D1A)
val md_light_surface = Color(0xFFF5FBF6)
val md_light_onSurface = Color(0xFF171D1A)
val md_light_surfaceVariant = Color(0xFFDBE5DE)
val md_light_onSurfaceVariant = Color(0xFF3F4945)
val md_light_surfaceContainerLow = Color(0xFFEFF5F0)
val md_light_surfaceContainer = Color(0xFFE9EFEA)
val md_light_surfaceContainerHigh = Color(0xFFE3E9E5)
val md_light_outline = Color(0xFF6F7975)
val md_light_outlineVariant = Color(0xFFBFC9C3)
val md_light_inverseSurface = Color(0xFF2B322F)
val md_light_inverseOnSurface = Color(0xFFEDF2EE)
val md_light_inversePrimary = Color(0xFF60DBB9)

// ---------- Dark scheme ----------
val md_dark_primary = Color(0xFF60DBB9)
val md_dark_onPrimary = Color(0xFF00382C)
val md_dark_primaryContainer = Color(0xFF005140)
val md_dark_onPrimaryContainer = Color(0xFF7FF8D5)

val md_dark_secondary = Color(0xFFB1CCC1)
val md_dark_onSecondary = Color(0xFF1D352E)
val md_dark_secondaryContainer = Color(0xFF334B44)
val md_dark_onSecondaryContainer = Color(0xFFCDE9DD)

val md_dark_tertiary = Color(0xFFF6BD48)
val md_dark_onTertiary = Color(0xFF412D00)
val md_dark_tertiaryContainer = Color(0xFF5E4200)
val md_dark_onTertiaryContainer = Color(0xFFFFDEA8)

val md_dark_error = Color(0xFFFFB4AB)
val md_dark_onError = Color(0xFF690005)
val md_dark_errorContainer = Color(0xFF93000A)
val md_dark_onErrorContainer = Color(0xFFFFDAD6)

val md_dark_background = Color(0xFF0E1512)
val md_dark_onBackground = Color(0xFFDDE4DF)
val md_dark_surface = Color(0xFF0E1512)
val md_dark_onSurface = Color(0xFFDDE4DF)
val md_dark_surfaceVariant = Color(0xFF3F4945)
val md_dark_onSurfaceVariant = Color(0xFFBFC9C3)
val md_dark_surfaceContainerLow = Color(0xFF171D1A)
val md_dark_surfaceContainer = Color(0xFF1B2320)
val md_dark_surfaceContainerHigh = Color(0xFF252D2A)
val md_dark_outline = Color(0xFF89938E)
val md_dark_outlineVariant = Color(0xFF3F4945)
val md_dark_inverseSurface = Color(0xFFDDE4DF)
val md_dark_inverseOnSurface = Color(0xFF2B322F)
val md_dark_inversePrimary = Color(0xFF006B57)

// ---------- Semantic finance colours ----------
// Kept outside the Material roles because "money out / money in" is domain
// meaning, not a UI surface. Tuned per-mode for legible contrast. Exposed to
// composables through [ExtendedColors] / SpendLensTheme.colors.
val DebitLight = Color(0xFFC4362C)
val DebitDark = Color(0xFFFF897D)
val CreditLight = Color(0xFF1A7A52)
val CreditDark = Color(0xFF6FD7A8)
