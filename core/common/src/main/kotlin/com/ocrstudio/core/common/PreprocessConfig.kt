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
    // Off by default: Tesseract 4/5's LSTM engine does its own internal binarization and is
    // trained on grayscale scans; a hard black/white threshold ahead of it tends to break the
    // thin connecting strokes and diacritics in Arabic script rather than help, which is a
    // documented cause of accuracy regressions. Leave it available for genuinely degraded scans
    // (very uneven lighting, heavy yellowing) where thresholding does more good than harm.
    val binarization: Boolean = false,
    val binarizationMethod: BinarizationMethod = BinarizationMethod.ADAPTIVE_GAUSSIAN,
    val adaptiveBlockSize: Int = 31,
    val adaptiveC: Double = 15.0,
    val sauvolaWindowSize: Int = 25,
    val sauvolaK: Double = 0.2,
    val despeckle: Boolean = false,
    val despeckleKernelSize: Int = 2
) {
    companion object {
        val STANDARD = PreprocessConfig()

        /** "Old book" preset: stronger denoise + Sauvola binarization for degraded scans. */
        val OLD_BOOK = PreprocessConfig(
            denoiseH = 15f,
            binarization = true,
            binarizationMethod = BinarizationMethod.SAUVOLA,
            despeckle = true
        )
    }
}

enum class DpiPreset(val dpi: Int) {
    STANDARD(300),
    OLD_BOOK(400),
    MAX(600)
}
