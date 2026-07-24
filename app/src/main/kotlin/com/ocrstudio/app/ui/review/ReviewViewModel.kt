package com.ocrstudio.app.ui.review

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.CorrectionChainEntry
import com.ocrstudio.core.common.CorrectionScopeSerializer
import com.ocrstudio.core.common.OcrConfig
import com.ocrstudio.core.common.OcrWordsSerializer
import com.ocrstudio.core.common.PageSegmentationMode
import com.ocrstudio.core.common.ReviewType
import com.ocrstudio.core.database.dao.BookGlossaryDao
import com.ocrstudio.core.database.dao.BookJobDao
import com.ocrstudio.core.database.dao.CorrectionMemoryDao
import com.ocrstudio.core.database.dao.PageRecordDao
import com.ocrstudio.core.database.entity.CorrectionMemoryEntry
import com.ocrstudio.core.database.entity.PageRecord
import com.ocrstudio.core.ui.components.WordDiff
import com.ocrstudio.engine.ocr.EngineRegistry
import com.ocrstudio.engine.parser.ParserProfileRegistry
import com.ocrstudio.engine.pdf.PdfPageRenderer
import com.ocrstudio.worker.CorrectionChainRepository
import com.ocrstudio.worker.PageProcessor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class UndoRedoState(val canUndo: Boolean = false, val canRedo: Boolean = false)

data class CompareResult(
    val entry: CorrectionChainEntry,
    val correctedText: String,
    val llmApplied: Boolean
)

