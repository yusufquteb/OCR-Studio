package com.ocrstudio.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ocrstudio.core.ui.theme.ErrorColor
import com.ocrstudio.core.ui.theme.SuccessColor
import com.ocrstudio.core.ui.theme.WarningColor

enum class ChipTone { NEUTRAL, SUCCESS, WARNING, ERROR }

@Composable
fun StatusChip(label: String, tone: ChipTone, modifier: Modifier = Modifier) {
    val color: Color = when (tone) {
        ChipTone.NEUTRAL -> MaterialTheme.colorScheme.primary
        ChipTone.SUCCESS -> SuccessColor
        ChipTone.WARNING -> WarningColor
        ChipTone.ERROR -> ErrorColor
    }
    SuggestionChip(
        onClick = {},
        modifier = modifier.padding(horizontal = 2.dp),
        label = { Text(label) },
        colors = SuggestionChipDefaults.suggestionChipColors(labelColor = color)
    )
}
