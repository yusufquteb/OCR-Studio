package com.ocrstudio.engine.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.AppResult
import com.shockwave.pdfium.PdfiumCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

private const val POINTS_PER_INCH = 72.0
private const val MAX_RENDER_WIDTH_PX = 4000

/**
 * Renders PDF pages via Pdfium at a given DPI. Opens the PDF through a SAF
 * ParcelFileDescriptor so no filesystem path/permission beyond the persisted
 * SAF grant is ever required.
 */
class PdfPageRenderer @Inject constructor(
    @AppContext private val context: Context
) {
    suspend fun open(uri: Uri): AppResult<PdfDocumentHandle> = withContext(Dispatchers.IO) {
        AppResult.runCatching {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: error("Unable to open ParcelFileDescriptor for $uri")
            val core = PdfiumCore(context)
            val document = core.newDocument(pfd)
            val pageCount = core.getPageCount(document)
            PdfDocumentHandle(core, document, pfd, pageCount)
        }
    }

    /**
     * Renders one page to an ARGB_8888 bitmap at [dpi]. Width/height are derived from the
     * page's point size (page size in points * dpi/72), then auto-downsampled if the
     * resulting width would exceed MAX_RENDER_WIDTH_PX to bound memory use on huge scans.
     */
    suspend fun renderPage(handle: PdfDocumentHandle, pageIndex: Int, dpi: Int): AppResult<Bitmap> =
        withContext(Dispatchers.IO) {
            AppResult.runCatching {
                handle.core.openPage(handle.document, pageIndex)
                try {
                    val pageWidthPoints = handle.core.getPageWidthPoint(handle.document, pageIndex)
                    val pageHeightPoints = handle.core.getPageHeightPoint(handle.document, pageIndex)

                    var width = (pageWidthPoints * dpi / POINTS_PER_INCH).toInt().coerceAtLeast(1)
                    var height = (pageHeightPoints * dpi / POINTS_PER_INCH).toInt().coerceAtLeast(1)

                    if (width > MAX_RENDER_WIDTH_PX) {
                        val scale = MAX_RENDER_WIDTH_PX.toFloat() / width
                        width = MAX_RENDER_WIDTH_PX
                        height = (height * scale).toInt().coerceAtLeast(1)
                    }

                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    handle.core.renderPageBitmap(
                        handle.document,
                        bitmap,
                        pageIndex,
                        0,
                        0,
                        width,
                        height,
                        true
                    )
                    bitmap
                } finally {
                    handle.core.closePage(handle.document, pageIndex)
                }
            }
        }
}
