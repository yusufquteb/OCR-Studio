package com.ocrstudio.worker

sealed class DownloadState {
    data object Idle : DownloadState()
    data class InProgress(val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
    data class Completed(val filePath: String) : DownloadState()
    data class Failed(val message: String) : DownloadState()
}
