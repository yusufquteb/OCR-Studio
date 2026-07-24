package com.ocrstudio.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.database.dao.BookGlossaryDao
import com.ocrstudio.core.database.dao.BookJobDao
import com.ocrstudio.core.database.dao.CorrectionMemoryDao
import com.ocrstudio.core.database.dao.ErrorRecordDao
import com.ocrstudio.core.database.dao.PageRecordDao
import com.ocrstudio.core.database.entity.BookJob
import com.ocrstudio.worker.JobScheduler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class JobProgressUiState(
    val job: BookJob? = null,
    val pagesProcessed: Int = 0,
    val errorCount: Int = 0,
    val estimatedSecondsRemaining: Int? = null,
    val currentTaskLabel: String? = null
)

@HiltViewModel(assistedFactory = JobProgressViewModel.Factory::class)
class JobProgressViewModel @AssistedInject constructor(
    @Assisted private val jobId: String,
    private val bookJobDao: BookJobDao,
    private val pageRecordDao: PageRecordDao,
    private val errorRecordDao: ErrorRecordDao,
    private val jobScheduler: JobScheduler,
    private val bookGlossaryDao: BookGlossaryDao,
    private val correctionMemoryDao: CorrectionMemoryDao
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(jobId: String): JobProgressViewModel
    }

    val uiState: StateFlow<JobProgressUiState> = combine(
        bookJobDao.observeById(jobId),
        errorRecordDao.observeByJob(jobId)
    ) { job, errors ->
        JobProgressUiState(
            job = job,
            pagesProcessed = job?.currentPage ?: 0,
            errorCount = errors.size,
            estimatedSecondsRemaining = job?.let { estimateSecondsRemaining(it) },
            currentTaskLabel = job?.ocrEngineId
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JobProgressUiState())

    /** Rough ETA from the average pace observed so far this job -- no separate timing
     *  instrumentation needed, just the checkpoint fields BatchWorker already persists. */
    private fun estimateSecondsRemaining(job: BookJob): Int? {
        if (job.status != JobStatus.RUNNING || job.currentPage <= 0) return null
        val pagesRemaining = job.pageCount - job.currentPage
        if (pagesRemaining <= 0) return null
        val elapsedMs = System.currentTimeMillis() - job.createdAtEpochMs
        if (elapsedMs <= 0) return null
        val msPerPage = elapsedMs.toDouble() / job.currentPage
        return ((msPerPage * pagesRemaining) / 1000).toInt()
    }

    fun pause() {
        viewModelScope.launch {
            bookJobDao.updateStatus(jobId, JobStatus.PAUSED, System.currentTimeMillis())
            jobScheduler.cancel(jobId)
        }
    }

    fun resume() {
        viewModelScope.launch {
            val job = bookJobDao.getById(jobId) ?: return@launch
            bookJobDao.updateStatus(jobId, JobStatus.RUNNING, System.currentTimeMillis())
            jobScheduler.enqueueRemaining(job)
        }
    }

    fun cancel() {
        viewModelScope.launch {
            bookJobDao.updateStatus(jobId, JobStatus.FAILED, System.currentTimeMillis())
            jobScheduler.cancel(jobId)
        }
    }

    /** Clears all book-level AI memory: glossary terms and correction-memory substitutions.
     *  Caller is responsible for showing a confirmation dialog before invoking this. */
    fun clearBookMemory() {
        viewModelScope.launch {
            val job = bookJobDao.getById(jobId) ?: return@launch
            bookGlossaryDao.clearForBook(job.bookId)
            correctionMemoryDao.clearForBook(job.bookId)
        }
    }
}
