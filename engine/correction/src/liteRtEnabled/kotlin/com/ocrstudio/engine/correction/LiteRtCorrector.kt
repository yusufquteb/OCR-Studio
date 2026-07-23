package com.ocrstudio.engine.correction

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.ocrstudio.core.common.LlmResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LiteRT-LM Engine/Conversation backed corrector, matching the real litertlm-android Kotlin API
 * (Engine/EngineConfig/Conversation/ConversationConfig -- the previous Session/generateContent
 * surface here didn't exist in the actual artifact). Backend is pinned to CPU for broad device
 * compatibility; GPU/NPU require per-device capability checks this doesn't attempt. Only
 * compiled when ocrstudio.enableLiteRtLm=true.
 */
class LiteRtCorrector(private val modelPath: String) : LlmCorrector {

    companion object {
        private const val SYSTEM_PROMPT =
            "You are an Arabic OCR proofreader. Fix OCR errors only. Do not add, remove, or " +
                "rephrase anything. Output the corrected text only."
    }

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    // Engine.initialize() can take significant time, so it's deferred to the first correct()
    // call (already off the main thread via withContext below) rather than the constructor.
    private fun ensureConversation(): Conversation {
        conversation?.let { return it }
        val newEngine = Engine(EngineConfig(modelPath = modelPath, backend = Backend.CPU()))
        newEngine.initialize()
        engine = newEngine
        return newEngine.createConversation(
            ConversationConfig(systemInstruction = Contents.of(SYSTEM_PROMPT))
        ).also { conversation = it }
    }

    override suspend fun correct(chunk: String): LlmResult = withContext(Dispatchers.Default) {
        val rawOutput = try {
            ensureConversation().sendMessage(chunk).text
        } catch (t: Throwable) {
            return@withContext LlmResult(text = chunk, accepted = false, rejectionReason = "LLM call failed: ${t.message}")
        }

        val cleaned = LlmOutputValidator.stripCodeFences(rawOutput)
        LlmOutputValidator.validate(chunk, cleaned)
    }

    override fun close() {
        runCatching { conversation?.close() }
        runCatching { engine?.close() }
    }
}
