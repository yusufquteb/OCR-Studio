package com.ocrstudio.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
/**
 * Character-level diacritics comparison between the raw scanned word and the AI-suggested form.
 * Shows a table of base letters with "OCR mark" vs "suggested mark" per character, highlighting
 * additions (green) and differences (amber). Useful for SMART tashkeel mode review.
 */
@Composable
fun TashkeelDiff(rawWord: String, correctedWord: String, modifier: Modifier = Modifier) {
    val pairs = remember(rawWord, correctedWord) { alignCharacters(rawWord, correctedWord) }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(modifier = modifier) {
            Text(
                "تفصيل الحركات",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text("الحرف", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("قبل التصحيح", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("بعد التصحيح", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                Text("", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.5f))
            }
            HorizontalDivider()
            LazyColumn {
                items(pairs) { pair ->
                    TashkeelRow(pair)
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun TashkeelRow(pair: CharPair) {
    val unchanged = pair.rawMark == pair.correctedMark
    val rowBg = when {
        unchanged -> Color.Transparent
        pair.rawMark == null -> Color(0xFF2E7D32).copy(alpha = 0.12f)  // added — green tint
        else -> Color(0xFFE65100).copy(alpha = 0.10f)                   // changed — amber tint
    }
    Row(
        modifier = Modifier.fillMaxWidth().background(rowBg).padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            pair.baseLetter.toString(),
            style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl),
            modifier = Modifier.weight(1f)
        )
        Text(
            pair.rawMark?.toString() ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = if (pair.rawMark == null) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            modifier = Modifier.weight(1f)
        )
        Text(
            pair.correctedMark?.toString() ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = if (!unchanged) Color(0xFF2E7D32) else Color.Unspecified,
            modifier = Modifier.weight(1f)
        )
        Text(
            when {
                unchanged -> "✔"
                pair.rawMark == null -> "+"
                else -> "≠"
            },
            modifier = Modifier.weight(0.5f)
        )
    }
}

data class CharPair(
    val baseLetter: Char,
    val rawMark: Char?,
    val correctedMark: Char?
)

/** Strips all Arabic diacritic marks, returning the base-letter string. */
private fun stripDiacritics(word: String): String = word.filter { !isDiacritic(it) }

/** Aligns two Arabic words base-letter by base-letter, extracting diacritic marks per position. */
private fun alignCharacters(raw: String, corrected: String): List<CharPair> {
    val rawBase = stripDiacritics(raw)
    val correctedBase = stripDiacritics(corrected)

    val rawMarks = extractMarkMap(raw)
    val correctedMarks = extractMarkMap(corrected)

    val baseLetters = rawBase.toList()

    return baseLetters.mapIndexed { index, ch ->
        CharPair(
            baseLetter = ch,
            rawMark = rawMarks[index],
            correctedMark = correctedMarks[index]
        )
    }
}

/** Returns a list of diacritic marks (or null for no mark) per base-letter position. */
private fun extractMarkMap(word: String): List<Char?> {
    val result = mutableListOf<Char?>()
    var pendingMark: Char? = null
    for (ch in word) {
        if (isDiacritic(ch)) {
            pendingMark = ch
        } else {
            if (result.isNotEmpty()) {
                // Assign the pending mark to the previous letter (mark follows base in Unicode)
                result[result.lastIndex] = pendingMark
            }
            result.add(null)
            pendingMark = null
        }
    }
    // Assign last pending mark
    if (pendingMark != null && result.isNotEmpty()) {
        result[result.lastIndex] = pendingMark
    }
    return result
}

/** Arabic diacritic characters (harakat, shadda, tanwin, sukun, madda). */
private fun isDiacritic(ch: Char): Boolean = ch in 'ً'..'ٟ' || ch == 'ٓ'
