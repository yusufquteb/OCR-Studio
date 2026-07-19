package com.ocrstudio.engine.correction

import com.ocrstudio.core.common.LlmResult

/** Default binding: rule-based correction only, no on-device LLM pass. */
class NoOpCorrector : LlmCorrector {
    override suspend fun correct(chunk: String): LlmResult =
        LlmResult(text = chunk, accepted = false, rejectionReason = "LLM correction disabled")

    override fun close() = Unit
}
