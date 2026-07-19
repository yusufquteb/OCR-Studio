package com.ocrstudio.engine.image

import org.opencv.android.OpenCVLoader

/** Call once (Application.onCreate) before any ImagePreprocessor use. Safe to call repeatedly. */
object OpenCvInitializer {
    @Volatile private var initialized = false

    fun ensureInitialized(): Boolean {
        if (initialized) return true
        initialized = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
        return initialized
    }
}
