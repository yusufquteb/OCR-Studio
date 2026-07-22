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

        val cleaned = LlmOutputValidator.stripCodeFences(rawOutput)
        LlmOutputValidator.validate(chunk, cleaned)
    }

    override fun close() {
        runCatching { session.close() }
        runCatching { engine.close() }
    }
}
