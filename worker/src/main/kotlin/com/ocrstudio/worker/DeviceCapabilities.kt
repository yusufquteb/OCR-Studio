package com.ocrstudio.worker

import android.app.ActivityManager
import android.content.Context
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.LlmModelCatalog
import com.ocrstudio.core.common.LlmModelInfo
import javax.inject.Inject

/** Gates the Models screen's LLM list by total device RAM so unusable models are hidden. */
class DeviceCapabilities @Inject constructor(
    @AppContext private val context: Context
) {
    fun totalRamMb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)
        return (info.totalMem / (1024 * 1024)).toInt()
    }

    fun availableLlmModels(): List<LlmModelInfo> = LlmModelCatalog.availableFor(totalRamMb())
}
