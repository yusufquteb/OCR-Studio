package com.ocrstudio.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.ocrstudio.engine.image.OpenCvInitializer
import com.ocrstudio.worker.JobRecoveryManager
import com.ocrstudio.worker.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class OcrStudioApplication : Application(), Configuration.Provider {

    @Inject lateinit var hiltWorkerFactory: HiltWorkerFactory
    @Inject lateinit var jobRecoveryManager: JobRecoveryManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(hiltWorkerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        OpenCvInitializer.ensureInitialized()
        NotificationHelper.ensureChannels(this)

        // Resumes any job left RUNNING whose WorkManager chain died with the previous process.
        CoroutineScope(Dispatchers.Default).launch {
            jobRecoveryManager.recoverIncompleteJobs()
        }
    }
}
