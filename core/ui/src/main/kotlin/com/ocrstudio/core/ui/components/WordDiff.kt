package com.ocrstudio.core.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.withStyle
import com.ocrstudio.core.ui.theme.ErrorColor
import com.ocrstudio.core.ui.theme.SuccessColor

/**
 * Word-level diff between raw OCR text and corrected text, using a classic
 * LCS alignment over whitespace-split tokens. Tokens only in the corrected
 * text are highlighted green (correction added), tokens only in the raw
 * text are shown struck through in red (correction removed/changed).
 */
object WordDiff {
    private fun tokenize(text: String): List<String> =
        text.split(Regex("\\s+")).filter { it.isNotEmpty() }

    /** Backtracked LCS producing a list of (rawToken?, correctedToken?) aligned pairs. */
    fun align(raw: String, corrected: String): List<Pair<String?, String?>> {
        val a = tokenize(raw)
        val b = tokenize(corrected)
        val n = a.size
        val m = b.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                dp[i][j] = if (a[i] == b[j]) {
                    dp[i + 1][j + 1] + 1
                } else {
                    maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }
        val result = mutableListOf<Pair<String?, String?>>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    result.add(a[i] to b[j])
                    i++; j++
                }
                dp[i + 1][j] >= dp[i][j + 1] -> {
                    result.add(a[i] to null)
                    i++
                }
                else -> {
                    result.add(null to b[j])
                    j++
                }
            }
        }
        while (i < n) { result.add(a[i] to null); i++ }
        while (j < m) { result.add(null to b[j]); j++ }
        return result
    }
}

@Composable
fun WordDiffText(rawText: String, correctedText: String, modifier: Modifier = Modifier) {
    val aligned = remember(rawText, correctedText) { WordDiff.align(rawText, correctedText) }
    val annotated = buildAnnotatedString {
        aligned.forEachIndexed { index, (rawToken, correctedToken) ->
            when {
                rawToken != null && correctedToken != null && rawToken == correctedToken -> {
                    append(correctedToken)
                }
                rawToken != null && correctedToken != null -> {
                    withStyle(SpanStyle(color = ErrorColor, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                        append(rawToken)
                    }
                    append(" ")
                    withStyle(SpanStyle(color = SuccessColor)) {
                        append(correctedToken)
                    }
                }
                correctedToken != null -> {
                    withStyle(SpanStyle(color = SuccessColor)) {
                        append(correctedToken)
                    }
                }
                rawToken != null -> {
                    withStyle(SpanStyle(color = ErrorColor, textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                        append(rawToken)
                    }
                }
            }
            if (index != aligned.lastIndex) append(" ")
        }
    }
    Text(
        text = annotated,
        // Base the paragraph's bidi direction on its actual content rather than the app's
        // ambient LayoutDirection (which is LTR whenever the device locale is English) --
        // otherwise this Arabic OCR text can visually misorder even though the underlying
        // string itself is correct.
        style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Content),
        modifier = modifier
    )
}
