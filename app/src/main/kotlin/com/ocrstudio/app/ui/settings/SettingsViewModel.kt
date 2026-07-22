package com.ocrstudio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.common.OnlineCorrectionConfig
import com.ocrstudio.core.common.OnlineModelCatalog
import com.ocrstudio.core.common.OnlineProvider
import com.ocrstudio.engine.correction.ProviderModelChecker
import com.ocrstudio.worker.OnlineCorrectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val onlineCorrectionRepository: OnlineCorrectionRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())

    val onlineCorrectionConfig: StateFlow<OnlineCorrectionConfig> = onlineCorrectionRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), OnlineCorrectionConfig())

    val onlineModels = OnlineModelCatalog.ALL

    /** modelId (catalog id) -> whether the provider's own API currently lists it as available. */
    private val _modelAvailability = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val modelAvailability: StateFlow<Map<String, Boolean>> = _modelAvailability.asStateFlow()

    private val _isRefreshingModels = MutableStateFlow(false)
    val isRefreshingModels: StateFlow<Boolean> = _isRefreshingModels.asStateFlow()

    fun setDefaultBatchSize(value: Int) = viewModelScope.launch { settingsRepository.setDefaultBatchSize(value) }
    fun setDefaultDpi(value: Int) = viewModelScope.launch { settingsRepository.setDefaultDpi(value) }
    fun setKeepImagesByDefault(value: Boolean) = viewModelScope.launch { settingsRepository.setKeepImagesByDefault(value) }
    fun setBatteryConstraintOnly(value: Boolean) = viewModelScope.launch { settingsRepository.setBatteryConstraintOnly(value) }
    fun clearCache() = settingsRepository.clearCache()

    fun setOnlineCorrectionEnabled(enabled: Boolean) = viewModelScope.launch { onlineCorrectionRepository.setEnabled(enabled) }
    fun setOnlineModelId(modelId: String) = viewModelScope.launch { onlineCorrectionRepository.setModelId(modelId) }
    fun setOnlineApiKey(apiKey: String) = onlineCorrectionRepository.setApiKey(apiKey)

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
}
