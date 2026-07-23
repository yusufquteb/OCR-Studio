package com.ocrstudio.app.navigation

import androidx.lifecycle.ViewModel
import com.ocrstudio.worker.JobRecoveryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/** Thin bridge so Compose can observe app-level, non-screen-scoped signals -- currently just
 *  which jobs (if any) JobRecoveryManager silently re-enqueued this launch, so the UI can
 *  surface a one-time "resuming your last job" notice instead of that happening invisibly. */
@HiltViewModel
class AppSignalsViewModel @Inject constructor(
    private val jobRecoveryManager: JobRecoveryManager
) : ViewModel() {
    val recoveredJobIds: StateFlow<List<String>> = jobRecoveryManager.recoveredJobIds

    fun clearRecoveredJobIds() = jobRecoveryManager.clearRecoveredJobIds()
}
