package com.ocrstudio.engine.ocr.onnx

/** ocrstudio.enablePaddleOcr=true: constructs the real ONNX-backed PP-OCR runtime. */
object OnnxOcrRuntimeFactory : OnnxOcrRuntimeFactoryContract {
    override fun create(detModelPath: String, recModelPath: String, dictPath: String): OnnxOcrRuntime =
        PaddleOnnxOcrRuntime(detModelPath, recModelPath, dictPath)
}
