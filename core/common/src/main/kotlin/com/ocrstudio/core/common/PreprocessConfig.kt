package com.ocrstudio.core.common

enum class BinarizationMethod {
    ADAPTIVE_GAUSSIAN,
    SAUVOLA
}

/**
 * Toggles and parameters for the :engine:image preprocessing pipeline.
 * Persisted as part of a BookJob so re-runs/preview stay reproducible.
 */
data class PreprocessConfig(
    val grayscale: Boolean = true,
    val deskew: Boolean = true,
    val cropToContent: Boolean = true,
    val denoise: Boolean = true,
    val denoiseH: Float = 10f,
    val backgroundFlatten: Boolean = true,
    val backgroundBlurKernel: Int = 51,
    val binarization: Boolean = true,
    val binarizationMethod: BinarizationMethod = BinarizationMethod.ADAPTIVE_GAUSSIAN,
    val adaptiveBlockSize: Int = 31,
    val adaptiveC: Double = 15.0,
    val sauvolaWindowSize: Int = 25,
    val sauvolaK: Double = 0.2,
    val despeckle: Boolean = true,
    val despeckleKernelSize: Int = 2
) {
    companion object {
        val STANDARD = PreprocessConfig()

        /** "Old book" preset: stronger denoise + Sauvola binarization for degraded scans. */
        val OLD_BOOK = PreprocessConfig(
            denoiseH = 15f,
            binarizationMethod = BinarizationMethod.SAUVOLA
        )
    }
}

enum class DpiPreset(val dpi: Int) {
    STANDARD(300),
    OLD_BOOK(400),
    MAX(600)
}
