package com.ocrstudio.app.ui.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
    val allJobs by viewModel.jobs.collectAsState()
    val jobs = allJobs.filter { it.status == JobStatus.DONE }
    val availableEngines by viewModel.availableEngineIds.collectAsState()
    val batchAddMessage by viewModel.batchAddMessage.collectAsState()

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

    val pickPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.onPdfPicked(uri)
            onAddPdf()
        }
    }

    val pickMultiplePdfsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.onMultiplePdfsPicked(uris)
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = {
                    IconButton(onClick = { pickMultiplePdfsLauncher.launch(arrayOf("application/pdf")) }) {
                        Icon(Icons.Filled.LibraryAdd, contentDescription = stringResource(R.string.library_batch_add))
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pickPdfLauncher.launch(arrayOf("application/pdf")) },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.library_add_pdf)) }
            )
        },
        snackbarHost = {
            batchAddMessage?.let { message ->
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = { TextButton(onClick = viewModel::clearBatchAddMessage) { Text(stringResource(R.string.common_ok)) } }
                ) { Text(message) }
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
                        availableEngines = availableEngines,
                        onClick = { onOpenJob(job.id) },
                        onPause = { viewModel.pauseJob(job.id) },
                        onResume = { viewModel.resumeJob(job) },
                        onChangeEngine = { engineId -> viewModel.changeEngine(job.id, engineId) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun JobListItem(
    job: BookJob,
    availableEngines: List<String>,
    onClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onChangeEngine: (String) -> Unit
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
                // Rtl LocalLayoutDirection, not just textDirection = Content, so a long title
                // that wraps to a second line aligns and wraps from the right like Arabic text
                // should, instead of the ambient-LTR default.
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Text(
                        job.title,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                            .copy(textDirection = TextDirection.Content)
                    )
                }
                statusChip(job.status)
            }
            Text("${job.currentPage} / ${job.pageCount} pages")
            if (job.pageCount > 0) {
                LinearProgressIndicator(
                    progress = { job.currentPage.toFloat() / job.pageCount },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
            // The OCR engine is only safe to change while no batch for this job is currently
            // running (it's read once per batch at the top of BatchWorker.doWork()) -- offer
            // the picker for every non-RUNNING status, including a job that just FAILED because
            // its original engine had no downloaded model.
            if (job.status != JobStatus.RUNNING && job.status != JobStatus.DONE && availableEngines.isNotEmpty()) {
                var engineMenuExpanded by remember(job.id) { mutableStateOf(false) }
                Box(modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = { engineMenuExpanded = true }) {
                        Text("${stringResource(R.string.new_job_ocr_engine)}: ${job.ocrEngineId}")
                    }
                    DropdownMenu(expanded = engineMenuExpanded, onDismissRequest = { engineMenuExpanded = false }) {
                        availableEngines.forEach { engineId ->
                            DropdownMenuItem(
                                text = { Text(engineId) },
                                onClick = { engineMenuExpanded = false; onChangeEngine(engineId) }
                            )
                        }
                    }
                }
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
internal fun statusChip(status: JobStatus) {
    val (labelRes, tone) = when (status) {
        JobStatus.QUEUED -> R.string.job_status_queued to ChipTone.NEUTRAL
        JobStatus.RUNNING -> R.string.job_status_running to ChipTone.NEUTRAL
        JobStatus.PAUSED -> R.string.job_status_paused to ChipTone.WARNING
        JobStatus.DONE -> R.string.job_status_done to ChipTone.SUCCESS
        JobStatus.FAILED -> R.string.job_status_failed to ChipTone.ERROR
    }
    StatusChip(label = stringResource(labelRes), tone = tone)
}
