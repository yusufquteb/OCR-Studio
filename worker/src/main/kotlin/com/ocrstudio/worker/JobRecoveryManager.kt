package com.ocrstudio.worker

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.database.dao.BookJobDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Called on app start. Any BookJob left in RUNNING state whose unique WorkManager chain is no
 * longer alive (the process was killed mid-batch) gets re-enqueued from its last checkpoint.
 * Completed pages are never reprocessed since JobScheduler always starts at currentPage+1.
 */
@Singleton
class JobRecoveryManager @Inject constructor(
    private val workManager: WorkManager,
    private val bookJobDao: BookJobDao,
    private val jobScheduler: JobScheduler
) {
    private val liveStates = setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)

    private val _recoveredJobIds = MutableStateFlow<List<String>>(emptyList())
    /** Job ids that were silently re-enqueued after this recovery pass, so the UI can surface a
     *  one-time "Resuming your last job" notice instead of the recovery happening invisibly. */
    val recoveredJobIds: StateFlow<List<String>> = _recoveredJobIds.asStateFlow()

    suspend fun recoverIncompleteJobs() {
        val runningJobs = bookJobDao.getByStatus(JobStatus.RUNNING)
        val recovered = mutableListOf<String>()
        for (job in runningJobs) {
            val workInfos = withContext(Dispatchers.IO) {
                workManager.getWorkInfosForUniqueWork(job.id).get()
            }
            val hasLiveWork = workInfos.any { it.state in liveStates }
            if (!hasLiveWork) {
                jobScheduler.enqueueRemaining(job)
                recovered.add(job.id)
            }
        }
        if (recovered.isNotEmpty()) {
            _recoveredJobIds.value = recovered
        }
    }

    /** Call once the UI has shown/acted on the notice, so it doesn't reappear on recomposition. */
    fun clearRecoveredJobIds() {
        _recoveredJobIds.value = emptyList()
    }
}
