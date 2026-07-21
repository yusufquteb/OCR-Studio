package com.ocrstudio.engine.export

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.ExportFormat
import com.ocrstudio.core.common.OcrWordsSerializer
import com.ocrstudio.core.database.dao.BookJobDao
import com.ocrstudio.core.database.dao.PageRecordDao
import com.ocrstudio.engine.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Renders each page exactly as it appears in the source PDF (same render used during OCR, at
 * the job's DPI) and overlays the raw OCR words as invisible, positioned text -- the same trick
 * OCRmyPDF/Tesseract's own PDF renderer use. Because the visible page is the original render,
 * margins, page numbers, and layout are preserved automatically; nothing is reconstructed.
 */
class SearchablePdfExportPlugin @Inject constructor(
    @AppContext private val context: Context,
    private val bookJobDao: BookJobDao,
    private val pageRecordDao: PageRecordDao,
    private val pdfPageRenderer: PdfPageRenderer
) : ExportPlugin {
    override val format = ExportFormat.SEARCHABLE_PDF

    override suspend fun export(jobId: String, destination: Uri): Int = withContext(Dispatchers.IO) {
        val job = bookJobDao.getById(jobId) ?: error("Job $jobId not found")
        val documentResult = pdfPageRenderer.open(Uri.parse(job.pdfUri))
        val handle = documentResult.getOrNull() ?: error("Unable to open source PDF for job $jobId")

        val outputDocument = PdfDocument()
        var pageCount = 0
        try {
            val streamer = PageStreamer(pageRecordDao)
            pageCount = streamer.forEachPage(jobId) { page ->
                val bitmapResult = pdfPageRenderer.renderPage(handle, page.pageNumber - 1, job.dpi)
                val bitmap = bitmapResult.getOrNull() ?: return@forEachPage

                val pdfPage = outputDocument.startPage(
                    PdfDocument.PageInfo.Builder(bitmap.width, bitmap.height, page.pageNumber).create()
                )
                val canvas = pdfPage.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                bitmap.recycle()

                val invisiblePaint = Paint().apply {
                    color = Color.BLACK
                    alpha = 0
                    isAntiAlias = true
                }
                OcrWordsSerializer.decode(page.rawWordsJson).forEach { word ->
                    val boxHeight = (word.bottom - word.top).coerceAtLeast(1)
                    val boxWidth = (word.right - word.left).coerceAtLeast(1)
                    invisiblePaint.textSize = boxHeight.toFloat()
                    invisiblePaint.textScaleX = 1f
                    val naturalWidth = invisiblePaint.measureText(word.text).coerceAtLeast(1f)
                    invisiblePaint.textScaleX = boxWidth / naturalWidth
                    canvas.drawText(word.text, word.left.toFloat(), word.bottom.toFloat(), invisiblePaint)
                }

                outputDocument.finishPage(pdfPage)
            }

            val output = context.contentResolver.openOutputStream(destination)
                ?: error("Unable to open output stream for $destination")
            output.use { outputDocument.writeTo(it) }
        } finally {
            outputDocument.close()
            handle.close()
        }
        pageCount
    }
}
