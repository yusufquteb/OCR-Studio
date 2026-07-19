package com.ocrstudio.app.ui.newjob

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.DpiPreset
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.common.OcrEngineIds
import com.ocrstudio.core.common.ParserProfileIds
import com.ocrstudio.core.common.PreprocessConfig
import com.ocrstudio.core.common.PreprocessConfigSerializer
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

data class NewJobFormState(
    val title: String = "",
    val profileId: String = ParserProfileIds.GENERIC,
    val dpiPreset: DpiPreset = DpiPreset.STANDARD,
    val batchSize: Int = 20,
    val ocrEngineId: String = OcrEngineIds.TESSERACT,
    val useLlmCorrection: Boolean = false,
    val llmModelId: String? = null,
    val keepImages: Boolean = false
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

    init {
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
