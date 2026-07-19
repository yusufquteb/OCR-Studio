package com.ocrstudio.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ocrstudio.core.ui.theme.ErrorColor
import com.ocrstudio.core.ui.theme.SuccessColor
import com.ocrstudio.core.ui.theme.WarningColor
import kotlin.math.roundToInt

@Composable
fun ConfidenceBadge(score: Float, modifier: Modifier = Modifier) {
    val color: Color = when {
        score >= 0.80f -> SuccessColor
        score >= 0.60f -> WarningColor
        else -> ErrorColor
    }
    Text(
        text = "${(score * 100).roundToInt()}%",
        color = Color.White,
        style = MaterialTheme.typography.labelMedium,
        modifier = modifier
            .background(color, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}
