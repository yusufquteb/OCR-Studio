package com.ocrstudio.app.ui.newjob

import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.core.common.DpiPreset
import kotlinx.coroutines.launch

@Composable
fun NewJobWizardScreen(
    onDone: (String) -> Unit,
    viewModel: NewJobWizardViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val pdfName by viewModel.pdfDisplayName.collectAsState()
    val preview by viewModel.previewBitmap.collectAsState()
    val availableEngines by viewModel.availableEngineIds.collectAsState()
    val scope = rememberCoroutineScope()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.new_job_title)) }) }) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                Text(pdfName ?: "No PDF selected", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = form.title,
                    onValueChange = { value -> viewModel.update { it.copy(title = value) } },
                    label = { Text(stringResource(R.string.new_job_book_title)) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                )
            }

            item {
                Text(stringResource(R.string.new_job_parser_profile), style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    viewModel.availableProfiles.forEach { profileId ->
                        FilterChip(
                            selected = form.profileId == profileId,
                            onClick = { viewModel.update { it.copy(profileId = profileId) } },
                            label = { Text(profileId) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }

            item {
                Text(stringResource(R.string.new_job_dpi_preset), style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    DpiPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = form.dpiPreset == preset,
                            onClick = { viewModel.update { it.copy(dpiPreset = preset) } },
                            label = { Text(dpiPresetLabel(preset)) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }

            item {
                Text("${stringResource(R.string.new_job_batch_size)}: ${form.batchSize}", style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = form.batchSize.toFloat(),
                    onValueChange = { value -> viewModel.update { it.copy(batchSize = value.toInt()) } },
                    valueRange = 10f..50f,
                    steps = 7
                )
            }

            item {
                Text(stringResource(R.string.new_job_ocr_engine), style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    availableEngines.forEach { engineId ->
                        FilterChip(
                            selected = form.ocrEngineId == engineId,
                            onClick = { viewModel.update { it.copy(ocrEngineId = engineId) } },
                            label = { Text(engineId) },
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.new_job_correction_mode))
                    Switch(
                        checked = form.useLlmCorrection,
                        onCheckedChange = { checked -> viewModel.update { it.copy(useLlmCorrection = checked) } }
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.new_job_keep_images))
                    Switch(
                        checked = form.keepImages,
                        onCheckedChange = { checked -> viewModel.update { it.copy(keepImages = checked) } }
                    )
                }
            }

            item {
                Button(
                    onClick = { viewModel.generatePreview() },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) { Text(stringResource(R.string.new_job_preview_step)) }
            }

            preview?.let { bitmap ->
                item {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            }

            item {
                Button(
                    onClick = {
                        scope.launch {
                            val jobId = viewModel.createAndStartJob()
                            if (jobId != null) onDone(jobId)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) { Text(stringResource(R.string.new_job_start)) }
            }
        }
    }
}

@Composable
private fun dpiPresetLabel(preset: DpiPreset): String = when (preset) {
    DpiPreset.STANDARD -> stringResource(R.string.new_job_dpi_standard)
    DpiPreset.OLD_BOOK -> stringResource(R.string.new_job_dpi_old_book)
    DpiPreset.MAX -> stringResource(R.string.new_job_dpi_max)
}
