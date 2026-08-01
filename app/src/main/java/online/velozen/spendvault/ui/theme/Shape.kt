package com.spendlens.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shapes for the SpendLens Pro minimalist design system.
 */
val SpendLensShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp), // sm
    small = RoundedCornerShape(8.dp),      // DEFAULT
    medium = RoundedCornerShape(12.dp),    // md
    large = RoundedCornerShape(16.dp),     // lg
    extraLarge = RoundedCornerShape(24.dp) // xl
)
