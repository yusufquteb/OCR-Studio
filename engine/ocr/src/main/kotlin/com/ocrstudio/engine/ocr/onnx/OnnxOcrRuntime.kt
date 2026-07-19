package com.ocrstudio.engine.ocr.onnx

import android.graphics.Bitmap
import com.ocrstudio.core.common.OcrPage

/**
 * Isolates all ONNX Runtime calls (PaddleOCR detection + recognition) behind this interface.
 * PaddleOcrEngine (src/main) only ever talks to this interface, never to onnxruntime-android
 * classes directly, so it compiles regardless of whether the ONNX build flag is on.
 */
interface OnnxOcrRuntime {
    val isAvailable: Boolean

    suspend fun recognize(bitmap: Bitmap): OcrPage

    fun close()
}
