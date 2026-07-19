package com.ocrstudio.engine.ocr.onnx

import com.ocrstudio.core.common.OcrPage
import com.ocrstudio.core.common.OcrWord

/**
 * Groups recognized boxes into lines by vertical (y-range) IoU >= 0.5, orders lines
 * top-to-bottom, and orders boxes within each line right-to-left (RTL, descending x-center).
 * Page mean confidence is the box-confidence average weighted by recognized text length.
 */
internal object LineAssembly {
    private const val Y_IOU_THRESHOLD = 0.5

    fun assemble(boxes: List<RecognizedBox>, engineId: String): OcrPage {
        if (boxes.isEmpty()) {
            return OcrPage(text = "", words = emptyList(), meanConfidence = 0f, engineId = engineId)
        }

        val remaining = boxes.toMutableList()
        val lines = mutableListOf<MutableList<RecognizedBox>>()

        while (remaining.isNotEmpty()) {
            val seed = remaining.removeAt(0)
            val line = mutableListOf(seed)
            val iterator = remaining.iterator()
            while (iterator.hasNext()) {
                val candidate = iterator.next()
                if (line.any { yIou(it.box, candidate.box) >= Y_IOU_THRESHOLD }) {
                    line.add(candidate)
                    iterator.remove()
                }
            }
            lines.add(line)
        }

        lines.sortBy { line -> line.minOf { it.box.minY } }
        lines.forEach { line -> line.sortByDescending { it.box.centerX } }

        val textBuilder = StringBuilder()
        val words = mutableListOf<OcrWord>()
        var weightedConfidenceSum = 0f
        var totalLength = 0

        lines.forEachIndexed { lineIndex, line ->
            if (lineIndex > 0) textBuilder.append("\n")
            line.forEachIndexed { boxIndex, recognized ->
                if (boxIndex > 0) textBuilder.append(" ")
                textBuilder.append(recognized.text)

                words.add(
                    OcrWord(
                        text = recognized.text,
                        confidence = recognized.confidence,
                        left = recognized.box.minX.toInt(),
                        top = recognized.box.minY.toInt(),
                        right = recognized.box.maxX.toInt(),
                        bottom = recognized.box.maxY.toInt()
                    )
                )
                val length = recognized.text.length.coerceAtLeast(1)
                weightedConfidenceSum += recognized.confidence * length
                totalLength += length
            }
        }

        val meanConfidence = if (totalLength > 0) weightedConfidenceSum / totalLength else 0f
        return OcrPage(text = textBuilder.toString(), words = words, meanConfidence = meanConfidence, engineId = engineId)
    }

    private fun yIou(a: TextBox, b: TextBox): Double {
        val top = maxOf(a.minY, b.minY)
        val bottom = minOf(a.maxY, b.maxY)
        val intersection = (bottom - top).coerceAtLeast(0f)
        val union = (a.maxY - a.minY) + (b.maxY - b.minY) - intersection
        return if (union <= 0f) 0.0 else (intersection / union).toDouble()
    }
}
