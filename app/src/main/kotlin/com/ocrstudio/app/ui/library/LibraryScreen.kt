package com.ocrstudio.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.database.entity.BookJob
import com.ocrstudio.core.ui.components.ChipTone
import com.ocrstudio.core.ui.components.EmptyState
import com.ocrstudio.core.ui.components.StatusChip

@Composable
fun LibraryScreen(
    onAddPdf: () -> Unit,
    onOpenJob: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val jobs by viewModel.jobs.collectAsState()

    val pickPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.onPdfPicked(uri)
            onAddPdf()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.library_title)) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) }) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.library_add_pdf))
            }
        }
    ) { padding ->
        if (jobs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.LibraryBooks,
                title = stringResource(R.string.library_empty_title),
                subtitle = stringResource(R.string.library_empty_subtitle),
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(jobs, key = { it.id }) { job ->
                    JobListItem(
                        job = job,
                        onClick = { onOpenJob(job.id) },
                        onPause = { viewModel.pauseJob(job.id) },
                        onResume = { viewModel.resumeJob(job) }
                    )
                }
            }
        }
    }
}

@Composable
private fun JobListItem(
    job: BookJob,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { onClick() }
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    job.title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                        .copy(textDirection = TextDirection.Content)
                )
                statusChip(job.status)
            }
            Text("${job.currentPage} / ${job.pageCount} pages")
            if (job.pageCount > 0) {
                LinearProgressIndicator(
                    progress = { job.currentPage.toFloat() / job.pageCount },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when (job.status) {
                    JobStatus.RUNNING -> IconButton(onClick = onPause) {
                        Icon(Icons.Filled.Pause, contentDescription = stringResource(R.string.progress_pause))
                    }
                    JobStatus.PAUSED, JobStatus.QUEUED, JobStatus.FAILED -> IconButton(onClick = onResume) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.progress_resume))
                    }
                    JobStatus.DONE -> Unit
                }
            }
        }
    }
}

@Composable
private fun statusChip(status: JobStatus) {
    val (labelRes, tone) = when (status) {
        JobStatus.QUEUED -> R.string.job_status_queued to ChipTone.NEUTRAL
        JobStatus.RUNNING -> R.string.job_status_running to ChipTone.NEUTRAL
        JobStatus.PAUSED -> R.string.job_status_paused to ChipTone.WARNING
        JobStatus.DONE -> R.string.job_status_done to ChipTone.SUCCESS
        JobStatus.FAILED -> R.string.job_status_failed to ChipTone.ERROR
    }
    StatusChip(label = stringResource(labelRes), tone = tone)
}
