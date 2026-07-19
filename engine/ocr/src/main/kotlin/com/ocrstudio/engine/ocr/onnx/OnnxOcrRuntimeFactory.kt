package com.ocrstudio.engine.ocr.onnx

/**
 * Implemented differently per build-flag source set (src/paddleEnabled vs src/paddleDisabled,
 * selected by ocrstudio.enablePaddleOcr in gradle.properties). The paddleEnabled variant
 * constructs a real ONNX-backed runtime; paddleDisabled always returns NoOpOnnxOcrRuntime so
 * the module compiles even when onnxruntime-android cannot be resolved.
 */
interface OnnxOcrRuntimeFactoryContract {
    fun create(detModelPath: String, recModelPath: String, dictPath: String): OnnxOcrRuntime
}
