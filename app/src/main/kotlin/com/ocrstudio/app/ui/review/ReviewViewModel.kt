package com.ocrstudio.app.ui.review

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.OcrConfig
import com.ocrstudio.core.common.PageSegmentationMode
import com.ocrstudio.core.database.dao.BookJobDao
import com.ocrstudio.core.database.dao.PageRecordDao
import com.ocrstudio.core.database.entity.PageRecord
import com.ocrstudio.engine.ocr.EngineRegistry
import com.ocrstudio.engine.parser.ParserProfileRegistry
import com.ocrstudio.engine.pdf.PdfPageRenderer
import com.ocrstudio.worker.PageProcessor
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel(assistedFactory = ReviewViewModel.Factory::class)
class ReviewViewModel @AssistedInject constructor(
    @Assisted private val jobId: String,
    private val pageRecordDao: PageRecordDao,
    private val bookJobDao: BookJobDao,
    private val pdfPageRenderer: PdfPageRenderer,
    private val pageProcessor: PageProcessor,
    private val engineRegistry: EngineRegistry,
    private val parserProfileRegistry: ParserProfileRegistry,
    @AppContext private val context: Context
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(jobId: String): ReviewViewModel
    }

    val pagesNeedingReview: StateFlow<List<PageRecord>> = pageRecordDao.observeNeedsReview(jobId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun saveCorrection(page: PageRecord, newText: String) {
        viewModelScope.launch {
            pageRecordDao.insert(page.copy(correctedText = newText, needsReview = false))
        }
    }

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
                    OcrConfig(language = "ara", psm = PageSegmentationMode.AUTO, dataDir = context.filesDir.absolutePath)
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
                    llmModelId = job.llmModelId
                )
            } finally {
                handle.close()
            }
        }
    }
}
