package com.ocrstudio.worker

import android.content.Context
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.AssetPaths
import com.ocrstudio.core.common.BookContext
import com.ocrstudio.core.common.OcrWordsSerializer
import com.ocrstudio.core.common.OnlineModelCatalog
import com.ocrstudio.core.common.PreprocessConfig
import com.ocrstudio.core.common.ValidationScorer
import com.ocrstudio.core.database.entity.PageRecord
import com.ocrstudio.core.database.entity.ReferenceEntry
import com.ocrstudio.core.database.entity.RootEntry
import com.ocrstudio.core.database.entity.WordRecord
import com.ocrstudio.core.database.repository.PageRepository
import com.ocrstudio.engine.correction.CorrectionPipeline
import com.ocrstudio.engine.correction.LlmCorrector
import com.ocrstudio.engine.correction.LlmCorrectorFactory
import com.ocrstudio.engine.correction.OnlineLlmCorrector
import com.ocrstudio.engine.image.ImagePreprocessor
import com.ocrstudio.engine.ocr.EngineRegistry
import com.ocrstudio.engine.ocr.OcrEngine
import com.ocrstudio.engine.parser.ParserProfile
import com.ocrstudio.engine.pdf.PdfDocumentHandle
import com.ocrstudio.engine.pdf.PdfPageRenderer
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * Single-page render -> preprocess -> ocr(+escalation) -> parse -> correct -> validate ->
 * persist pipeline. Shared by BatchWorker (sequential batch processing) and the Review
 * screen's "reprocess this page" action, so both paths stay in sync.
 */
class PageProcessor @Inject constructor(
    private val pdfPageRenderer: PdfPageRenderer,
    private val imagePreprocessor: ImagePreprocessor,
    private val engineRegistry: EngineRegistry,
    private val correctionPipeline: CorrectionPipeline,
    private val pageRepository: PageRepository,
    private val onlineCorrectionRepository: OnlineCorrectionRepository,
    @AppContext private val context: Context
) {
    suspend fun processPage(
        jobId: String,
        bookId: String,
        pageNumber: Int,
        handle: PdfDocumentHandle,
        dpi: Int,
        preprocessConfig: PreprocessConfig,
        primaryEngine: OcrEngine,
        parserProfile: ParserProfile,
        llmModelId: String?
    ): Result<PageRecord> = runCatching {
        val llmCorrector = openLlmCorrector(llmModelId)
        try {
            val renderResult = pdfPageRenderer.renderPage(handle, pageNumber - 1, dpi)
            val rawBitmap = renderResult.getOrNull() ?: error("Failed to render page $pageNumber")

            val preprocessResult = imagePreprocessor.process(rawBitmap, preprocessConfig)
            rawBitmap.recycle()
            val processedBitmap = preprocessResult.getOrNull() ?: error("Failed to preprocess page $pageNumber")

            val recognition = engineRegistry.recognizeWithEscalation(context, primaryEngine.id, processedBitmap)
            processedBitmap.recycle()

            val bookContext = BookContext(bookId = bookId, jobId = jobId, pageNumber = pageNumber)
            val parsedPage = parserProfile.parse(recognition.page, bookContext)

            val correction = correctionPipeline.run(parsedPage.text, llmCorrector)

            val validation = ValidationScorer.score(
                ocrConfidence = recognition.page.meanConfidence,
                dictionaryHitRate = correction.dictionaryHitRate,
                parserConfidence = parsedPage.parserConfidence
            )

            val pageRecord = PageRecord(
                id = UUID.randomUUID().toString(),
                jobId = jobId,
                pageNumber = pageNumber,
                rawText = recognition.page.text,
                correctedText = correction.correctedText,
                ocrConfidence = recognition.page.meanConfidence,
                dictionaryHitRate = correction.dictionaryHitRate,
                parserConfidence = parsedPage.parserConfidence,
                finalScore = validation.finalScore,
                needsReview = validation.needsReview,
                winningEngineId = recognition.winningEngineId,
                imagePath = null,
                processedAtEpochMs = System.currentTimeMillis(),
                rawWordsJson = OcrWordsSerializer.encode(recognition.page.words)
            )

            val words = correction.correctedText.split(Regex("\\s+")).filter { it.isNotBlank() }
                .mapIndexed { index, word ->
                    WordRecord(
                        id = UUID.randomUUID().toString(),
                        jobId = jobId,
                        pageNumber = pageNumber,
                        word = word,
                        root = null,
                        positionInPage = index,
                        confidence = recognition.page.meanConfidence
                    )
                }

            val roots = parsedPage.roots.map {
                RootEntry(
                    id = UUID.randomUUID().toString(),
                    jobId = jobId,
                    pageNumber = pageNumber,
                    root = it.root,
                    lineIndex = it.lineIndex
                )
            }

            val references = parsedPage.references.map {
                ReferenceEntry(
                    id = UUID.randomUUID().toString(),
                    jobId = jobId,
                    pageNumber = pageNumber,
                    bookAbbreviation = it.bookAbbreviation,
                    bookFullName = it.bookFullName,
                    number = it.number,
                    lineIndex = it.lineIndex
                )
            }

            pageRepository.persistPage(pageRecord, words, roots, references)
            pageRecord
        } finally {
            llmCorrector?.close()
        }
    }

    /**
     * Online correction (if the user opted in and configured a provider/model/API key) takes
     * priority over the on-device LiteRT-LM path -- both are optional layers on top of the
     * always-on rule engine, never a requirement, so preferring whichever one is actually usable
     * is fine.
     */
    private suspend fun openLlmCorrector(llmModelId: String?): LlmCorrector? {
        val onlineConfig = onlineCorrectionRepository.currentConfig()
        if (onlineConfig.isUsable) {
            val modelInfo = OnlineModelCatalog.byId(onlineConfig.modelId!!)
            if (modelInfo != null) {
                return OnlineLlmCorrector(modelInfo.provider, modelInfo.modelId, onlineConfig.apiKey)
            }
        }

        if (llmModelId == null || !BuildConfigFlags.liteRtLmAvailable()) return null
        val modelFile = File(context.filesDir, "${AssetPaths.LLM_MODELS_DIR}/$llmModelId.litertlm")
        if (!modelFile.exists()) return null
        return LlmCorrectorFactory.createLiteRt(modelFile.absolutePath)
    }
}
