package com.ocrstudio.engine.correction

data class CorrectionResult(
    val correctedText: String,
    val dictionaryHitRate: Float
)

/**
 * Layer 1 correction (always on): dictionary-guided normalization + OCR confusion-map
 * candidate substitution. Never applies a change that isn't validated against the dictionary.
 */
class RuleEngine(
    private val dictionary: Dictionary,
    private val normalizeDigits: Boolean = false
) {
    private val arabicWordRegex = Regex("[\\u0621-\\u064A\\u0670-\\u06D3\\uFE70-\\uFEFF]+")

    fun correctText(text: String): CorrectionResult {
        var totalWords = 0
        var foundWords = 0

        var working = text
        if (normalizeDigits) {
            working = Normalization.arabicDigitsToWestern(working)
        }

        val corrected = arabicWordRegex.replace(working) { match ->
            totalWords++
            val (result, found) = correctWord(match.value)
            if (found) foundWords++
            result
        }

        val hitRate = if (totalWords == 0) 1.0f else foundWords.toFloat() / totalWords
        return CorrectionResult(correctedText = corrected, dictionaryHitRate = hitRate)
    }

    /** Returns (finalWord, foundInDictionary). Only ever changes a word if the change is dictionary-validated. */
    fun correctWord(rawWord: String): Pair<String, Boolean> {
        val baseline = Normalization.baseline(rawWord)

        if (dictionary.contains(baseline)) {
            return baseline to true
        }

        // Ya/alif-maqsura, ta-marbuta/ha, and hamza-form variants.
        for (variant in Normalization.candidateVariants(baseline)) {
            if (dictionary.contains(variant)) return variant to true
        }

        // OCR confusion-map candidates: only accepted because the original is NOT in the
        // dictionary and the candidate IS -- never overrides a word already recognized as valid.
        for (candidate in ConfusionMap.candidates(baseline)) {
            if (dictionary.contains(candidate)) return candidate to true
        }

        return baseline to false
    }
}
