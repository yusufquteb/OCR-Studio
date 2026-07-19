package com.ocrstudio.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import com.ocrstudio.core.common.AssetPaths
import com.ocrstudio.core.common.OcrConfig
import com.ocrstudio.core.common.OcrEngineIds
import com.ocrstudio.core.common.OcrPage
import com.ocrstudio.engine.ocr.onnx.OnnxOcrRuntime
import com.ocrstudio.engine.ocr.onnx.OnnxOcrRuntimeFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * PP-OCR (detection + recognition) engine. Only ever calls into OnnxOcrRuntime, never
 * onnxruntime-android directly, so this class compiles regardless of the
 * ocrstudio.enablePaddleOcr build flag. It only reports available() once its three
 * model files (det.onnx, rec_ar.onnx, dict_ar.txt) have been downloaded.
 */
class PaddleOcrEngine @Inject constructor() : OcrEngine {

    override val id: String = OcrEngineIds.PADDLE

    private var runtime: OnnxOcrRuntime? = null

    private fun detFile(context: Context) = File(context.filesDir, "${AssetPaths.PADDLE_DIR}/${AssetPaths.PADDLE_DET_FILE}")
    private fun recFile(context: Context) = File(context.filesDir, "${AssetPaths.PADDLE_DIR}/${AssetPaths.PADDLE_REC_AR_FILE}")
    private fun dictFile(context: Context) = File(context.filesDir, "${AssetPaths.PADDLE_DIR}/${AssetPaths.PADDLE_DICT_AR_FILE}")

    override suspend fun isAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        detFile(context).exists() && recFile(context).exists() && dictFile(context).exists()
    }

    override suspend fun init(context: Context, config: OcrConfig) = withContext(Dispatchers.Default) {
        if (runtime != null) return@withContext // idempotent: safe to call again mid-batch on escalation
        check(isAvailable(context)) { "Paddle OCR model files are not downloaded yet" }
        runtime = OnnxOcrRuntimeFactory.create(
            detModelPath = detFile(context).absolutePath,
            recModelPath = recFile(context).absolutePath,
            dictPath = dictFile(context).absolutePath
        )
    }

    override suspend fun recognize(bitmap: Bitmap): OcrPage {
        val activeRuntime = checkNotNull(runtime) { "PaddleOcrEngine.init() must be called before recognize()" }
        return activeRuntime.recognize(bitmap)
    }

    override fun close() {
        runtime?.close()
        runtime = null
    }
}
