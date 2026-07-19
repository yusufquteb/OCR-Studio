package com.ocrstudio.engine.ocr.onnx

import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import com.ocrstudio.core.common.OcrEngineIds
import com.ocrstudio.core.common.OcrPage
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc
import java.io.File

internal class PaddleOnnxOcrRuntime(
    detModelPath: String,
    recModelPath: String,
    dictPath: String
) : OnnxOcrRuntime {

    override val isAvailable: Boolean = true

    private val env: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val detSession: OrtSession = createSession(env, detModelPath)
    private val recSession: OrtSession = createSession(env, recModelPath)
    private val vocab: List<String> = loadVocab(dictPath)

    private fun createSession(env: OrtEnvironment, modelPath: String): OrtSession {
        val options = OrtSession.SessionOptions()
        // NNAPI EP when available; ONNX Runtime automatically falls back to the CPU EP
        // per-node for anything NNAPI can't execute, so no separate CPU path is needed.
        runCatching { options.addNnapi() }
        return env.createSession(modelPath, options)
    }

    private fun loadVocab(dictPath: String): List<String> {
        val lines = File(dictPath).readLines().filter { it.isNotEmpty() }
        // Convention: index 0 is the CTC blank (not in the file); a space token is
        // appended after the last dictionary entry.
        return lines + " "
    }

    override suspend fun recognize(bitmap: Bitmap): OcrPage {
        val boxes = Detection.detect(env, detSession, bitmap)
        if (boxes.isEmpty()) {
            return OcrPage(text = "", words = emptyList(), meanConfidence = 0f, engineId = OcrEngineIds.PADDLE)
        }

        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val rgb = Mat()
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        rgba.release()

        val recognized = try {
            boxes.map { box -> Recognition.recognize(env, recSession, rgb, box, vocab) }
        } finally {
            rgb.release()
        }

        return LineAssembly.assemble(recognized, OcrEngineIds.PADDLE)
    }

    override fun close() {
        runCatching { detSession.close() }
        runCatching { recSession.close() }
    }
}
