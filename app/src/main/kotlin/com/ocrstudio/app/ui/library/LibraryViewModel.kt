package com.ocrstudio.app.ui.library

import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.app.R
import com.ocrstudio.app.ui.newjob.NewJobDraftHolder
import com.ocrstudio.app.ui.settings.SettingsRepository
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.common.ParserProfileIds
import com.ocrstudio.core.common.PreprocessConfig
import com.ocrstudio.core.common.PreprocessConfigSerializer
import com.ocrstudio.core.database.dao.BookDao
import com.ocrstudio.core.database.dao.BookJobDao
import com.ocrstudio.core.database.entity.Book
import com.ocrstudio.core.database.entity.BookJob
import com.ocrstudio.engine.ocr.EngineRegistry
import com.ocrstudio.engine.pdf.PdfPageRenderer
import com.ocrstudio.worker.JobScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import java.util.UUID
import javax.inject.Inject

/** Rough per-page footprint (decoded bitmap + preprocessing temporaries) used to warn before a
 *  batch add would run the device out of storage mid-job, rather than failing pages silently. */
private const val ESTIMATED_BYTES_PER_PAGE = 3_000_000L

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookJobDao: BookJobDao,
    private val bookDao: BookDao,
    private val jobScheduler: JobScheduler,
    private val draftHolder: NewJobDraftHolder,
    private val engineRegistry: EngineRegistry,
    private val pdfPageRenderer: PdfPageRenderer,
    private val settingsRepository: SettingsRepository,
    @AppContext private val context: Context
) : ViewModel() {

    val jobs: StateFlow<List<BookJob>> = bookJobDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _availableEngineIds = MutableStateFlow<List<String>>(emptyList())
    val availableEngineIds: StateFlow<List<String>> = _availableEngineIds.asStateFlow()

    private val _batchAddMessage = MutableStateFlow<String?>(null)
    val batchAddMessage: StateFlow<String?> = _batchAddMessage.asStateFlow()

    init {
        refreshAvailableEngines()
    }

    fun clearBatchAddMessage() {
        _batchAddMessage.value = null
    }

    /** Quick-add path for scanning several PDFs at once: each gets its own job with the user's
     *  default settings (Settings screen), skipping the per-file Wizard. Falls back to the same
     *  "no engine downloaded" guard the Wizard enforces, and skips (with a message) any file that
     *  wouldn't fit in the device's remaining storage. */
    fun onMultiplePdfsPicked(uris: List<Uri>) {
        viewModelScope.launch {
            val engineId = _availableEngineIds.value.firstOrNull()
            if (engineId == null) {
                _batchAddMessage.value = context.getString(R.string.library_batch_no_engine)
                return@launch
            }
            val settings = settingsRepository.settings.first()
            val skipped = mutableListOf<String>()
            var added = 0

            for (uri in uris) {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val displayName = queryDisplayName(uri) ?: uri.lastPathSegment ?: "Untitled book"
                val handle = pdfPageRenderer.open(uri).getOrNull()
                if (handle == null) {
                    skipped.add(displayName)
                    continue
                }
                val pageCount = handle.pageCount
                handle.close()

                if (!hasEnoughFreeSpace(pageCount * ESTIMATED_BYTES_PER_PAGE)) {
                    skipped.add(displayName)
                    continue
                }

                createJob(uri, displayName, pageCount, engineId, settings)
                added++
            }

            _batchAddMessage.value = when {
                skipped.isEmpty() -> context.getString(R.string.library_batch_queued, added)
                added == 0 -> context.getString(R.string.library_batch_skipped_all, skipped.joinToString(", "))
                else -> context.getString(R.string.library_batch_partial, added, skipped.joinToString(", "))
            }
        }
    }

    private fun hasEnoughFreeSpace(requiredBytes: Long): Boolean =
        StatFs(context.filesDir.path).availableBytes >= requiredBytes

    private suspend fun createJob(
        uri: Uri,
        title: String,
        pageCount: Int,
        engineId: String,
        settings: com.ocrstudio.app.ui.settings.AppSettings
    ) {
        val bookId = UUID.randomUUID().toString()
        val jobId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        bookDao.insert(Book(id = bookId, title = title, pdfUri = uri.toString(), pageCount = pageCount, addedAtEpochMs = now))

        val job = BookJob(
            id = jobId,
            bookId = bookId,
            pdfUri = uri.toString(),
            title = title,
            pageCount = pageCount,
            currentPage = 0,
            batchSize = settings.defaultBatchSize,
            dpi = settings.defaultDpi,
            profileId = ParserProfileIds.GENERIC,
            ocrEngineId = engineId,
            llmModelId = null,
            preprocessConfigJson = PreprocessConfigSerializer.encode(PreprocessConfig.STANDARD),
            status = JobStatus.QUEUED,
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        bookJobDao.insert(job)
        jobScheduler.enqueueRemaining(job)
    }

    /** Re-checks which OCR engines have a downloaded model; call again on screen resume so a
     *  download that finished on the Models screen is reflected without recreating this ViewModel. */
    fun refreshAvailableEngines() {
        viewModelScope.launch {
            _availableEngineIds.value = engineRegistry.availableEngineIds(context)
        }
    }

    fun onPdfPicked(uri: Uri) {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val displayName = queryDisplayName(uri)
        draftHolder.set(uri, displayName)
    }

    fun pauseJob(jobId: String) {
        viewModelScope.launch {
            bookJobDao.updateStatus(jobId, JobStatus.PAUSED, System.currentTimeMillis())
            jobScheduler.cancel(jobId)
        }
    }

    fun resumeJob(job: BookJob) {
        viewModelScope.launch {
            bookJobDao.updateStatus(job.id, JobStatus.RUNNING, System.currentTimeMillis())
            jobScheduler.enqueueRemaining(job)
        }
    }

    /** Only valid while the job isn't RUNNING -- call before resuming, not while it's in flight. */
    fun changeEngine(jobId: String, engineId: String) {
        viewModelScope.launch {
            bookJobDao.updateOcrEngine(jobId, engineId, System.currentTimeMillis())
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
    }
}
