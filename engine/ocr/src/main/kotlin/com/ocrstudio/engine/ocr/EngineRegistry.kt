package com.ocrstudio.engine.ocr

import android.content.Context
import com.ocrstudio.core.common.OcrConfig
import com.ocrstudio.core.common.OcrEngineIds
import com.ocrstudio.core.common.OcrPage
import com.ocrstudio.core.common.ValidationScorer
import javax.inject.Inject
import javax.inject.Singleton

data class RecognitionOutcome(
    val page: OcrPage,
    val winningEngineId: String,
    val escalated: Boolean
)

/**
 * Holds the set of registered OcrEngine implementations and implements the escalation
 * strategy: pages with mean confidence below the review threshold are automatically
 * re-run through PaddleOcrEngine (if its models are downloaded) and the higher-scoring
 * result is kept.
 */
@Singleton
class EngineRegistry @Inject constructor(
    private val tesseractEngine: TesseractEngine,
    private val paddleOcrEngine: PaddleOcrEngine
) {
    private val engines: Map<String, OcrEngine> = mapOf(
        tesseractEngine.id to tesseractEngine,
        paddleOcrEngine.id to paddleOcrEngine
    )

    fun engineById(id: String): OcrEngine =
        engines[id] ?: error("Unknown OCR engine id: $id")

    suspend fun availableEngineIds(context: Context): List<String> =
        engines.values.filter { it.isAvailable(context) }.map { it.id }

    /**
     * Recognizes [bitmap] with the given [primaryEngineId]. If the result's mean confidence
     * is below the review threshold and PaddleOCR models are present (and it isn't already
     * the primary engine), automatically re-runs with PaddleOCR and keeps whichever result
     * scored higher.
     */
    suspend fun recognizeWithEscalation(
        context: Context,
        primaryEngineId: String,
        bitmap: android.graphics.Bitmap
    ): RecognitionOutcome {
        val primaryEngine = engineById(primaryEngineId)
        val primaryResult = primaryEngine.recognize(bitmap)

        if (primaryResult.meanConfidence >= ValidationScorer.REVIEW_THRESHOLD ||
            primaryEngineId == OcrEngineIds.PADDLE
        ) {
            return RecognitionOutcome(primaryResult, primaryEngineId, escalated = false)
        }

        if (!paddleOcrEngine.isAvailable(context)) {
            return RecognitionOutcome(primaryResult, primaryEngineId, escalated = false)
        }

        // init() is idempotent, so this is a no-op after the first escalation in a batch.
        paddleOcrEngine.init(context, OcrConfig(language = "ara", dataDir = context.filesDir.absolutePath))
        val escalatedResult = paddleOcrEngine.recognize(bitmap)
        return if (escalatedResult.meanConfidence > primaryResult.meanConfidence) {
            RecognitionOutcome(escalatedResult, OcrEngineIds.PADDLE, escalated = true)
        } else {
            RecognitionOutcome(primaryResult, primaryEngineId, escalated = true)
        }
    }
}
