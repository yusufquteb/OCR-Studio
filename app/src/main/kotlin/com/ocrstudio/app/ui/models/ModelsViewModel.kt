package com.ocrstudio.app.ui.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.common.LlmModelInfo
import com.ocrstudio.worker.AssetDownloadManager
import com.ocrstudio.worker.DeviceCapabilities
import com.ocrstudio.worker.DownloadState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val assetDownloadManager: AssetDownloadManager,
    private val deviceCapabilities: DeviceCapabilities
) : ViewModel() {

    val availableLlmModels: List<LlmModelInfo> get() = deviceCapabilities.availableLlmModels()

    fun tesseractStatusFlow(): Flow<DownloadState> =
        assetDownloadManager.observe("download_${assetDownloadManager.tesseractArabicDestination().absolutePath}")

    fun downloadTesseract() {
        assetDownloadManager.downloadTesseractArabic()
    }

    fun downloadPaddle() {
        assetDownloadManager.downloadPaddleModels()
    }

    fun paddleDetStatusFlow(): Flow<DownloadState> =
        assetDownloadManager.observe("download_${assetDownloadManager.paddleDetDestination().absolutePath}")

    fun downloadLlmModel(model: LlmModelInfo) {
        assetDownloadManager.downloadLlmModel(model)
    }

    fun llmModelStatusFlow(model: LlmModelInfo): Flow<DownloadState> =
        assetDownloadManager.observe("download_${assetDownloadManager.llmModelDestination(model).absolutePath}")

    fun importLocalFile(model: LlmModelInfo, uri: Uri) {
        viewModelScope.launch {
            assetDownloadManager.importLocalFile(uri, assetDownloadManager.llmModelDestination(model))
        }
    }
}
