package com.ocrstudio.app.ui.jobs

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.ocrstudio.app.R
import com.ocrstudio.app.ui.library.JobListItem
import com.ocrstudio.app.ui.library.LibraryViewModel
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.ui.components.EmptyState

/** Shows every job that isn't finished yet (queued/running/paused/failed) -- the queue view
 *  complementing Home's finished-books library. Reuses [LibraryViewModel] since job state is
 *  shared, not screen-specific. */
@Composable
fun JobsScreen(
    onOpenJob: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel()
) {
    val allJobs by viewModel.jobs.collectAsState()
    val activeJobs = allJobs.filter { it.status != JobStatus.DONE }
    val availableEngines by viewModel.availableEngineIds.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.jobs_title)) }) }) { padding ->
        if (activeJobs.isEmpty()) {
            EmptyState(
                icon = Icons.Filled.PlayCircle,
                title = stringResource(R.string.jobs_empty_title),
                subtitle = stringResource(R.string.jobs_empty_subtitle),
                modifier = Modifier.padding(padding)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(activeJobs, key = { it.id }) { job ->
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
