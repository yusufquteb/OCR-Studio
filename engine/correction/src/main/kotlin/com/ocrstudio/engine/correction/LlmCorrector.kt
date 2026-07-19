package com.ocrstudio.engine.correction

import com.ocrstudio.core.common.LlmResult

interface LlmCorrector {
    suspend fun correct(chunk: String): LlmResult
    fun close()
}
