package com.ocrstudio.engine.image

import android.graphics.Bitmap
import com.ocrstudio.core.common.AppResult
import com.ocrstudio.core.common.BinarizationMethod
import com.ocrstudio.core.common.PreprocessConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.photo.Photo
import javax.inject.Inject
import kotlin.math.max
import kotlin.math.min

private const val MAX_DESKEW_DEGREES = 15.0
private const val CROP_PADDING_PX = 8

class ImagePreprocessor @Inject constructor() {

    init {
        OpenCvInitializer.ensureInitialized()
    }

    /** Runs the full 7-step pipeline (each step individually toggleable via [config]). */
    suspend fun process(bitmap: Bitmap, config: PreprocessConfig): AppResult<Bitmap> =
        withContext(Dispatchers.Default) {
            AppResult.runCatching {
                var mat = Mat()
                Utils.bitmapToMat(bitmap, mat)

                // 1. Grayscale
                if (config.grayscale) {
                    val gray = Mat()
                    Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
                    mat.release()
                    mat = gray
                }

                // 2. Deskew
                if (config.deskew) {
                    val deskewed = deskew(mat)
                    mat.release()
                    mat = deskewed
                }

                // 3. Border removal / crop to content bounding box
                if (config.cropToContent) {
                    val cropped = cropToContent(mat)
                    mat.release()
                    mat = cropped
                }

                // 4. Denoise
                if (config.denoise) {
                    val denoised = Mat()
                    Photo.fastNlMeansDenoising(mat, denoised, config.denoiseH, 7, 21)
                    mat.release()
                    mat = denoised
                }

                // 5. Background flattening: divide(gray, blur(gray)) * 255
                if (config.backgroundFlatten) {
                    val flattened = flattenBackground(mat, config.backgroundBlurKernel)
                    mat.release()
                    mat = flattened
                }

                // 6. Binarization
                if (config.binarization) {
                    val binary = when (config.binarizationMethod) {
                        BinarizationMethod.ADAPTIVE_GAUSSIAN -> {
                            val dst = Mat()
                            val blockSize = if (config.adaptiveBlockSize % 2 == 0) {
                                config.adaptiveBlockSize + 1
                            } else {
                                config.adaptiveBlockSize
                            }
                            Imgproc.adaptiveThreshold(
                                mat, dst, 255.0,
                                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                                Imgproc.THRESH_BINARY,
                                blockSize,
                                config.adaptiveC
                            )
                            dst
                        }
                        BinarizationMethod.SAUVOLA -> Sauvola.threshold(
                            mat,
                            config.sauvolaWindowSize,
                            config.sauvolaK
                        )
                    }
                    mat.release()
                    mat = binary
                }

                // 7. Despeckle
                if (config.despeckle) {
                    val kernel = Imgproc.getStructuringElement(
                        Imgproc.MORPH_RECT,
                        Size(config.despeckleKernelSize.toDouble(), config.despeckleKernelSize.toDouble())
                    )
                    val opened = Mat()
                    Imgproc.morphologyEx(mat, opened, Imgproc.MORPH_OPEN, kernel)
                    mat.release()
                    mat = opened
                }

                val result = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
                Utils.matToBitmap(mat, result)
                mat.release()
                result
            }
        }

    private fun deskew(gray: Mat): Mat {
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

        val points = MatOfPoint()
        Core.findNonZero(binary, points)
        if (points.empty()) {
            binary.release(); points.release()
            return gray.clone()
        }

        val points2f = org.opencv.core.MatOfPoint2f()
        points.convertTo(points2f, CvType.CV_32FC2)
        val rotatedRect = Imgproc.minAreaRect(points2f)
        binary.release(); points.release(); points2f.release()

        var angle = rotatedRect.angle
        if (angle < -45) angle += 90.0
        val correction = (-angle).coerceIn(-MAX_DESKEW_DEGREES, MAX_DESKEW_DEGREES)

        if (kotlin.math.abs(correction) < 0.1) return gray.clone()

        val center = Point(gray.cols() / 2.0, gray.rows() / 2.0)
        val rotationMatrix = Imgproc.getRotationMatrix2D(center, correction, 1.0)
        val rotated = Mat()
        Imgproc.warpAffine(
            gray, rotated, rotationMatrix, gray.size(),
            Imgproc.INTER_CUBIC, Core.BORDER_CONSTANT, Scalar(255.0)
        )
        rotationMatrix.release()
        return rotated
    }

    private fun cropToContent(gray: Mat): Mat {
        val binary = Mat()
        Imgproc.threshold(gray, binary, 0.0, 255.0, Imgproc.THRESH_BINARY_INV + Imgproc.THRESH_OTSU)

        val points = MatOfPoint()
        Core.findNonZero(binary, points)
        if (points.empty()) {
            binary.release(); points.release()
            return gray.clone()
        }
        val bounds = Imgproc.boundingRect(points)
        binary.release(); points.release()

        val x = max(0, bounds.x - CROP_PADDING_PX)
        val y = max(0, bounds.y - CROP_PADDING_PX)
        val width = min(gray.cols() - x, bounds.width + 2 * CROP_PADDING_PX)
        val height = min(gray.rows() - y, bounds.height + 2 * CROP_PADDING_PX)
        if (width <= 0 || height <= 0) return gray.clone()

        return Mat(gray, Rect(x, y, width, height)).clone()
    }

    private fun flattenBackground(gray: Mat, blurKernel: Int): Mat {
        val kernel = if (blurKernel % 2 == 0) blurKernel + 1 else blurKernel

        val grayF = Mat()
        gray.convertTo(grayF, CvType.CV_32F)

        val blurred = Mat()
        Imgproc.GaussianBlur(grayF, blurred, Size(kernel.toDouble(), kernel.toDouble()), 0.0)
        // avoid divide-by-zero on pure-white regions
        Core.add(blurred, Scalar(1.0), blurred)

        val divided = Mat()
        Core.divide(grayF, blurred, divided, 255.0)

        val clipped = Mat()
        Core.min(divided, Scalar(255.0), clipped)

        val result = Mat()
        clipped.convertTo(result, CvType.CV_8UC1)

        grayF.release(); blurred.release(); divided.release(); clipped.release()
        return result
    }
}
