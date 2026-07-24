package com.ocrstudio.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ocrstudio.app.navigation.OcrStudioNavHost
import com.ocrstudio.core.ui.theme.OcrStudioTheme
import dagger.hilt.android.AndroidEntryPoint

private const val EXTRA_JOB_ID = "job_id"

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var pendingJobIdState = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingJobIdState.value = intent?.getStringExtra(EXTRA_JOB_ID)
        setContent {
            val pendingJobId by pendingJobIdState
            OcrStudioTheme {
                OcrStudioNavHost(
                    pendingJobId = pendingJobId,
                    onPendingJobIdConsumed = { pendingJobIdState.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingJobIdState.value = intent.getStringExtra(EXTRA_JOB_ID)
    }
}
