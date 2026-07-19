package com.ocrstudio.engine.image

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Manual Sauvola local thresholding: T(x,y) = mean(x,y) * (1 + k * (stddev(x,y)/R - 1)),
 * R=128 (standard dynamic-range constant for 8-bit images). Output follows the same
 * convention as OpenCV's THRESH_BINARY: background (brighter than threshold) -> 255,
 * text (darker than threshold) -> 0.
 */
object Sauvola {
    private const val DYNAMIC_RANGE = 128.0

    fun threshold(gray8u: Mat, windowSize: Int, k: Double): Mat {
        val win = if (windowSize % 2 == 0) windowSize + 1 else windowSize
        val size = Size(win.toDouble(), win.toDouble())

        val grayF = Mat()
        gray8u.convertTo(grayF, CvType.CV_32F)

        val mean = Mat()
        Imgproc.boxFilter(grayF, mean, CvType.CV_32F, size)

        val sq = Mat()
        Core.multiply(grayF, grayF, sq)
        val sqMean = Mat()
        Imgproc.boxFilter(sq, sqMean, CvType.CV_32F, size)

        val meanSq = Mat()
        Core.multiply(mean, mean, meanSq)
        val variance = Mat()
        Core.subtract(sqMean, meanSq, variance)
        Core.max(variance, Scalar(0.0), variance) // guard against tiny negative fp noise

        val stddev = Mat()
        Core.sqrt(variance, stddev)

        val factor = Mat()
        Core.divide(stddev, Scalar(DYNAMIC_RANGE), factor)       // stddev / R
        Core.subtract(factor, Scalar(1.0), factor)                // (stddev / R) - 1
        Core.multiply(factor, Scalar(k), factor)                  // k * (...)
        Core.add(factor, Scalar(1.0), factor)                      // 1 + k * (...)

        val thresholdMat = Mat()
        Core.multiply(mean, factor, thresholdMat)

        val result = Mat(gray8u.size(), CvType.CV_8UC1)
        Core.compare(grayF, thresholdMat, result, Core.CMP_GT)

        grayF.release(); mean.release(); sq.release(); sqMean.release()
        meanSq.release(); variance.release(); stddev.release(); factor.release(); thresholdMat.release()

        return result
    }
}
