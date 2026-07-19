package com.ocrstudio.worker

import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.database.dao.BookJobDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Called on app start. Any BookJob left in RUNNING state whose unique WorkManager chain is no
 * longer alive (the process was killed mid-batch) gets re-enqueued from its last checkpoint.
 * Completed pages are never reprocessed since JobScheduler always starts at currentPage+1.
 */
class JobRecoveryManager @Inject constructor(
    private val workManager: WorkManager,
    private val bookJobDao: BookJobDao,
    private val jobScheduler: JobScheduler
) {
    private val liveStates = setOf(WorkInfo.State.ENQUEUED, WorkInfo.State.RUNNING, WorkInfo.State.BLOCKED)

    suspend fun recoverIncompleteJobs() {
        val runningJobs = bookJobDao.getByStatus(JobStatus.RUNNING)
        for (job in runningJobs) {
            val workInfos = withContext(Dispatchers.IO) {
                workManager.getWorkInfosForUniqueWork(job.id).get()
            }
            val hasLiveWork = workInfos.any { it.state in liveStates }
            if (!hasLiveWork) {
                jobScheduler.enqueueRemaining(job)
            }
        }
    }
}
