package com.ocrstudio.engine.ocr.onnx

/** A detected text region: 4 corner points (clockwise from top-left) in original image pixel space. */
data class TextBox(
    val points: List<Pair<Float, Float>>,
    val score: Float
) {
    val minX: Float get() = points.minOf { it.first }
    val maxX: Float get() = points.maxOf { it.first }
    val minY: Float get() = points.minOf { it.second }
    val maxY: Float get() = points.maxOf { it.second }
    val centerX: Float get() = (minX + maxX) / 2f
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
}

data class RecognizedBox(
    val box: TextBox,
    val text: String,
    val confidence: Float
)
