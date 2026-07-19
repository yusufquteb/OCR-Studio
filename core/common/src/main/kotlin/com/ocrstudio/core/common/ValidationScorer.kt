package com.ocrstudio.core.common

data class ValidationResult(
    val finalScore: Float,
    val needsReview: Boolean
)

object ValidationScorer {
    const val REVIEW_THRESHOLD = 0.80f

    private const val OCR_WEIGHT = 0.5f
    private const val DICTIONARY_WEIGHT = 0.3f
    private const val PARSER_WEIGHT = 0.2f

    fun score(ocrConfidence: Float, dictionaryHitRate: Float, parserConfidence: Float): ValidationResult {
        val final = OCR_WEIGHT * ocrConfidence.coerceIn(0f, 1f) +
            DICTIONARY_WEIGHT * dictionaryHitRate.coerceIn(0f, 1f) +
            PARSER_WEIGHT * parserConfidence.coerceIn(0f, 1f)
        return ValidationResult(
            finalScore = final,
            needsReview = final < REVIEW_THRESHOLD
        )
    }
}
