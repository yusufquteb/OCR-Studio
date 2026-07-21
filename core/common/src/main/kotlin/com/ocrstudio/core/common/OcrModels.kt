package com.ocrstudio.core.common

/** A single recognized word/token with its bounding box (image pixel space) and confidence [0,1]. */
data class OcrWord(
    val text: String,
    val confidence: Float,
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
)

/** Result of running one OcrEngine over a single page bitmap. */
data class OcrPage(
    val text: String,
    val words: List<OcrWord>,
    val meanConfidence: Float,
    val engineId: String
)

enum class PageSegmentationMode {
    AUTO,
    SINGLE_COLUMN,
    SINGLE_BLOCK
}

data class OcrConfig(
    val language: String = "ara",
    val psm: PageSegmentationMode = PageSegmentationMode.AUTO,
    val dataDir: String,
    // Tesseract's LSTM models are trained assuming ~300dpi source text; without telling it the
    // actual render DPI it falls back to guessing (often defaulting to a much lower value),
    // which visibly degrades accuracy on higher-resolution scans.
    val dpi: Int = 300
)
