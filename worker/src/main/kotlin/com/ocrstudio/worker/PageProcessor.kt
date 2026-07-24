package com.ocrstudio.worker

import android.content.Context
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.AssetPaths
import com.ocrstudio.core.common.BookContext
import com.ocrstudio.core.common.CorrectionChainEntry
import com.ocrstudio.core.common.CorrectionScope
import com.ocrstudio.core.common.CorrectorKind
import com.ocrstudio.core.common.OcrWordsSerializer
import com.ocrstudio.core.common.OnlineModelCatalog
import com.ocrstudio.core.common.PreprocessConfig
import com.ocrstudio.core.common.TashkeelMode
import com.ocrstudio.core.common.ValidationScorer
import com.ocrstudio.core.database.dao.BookGlossaryDao
import com.ocrstudio.core.database.dao.CorrectionMemoryDao
import com.ocrstudio.core.database.entity.BookGlossaryEntry
import com.ocrstudio.core.database.entity.CorrectionMemoryEntry
import com.ocrstudio.core.database.entity.PageRecord
import com.ocrstudio.core.database.entity.ReferenceEntry
import com.ocrstudio.core.database.entity.RootEntry
import com.ocrstudio.core.database.entity.WordRecord
import com.ocrstudio.core.database.repository.PageRepository
import com.ocrstudio.engine.correction.CorrectionPipeline
import com.ocrstudio.engine.correction.LlmCorrector
import com.ocrstudio.engine.correction.LlmCorrectorFactory
import com.ocrstudio.engine.correction.Normalization
import com.ocrstudio.engine.correction.OnlineLlmCorrector
import com.ocrstudio.engine.correction.PipelineResult
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
    private val correctionChainRepository: CorrectionChainRepository,
    private val correctionMemoryDao: CorrectionMemoryDao,
    private val bookGlossaryDao: BookGlossaryDao,
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
        llmModelId: String?,
        tashkeelMode: TashkeelMode = TashkeelMode.NORMAL,
        correctionScope: CorrectionScope? = null
    ): Result<PageRecord> = runCatching {
        // EXACT preserves whatever diacritics the OCR engine recognized -- no corrector is
        // allowed to rewrite the text, only the always-on rule engine's non-destructive cleanup.
        val glossaryTerms = bookGlossaryDao.getByBook(bookId).take(30)
        val glossarySuffix = if (glossaryTerms.isNotEmpty()) {
            "Domain glossary (use consistent spelling for these terms): " +
                glossaryTerms.joinToString(", ") { it.term }
        } else ""
        val scopeSuffix = correctionScope?.systemPromptSuffix() ?: ""
        val systemPromptSuffix = listOf(glossarySuffix, scopeSuffix).filter { it.isNotBlank() }.joinToString("\n")

        val llmCorrectors = if (tashkeelMode == TashkeelMode.EXACT) emptyList()
            else openLlmCorrectors(llmModelId, systemPromptSuffix)
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

            val correction = correctionPipeline.run(parsedPage.text, llmCorrectors, correctionScope)

            // Auto-applies any word-level fix the user already made once elsewhere in this book
            // (Review screen's "correction memory"), so it doesn't have to be corrected again by
            // hand on every later page it appears on.
            val memoryEntries = correctionMemoryDao.getByBook(bookId)
            val finalCorrectedText = applyCorrectionMemory(correction.correctedText, memoryEntries)

            val tashkeelAiCompleted = tashkeelMode == TashkeelMode.SMART &&
                correction.llmApplied &&
                !Normalization.hasDiacritics(recognition.page.text) &&
                Normalization.hasDiacritics(finalCorrectedText)

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
                correctedText = finalCorrectedText,
                ocrConfidence = recognition.page.meanConfidence,
                dictionaryHitRate = correction.dictionaryHitRate,
                parserConfidence = parsedPage.parserConfidence,
                finalScore = validation.finalScore,
                needsReview = validation.needsReview,
                winningEngineId = recognition.winningEngineId,
                imagePath = null,
                processedAtEpochMs = System.currentTimeMillis(),
                rawWordsJson = OcrWordsSerializer.encode(recognition.page.words),
                aiCorrectionApplied = correction.llmApplied,
                tashkeelAiCompleted = tashkeelAiCompleted
            )

            val words = finalCorrectedText.split(Regex("\\s+")).filter { it.isNotBlank() }
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

            // Upsert extracted root terms into the book glossary for cross-page consistency.
            val glossaryEntries = parsedPage.roots.map { root ->
                BookGlossaryEntry(
                    id = "${bookId}_${root.root}",
                    bookId = bookId,
                    term = root.root,
                    source = "roots",
                    firstSeenPageNumber = pageNumber,
                    createdAtEpochMs = System.currentTimeMillis()
                )
            }
            if (glossaryEntries.isNotEmpty()) bookGlossaryDao.insertAll(glossaryEntries)

            pageRecord
        } finally {
            llmCorrectors.forEach { it.close() }
        }
    }

    /** Replaces whole-token matches of a remembered OCR error with its remembered correction,
     *  preserving all whitespace/newlines exactly (splits on whitespace boundaries without
     *  discarding them, rather than re-joining with a single space). */
    private fun applyCorrectionMemory(text: String, entries: List<CorrectionMemoryEntry>): String {
        if (entries.isEmpty()) return text
        val replacements = entries.associate { it.original to it.corrected }
        val tokens = text.split(Regex("(?<=\\s)|(?=\\s)"))
        return tokens.joinToString("") { token -> replacements[token] ?: token }
    }

    /** Re-runs only the correction step for already-recognized text against a single chosen
     *  chain entry -- used by Review's per-page "switch AI provider" action, which shouldn't
     *  have to re-run OCR just to try a different corrector on the same raw text. */
    suspend fun recorrectWithEntry(rawText: String, entry: CorrectionChainEntry, systemPromptSuffix: String = ""): PipelineResult {
        val corrector = buildCorrector(entry, systemPromptSuffix)
        try {
            return correctionPipeline.run(rawText, listOfNotNull(corrector))
        } finally {
            corrector?.close()
        }
    }

    /**
     * Builds the correction fallback chain: if the user has configured one (Models -> AI
     * Settings), every OFFLINE entry runs before every ONLINE entry, per their explicit
     * requirement that on-device models are always tried first. If no chain is configured, falls
     * back to the legacy single-model behavior (the one globally "active" online model, else
     * this job's chosen offline model) so existing jobs/setups keep working unchanged.
     */
    private suspend fun openLlmCorrectors(llmModelId: String?, systemPromptSuffix: String = ""): List<LlmCorrector> {
        val chain = correctionChainRepository.currentChain()
        if (chain.isNotEmpty()) {
            return correctionChainRepository.executionOrder(chain).mapNotNull { buildCorrector(it, systemPromptSuffix) }
        }

        val onlineConfig = onlineCorrectionRepository.currentConfig()
        if (onlineConfig.isUsable) {
            val modelInfo = OnlineModelCatalog.byId(onlineConfig.modelId!!)
            if (modelInfo != null) {
                return listOf(OnlineLlmCorrector(modelInfo.provider, modelInfo.modelId, onlineConfig.apiKey, systemPromptSuffix))
            }
        }

        if (llmModelId != null && BuildConfigFlags.liteRtLmAvailable()) {
            val modelFile = File(context.filesDir, "${AssetPaths.LLM_MODELS_DIR}/$llmModelId.litertlm")
            if (modelFile.exists()) return listOf(LlmCorrectorFactory.createLiteRt(modelFile.absolutePath))
        }
        return emptyList()
    }

    private fun buildCorrector(entry: CorrectionChainEntry, systemPromptSuffix: String = ""): LlmCorrector? = when (entry.kind) {
        CorrectorKind.OFFLINE -> {
            if (!BuildConfigFlags.liteRtLmAvailable()) {
                null
            } else {
                val modelFile = File(context.filesDir, "${AssetPaths.LLM_MODELS_DIR}/${entry.modelId}.litertlm")
                if (modelFile.exists()) LlmCorrectorFactory.createLiteRt(modelFile.absolutePath) else null
            }
        }
        CorrectorKind.ONLINE -> {
            val modelInfo = OnlineModelCatalog.byId(entry.modelId)
            val apiKey = modelInfo?.let { onlineCorrectionRepository.apiKeyFor(it.provider) }
            if (modelInfo != null && !apiKey.isNullOrBlank()) {
                OnlineLlmCorrector(modelInfo.provider, modelInfo.modelId, apiKey, systemPromptSuffix)
            } else {
                null
            }
        }
    }
}
