package com.ocrstudio.app.ui.models

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.core.common.LlmModelInfo
import com.ocrstudio.core.common.OnlineModelInfo
import com.ocrstudio.core.common.OnlineProvider
import com.ocrstudio.worker.DownloadState

@Composable
fun ModelsScreen(viewModel: ModelsViewModel = hiltViewModel()) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.models_title)) }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                Text(stringResource(R.string.models_section_offline), style = MaterialTheme.typography.titleLarge)
            }

            item {
                Text("OCR engines", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                AssetCard(
                    title = "Tesseract Arabic",
                    subtitle = "Guaranteed OCR path (tessdata_best)",
                    statusFlow = viewModel.tesseractStatusFlow(),
                    onDownload = viewModel::downloadTesseract,
                    onPause = viewModel::pauseTesseract,
                    onCancel = viewModel::cancelTesseract,
                    onDelete = viewModel::deleteTesseract
                )
                AssetCard(
                    title = "PaddleOCR Arabic",
                    subtitle = "Second engine: detection + recognition + dictionary",
                    statusFlow = viewModel.paddleDetStatusFlow(),
                    onDownload = viewModel::downloadPaddle,
                    onPause = viewModel::pausePaddle,
                    onCancel = viewModel::cancelPaddle,
                    onDelete = viewModel::deletePaddle
                )
            }

            item {
                Text("Correction models", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            }

            items(viewModel.availableLlmModels, key = { it.id }) { model ->
                LlmModelCard(model = model, viewModel = viewModel)
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                Text(stringResource(R.string.models_section_online), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.settings_online_correction_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            item { OnlineCorrectionSection(viewModel) }
        }
    }
}

@Composable
private fun OnlineCorrectionSection(viewModel: ModelsViewModel) {
    val onlineConfig by viewModel.onlineCorrectionConfig.collectAsState()
    val modelAvailability by viewModel.modelAvailability.collectAsState()
    val isRefreshingModels by viewModel.isRefreshingModels.collectAsState()
    val context = LocalContext.current

    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.settings_online_correction_enable))
            Switch(checked = onlineConfig.enabled, onCheckedChange = viewModel::setOnlineCorrectionEnabled)
        }

        val selectedModel: OnlineModelInfo? = viewModel.onlineModels.find { it.id == onlineConfig.modelId }
        var selectedProvider by remember(selectedModel) {
            mutableStateOf(selectedModel?.provider ?: OnlineProvider.GOOGLE_AI_STUDIO)
        }

        LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            items(OnlineProvider.entries) { provider ->
                FilterChip(
                    selected = selectedProvider == provider,
                    onClick = { selectedProvider = provider },
                    label = { Text(provider.displayName) },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }

        val providerModels = viewModel.onlineModels.filter { it.provider == selectedProvider }
        val availableCount = providerModels.count { modelAvailability[it.id] == true }
        val checkedCount = providerModels.count { modelAvailability.containsKey(it.id) }

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(selectedProvider.keyPageUrl)))
            }) { Text(stringResource(R.string.settings_online_correction_get_key)) }

            OutlinedButton(
                onClick = { viewModel.refreshModels(selectedProvider, onlineConfig.apiKey) },
                enabled = !isRefreshingModels
            ) {
                if (isRefreshingModels) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp).padding(end = 8.dp),
                        strokeWidth = 2.dp
                    )
                }
                Text(stringResource(R.string.settings_online_correction_refresh))
            }
        }
        if (checkedCount > 0) {
            Text(
                stringResource(R.string.settings_online_correction_available_count, availableCount, providerModels.size),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }

        var modelMenuExpanded by remember { mutableStateOf(false) }
        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            TextButton(onClick = { modelMenuExpanded = true }) {
                Text(
                    "${stringResource(R.string.settings_online_correction_model)}: " +
                        (selectedModel?.displayName ?: "—")
                )
            }
            DropdownMenu(expanded = modelMenuExpanded, onDismissRequest = { modelMenuExpanded = false }) {
                providerModels.forEach { model ->
                    val mark = when (modelAvailability[model.id]) {
                        true -> " ✓"
                        false -> " ✗"
                        null -> ""
                    }
                    DropdownMenuItem(
                        text = { Text("${model.displayName}$mark") },
                        onClick = { modelMenuExpanded = false; viewModel.setOnlineModelId(model.id) }
                    )
                }
            }
        }
        selectedModel?.let {
            Text(it.note, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))
        }

        var apiKeyField by remember(onlineConfig.apiKey) { mutableStateOf(onlineConfig.apiKey) }
        OutlinedTextField(
            value = apiKeyField,
            onValueChange = { apiKeyField = it },
            label = { Text(stringResource(R.string.settings_online_correction_api_key)) },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        )
        Button(
            onClick = { viewModel.setOnlineApiKey(apiKeyField) },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.settings_online_correction_save)) }
    }
}

@Composable
private fun AssetCard(
    title: String,
    subtitle: String,
    statusFlow: kotlinx.coroutines.flow.Flow<DownloadState>,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    val state by remember(statusFlow) { statusFlow }.collectAsState(initial = DownloadState.Idle)

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            StatusRow(state)
            DownloadActions(state, onDownload, onPause, onCancel, onDelete)
        }
    }
}

@Composable
private fun LlmModelCard(model: LlmModelInfo, viewModel: ModelsViewModel) {
    val state by remember(model.id) { viewModel.llmModelStatusFlow(model) }.collectAsState(initial = DownloadState.Idle)

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium)
            Text("${model.approxSizeMb} MB · ${model.licenseNote}", style = MaterialTheme.typography.bodyMedium)
            StatusRow(state)
            DownloadActions(
                state = state,
                onDownload = { viewModel.downloadLlmModel(model) },
                onPause = { viewModel.pauseLlmModel(model) },
                onCancel = { viewModel.cancelLlmModel(model) },
                onDelete = { viewModel.deleteLlmModel(model) }
            )
        }
    }
}

@Composable
private fun DownloadActions(
    state: DownloadState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
        when (state) {
            is DownloadState.InProgress -> {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.models_cancel)) }
                Button(onClick = onPause) { Text(stringResource(R.string.models_pause)) }
            }
            is DownloadState.Failed -> {
                // "Paused" is surfaced as Failed(message="Paused") by AssetDownloadManager.observe
                // since WorkManager has no distinct paused state -- resuming just re-downloads,
                // and DownloadWorker picks up the .part file left on disk automatically.
                Button(onClick = onDownload) {
                    Text(stringResource(if (state.message == "Paused") R.string.models_resume else R.string.models_retry))
                }
            }
            is DownloadState.Completed -> {
                IconButton(onClick = onDelete) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.models_delete)
                    )
                }
            }
            DownloadState.Idle -> Button(onClick = onDownload) { Text(stringResource(R.string.models_download)) }
        }
    }
}

@Composable
private fun StatusRow(state: DownloadState) {
    when (state) {
        is DownloadState.InProgress -> Column(Modifier.padding(top = 8.dp)) {
            LinearProgressIndicator(
                progress = { if (state.percent >= 0) state.percent / 100f else 0f },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (state.percent >= 0) "${state.percent}%" else "Downloading…",
                style = MaterialTheme.typography.bodySmall
            )
        }
        is DownloadState.Completed -> Text("Downloaded", color = MaterialTheme.colorScheme.primary)
        is DownloadState.Failed -> if (state.message != "Paused") {
            Text("Failed: ${state.message}", color = MaterialTheme.colorScheme.error)
        }
        DownloadState.Idle -> Unit
    }
}
