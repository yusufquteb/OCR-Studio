package com.ocrstudio.engine.correction

import com.ocrstudio.core.common.LlmResult
import javax.inject.Inject

data class PipelineResult(
    val correctedText: String,
    val dictionaryHitRate: Float,
    val llmApplied: Boolean
)

/**
 * Runs Layer 1 (RuleEngine, always) then optionally Layer 2 (a fallback chain of LlmCorrectors)
 * chunk-by-chunk. For each chunk, correctors are tried in the order given until one returns an
 * accepted result; if none do, the rule-engine text for that chunk is kept unchanged. Callers are
 * expected to have already ordered [correctors] per their own priority (this app tries every
 * on-device model before any online one).
 */
class CorrectionPipeline @Inject constructor(
    private val ruleEngine: RuleEngine
) {
    suspend fun run(rawText: String, correctors: List<LlmCorrector>): PipelineResult {
        val ruleResult = ruleEngine.correctText(rawText)

        if (correctors.isEmpty()) {
            return PipelineResult(ruleResult.correctedText, ruleResult.dictionaryHitRate, llmApplied = false)
        }

        val chunks = TextChunker.chunk(ruleResult.correctedText)
        if (chunks.isEmpty()) {
            return PipelineResult(ruleResult.correctedText, ruleResult.dictionaryHitRate, llmApplied = false)
        }

        var anyApplied = false
        val correctedChunks = mutableListOf<String>()
        for (chunk in chunks) {
            var accepted: LlmResult? = null
            for (corrector in correctors) {
                val attempt = runCatching { corrector.correct(chunk) }.getOrNull() ?: continue
                if (attempt.accepted) {
                    accepted = attempt
                    break
                }
            }
            if (accepted != null) anyApplied = true
            correctedChunks.add(accepted?.text ?: chunk)
        }
        val rebuilt = correctedChunks.joinToString(separator = "\n\n")

        return PipelineResult(rebuilt, ruleResult.dictionaryHitRate, llmApplied = anyApplied)
    }
}
