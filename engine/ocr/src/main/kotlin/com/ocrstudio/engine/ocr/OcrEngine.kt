package com.ocrstudio.engine.ocr

import android.content.Context
import android.graphics.Bitmap
import com.ocrstudio.core.common.OcrConfig
import com.ocrstudio.core.common.OcrPage

interface OcrEngine {
    val id: String

    /** True once the engine's required model/asset files exist on disk. */
    suspend fun isAvailable(context: Context): Boolean

    suspend fun init(context: Context, config: OcrConfig)

    suspend fun recognize(bitmap: Bitmap): OcrPage

    fun close()
}
