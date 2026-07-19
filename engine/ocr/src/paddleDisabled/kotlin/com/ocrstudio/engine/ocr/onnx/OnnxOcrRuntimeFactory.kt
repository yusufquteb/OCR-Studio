package com.ocrstudio.engine.ocr.onnx

/** ocrstudio.enablePaddleOcr=false: always yields the no-op runtime. */
object OnnxOcrRuntimeFactory : OnnxOcrRuntimeFactoryContract {
    override fun create(detModelPath: String, recModelPath: String, dictPath: String): OnnxOcrRuntime =
        NoOpOnnxOcrRuntime()
}
