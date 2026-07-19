package com.ocrstudio.worker

/** Thin wrapper so callers don't need to know which module's generated BuildConfig to read. */
object BuildConfigFlags {
    fun liteRtLmAvailable(): Boolean = com.ocrstudio.engine.correction.BuildConfig.LITERT_LM_AVAILABLE
    fun paddleOcrAvailable(): Boolean = com.ocrstudio.engine.ocr.BuildConfig.PADDLE_OCR_AVAILABLE
}
