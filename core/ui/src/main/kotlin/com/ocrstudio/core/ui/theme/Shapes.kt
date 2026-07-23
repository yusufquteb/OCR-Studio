package com.ocrstudio.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** Material 3 Expressive leans on more pronounced rounding than the M3 defaults. `medium` is
 *  bumped to 24dp since Card (job/model/review cards throughout the app) uses it by default --
 *  this changes their look everywhere without touching each call site. */
val OcrStudioShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)
