package com.ocrstudio.worker

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkContinuation
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ocrstudio.core.database.entity.BookJob
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Splits a job's remaining pages (currentPage+1 .. pageCount) into batchSize-sized WorkRequests
 * and chains them sequentially under one unique work name (the jobId), per
 * ExistingWorkPolicy.APPEND_OR_REPLACE. Safe to call for both a fresh job (currentPage=0) and a
 * resumed job (currentPage>0) since it always starts from the checkpoint.
 */
@Singleton
class JobScheduler @Inject constructor(
    private val workManager: WorkManager
) {
    fun enqueueRemaining(job: BookJob) {
        val startPage = job.currentPage + 1
        if (startPage > job.pageCount) return

        val batches = buildBatches(startPage, job.pageCount, job.batchSize.coerceIn(10, 50))
        if (batches.isEmpty()) return

        var continuation: WorkContinuation? = null
        batches.forEachIndexed { index, (start, end) ->
            val request = OneTimeWorkRequestBuilder<BatchWorker>()
                .setInputData(
                    workDataOf(
                        WorkerConstants.KEY_JOB_ID to job.id,
                        WorkerConstants.KEY_START_PAGE to start,
                        WorkerConstants.KEY_END_PAGE to end,
                        WorkerConstants.KEY_BATCH_INDEX to index,
                        WorkerConstants.KEY_BATCH_COUNT to batches.size
                    )
                )
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()

            continuation = continuation?.then(request)
                ?: workManager.beginUniqueWork(job.id, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
        continuation?.enqueue()
    }

    fun cancel(jobId: String) {
        workManager.cancelUniqueWork(jobId)
    }

    private fun buildBatches(start: Int, end: Int, batchSize: Int): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var s = start
        while (s <= end) {
            val e = (s + batchSize - 1).coerceAtMost(end)
            result.add(s to e)
            s = e + 1
        }
        return result
    }
}
