package com.ocrstudio.engine.correction

/** ocrstudio.enableLiteRtLm=true: constructs the real LiteRT-LM backed corrector. */
object LlmCorrectorFactory : LlmCorrectorFactoryContract {
    override fun createLiteRt(modelPath: String): LlmCorrector = LiteRtCorrector(modelPath)
}
