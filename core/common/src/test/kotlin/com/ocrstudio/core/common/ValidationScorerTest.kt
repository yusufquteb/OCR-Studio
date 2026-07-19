package com.ocrstudio.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidationScorerTest {

    @Test
    fun `weighted average matches the spec formula`() {
        val result = ValidationScorer.score(ocrConfidence = 0.9f, dictionaryHitRate = 0.8f, parserConfidence = 1.0f)
        // 0.5*0.9 + 0.3*0.8 + 0.2*1.0 = 0.45 + 0.24 + 0.2 = 0.89
        assertEquals(0.89f, result.finalScore, 0.001f)
    }

    @Test
    fun `score below threshold needs review`() {
        val result = ValidationScorer.score(ocrConfidence = 0.5f, dictionaryHitRate = 0.5f, parserConfidence = 0.5f)
        assertEquals(0.5f, result.finalScore, 0.001f)
        assertTrue(result.needsReview)
    }

    @Test
    fun `score at or above threshold does not need review`() {
        val result = ValidationScorer.score(ocrConfidence = 1.0f, dictionaryHitRate = 1.0f, parserConfidence = 1.0f)
        assertEquals(1.0f, result.finalScore, 0.001f)
        assertFalse(result.needsReview)
    }

    @Test
    fun `boundary exactly at threshold is not flagged since comparison is strictly less than`() {
        val result = ValidationScorer.score(ocrConfidence = 0.8f, dictionaryHitRate = 0.8f, parserConfidence = 0.8f)
        assertEquals(0.80f, result.finalScore, 0.001f)
        assertFalse(result.needsReview)
    }

    @Test
    fun `inputs are coerced into the valid range`() {
        val result = ValidationScorer.score(ocrConfidence = 1.5f, dictionaryHitRate = -0.2f, parserConfidence = 1.0f)
        // ocr coerced to 1.0, dictionary coerced to 0.0, parser stays 1.0
        // 0.5*1.0 + 0.3*0.0 + 0.2*1.0 = 0.7
        assertEquals(0.7f, result.finalScore, 0.001f)
    }
}
