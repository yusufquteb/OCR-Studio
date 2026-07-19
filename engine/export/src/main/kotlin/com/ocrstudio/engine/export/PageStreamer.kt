package com.ocrstudio.engine.export

import com.ocrstudio.core.database.dao.PageRecordDao
import com.ocrstudio.core.database.entity.PageRecord

/** Reads a job's pages in fixed-size batches so exporters never hold a whole book in memory. */
internal class PageStreamer(
    private val pageRecordDao: PageRecordDao,
    private val batchSize: Int = 200
) {
    suspend fun forEachPage(jobId: String, action: suspend (PageRecord) -> Unit): Int {
        var offset = 0
        var total = 0
        while (true) {
            val batch = pageRecordDao.getPageBatch(jobId, batchSize, offset)
            if (batch.isEmpty()) break
            batch.forEach { action(it); total++ }
            offset += batchSize
        }
        return total
    }
}
