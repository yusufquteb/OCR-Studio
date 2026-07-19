package com.ocrstudio.engine.ocr.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.nio.FloatBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * DB (Differentiable Binarization) text detection: resize -> normalize -> run det.onnx ->
 * binarize probability map at 0.3 -> contours -> unclip (ratio 1.5) -> filter by score.
 */
internal object Detection {
    private const val MAX_SIDE = 960
    private const val STRIDE = 32
    private const val PROB_THRESHOLD = 0.3
    private const val BOX_SCORE_THRESHOLD = 0.6
    private const val UNCLIP_RATIO = 1.5
    private const val MIN_BOX_SIZE = 4.0

    private val MEAN = floatArrayOf(0.485f, 0.456f, 0.406f)
    private val STD = floatArrayOf(0.229f, 0.224f, 0.225f)

    fun detect(env: OrtEnvironment, session: OrtSession, bitmap: Bitmap): List<TextBox> {
        val rgba = Mat()
        Utils.bitmapToMat(bitmap, rgba)
        val rgb = Mat()
        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
        rgba.release()

        val origW = rgb.cols()
        val origH = rgb.rows()

        val longSide = max(origW, origH)
        val scale = if (longSide > MAX_SIDE) MAX_SIDE.toFloat() / longSide else 1f
        var newW = (origW * scale).roundToInt().coerceAtLeast(STRIDE)
        var newH = (origH * scale).roundToInt().coerceAtLeast(STRIDE)
        newW = ((newW + STRIDE - 1) / STRIDE) * STRIDE
        newH = ((newH + STRIDE - 1) / STRIDE) * STRIDE

        val resized = Mat()
        Imgproc.resize(rgb, resized, Size(newW.toDouble(), newH.toDouble()))
        rgb.release()

        val inputBuffer = FloatBuffer.allocate(3 * newH * newW)
        val chwPlanes = Array(3) { FloatArray(newH * newW) }
        val data = ByteArray((resized.total() * resized.elemSize()).toInt())
        resized.get(0, 0, data)
        for (y in 0 until newH) {
            for (x in 0 until newW) {
                val base = (y * newW + x) * 3
                for (c in 0 until 3) {
                    val v = (data[base + c].toInt() and 0xFF) / 255f
                    chwPlanes[c][y * newW + x] = (v - MEAN[c]) / STD[c]
                }
            }
        }
        resized.release()
        chwPlanes.forEach { inputBuffer.put(it) }
        inputBuffer.rewind()

        val inputName = session.inputNames.iterator().next()
        val inputTensor = OnnxTensor.createTensor(env, inputBuffer, longArrayOf(1, 3, newH.toLong(), newW.toLong()))
        val boxes = inputTensor.use {
            session.run(mapOf(inputName to it)).use { result ->
                val output = result[0].value
                val probMap = extractProbMap(output, newH, newW)
                postProcess(probMap, newH, newW, origW.toFloat() / newW, origH.toFloat() / newH)
            }
        }
        return boxes
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractProbMap(rawOutput: Any, h: Int, w: Int): Array<FloatArray> {
        // Output is typically [1,1,H,W] or [1,H,W]; normalize to a simple 2D array.
        return when (rawOutput) {
            is Array<*> -> {
                var node: Any = rawOutput
                while (node is Array<*> && node.isNotEmpty() && node[0] is Array<*>) {
                    node = node[0] as Any
                }
                @Suppress("UNCHECKED_CAST")
                (node as Array<FloatArray>)
            }
            else -> Array(h) { FloatArray(w) }
        }
    }

    private fun postProcess(
        probMap: Array<FloatArray>,
        h: Int,
        w: Int,
        scaleX: Float,
        scaleY: Float
    ): List<TextBox> {
        val binary = Mat(h, w, CvType.CV_8UC1)
        val row = ByteArray(w)
        for (y in 0 until h) {
            for (x in 0 until w) {
                row[x] = if (probMap[y][x] >= PROB_THRESHOLD) 255.toByte() else 0
            }
            binary.put(y, 0, row)
        }

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        binary.release(); hierarchy.release()

        val result = mutableListOf<TextBox>()
        for (contour in contours) {
            val points2f = MatOfPoint2f()
            contour.convertTo(points2f, CvType.CV_32FC2)
            val rect = Imgproc.minAreaRect(points2f)
            points2f.release()

            if (rect.size.width < MIN_BOX_SIZE || rect.size.height < MIN_BOX_SIZE) continue

            val boxScore = averageScoreInRect(probMap, h, w, rect.boundingRect())
            if (boxScore < BOX_SCORE_THRESHOLD) continue

            val unclipped = unclip(rect, UNCLIP_RATIO)
            val vertices = arrayOfNulls<org.opencv.core.Point>(4)
            unclipped.points(vertices)

            val mapped = vertices.filterNotNull().map { (it.x.toFloat() * scaleX) to (it.y.toFloat() * scaleY) }
            if (mapped.size == 4) {
                result.add(TextBox(points = mapped, score = boxScore))
            }
        }
        return result
    }

    private fun averageScoreInRect(probMap: Array<FloatArray>, h: Int, w: Int, rect: org.opencv.core.Rect): Float {
        val x0 = rect.x.coerceIn(0, w - 1)
        val y0 = rect.y.coerceIn(0, h - 1)
        val x1 = (rect.x + rect.width).coerceIn(1, w)
        val y1 = (rect.y + rect.height).coerceIn(1, h)
        if (x1 <= x0 || y1 <= y0) return 0f
        var sum = 0.0
        var count = 0
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                sum += probMap[y][x]
                count++
            }
        }
        return if (count == 0) 0f else (sum / count).toFloat()
    }

    /** Expands a rotated rect outward by d = area * ratio / perimeter on each side (DB paper formula). */
    private fun unclip(rect: org.opencv.core.RotatedRect, ratio: Double): org.opencv.core.RotatedRect {
        val area = rect.size.width * rect.size.height
        val perimeter = 2 * (rect.size.width + rect.size.height)
        val distance = if (perimeter > 0) area * ratio / perimeter else 0.0
        val newSize = Size(rect.size.width + 2 * distance, rect.size.height + 2 * distance)
        return org.opencv.core.RotatedRect(rect.center, newSize, rect.angle)
    }
}
