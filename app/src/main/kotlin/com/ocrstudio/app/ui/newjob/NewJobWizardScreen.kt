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
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ocrstudio.app.R
import com.ocrstudio.core.common.DpiPreset
import kotlinx.coroutines.launch

@Composable
fun NewJobWizardScreen(
    onDone: (String) -> Unit,
    onNavigateToModels: () -> Unit,
    viewModel: NewJobWizardViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsState()
    val pdfName by viewModel.pdfDisplayName.collectAsState()
    val preview by viewModel.previewBitmap.collectAsState()
    val availableEngines by viewModel.availableEngineIds.collectAsState()
    val currentStep by viewModel.currentStep.collectAsState()
    val pageCount by viewModel.pageCount.collectAsState()
    val scope = rememberCoroutineScope()
    val noEngineAvailable = availableEngines.isEmpty()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshAvailableEngines()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.new_job_title)) }) }) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
            LinearProgressIndicator(
                progress = { (currentStep + 1) / WIZARD_STEP_COUNT.toFloat() },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            Text(
                stepLabel(currentStep),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                when (currentStep) {
                    0 -> fileStep(pdfName, pageCount, form, viewModel)
                    1 -> optionsStep(form, viewModel)
                    2 -> engineStep(form, availableEngines, noEngineAvailable, onNavigateToModels, viewModel)
                    3 -> reviewStep(form, preview, viewModel)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = if (currentStep > 0) Arrangement.SpaceBetween else Arrangement.End
            ) {
                if (currentStep > 0) {
                    OutlinedButton(onClick = { viewModel.previousStep() }) {
                        Text(stringResource(R.string.new_job_back))
                    }
                }

                if (currentStep < WIZARD_STEP_COUNT - 1) {
                    Button(onClick = { viewModel.nextStep() }) {
                        Text(stringResource(R.string.new_job_next))
                    }
                } else {
                    Button(
                        onClick = {
                            scope.launch {
                                val jobId = viewModel.createAndStartJob()
                                if (jobId != null) onDone(jobId)
                            }
                        },
                        enabled = !noEngineAvailable
                    ) { Text(stringResource(R.string.new_job_start)) }
                }
            }
        }
    }
}

@Composable
private fun stepLabel(step: Int): String = when (step) {
    0 -> stringResource(R.string.new_job_step_file)
    1 -> stringResource(R.string.new_job_step_options)
    2 -> stringResource(R.string.new_job_step_engine)
    else -> stringResource(R.string.new_job_step_review)
}

private fun androidx.compose.foundation.lazy.LazyListScope.fileStep(
    pdfName: String?,
    pageCount: Int?,
    form: NewJobFormState,
    viewModel: NewJobWizardViewModel
) {
    item {
        Text(
            pdfName ?: "No PDF selected",
            style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Content)
        )
        // Rtl LocalLayoutDirection so the (Arabic) book title's cursor and wrapping
        // behave correctly, not just its bidi run order.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            OutlinedTextField(
                value = form.title,
                onValueChange = { value -> viewModel.update { it.copy(title = value) } },
                label = { Text(stringResource(R.string.new_job_book_title)) },
                textStyle = androidx.compose.ui.text.TextStyle.Default.copy(textDirection = TextDirection.Content),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            )
        }

        if (pageCount != null && pageCount > LARGE_PDF_PAGE_THRESHOLD) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(
                    stringResource(
                        R.string.new_job_large_pdf_warning,
                        pageCount,
                        viewModel.estimatedSecondsForPageCount(pageCount) / 60
                    ),
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.optionsStep(
    form: NewJobFormState,
    viewModel: NewJobWizardViewModel
) {
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
}

private fun androidx.compose.foundation.lazy.LazyListScope.engineStep(
    form: NewJobFormState,
    availableEngines: List<String>,
    noEngineAvailable: Boolean,
    onNavigateToModels: () -> Unit,
    viewModel: NewJobWizardViewModel
) {
    item {
        Text(stringResource(R.string.new_job_ocr_engine), style = MaterialTheme.typography.labelMedium)
        if (noEngineAvailable) {
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        stringResource(R.string.new_job_no_engine_warning),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(onClick = onNavigateToModels) {
                        Text(stringResource(R.string.new_job_go_to_models))
                    }
                }
            }
        } else {
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
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.reviewStep(
    form: NewJobFormState,
    preview: android.graphics.Bitmap?,
    viewModel: NewJobWizardViewModel
) {
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
}

@Composable
private fun dpiPresetLabel(preset: DpiPreset): String = when (preset) {
    DpiPreset.STANDARD -> stringResource(R.string.new_job_dpi_standard)
    DpiPreset.OLD_BOOK -> stringResource(R.string.new_job_dpi_old_book)
    DpiPreset.MAX -> stringResource(R.string.new_job_dpi_max)
}