@HiltViewModel(assistedFactory = ReviewViewModel.Factory::class)
class ReviewViewModel @AssistedInject constructor(
    @Assisted private val jobId: String,
    private val pageRecordDao: PageRecordDao,
    private val bookJobDao: BookJobDao,
    private val pdfPageRenderer: PdfPageRenderer,
    private val pageProcessor: PageProcessor,
    private val engineRegistry: EngineRegistry,
    private val parserProfileRegistry: ParserProfileRegistry,
    private val correctionChainRepository: CorrectionChainRepository,
    private val correctionMemoryDao: CorrectionMemoryDao,
    private val bookGlossaryDao: BookGlossaryDao,
    @AppContext private val context: Context
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(jobId: String): ReviewViewModel
    }

    val pagesNeedingReview: StateFlow<List<PageRecord>> = pageRecordDao.observeNeedsReview(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val correctionProviders: StateFlow<List<CorrectionChainEntry>> = correctionChainRepository.chain
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pageImages = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val pageImages: StateFlow<Map<String, Bitmap>> = _pageImages

    /** Renders the original page bitmap for the split-view/zoom comparison, on demand -- not
     *  eagerly for every flagged page, since a book can have hundreds of them. */
    fun loadPageImage(page: PageRecord) {
        if (_pageImages.value.containsKey(page.id)) return
        viewModelScope.launch {
            val job = bookJobDao.getById(jobId) ?: return@launch
            val handle = pdfPageRenderer.open(Uri.parse(job.pdfUri)).getOrNull() ?: return@launch
            try {
                val bitmap = pdfPageRenderer.renderPage(handle, page.pageNumber - 1, job.dpi).getOrNull() ?: return@launch
                _pageImages.value = _pageImages.value + (page.id to bitmap)
            } finally {
                handle.close()
            }
        }
    }

    private val undoStacks = mutableMapOf<String, ArrayDeque<String>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<String>>()
    private val _undoRedoState = MutableStateFlow<Map<String, UndoRedoState>>(emptyMap())
    val undoRedoState: StateFlow<Map<String, UndoRedoState>> = _undoRedoState

    fun saveCorrection(page: PageRecord, newText: String) {
        viewModelScope.launch {
            if (newText != page.correctedText) {
                undoStacks.getOrPut(page.id) { ArrayDeque() }.addLast(page.correctedText)
                redoStacks[page.id]?.clear()
                updateUndoRedoState(page.id)
                rememberWordCorrections(page.correctedText, newText)
            }
            pageRecordDao.insert(page.copy(correctedText = newText, needsReview = false))
        }
    }

    /** Records each word the user just changed (old -> new) against this job's book, so
     *  PageProcessor can auto-apply the same fix on later pages of the same book without asking
     *  again -- e.g. correcting "إبن" to "ابن" once applies for the rest of that book. */
    private suspend fun rememberWordCorrections(oldText: String, newText: String) {
        val bookId = bookJobDao.getById(jobId)?.bookId ?: return
        val now = System.currentTimeMillis()
        WordDiff.align(oldText, newText)
            .filter { (old, new) -> old != null && new != null && old != new }
            .forEach { (old, new) ->
                correctionMemoryDao.insert(
                    CorrectionMemoryEntry(
                        id = UUID.randomUUID().toString(),
                        bookId = bookId,
                        original = old!!,
                        corrected = new!!,
                        createdAtEpochMs = now
                    )
                )
            }
    }

    fun undo(page: PageRecord) {
        viewModelScope.launch {
            val stack = undoStacks[page.id]?.takeIf { it.isNotEmpty() } ?: return@launch
            val previous = stack.removeLast()
            redoStacks.getOrPut(page.id) { ArrayDeque() }.addLast(page.correctedText)
            updateUndoRedoState(page.id)
            pageRecordDao.insert(page.copy(correctedText = previous))
        }
    }

    fun redo(page: PageRecord) {
        viewModelScope.launch {
            val stack = redoStacks[page.id]?.takeIf { it.isNotEmpty() } ?: return@launch
            val next = stack.removeLast()
            undoStacks.getOrPut(page.id) { ArrayDeque() }.addLast(page.correctedText)
            updateUndoRedoState(page.id)
            pageRecordDao.insert(page.copy(correctedText = next))
        }
    }

    private fun updateUndoRedoState(pageId: String) {
        _undoRedoState.value = _undoRedoState.value + (
            pageId to UndoRedoState(
                canUndo = undoStacks[pageId]?.isNotEmpty() == true,
                canRedo = redoStacks[pageId]?.isNotEmpty() == true
            )
            )
    }

    private val _compareResults = MutableStateFlow<List<CompareResult>>(emptyList())
    val compareResults: StateFlow<List<CompareResult>> = _compareResults

    private val _isComparing = MutableStateFlow(false)
    val isComparing: StateFlow<Boolean> = _isComparing

    /** Runs the same raw text through multiple providers in parallel and surfaces results for
     *  side-by-side comparison; doesn't persist anything until the user calls saveCorrection. */
    fun compareProviders(page: PageRecord, entries: List<CorrectionChainEntry>) {
        viewModelScope.launch {
            _isComparing.value = true
            _compareResults.value = emptyList()
            val results = entries.map { entry ->
                async {
                    val result = runCatching { pageProcessor.recorrectWithEntry(page.rawText, entry) }.getOrNull()
                    if (result != null) CompareResult(entry, result.correctedText, result.llmApplied) else null
                }
            }.awaitAll().filterNotNull()
            _compareResults.value = results
            _isComparing.value = false
        }
    }

    fun clearCompareResults() {
        _compareResults.value = emptyList()
    }

    /** Re-runs correction for a page range (chapter-scope) with a chosen provider. */
    fun recorrectChapter(pages: List<PageRecord>, entry: CorrectionChainEntry) {
        viewModelScope.launch {
            for (page in pages) {
                val result = runCatching { pageProcessor.recorrectWithEntry(page.rawText, entry) }.getOrNull()
                    ?: continue
                pageRecordDao.insert(page.copy(correctedText = result.correctedText, aiCorrectionApplied = result.llmApplied))
            }
        }
    }

    /** Re-runs correction for all flagged pages in the job with a chosen provider. */
    fun recorrectBook(entry: CorrectionChainEntry) {
        viewModelScope.launch {
            val pages = pagesNeedingReview.value
            recorrectChapter(pages, entry)
        }
    }

    /** Re-runs correction with a specific ReviewType, applying its system-prompt specialization. */
    fun recorrectWithType(page: PageRecord, entry: CorrectionChainEntry, reviewType: ReviewType) {
        viewModelScope.launch {
            val suffix = reviewType.systemPromptSuffix()
            val result = runCatching { pageProcessor.recorrectWithEntry(page.rawText, entry, suffix) }.getOrNull()
                ?: return@launch
            if (reviewType == ReviewType.TRANSLATION) {
                pageRecordDao.insert(page.copy(translatedText = result.correctedText))
            } else {
                undoStacks.getOrPut(page.id) { ArrayDeque() }.addLast(page.correctedText)
                redoStacks[page.id]?.clear()
                updateUndoRedoState(page.id)
                pageRecordDao.insert(page.copy(correctedText = result.correctedText, aiCorrectionApplied = result.llmApplied))
            }
        }
    }

    /** Re-runs only the AI-correction step against the page's existing raw OCR text with a
     *  single chosen provider, without re-running OCR. */
    fun recorrectPage(page: PageRecord, entry: CorrectionChainEntry) {
        viewModelScope.launch {
            undoStacks.getOrPut(page.id) { ArrayDeque() }.addLast(page.correctedText)
            redoStacks[page.id]?.clear()
            updateUndoRedoState(page.id)
            val result = pageProcessor.recorrectWithEntry(page.rawText, entry)
            pageRecordDao.insert(page.copy(correctedText = result.correctedText, aiCorrectionApplied = result.llmApplied))
        }
    }

    private val _wordZoomBitmap = MutableStateFlow<Bitmap?>(null)
    val wordZoomBitmap: StateFlow<Bitmap?> = _wordZoomBitmap

    /** Crops the bounding-box region for [wordText] out of the rendered page bitmap and scales it
     *  up for display in the word-zoom slot next to TashkeelDiff.  Strips diacritics before
     *  matching so the (possibly corrected) word still maps to its OCR box. */
    fun cropWordBitmap(page: PageRecord, wordText: String) {
        viewModelScope.launch {
            val pageBitmap = _pageImages.value[page.id] ?: run {
                val job = bookJobDao.getById(jobId) ?: return@launch
                val handle = pdfPageRenderer.open(Uri.parse(job.pdfUri)).getOrNull() ?: return@launch
                try {
                    pdfPageRenderer.renderPage(handle, page.pageNumber - 1, job.dpi).getOrNull()
                        ?.also { _pageImages.value = _pageImages.value + (page.id to it) }
                } finally {
                    handle.close()
                }
            } ?: return@launch

            val words = OcrWordsSerializer.decode(page.rawWordsJson)
            val targetBase = wordText.filter { it !in 'ً'..'ٟ' && it != 'ٓ' }
            val match = words.firstOrNull { w ->
                val base = w.text.filter { it !in 'ً'..'ٟ' && it != 'ٓ' }
                base == targetBase || w.text == wordText
            } ?: return@launch

            val pad = 16
            val l = (match.left - pad).coerceAtLeast(0)
            val t = (match.top - pad).coerceAtLeast(0)
            val r = (match.right + pad).coerceAtMost(pageBitmap.width)
            val b = (match.bottom + pad).coerceAtMost(pageBitmap.height)
            if (r <= l || b <= t) return@launch

            val cropped = android.graphics.Bitmap.createBitmap(pageBitmap, l, t, r - l, b - t)
            val minDim = minOf(cropped.width, cropped.height).coerceAtLeast(1)
            _wordZoomBitmap.value = if (minDim < 200) {
                val scale = 200f / minDim
                android.graphics.Bitmap.createScaledBitmap(
                    cropped, (cropped.width * scale).toInt(), (cropped.height * scale).toInt(), true
                )
            } else cropped
        }
    }

    fun clearWordZoom() { _wordZoomBitmap.value = null }

    /** Re-runs the full page pipeline for one flagged page with a possibly different engine/DPI. */
    fun reprocessPage(page: PageRecord, engineId: String, dpi: Int) {
        viewModelScope.launch {
            val job = bookJobDao.getById(jobId) ?: return@launch
            val documentResult = pdfPageRenderer.open(Uri.parse(job.pdfUri))
            val handle = documentResult.getOrNull() ?: return@launch
            try {
                val engine = engineRegistry.engineById(engineId)
                engine.init(
                    context,
                    OcrConfig(language = "ara", psm = PageSegmentationMode.AUTO, dataDir = context.filesDir.absolutePath, dpi = dpi)
                )
                val parserProfile = parserProfileRegistry.byId(job.profileId)
                pageProcessor.processPage(
                    jobId = jobId,
                    bookId = job.bookId,
                    pageNumber = page.pageNumber,
                    handle = handle,
                    dpi = dpi,
                    preprocessConfig = com.ocrstudio.core.common.PreprocessConfigSerializer.decode(job.preprocessConfigJson),
                    primaryEngine = engine,
                    parserProfile = parserProfile,
                    llmModelId = job.llmModelId,
                    tashkeelMode = job.tashkeelMode,
                    correctionScope = CorrectionScopeSerializer.decode(job.correctionScopeJson)
                )
            } finally {
                handle.close()
            }
        }
    }
}
