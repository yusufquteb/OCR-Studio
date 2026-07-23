package com.ocrstudio.app.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.core.common.JobStatus

@Composable
fun JobProgressScreen(
    jobId: String,
    onOpenReview: () -> Unit,
    onBack: () -> Unit,
    viewModel: JobProgressViewModel = hiltViewModel(
        creationCallback = { factory: JobProgressViewModel.Factory -> factory.create(jobId) }
    )
) {
    val state by viewModel.uiState.collectAsState()
    val job = state.job

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.progress_title)) }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            if (job == null) {
                Text("Loading…")
                return@Column
            }
            // This screen was missed by the earlier content-direction fix entirely -- job.title
            // was still rendered with the app's ambient (LTR) text direction and alignment.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Text(
                    job.title,
                    style = MaterialTheme.typography.titleLarge.copy(textDirection = TextDirection.Content)
                )
            }
            Text("Page ${job.currentPage} / ${job.pageCount}")
            LinearProgressIndicator(
                progress = { if (job.pageCount > 0) job.currentPage.toFloat() / job.pageCount else 0f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )
            state.currentTaskLabel?.let { label ->
                Text(stringResource(R.string.progress_current_task, label))
            }
            state.estimatedSecondsRemaining?.let { seconds ->
                Text(stringResource(R.string.progress_remaining, formatDuration(seconds)))
            }
            Text(stringResource(R.string.progress_errors, state.errorCount))

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                when (job.status) {
                    JobStatus.RUNNING -> Button(onClick = viewModel::pause) { Text(stringResource(R.string.progress_pause)) }
                    JobStatus.PAUSED, JobStatus.QUEUED, JobStatus.FAILED ->
                        Button(onClick = viewModel::resume) { Text(stringResource(R.string.progress_resume)) }
                    JobStatus.DONE -> Unit
                }
                Button(onClick = viewModel::cancel) { Text(stringResource(R.string.progress_cancel)) }
                Button(onClick = onOpenReview) { Text(stringResource(R.string.review_title)) }
            }
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
