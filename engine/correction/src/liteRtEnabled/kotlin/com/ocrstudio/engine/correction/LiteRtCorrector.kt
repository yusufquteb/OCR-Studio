package com.ocrstudio.engine.correction

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SessionConfig
import com.ocrstudio.core.common.LlmResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM Engine/Session backed corrector. This targets the LiteRT-LM Kotlin API surface
 * documented alongside the litert-lm Maven artifact at the time this was written
 * (Engine/EngineConfig/SessionConfig, backend auto-selection NPU -> GPU -> CPU). If a newer
 * artifact renames these types, only this file needs updating -- LlmCorrector callers are
 * unaffected. Only compiled when ocrstudio.enableLiteRtLm=true.
 */
class LiteRtCorrector(modelPath: String) : LlmCorrector {

    companion object {
        private const val SYSTEM_PROMPT =
            "You are an Arabic OCR proofreader. Fix OCR errors only. Do not add, remove, or " +
                "rephrase anything. Output the corrected text only."
        private const val MAX_LENGTH_DELTA_RATIO = 0.15
        private val LATIN_SENTENCE_REGEX = Regex("[A-Za-z]{4,}\\s+[A-Za-z]{2,}")
        private val CODE_FENCE_REGEX = Regex("```[a-zA-Z]*\\n?|```")
    }

    private val engine: Engine = Engine(
        EngineConfig(
            modelPath = modelPath,
            preferredBackendOrder = listOf(
                EngineConfig.Backend.NPU,
                EngineConfig.Backend.GPU,
                EngineConfig.Backend.CPU
            )
        )
    )
    private val session = engine.createSession(SessionConfig(systemPrompt = SYSTEM_PROMPT))

    override suspend fun correct(chunk: String): LlmResult = withContext(Dispatchers.Default) {
        val rawOutput = try {
            session.generateContent(chunk)
        } catch (t: Throwable) {
            return@withContext LlmResult(text = chunk, accepted = false, rejectionReason = "LLM call failed: ${t.message}")
        }

        val cleaned = stripCodeFences(rawOutput).trim()
        validate(chunk, cleaned)
    }

    private fun stripCodeFences(text: String): String =
        text.replace(CODE_FENCE_REGEX, "").trim()

    /** Mandatory defensive post-processing: reject outputs that look unsafe to apply. */
    private fun validate(original: String, candidate: String): LlmResult {
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

    override fun close() {
        runCatching { session.close() }
        runCatching { engine.close() }
    }
}
