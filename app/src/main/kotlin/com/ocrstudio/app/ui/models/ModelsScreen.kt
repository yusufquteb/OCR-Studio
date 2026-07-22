package com.ocrstudio.app.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.core.common.CorrectorKind
import com.ocrstudio.core.common.LlmModelInfo
import com.ocrstudio.worker.DownloadState

@Composable
fun ModelsScreen(onOpenAiSettings: () -> Unit, viewModel: ModelsViewModel = hiltViewModel()) {
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
                OnlineCorrectionEntryCard(viewModel = viewModel, onOpenAiSettings = onOpenAiSettings)
            }
        }
    }
}

@Composable
private fun OnlineCorrectionEntryCard(viewModel: ModelsViewModel, onOpenAiSettings: () -> Unit) {
    val chain by viewModel.correctionChain.collectAsState()
    val onlineCount = remember(chain) { chain.count { it.kind == CorrectorKind.ONLINE } }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(stringResource(R.string.settings_online_correction_subtitle), style = MaterialTheme.typography.bodySmall)
                Text(
                    if (onlineCount == 1) {
                        stringResource(R.string.ai_settings_model_count, onlineCount)
                    } else if (onlineCount > 1) {
                        stringResource(R.string.ai_settings_model_count_plural, onlineCount)
                    } else {
                        stringResource(R.string.ai_settings_disabled)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            TextButton(onClick = onOpenAiSettings) {
                Text(stringResource(R.string.ai_settings_open))
                Icon(Icons.Filled.ChevronRight, contentDescription = null)
            }
        }
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
    val chain by viewModel.correctionChain.collectAsState()
    val inChain = remember(chain, model.id) { chain.any { it.kind == CorrectorKind.OFFLINE && it.modelId == model.id } }

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
            if (state is DownloadState.Completed) {
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = { viewModel.toggleOfflineInChain(model) },
                        enabled = inChain || !viewModel.isChainFull
                    ) {
                        Text(
                            stringResource(
                                if (inChain) R.string.ai_settings_remove_from_chain
                                else R.string.ai_settings_add_to_chain
                            )
                        )
                    }
                }
            }
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
