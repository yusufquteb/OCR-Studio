package com.ocrstudio.app.ui.newjob

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.CorrectionScope
import com.ocrstudio.core.common.CorrectionScopeSerializer
import com.ocrstudio.core.common.DpiPreset
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.common.OcrEngineIds
import com.ocrstudio.core.common.ParserProfileIds
import com.ocrstudio.core.common.PreprocessConfig
import com.ocrstudio.core.common.PreprocessConfigSerializer
import com.ocrstudio.core.common.TashkeelMode
import com.ocrstudio.core.database.dao.BookDao
import com.ocrstudio.core.database.dao.BookJobDao
import com.ocrstudio.core.database.entity.Book
import com.ocrstudio.core.database.entity.BookJob
import com.ocrstudio.engine.image.ImagePreprocessor
import com.ocrstudio.engine.ocr.EngineRegistry
import com.ocrstudio.engine.parser.ParserProfileRegistry
import com.ocrstudio.engine.pdf.PdfPageRenderer
import com.ocrstudio.worker.JobScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Pages above this show a "this may take a while" warning on the file-selection step, with a
 *  rough estimate derived from [SECONDS_PER_PAGE_ESTIMATE]. */
const val LARGE_PDF_PAGE_THRESHOLD = 150
const val WIZARD_STEP_COUNT = 4
private const val SECONDS_PER_PAGE_ESTIMATE = 3.3

data class NewJobFormState(
    val title: String = "",
    val profileId: String = ParserProfileIds.GENERIC,
    val dpiPreset: DpiPreset = DpiPreset.STANDARD,
    val batchSize: Int = 20,
    val ocrEngineId: String = OcrEngineIds.TESSERACT,
    val useLlmCorrection: Boolean = false,
    val llmModelId: String? = null,
    val keepImages: Boolean = false,
    val tashkeelMode: TashkeelMode = TashkeelMode.NORMAL,
    val correctionScope: CorrectionScope = CorrectionScope()
)

@HiltViewModel
class NewJobWizardViewModel @Inject constructor(
    private val draftHolder: NewJobDraftHolder,
    private val pdfPageRenderer: PdfPageRenderer,
    private val imagePreprocessor: ImagePreprocessor,
    private val bookDao: BookDao,
    private val bookJobDao: BookJobDao,
    private val jobScheduler: JobScheduler,
    private val parserProfileRegistry: ParserProfileRegistry,
    private val engineRegistry: EngineRegistry,
    @AppContext private val context: Context
) : ViewModel() {

    val pdfDisplayName: StateFlow<String?> = draftHolder.pdfDisplayName

    private val _form = MutableStateFlow(NewJobFormState())
    val form: StateFlow<NewJobFormState> = _form

    private val _previewBitmap = MutableStateFlow<Bitmap?>(null)
    val previewBitmap: StateFlow<Bitmap?> = _previewBitmap

    val availableProfiles get() = parserProfileRegistry.all().map { it.id }

    private val _availableEngineIds = MutableStateFlow(listOf(OcrEngineIds.TESSERACT))
    val availableEngineIds: StateFlow<List<String>> = _availableEngineIds

    private val _currentStep = MutableStateFlow(0)
    val currentStep: StateFlow<Int> = _currentStep

    private val _pageCount = MutableStateFlow<Int?>(null)
    val pageCount: StateFlow<Int?> = _pageCount

    val estimatedSecondsForPageCount: (Int) -> Int = { pages -> (pages * SECONDS_PER_PAGE_ESTIMATE).toInt() }

    init {
        refreshAvailableEngines()
        loadPageCount()
    }

    fun goToStep(step: Int) {
        _currentStep.value = step.coerceIn(0, WIZARD_STEP_COUNT - 1)
    }

    fun nextStep() = goToStep(_currentStep.value + 1)

    fun previousStep() = goToStep(_currentStep.value - 1)

    private fun loadPageCount() {
        viewModelScope.launch {
            val uri = draftHolder.pdfUri.value ?: return@launch
            val handle = pdfPageRenderer.open(uri).getOrNull() ?: return@launch
            try {
                _pageCount.value = handle.pageCount
            } finally {
                handle.close()
            }
        }
    }

    /**
     * Re-checks which OCR engines have a downloaded model. Called both at creation and every
     * time this screen resumes (e.g. returning from the Models screen after a download) --
     * a one-shot check in init() alone would keep showing "not downloaded" for the lifetime of
     * this ViewModel if the models finished downloading after the screen was first opened.
     */
    fun refreshAvailableEngines() {
        viewModelScope.launch {
            _availableEngineIds.value = engineRegistry.availableEngineIds(context)
        }
    }

    fun update(transform: (NewJobFormState) -> NewJobFormState) {
        _form.value = transform(_form.value)
    }

    fun generatePreview() {
        viewModelScope.launch {
            val uri = draftHolder.pdfUri.value ?: return@launch
            val documentResult = pdfPageRenderer.open(uri)
            val handle = documentResult.getOrNull() ?: return@launch
            try {
                val renderResult = pdfPageRenderer.renderPage(handle, 0, _form.value.dpiPreset.dpi)
                val bitmap = renderResult.getOrNull() ?: return@launch
                val config = configFor(_form.value.dpiPreset)
                val processed = imagePreprocessor.process(bitmap, config)
                _previewBitmap.value = processed.getOrNull()
            } finally {
                handle.close()
            }
        }
    }

    private fun configFor(preset: DpiPreset): PreprocessConfig = when (preset) {
        DpiPreset.OLD_BOOK -> PreprocessConfig.OLD_BOOK
        else -> PreprocessConfig.STANDARD
    }

    suspend fun createAndStartJob(): String? {
        val uri = draftHolder.pdfUri.value ?: return null
        val state = _form.value

        val documentResult = pdfPageRenderer.open(uri)
        val handle = documentResult.getOrNull() ?: return null
        val pageCount = handle.pageCount
        handle.close()

        val bookId = UUID.randomUUID().toString()
        val jobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val title = state.title.ifBlank { draftHolder.pdfDisplayName.value ?: "Untitled book" }

        bookDao.insert(Book(id = bookId, title = title, pdfUri = uri.toString(), pageCount = pageCount, addedAtEpochMs = now))

        val job = BookJob(
            id = jobId,
            bookId = bookId,
            pdfUri = uri.toString(),
            title = title,
            pageCount = pageCount,
            currentPage = 0,
            batchSize = state.batchSize,
            dpi = state.dpiPreset.dpi,
            profileId = state.profileId,
            ocrEngineId = state.ocrEngineId,
            llmModelId = if (state.useLlmCorrection) state.llmModelId else null,
            preprocessConfigJson = PreprocessConfigSerializer.encode(configFor(state.dpiPreset)),
            tashkeelMode = state.tashkeelMode,
            correctionScopeJson = CorrectionScopeSerializer.encode(state.correctionScope),
            status = JobStatus.QUEUED,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        bookJobDao.insert(job)
        jobScheduler.enqueueRemaining(job)
        draftHolder.clear()
        return jobId
    }
}
