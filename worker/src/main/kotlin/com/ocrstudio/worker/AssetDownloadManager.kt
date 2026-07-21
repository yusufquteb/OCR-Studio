package com.ocrstudio.worker

import android.content.Context
import android.net.Uri
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.AssetPaths
import com.ocrstudio.core.common.DownloadUrls
import com.ocrstudio.core.common.LlmModelInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time in-app downloader for OCR/LLM model assets (the only network access this app ever
 * needs). Runs each download as a foreground WorkManager job with progress notifications;
 * never crashes on failure (404, no network, etc.) -- callers observe DownloadState and can
 * offer a custom-URL or local-file-import fallback.
 */
@Singleton
class AssetDownloadManager @Inject constructor(
    @AppContext private val context: Context,
    private val workManager: WorkManager
) {
    fun tesseractArabicDestination(): File =
        File(context.filesDir, "${AssetPaths.TESSDATA_DIR}/${AssetPaths.TESSERACT_ARA_FILE}")

    fun paddleDetDestination(): File = File(context.filesDir, "${AssetPaths.PADDLE_DIR}/${AssetPaths.PADDLE_DET_FILE}")
    fun paddleRecDestination(): File = File(context.filesDir, "${AssetPaths.PADDLE_DIR}/${AssetPaths.PADDLE_REC_AR_FILE}")
    fun paddleDictDestination(): File = File(context.filesDir, "${AssetPaths.PADDLE_DIR}/${AssetPaths.PADDLE_DICT_AR_FILE}")
    fun llmModelDestination(model: LlmModelInfo): File = File(context.filesDir, "${AssetPaths.LLM_MODELS_DIR}/${model.fileName}")

    fun downloadTesseractArabic(): String =
        enqueue(DownloadUrls.TESSERACT_ARA, tesseractArabicDestination(), "Tesseract Arabic model")

    fun downloadPaddleModels(): List<String> = listOf(
        enqueue(DownloadUrls.PADDLE_DET, paddleDetDestination(), "PaddleOCR detection model"),
        enqueue(DownloadUrls.PADDLE_REC_AR, paddleRecDestination(), "PaddleOCR Arabic recognition model"),
        enqueue(DownloadUrls.PADDLE_DICT_AR, paddleDictDestination(), "PaddleOCR Arabic dictionary")
    )

    fun downloadLlmModel(model: LlmModelInfo): String =
        enqueue(model.downloadUrl, llmModelDestination(model), model.displayName)

    /** Fallback path when a bundled URL 404s: user pastes a custom URL for the same destination. */
    fun downloadFromCustomUrl(url: String, destination: File, label: String): String =
        enqueue(url, destination, label)

    /** Fallback path: user picks a local .traineddata/.litertlm file via SAF instead of downloading. */
    suspend fun importLocalFile(sourceUri: Uri, destination: File): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            destination.parentFile?.mkdirs()
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destination.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open $sourceUri")
            Unit
        }
    }

    fun uniqueWorkNameFor(destination: File): String = "$UNIQUE_WORK_PREFIX${destination.absolutePath}"

    fun observe(uniqueWorkName: String): Flow<DownloadState> =
        workManager.getWorkInfosForUniqueWorkFlow(uniqueWorkName).map { infos ->
            val info = infos.firstOrNull() ?: return@map DownloadState.Idle
            when (info.state) {
                WorkInfo.State.SUCCEEDED -> {
                    val path = info.outputData.getString(WorkerConstants.KEY_DOWNLOAD_DEST_PATH) ?: ""
                    DownloadState.Completed(path)
                }
                WorkInfo.State.FAILED -> DownloadState.Failed(
                    info.outputData.getString(DownloadWorker.KEY_ERROR_MESSAGE) ?: "Download failed"
                )
                WorkInfo.State.CANCELLED -> DownloadState.Failed("Paused")
                else -> DownloadState.InProgress(
                    bytesDownloaded = info.progress.getLong(WorkerConstants.KEY_BYTES_DOWNLOADED, 0L),
                    totalBytes = info.progress.getLong(WorkerConstants.KEY_TOTAL_BYTES, -1L),
                    percent = info.progress.getInt(WorkerConstants.KEY_PROGRESS_PERCENT, -1)
                )
            }
        }

    /** Pauses an in-flight download; the partial file on disk lets a later [enqueue] resume it. */
    fun pause(uniqueWorkName: String) {
        workManager.cancelUniqueWork(uniqueWorkName)
    }

    /** Cancels a download and discards its partial file, so a later download starts from zero. */
    fun cancel(uniqueWorkName: String) {
        workManager.cancelUniqueWork(uniqueWorkName)
        val destPath = uniqueWorkName.removePrefix(UNIQUE_WORK_PREFIX)
        File("$destPath.part").delete()
    }

    /**
     * Deletes a completed download's file and forgets the finished WorkManager job, so its
     * [observe] flow drops back to [DownloadState.Idle] and the download button reappears.
     */
    fun delete(destination: File) {
        destination.delete()
        workManager.pruneWork()
    }

    private fun enqueue(url: String, destination: File, label: String): String {
        val uniqueName = uniqueWorkNameFor(destination)
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    WorkerConstants.KEY_DOWNLOAD_URL to url,
                    WorkerConstants.KEY_DOWNLOAD_DEST_PATH to destination.absolutePath,
                    WorkerConstants.KEY_DOWNLOAD_LABEL to label
                )
            )
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        workManager.enqueueUniqueWork(uniqueName, ExistingWorkPolicy.KEEP, request)
        return uniqueName
    }

    private companion object {
        const val UNIQUE_WORK_PREFIX = "download_"
    }
}
