package com.ocrstudio.worker

import android.content.Context
import android.content.pm.ServiceInfo
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads one asset/model file to [WorkerConstants.KEY_DOWNLOAD_DEST_PATH]. Never throws:
 * network/HTTP failures (including 404s from a moved Hugging Face/GitHub asset) are reported
 * as Result.failure() with a message in the output Data, so the UI can offer a graceful
 * fallback (custom URL / local file import) instead of crashing.
 */
@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_ERROR_MESSAGE = "error_message"
        private const val PROGRESS_UPDATE_INTERVAL_BYTES = 512 * 1024L
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val url = inputData.getString(WorkerConstants.KEY_DOWNLOAD_URL)
        val destPath = inputData.getString(WorkerConstants.KEY_DOWNLOAD_DEST_PATH)
        val label = inputData.getString(WorkerConstants.KEY_DOWNLOAD_LABEL) ?: "Downloading"

        if (url.isNullOrBlank() || destPath.isNullOrBlank()) {
            return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Missing url or destination"))
        }

        setForeground(createForegroundInfo(label, 0))

        val destFile = File(destPath)
        destFile.parentFile?.mkdirs()
        val tempFile = File(destFile.parentFile, "${destFile.name}.part")
        // Resume from wherever a previous (paused/killed) attempt left off, if the server
        // honors Range requests; otherwise the response below falls back to a full 200 restart.
        val alreadyDownloaded = if (tempFile.exists()) tempFile.length() else 0L

        try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                if (alreadyDownloaded > 0) {
                    setRequestProperty("Range", "bytes=$alreadyDownloaded-")
                }
            }
            connection.connect()

            if (connection.responseCode !in 200..299) {
                val message = "HTTP ${connection.responseCode} for $url"
                connection.disconnect()
                return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to message))
            }

            val isResuming = alreadyDownloaded > 0 && connection.responseCode == HttpURLConnection.HTTP_PARTIAL
            if (alreadyDownloaded > 0 && !isResuming) {
                // Server ignored the Range header (full 200 response) -- start over cleanly.
                tempFile.delete()
            }
            val startOffset = if (isResuming) alreadyDownloaded else 0L
            val totalBytes = when {
                isResuming -> connection.contentLengthLong.let { if (it >= 0) it + startOffset else -1L }
                else -> connection.contentLengthLong
            }
            var downloaded = startOffset
            var lastReportedAt = startOffset
            setProgress(progressData(downloaded, totalBytes))

            connection.inputStream.use { input ->
                FileOutputStream(tempFile, isResuming).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (isStopped) {
                            connection.disconnect()
                            // Leave the .part file in place so the next enqueue can resume from here.
                            return@withContext Result.failure(workDataOf(KEY_ERROR_MESSAGE to "Cancelled"))
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastReportedAt >= PROGRESS_UPDATE_INTERVAL_BYTES) {
                            lastReportedAt = downloaded
                            val percent = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else -1
                            setForeground(createForegroundInfo(label, percent))
                            setProgress(progressData(downloaded, totalBytes))
                        }
                    }
                }
            }
            connection.disconnect()
            setProgress(progressData(downloaded, totalBytes))

            if (!tempFile.renameTo(destFile)) {
                tempFile.copyTo(destFile, overwrite = true)
                tempFile.delete()
            }

            Result.success(workDataOf(WorkerConstants.KEY_DOWNLOAD_DEST_PATH to destFile.absolutePath))
        } catch (t: Throwable) {
            // Keep the .part file on transient failures (timeout, dropped connection) so a retry
            // can resume; only a genuinely corrupt partial download would need manual cleanup.
            Result.failure(workDataOf(KEY_ERROR_MESSAGE to (t.message ?: t.toString())))
        }
    }

    private fun progressData(downloaded: Long, totalBytes: Long) = workDataOf(
        WorkerConstants.KEY_BYTES_DOWNLOADED to downloaded,
        WorkerConstants.KEY_TOTAL_BYTES to totalBytes,
        WorkerConstants.KEY_PROGRESS_PERCENT to (if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else -1)
    )

    private fun createForegroundInfo(label: String, percent: Int): ForegroundInfo {
        val notification = NotificationHelper.buildProgressNotification(
            applicationContext,
            label,
            if (percent >= 0) "$percent%" else "Downloading…",
            percent,
            WorkerConstants.DOWNLOAD_NOTIFICATION_CHANNEL_ID
        )
        return ForegroundInfo(
            WorkerConstants.DOWNLOAD_NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}
