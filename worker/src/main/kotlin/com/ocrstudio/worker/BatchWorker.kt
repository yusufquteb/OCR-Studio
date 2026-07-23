package com.ocrstudio.worker

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.common.OcrEngineIds
import com.ocrstudio.core.common.PageSegmentationMode
import com.ocrstudio.core.common.ParserProfileIds
import com.ocrstudio.core.common.PreprocessConfigSerializer
import com.ocrstudio.core.database.dao.BookJobDao
import com.ocrstudio.core.database.dao.ErrorRecordDao
import com.ocrstudio.core.database.entity.ErrorRecord
import com.ocrstudio.core.database.entity.ErrorStage
import com.ocrstudio.core.database.repository.PageRepository
import com.ocrstudio.engine.ocr.EngineRegistry
import com.ocrstudio.engine.parser.ParserProfileRegistry
import com.ocrstudio.engine.pdf.PdfPageRenderer
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Processes one batch (a contiguous page range) of a BookJob. Batches for a job are chained
 * sequentially by JobScheduler; only one page is ever fully in memory (bitmap + OCR result)
 * at a time, and the Tesseract instance for this batch is initialized once and recycled once.
 */
@HiltWorker
class BatchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val pdfPageRenderer: PdfPageRenderer,
    private val engineRegistry: EngineRegistry,
    private val parserProfileRegistry: ParserProfileRegistry,
    private val pageProcessor: PageProcessor,
    private val pageRepository: PageRepository,
    private val bookJobDao: BookJobDao,
    private val errorRecordDao: ErrorRecordDao
) : CoroutineWorker(context, params) {

    private val pausedForMemory = AtomicBoolean(false)
    private val memoryCallbacks = object : ComponentCallbacks2 {
        override fun onTrimMemory(level: Int) {
            if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL) {
                pausedForMemory.set(true)
            }
        }
        override fun onConfigurationChanged(newConfig: Configuration) = Unit
        @Deprecated("Deprecated in Java") override fun onLowMemory() {
            pausedForMemory.set(true)
        }
    }

    override suspend fun doWork(): androidx.work.ListenableWorker.Result = withContext(Dispatchers.Default) {
        val jobId = inputData.getString(WorkerConstants.KEY_JOB_ID) ?: return@withContext androidx.work.ListenableWorker.Result.failure()
        val startPage = inputData.getInt(WorkerConstants.KEY_START_PAGE, -1)
        val endPage = inputData.getInt(WorkerConstants.KEY_END_PAGE, -1)
        val batchIndex = inputData.getInt(WorkerConstants.KEY_BATCH_INDEX, 0)
        val batchCount = inputData.getInt(WorkerConstants.KEY_BATCH_COUNT, 1)
        if (startPage < 0 || endPage < startPage) return@withContext androidx.work.ListenableWorker.Result.failure()

        val job = bookJobDao.getById(jobId) ?: return@withContext androidx.work.ListenableWorker.Result.failure()
        if (job.status == JobStatus.DONE || job.status == JobStatus.PAUSED) {
            return@withContext androidx.work.ListenableWorker.Result.success()
        }

        setForeground(createForegroundInfo(jobId, job.title, batchIndex, batchCount, startPage, 0, endPage - startPage + 1, 0))
        bookJobDao.updateStatus(jobId, JobStatus.RUNNING, System.currentTimeMillis())
        applicationContext.registerComponentCallbacks(memoryCallbacks)

        val preprocessConfig = PreprocessConfigSerializer.decode(job.preprocessConfigJson)
        val parserProfile = parserProfileRegistry.byId(job.profileId)
        val psm = if (job.profileId == ParserProfileIds.MUJAM_MUFAHRAS) {
            PageSegmentationMode.SINGLE_COLUMN
        } else {
            PageSegmentationMode.AUTO
        }
        val ocrConfig = com.ocrstudio.core.common.OcrConfig(
            language = "ara", psm = psm, dataDir = applicationContext.filesDir.absolutePath, dpi = job.dpi
        )
        val primaryEngine = engineRegistry.engineById(job.ocrEngineId)
        if (!primaryEngine.isAvailable(applicationContext)) {
            // The job's chosen engine has no downloaded model (e.g. the user started a job
            // before downloading Tesseract/PaddleOCR from the Models screen). Fail loudly with
            // a recorded error instead of calling init(), which would throw and leave the job
            // stuck at RUNNING forever with no visible explanation.
            errorRecordDao.insert(
                ErrorRecord(
                    id = UUID.randomUUID().toString(),
                    jobId = jobId,
                    pageNumber = startPage,
                    stage = ErrorStage.OCR,
                    message = "OCR engine '${job.ocrEngineId}' has no downloaded model -- " +
                        "download it from the Models screen and restart this job.",
                    timestampEpochMs = System.currentTimeMillis()
                )
            )
            bookJobDao.updateStatus(jobId, JobStatus.FAILED, System.currentTimeMillis())
            applicationContext.unregisterComponentCallbacks(memoryCallbacks)
            return@withContext androidx.work.ListenableWorker.Result.failure()
        }
        primaryEngine.init(applicationContext, ocrConfig)

        var errorCount = 0
        val batchStartMs = System.currentTimeMillis()
        var pagesDone = 0
        val totalPagesInBatch = endPage - startPage + 1

        val documentResult = pdfPageRenderer.open(Uri.parse(job.pdfUri))
        val handle = documentResult.getOrNull()
        if (handle == null) {
            primaryEngine.close()
            applicationContext.unregisterComponentCallbacks(memoryCallbacks)
            return@withContext androidx.work.ListenableWorker.Result.retry()
        }

        try {
            for (pageNumber in startPage..endPage) {
                if (isStopped || pausedForMemory.get()) {
                    bookJobDao.updateStatus(jobId, JobStatus.PAUSED, System.currentTimeMillis())
                    break
                }

                pageProcessor.processPage(
                    jobId = jobId,
                    bookId = job.bookId,
                    pageNumber = pageNumber,
                    handle = handle,
                    dpi = job.dpi,
                    preprocessConfig = preprocessConfig,
                    primaryEngine = primaryEngine,
                    parserProfile = parserProfile,
                    llmModelId = job.llmModelId,
                    tashkeelMode = job.tashkeelMode
                ).onFailure { throwable ->
                    errorCount++
                    errorRecordDao.insert(
                        ErrorRecord(
                            id = UUID.randomUUID().toString(),
                            jobId = jobId,
                            pageNumber = pageNumber,
                            stage = ErrorStage.PERSIST,
                            message = throwable.message ?: throwable.toString(),
                            timestampEpochMs = System.currentTimeMillis()
                        )
                    )
                    bookJobDao.incrementErrorCount(jobId, System.currentTimeMillis())
                }

                pageRepository.checkpoint(jobId, pageNumber)
                pagesDone++

                val elapsedMinutes = (System.currentTimeMillis() - batchStartMs) / 60000.0
                val pagesPerMinute = if (elapsedMinutes > 0) pagesDone / elapsedMinutes else 0.0
                val remaining = totalPagesInBatch - pagesDone
                val etaMinutes = if (pagesPerMinute > 0) remaining / pagesPerMinute else 0.0

                setForeground(
                    createForegroundInfo(
                        jobId, job.title, batchIndex, batchCount, pageNumber, pagesDone,
                        totalPagesInBatch, errorCount, pagesPerMinute, etaMinutes
                    )
                )
            }
        } finally {
            handle.close()
            primaryEngine.close()
            // Also close the escalation engine (Paddle) in case this batch triggered escalation;
            // both engines are effectively singletons reused across batches, so this only tears
            // down the native resources actually touched in this batch, not the wrapper objects.
            if (primaryEngine.id != OcrEngineIds.PADDLE) {
                engineRegistry.engineById(OcrEngineIds.PADDLE).close()
            }
            applicationContext.unregisterComponentCallbacks(memoryCallbacks)
        }

        if (pausedForMemory.get() || isStopped) {
            return@withContext androidx.work.ListenableWorker.Result.success()
        }

        val refreshedJob = bookJobDao.getById(jobId)
        if (refreshedJob != null && refreshedJob.currentPage >= refreshedJob.pageCount) {
            bookJobDao.updateStatus(jobId, JobStatus.DONE, System.currentTimeMillis())
        }

        androidx.work.ListenableWorker.Result.success()
    }

    private fun createForegroundInfo(
        jobId: String,
        title: String,
        batchIndex: Int,
        batchCount: Int,
        currentPage: Int,
        pagesDoneInBatch: Int,
        totalPagesInBatch: Int,
        errorCount: Int,
        pagesPerMinute: Double = 0.0,
        etaMinutes: Double = 0.0
    ): ForegroundInfo {
        val progressPercent = if (totalPagesInBatch > 0) {
            (pagesDoneInBatch * 100 / totalPagesInBatch)
        } else 0
        val content = "Batch ${batchIndex + 1}/$batchCount · Page $currentPage · " +
            "${"%.1f".format(pagesPerMinute)} pages/min · ETA ${etaMinutes.roundToInt()}min · Errors: $errorCount"
        val notification = NotificationHelper.buildProgressNotification(
            applicationContext, title, content, progressPercent,
            contentIntent = NotificationHelper.jobPendingIntent(applicationContext, jobId)
        )
        return ForegroundInfo(
            WorkerConstants.NOTIFICATION_ID_BASE,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}
