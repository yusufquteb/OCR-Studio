package com.ocrstudio.engine.export

import android.net.Uri
import com.ocrstudio.core.common.ExportFormat

interface ExportPlugin {
    val format: ExportFormat

    /** Streams job [jobId]'s pages to [destination] (an ACTION_CREATE_DOCUMENT SAF Uri). Returns page count written. */
    suspend fun export(jobId: String, destination: Uri): Int
}
