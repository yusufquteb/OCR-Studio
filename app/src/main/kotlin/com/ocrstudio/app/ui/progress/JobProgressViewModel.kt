package com.ocrstudio.app.ui.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.database.dao.BookJobDao
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
    val errorCount: Int = 0
)

@HiltViewModel(assistedFactory = JobProgressViewModel.Factory::class)
class JobProgressViewModel @AssistedInject constructor(
    @Assisted private val jobId: String,
    private val bookJobDao: BookJobDao,
    private val pageRecordDao: PageRecordDao,
    private val errorRecordDao: ErrorRecordDao,
    private val jobScheduler: JobScheduler
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(jobId: String): JobProgressViewModel
    }

    val uiState: StateFlow<JobProgressUiState> = combine(
        bookJobDao.observeById(jobId),
        errorRecordDao.observeByJob(jobId)
    ) { job, errors ->
        JobProgressUiState(job = job, pagesProcessed = job?.currentPage ?: 0, errorCount = errors.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), JobProgressUiState())

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
}
