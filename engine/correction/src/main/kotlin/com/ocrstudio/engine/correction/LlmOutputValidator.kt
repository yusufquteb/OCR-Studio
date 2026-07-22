package com.ocrstudio.engine.correction

import com.ocrstudio.core.common.LlmResult

/**
 * Mandatory defensive post-processing shared by every LLM corrector (on-device LiteRT-LM and
 * every online provider): reject outputs that look unsafe to apply rather than trust them
 * blindly, since a hallucinated rewrite is worse than no correction at all.
 */
object LlmOutputValidator {
    private const val MAX_LENGTH_DELTA_RATIO = 0.15
    private val LATIN_SENTENCE_REGEX = Regex("[A-Za-z]{4,}\\s+[A-Za-z]{2,}")
    private val CODE_FENCE_REGEX = Regex("```[a-zA-Z]*\\n?|```")

    fun stripCodeFences(text: String): String = text.replace(CODE_FENCE_REGEX, "").trim()

    fun validate(original: String, candidate: String): LlmResult {
        if (candidate.isBlank()) {
            return LlmResult(text = original, accepted = false, rejectionReason = "Empty LLM output")
        }
        val lengthDelta = kotlin.math.abs(candidate.length - original.length).toDouble() /
            original.length.coerceAtLeast(1)
        if (lengthDelta > MAX_LENGTH_DELTA_RATIO) {
            return LlmResult(text = original, accepted = false, rejectionReason = "Length delta ${"%.2f".format(lengthDelta)} exceeds threshold")
        }
        if (LATIN_SENTENCE_REGEX.containsMatchIn(candidate)) {
            return LlmResult(text = original, accepted = false, rejectionReason = "Output contains Latin sentences")
        }
        return LlmResult(text = candidate, accepted = true)
    }
}
