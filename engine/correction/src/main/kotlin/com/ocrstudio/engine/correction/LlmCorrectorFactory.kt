package com.ocrstudio.engine.correction

/**
 * Implemented differently per build-flag source set (src/liteRtEnabled vs src/liteRtDisabled,
 * selected by ocrstudio.enableLiteRtLm). liteRtDisabled always returns NoOpCorrector so this
 * module compiles even when the LiteRT-LM artifact/API is unavailable.
 */
interface LlmCorrectorFactoryContract {
    fun createLiteRt(modelPath: String): LlmCorrector
}
