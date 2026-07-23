package com.ocrstudio.app.ui.models

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.common.CorrectionChainEntry
import com.ocrstudio.core.common.CorrectorKind
import com.ocrstudio.core.common.LlmModelInfo
import com.ocrstudio.core.common.MAX_CORRECTION_CHAIN_SIZE
import com.ocrstudio.core.common.OnlineModelCatalog
import com.ocrstudio.core.common.OnlineModelInfo
import com.ocrstudio.core.common.OnlineProvider
import com.ocrstudio.engine.correction.ProviderModelChecker
import com.ocrstudio.worker.AssetDownloadManager
import com.ocrstudio.worker.BuildConfigFlags
import com.ocrstudio.worker.CorrectionChainRepository
import com.ocrstudio.worker.DeviceCapabilities
import com.ocrstudio.worker.DownloadState
import com.ocrstudio.worker.OnlineCorrectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ModelsViewModel @Inject constructor(
    private val assetDownloadManager: AssetDownloadManager,
    private val deviceCapabilities: DeviceCapabilities,
    private val onlineCorrectionRepository: OnlineCorrectionRepository,
    private val correctionChainRepository: CorrectionChainRepository
) : ViewModel() {

    val availableLlmModels: List<LlmModelInfo> get() = deviceCapabilities.availableLlmModels()

    val onlineModels = OnlineModelCatalog.ALL

    /** False when this build wasn't compiled with LiteRT-LM support: offline correction models
     *  can be downloaded (harmless) but must never be offered as a chain entry that would
     *  silently never run. See [CorrectionChainRepository.pruneUnavailableOfflineEntries]. */
    val liteRtLmAvailable: Boolean get() = BuildConfigFlags.liteRtLmAvailable()

    init {
        viewModelScope.launch { correctionChainRepository.pruneUnavailableOfflineEntries() }
    }

    /** The user's correction fallback chain (insertion order; execution always runs offline
     *  entries before online ones -- see [CorrectionChainRepository.executionOrder]). */
    val correctionChain: StateFlow<List<CorrectionChainEntry>> = correctionChainRepository.chain
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun executionOrder(entries: List<CorrectionChainEntry>) = correctionChainRepository.executionOrder(entries)

    val isChainFull: Boolean get() = correctionChain.value.size >= MAX_CORRECTION_CHAIN_SIZE

    fun isInChain(kind: CorrectorKind, modelId: String): Boolean =
        correctionChain.value.any { it.kind == kind && it.modelId == modelId }

    fun toggleOfflineInChain(model: LlmModelInfo) = toggleChain(CorrectionChainEntry(CorrectorKind.OFFLINE, model.id))

    fun toggleOnlineInChain(model: OnlineModelInfo) = toggleChain(CorrectionChainEntry(CorrectorKind.ONLINE, model.id))

    fun removeFromChain(entry: CorrectionChainEntry) = viewModelScope.launch {
        correctionChainRepository.removeEntry(entry)
    }

    private fun toggleChain(entry: CorrectionChainEntry) {
        viewModelScope.launch {
            if (correctionChain.value.any { it == entry }) {
                correctionChainRepository.removeEntry(entry)
            } else {
                correctionChainRepository.addEntry(entry)
            }
        }
    }

    /** modelId (catalog id) -> whether the provider's own API currently lists it as available. */
    private val _modelAvailability = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val modelAvailability: StateFlow<Map<String, Boolean>> = _modelAvailability.asStateFlow()

    private val _isRefreshingModels = MutableStateFlow(false)
    val isRefreshingModels: StateFlow<Boolean> = _isRefreshingModels.asStateFlow()

    fun apiKeyFor(provider: OnlineProvider): String = onlineCorrectionRepository.apiKeyFor(provider)
    fun setApiKeyFor(provider: OnlineProvider, apiKey: String) = onlineCorrectionRepository.setApiKeyFor(provider, apiKey)

    /** Refresh: asks the provider which of our curated models it currently lists as live. */
    fun refreshModels(provider: OnlineProvider, apiKey: String) {
        viewModelScope.launch {
            _isRefreshingModels.value = true
            val curated = OnlineModelCatalog.forProvider(provider)
            val availability = if (provider == OnlineProvider.HUGGING_FACE) {
                curated.associate { it.id to ProviderModelChecker.huggingFaceModelExists(it.modelId) }
            } else {
                val liveIds = ProviderModelChecker.fetchAvailableModelIds(provider, apiKey).getOrDefault(emptySet())
                curated.associate { it.id to (it.modelId in liveIds) }
            }
            _modelAvailability.value = _modelAvailability.value + availability
            _isRefreshingModels.value = false
        }
    }

    fun tesseractStatusFlow(): Flow<DownloadState> =
        assetDownloadManager.observe(uniqueWorkNameFor(assetDownloadManager.tesseractArabicDestination()))

    fun downloadTesseract() {
        assetDownloadManager.downloadTesseractArabic()
    }

    fun pauseTesseract() = assetDownloadManager.pause(uniqueWorkNameFor(assetDownloadManager.tesseractArabicDestination()))
    fun cancelTesseract() = assetDownloadManager.cancel(uniqueWorkNameFor(assetDownloadManager.tesseractArabicDestination()))
    fun deleteTesseract() = assetDownloadManager.delete(assetDownloadManager.tesseractArabicDestination())

    fun downloadPaddle() {
        assetDownloadManager.downloadPaddleModels()
    }

    fun paddleDetStatusFlow(): Flow<DownloadState> =
        assetDownloadManager.observe(uniqueWorkNameFor(assetDownloadManager.paddleDetDestination()))

    fun pausePaddle() = assetDownloadManager.pause(uniqueWorkNameFor(assetDownloadManager.paddleDetDestination()))
    fun cancelPaddle() = assetDownloadManager.cancel(uniqueWorkNameFor(assetDownloadManager.paddleDetDestination()))
    fun deletePaddle() {
        assetDownloadManager.delete(assetDownloadManager.paddleDetDestination())
        assetDownloadManager.delete(assetDownloadManager.paddleRecDestination())
        assetDownloadManager.delete(assetDownloadManager.paddleDictDestination())
    }

    fun downloadLlmModel(model: LlmModelInfo) {
        assetDownloadManager.downloadLlmModel(model)
    }

    fun llmModelStatusFlow(model: LlmModelInfo): Flow<DownloadState> =
        assetDownloadManager.observe(uniqueWorkNameFor(assetDownloadManager.llmModelDestination(model)))

    fun pauseLlmModel(model: LlmModelInfo) = assetDownloadManager.pause(uniqueWorkNameFor(assetDownloadManager.llmModelDestination(model)))
    fun cancelLlmModel(model: LlmModelInfo) = assetDownloadManager.cancel(uniqueWorkNameFor(assetDownloadManager.llmModelDestination(model)))
    fun deleteLlmModel(model: LlmModelInfo) = assetDownloadManager.delete(assetDownloadManager.llmModelDestination(model))

    private fun uniqueWorkNameFor(destination: java.io.File) = assetDownloadManager.uniqueWorkNameFor(destination)

    fun importLocalFile(model: LlmModelInfo, uri: Uri) {
        viewModelScope.launch {
            assetDownloadManager.importLocalFile(uri, assetDownloadManager.llmModelDestination(model))
        }
    }
}
