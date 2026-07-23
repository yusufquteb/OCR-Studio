package com.ocrstudio.app.ui.export

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.core.common.ExportFormat
import com.ocrstudio.core.database.entity.BookJob
import com.ocrstudio.core.database.entity.ExportRecord
import kotlinx.coroutines.launch

@Composable
fun ExportScreen(viewModel: ExportViewModel = hiltViewModel()) {
    val jobs by viewModel.jobs.collectAsState()
    val history by viewModel.history.collectAsState()
    val scope = rememberCoroutineScope()

    var selectedJob by remember { mutableStateOf<BookJob?>(null) }
    var selectedFormat by remember { mutableStateOf(ExportFormat.JSON) }
    var preview by remember { mutableStateOf<ExportPreview?>(null) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val job = selectedJob
        if (uri != null && job != null) {
            viewModel.export(job.id, selectedFormat, uri) { }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.export_title)) }) }) { padding ->
        if (jobs.isEmpty()) {
            com.ocrstudio.core.ui.components.EmptyState(
                icon = androidx.compose.material.icons.Icons.Filled.Upload,
                title = stringResource(R.string.export_empty_title),
                subtitle = stringResource(R.string.export_empty_subtitle),
                modifier = Modifier.padding(padding)
            )
            return@Scaffold
        }
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Text(stringResource(R.string.export_select_job), style = MaterialTheme.typography.labelMedium)
            LazyColumn(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                items(jobs, key = { it.id }) { job ->
                    FilterChip(
                        selected = selectedJob?.id == job.id,
                        onClick = { selectedJob = job; preview = null },
                        label = {
                            Text(
                                job.title,
                                style = MaterialTheme.typography.labelLarge.copy(textDirection = TextDirection.Content)
                            )
                        },
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
            }

            Text(stringResource(R.string.export_select_format), style = MaterialTheme.typography.labelMedium)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                ExportFormat.entries.forEach { format ->
                    FilterChip(
                        selected = selectedFormat == format,
                        onClick = { selectedFormat = format; preview = null },
                        label = { Text(format.name) },
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }
            }

            Button(
                onClick = {
                    val job = selectedJob ?: return@Button
                    scope.launch { preview = viewModel.generatePreview(job.id, selectedFormat) }
                },
                enabled = selectedJob != null,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
            ) { Text(stringResource(R.string.export_preview)) }

            preview?.let { p ->
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.export_preview_summary, p.pageCount, selectedFormat.name),
                            style = MaterialTheme.typography.labelMedium
                        )
                        p.previewText?.let { text ->
                            Text(
                                text,
                                style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Content),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            Button(
                onClick = {
                    val job = selectedJob ?: return@Button
                    createDocumentLauncher.launch("${job.title}.${selectedFormat.extension}")
                },
                enabled = selectedJob != null,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) { Text(stringResource(R.string.export_start)) }

            Text(stringResource(R.string.export_history), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 24.dp))
            LazyColumn {
                items(history, key = { it.id }) { record -> ExportHistoryItem(record) }
            }
        }
    }
}

@Composable
private fun ExportHistoryItem(record: ExportRecord) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text("${record.format.name} · ${record.pageCount} pages", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
