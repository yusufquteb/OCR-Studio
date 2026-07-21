package com.ocrstudio.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ocrstudio.core.common.OnlineCorrectionConfig
import com.ocrstudio.core.common.OnlineModelCatalog
import com.ocrstudio.worker.OnlineCorrectionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    fun setDefaultBatchSize(value: Int) = viewModelScope.launch { settingsRepository.setDefaultBatchSize(value) }
    fun setDefaultDpi(value: Int) = viewModelScope.launch { settingsRepository.setDefaultDpi(value) }
    fun setKeepImagesByDefault(value: Boolean) = viewModelScope.launch { settingsRepository.setKeepImagesByDefault(value) }
    fun setBatteryConstraintOnly(value: Boolean) = viewModelScope.launch { settingsRepository.setBatteryConstraintOnly(value) }
    fun clearCache() = settingsRepository.clearCache()

    fun setOnlineCorrectionEnabled(enabled: Boolean) = viewModelScope.launch { onlineCorrectionRepository.setEnabled(enabled) }
    fun setOnlineModelId(modelId: String) = viewModelScope.launch { onlineCorrectionRepository.setModelId(modelId) }
    fun setOnlineApiKey(apiKey: String) = onlineCorrectionRepository.setApiKey(apiKey)
}
