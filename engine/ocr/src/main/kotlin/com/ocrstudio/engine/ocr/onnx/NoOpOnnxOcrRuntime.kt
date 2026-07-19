package com.ocrstudio.engine.ocr.onnx

import android.graphics.Bitmap
import com.ocrstudio.core.common.OcrEngineIds
import com.ocrstudio.core.common.OcrPage

/** Used when ocrstudio.enablePaddleOcr=false or model files are not yet downloaded. */
class NoOpOnnxOcrRuntime : OnnxOcrRuntime {
    override val isAvailable: Boolean = false

    override suspend fun recognize(bitmap: Bitmap): OcrPage =
        OcrPage(text = "", words = emptyList(), meanConfidence = 0f, engineId = OcrEngineIds.PADDLE)

    override fun close() = Unit
}
