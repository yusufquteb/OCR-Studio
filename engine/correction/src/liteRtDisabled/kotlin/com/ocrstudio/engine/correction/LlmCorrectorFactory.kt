package com.ocrstudio.engine.correction

/** ocrstudio.enableLiteRtLm=false: always yields the no-op corrector. */
object LlmCorrectorFactory : LlmCorrectorFactoryContract {
    override fun createLiteRt(modelPath: String): LlmCorrector = NoOpCorrector()
}
