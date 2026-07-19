package com.ocrstudio.app.ui.library

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.app.ui.newjob.NewJobDraftHolder
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.database.dao.BookJobDao
import com.ocrstudio.core.database.entity.BookJob
import com.ocrstudio.worker.JobScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import android.content.Context
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookJobDao: BookJobDao,
    private val jobScheduler: JobScheduler,
    private val draftHolder: NewJobDraftHolder,
    @AppContext private val context: Context
) : ViewModel() {

    val jobs: StateFlow<List<BookJob>> = bookJobDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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

    private fun queryDisplayName(uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) it.getString(it.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }
    }
}
