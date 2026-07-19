package com.ocrstudio.engine.ocr.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * CRNN/SVTR recognition: perspective-crop each detected box to height 48 (aspect-preserving,
 * padded width), normalize to [-0.5, 0.5], run rec_ar.onnx, then CTC greedy-decode using
 * dict_ar.txt (index 0 reserved as CTC blank, a space token appended after the last dict entry).
 */
internal object Recognition {
    private const val TARGET_HEIGHT = 48
    private const val MAX_WIDTH = 800
    private const val MIN_WIDTH = 16

    fun recognize(
        env: OrtEnvironment,
        session: OrtSession,
        sourceRgb: Mat,
        box: TextBox,
        vocab: List<String>
    ): RecognizedBox {
        val ordered = orderPoints(box.points)
        val (tl, tr, br, bl) = ordered

        val widthTop = distance(tl, tr)
        val widthBottom = distance(bl, br)
        val heightLeft = distance(tl, bl)
        val heightRight = distance(tr, br)
        val srcWidth = maxOf(widthTop, widthBottom).coerceAtLeast(1f)
        val srcHeight = maxOf(heightLeft, heightRight).coerceAtLeast(1f)

        var targetWidth = (srcWidth * TARGET_HEIGHT / srcHeight).roundToInt()
        targetWidth = targetWidth.coerceIn(MIN_WIDTH, MAX_WIDTH)

        val srcPoints = MatOfPoint2f(
            Point(tl.first.toDouble(), tl.second.toDouble()),
            Point(tr.first.toDouble(), tr.second.toDouble()),
            Point(br.first.toDouble(), br.second.toDouble()),
            Point(bl.first.toDouble(), bl.second.toDouble())
        )
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((targetWidth - 1).toDouble(), 0.0),
            Point((targetWidth - 1).toDouble(), (TARGET_HEIGHT - 1).toDouble()),
            Point(0.0, (TARGET_HEIGHT - 1).toDouble())
        )
        val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        val crop = Mat()
        Imgproc.warpPerspective(sourceRgb, crop, transform, Size(targetWidth.toDouble(), TARGET_HEIGHT.toDouble()))
        srcPoints.release(); dstPoints.release(); transform.release()

        val data = ByteArray((crop.total() * crop.elemSize()).toInt())
        crop.get(0, 0, data)
        crop.release()

        val buffer = FloatBuffer.allocate(3 * TARGET_HEIGHT * targetWidth)
        val planes = Array(3) { FloatArray(TARGET_HEIGHT * targetWidth) }
        for (y in 0 until TARGET_HEIGHT) {
            for (x in 0 until targetWidth) {
                val base = (y * targetWidth + x) * 3
                for (c in 0 until 3) {
                    val v = (data[base + c].toInt() and 0xFF) / 255f - 0.5f
                    planes[c][y * targetWidth + x] = v
                }
            }
        }
        planes.forEach { buffer.put(it) }
        buffer.rewind()

        val inputName = session.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(
            env, buffer, longArrayOf(1, 3, TARGET_HEIGHT.toLong(), targetWidth.toLong())
        )
        val (text, confidence) = inputTensor.use {
            session.run(mapOf(inputName to it)).use { result ->
                ctcDecode(result[0].value, vocab)
            }
        }
        return RecognizedBox(box = box, text = text, confidence = confidence)
    }

    /**
     * Greedy CTC decode. Output is assumed post-softmax probabilities per timestep
     * (standard PaddleOCR rec export includes the softmax head), shape [1, T, vocabSize].
     */
    @Suppress("UNCHECKED_CAST")
    private fun ctcDecode(rawOutput: Any, vocab: List<String>): Pair<String, Float> {
        val batch = rawOutput as Array<*>
        val timeSteps = batch[0] as Array<FloatArray>

        val sb = StringBuilder()
        var lastIndex = -1
        var confSum = 0f
        var confCount = 0

        for (step in timeSteps) {
            var bestIndex = 0
            var bestScore = step[0]
            for (i in 1 until step.size) {
                if (step[i] > bestScore) {
                    bestScore = step[i]
                    bestIndex = i
                }
            }
            if (bestIndex != 0 && bestIndex != lastIndex) {
                val char = vocab.getOrNull(bestIndex - 1)
                if (char != null) {
                    sb.append(char)
                    confSum += bestScore
                    confCount++
                }
            }
            lastIndex = bestIndex
        }

        val confidence = if (confCount > 0) confSum / confCount else 0f
        return sb.toString() to confidence
    }

    private fun distance(a: Pair<Float, Float>, b: Pair<Float, Float>): Float =
        hypot((a.first - b.first).toDouble(), (a.second - b.second).toDouble()).toFloat()

    /** Reorders 4 arbitrary corner points into (topLeft, topRight, bottomRight, bottomLeft). */
    private fun orderPoints(points: List<Pair<Float, Float>>): Quad {
        val sorted = points.sortedBy { it.first + it.second }
        val tl = sorted.first()
        val br = sorted.last()
        val remaining = points - tl - br
        val tr = if (remaining.size == 2) remaining.minByOrNull { it.second } ?: remaining[0] else remaining.getOrElse(0) { tl }
        val bl = remaining.firstOrNull { it != tr } ?: br
        return Quad(tl, tr, br, bl)
    }

    private data class Quad(
        val topLeft: Pair<Float, Float>,
        val topRight: Pair<Float, Float>,
        val bottomRight: Pair<Float, Float>,
        val bottomLeft: Pair<Float, Float>
    )
}
