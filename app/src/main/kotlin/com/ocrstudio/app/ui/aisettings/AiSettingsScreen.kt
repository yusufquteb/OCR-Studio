package com.ocrstudio.app.ui.aisettings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.app.ui.models.ModelsViewModel
import com.ocrstudio.core.common.OnlineModelInfo
import com.ocrstudio.core.common.OnlineProvider

private fun providerAvatarColor(provider: OnlineProvider): Color = when (provider) {
    OnlineProvider.GOOGLE_AI_STUDIO -> Color(0xFF2E5F4E)
    OnlineProvider.OPENROUTER -> Color(0xFF6C5CE7)
    OnlineProvider.NVIDIA_NIM -> Color(0xFF3E8E33)
    OnlineProvider.HUGGING_FACE -> Color(0xFFC98A1E)
}

private fun providerAvatarLabel(provider: OnlineProvider): String = when (provider) {
    OnlineProvider.GOOGLE_AI_STUDIO -> "G"
    OnlineProvider.OPENROUTER -> "O"
    OnlineProvider.NVIDIA_NIM -> "N"
    OnlineProvider.HUGGING_FACE -> "H"
}

@Composable
fun AiSettingsScreen(onBack: () -> Unit, viewModel: ModelsViewModel = hiltViewModel()) {
    val onlineConfig by viewModel.onlineCorrectionConfig.collectAsState()
    val modelAvailability by viewModel.modelAvailability.collectAsState()
    val isRefreshingModels by viewModel.isRefreshingModels.collectAsState()

    val activeProvider = viewModel.onlineModels.find { it.id == onlineConfig.modelId }?.provider

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.ai_settings_title), style = MaterialTheme.typography.titleLarge)
                        Text(
                            stringResource(R.string.ai_settings_subtitle),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            items(OnlineProvider.entries) { provider ->
                ProviderCard(
                    provider = provider,
                    isActive = onlineConfig.enabled && activeProvider == provider,
                    activeModelId = onlineConfig.modelId,
                    modelAvailability = modelAvailability,
                    isRefreshingModels = isRefreshingModels,
                    models = viewModel.onlineModels.filter { it.provider == provider },
                    apiKeyFor = viewModel::apiKeyFor,
                    onToggle = { turnedOn ->
                        if (turnedOn) {
                            val defaultModel = viewModel.onlineModels.filter { it.provider == provider }
                                .firstOrNull { it.id == onlineConfig.modelId } ?: viewModel.onlineModels
                                .firstOrNull { it.provider == provider }
                            if (defaultModel != null) viewModel.enableProvider(provider, defaultModel.id)
                        } else {
                            viewModel.disableOnlineCorrection()
                        }
                    },
                    onSelectModel = { modelId -> viewModel.enableProvider(provider, modelId) },
                    onSaveApiKey = { key -> viewModel.setApiKeyFor(provider, key) },
                    onRefresh = { viewModel.refreshModels(provider, viewModel.apiKeyFor(provider)) }
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: OnlineProvider,
    isActive: Boolean,
    activeModelId: String?,
    modelAvailability: Map<String, Boolean>,
    isRefreshingModels: Boolean,
    models: List<OnlineModelInfo>,
    apiKeyFor: (OnlineProvider) -> String,
    onToggle: (Boolean) -> Unit,
    onSelectModel: (String) -> Unit,
    onSaveApiKey: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .aspectRatio(1f)
                        .background(providerAvatarColor(provider), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        providerAvatarLabel(provider),
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                    Text(provider.displayName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(if (isActive) R.string.ai_settings_enabled else R.string.ai_settings_disabled),
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        if (models.size == 1) {
                            stringResource(R.string.ai_settings_model_count, models.size)
                        } else {
                            stringResource(R.string.ai_settings_model_count_plural, models.size)
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Switch(checked = isActive, onCheckedChange = onToggle)
            }

            Column(modifier = Modifier.padding(top = 8.dp)) {
                models.forEach { model ->
                    val mark = when (modelAvailability[model.id]) {
                        true -> " ✓"
                        false -> " ✗"
                        null -> ""
                    }
                    val selected = isActive && activeModelId == model.id
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "${model.displayName}$mark",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                model.note,
                                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content)
                            )
                        }
                        if (!selected) {
                            TextButton(onClick = { onSelectModel(model.id) }) {
                                Text(stringResource(R.string.ai_settings_use))
                            }
                        }
                    }
                }
            }

            if (isActive) {
                var apiKeyField by remember(provider) { mutableStateOf(apiKeyFor(provider)) }
                var showKey by remember(provider) { mutableStateOf(false) }

                Text(
                    stringResource(R.string.settings_online_correction_api_key),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
                OutlinedTextField(
                    value = apiKeyField,
                    onValueChange = { apiKeyField = it; onSaveApiKey(it) },
                    visualTransformation = if (showKey) androidx.compose.ui.text.input.VisualTransformation.None
                        else PasswordVisualTransformation(),
                    trailingIcon = {
                        Row {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                    contentDescription = null
                                )
                            }
                            IconButton(onClick = { clipboardManager.setText(AnnotatedString(apiKeyField)) }) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.primaryContainer,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp)
                        .clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(provider.keyPageUrl)))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                        Text(
                            stringResource(R.string.ai_settings_get_key_banner, provider.displayName),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.settings_online_correction_get_key), style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Filled.ChevronRight, contentDescription = null)
                    }
                }

                Row(
                    modifier = Modifier.padding(top = 12.dp).clickable(onClick = onRefresh),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isRefreshingModels) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Text(
                        stringResource(R.string.settings_online_correction_refresh),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
            }
        }
    }
}
