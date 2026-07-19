package com.ocrstudio.engine.correction

import javax.inject.Inject

data class PipelineResult(
    val correctedText: String,
    val dictionaryHitRate: Float,
    val llmApplied: Boolean
)

/**
 * Runs Layer 1 (RuleEngine, always) then optionally Layer 2 (LlmCorrector) chunk-by-chunk,
 * keeping the rule-engine text for any chunk the LLM output fails validation.
 */
class CorrectionPipeline @Inject constructor(
    private val ruleEngine: RuleEngine
) {
    suspend fun run(rawText: String, llmCorrector: LlmCorrector?): PipelineResult {
        val ruleResult = ruleEngine.correctText(rawText)

        if (llmCorrector == null) {
            return PipelineResult(ruleResult.correctedText, ruleResult.dictionaryHitRate, llmApplied = false)
        }

        val chunks = TextChunker.chunk(ruleResult.correctedText)
        if (chunks.isEmpty()) {
            return PipelineResult(ruleResult.correctedText, ruleResult.dictionaryHitRate, llmApplied = false)
        }

        var anyApplied = false
        val rebuilt = chunks.joinToString(separator = "\n\n") { chunk ->
            val result = llmCorrector.correct(chunk)
            if (result.accepted) anyApplied = true
            result.text
        }

        return PipelineResult(rebuilt, ruleResult.dictionaryHitRate, llmApplied = anyApplied)
    }
}
