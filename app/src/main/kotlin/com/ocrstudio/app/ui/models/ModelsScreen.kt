package com.ocrstudio.app.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.core.common.LlmModelInfo
import com.ocrstudio.worker.DownloadState

@Composable
fun ModelsScreen(viewModel: ModelsViewModel = hiltViewModel()) {
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.models_title)) }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                Text("OCR engines", style = MaterialTheme.typography.titleMedium)
                AssetCard(
                    title = "Tesseract Arabic",
                    subtitle = "Guaranteed OCR path (tessdata_best)",
                    statusFlow = viewModel.tesseractStatusFlow(),
                    onDownload = viewModel::downloadTesseract
                )
                AssetCard(
                    title = "PaddleOCR Arabic",
                    subtitle = "Second engine: detection + recognition + dictionary",
                    statusFlow = viewModel.paddleDetStatusFlow(),
                    onDownload = viewModel::downloadPaddle
                )
            }

            item {
                Text("Correction models", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
            }

            items(viewModel.availableLlmModels, key = { it.id }) { model ->
                LlmModelCard(model = model, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun AssetCard(
    title: String,
    subtitle: String,
    statusFlow: kotlinx.coroutines.flow.Flow<DownloadState>,
    onDownload: () -> Unit
) {
    val state by remember(statusFlow) { statusFlow }.collectAsState(initial = DownloadState.Idle)

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            StatusRow(state)
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onDownload) { Text(stringResource(R.string.models_download)) }
            }
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
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { viewModel.downloadLlmModel(model) }) { Text(stringResource(R.string.models_download)) }
            }
        }
    }
}

@Composable
private fun StatusRow(state: DownloadState) {
    when (state) {
        is DownloadState.InProgress -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
        is DownloadState.Completed -> Text("Downloaded", color = MaterialTheme.colorScheme.primary)
        is DownloadState.Failed -> Text("Failed: ${state.message}", color = MaterialTheme.colorScheme.error)
        DownloadState.Idle -> Unit
    }
}
