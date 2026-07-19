package com.ocrstudio.engine.pdf

import android.os.ParcelFileDescriptor
import com.shockwave.pdfium.PdfDocument
import com.shockwave.pdfium.PdfiumCore

/** Owns the SAF file descriptor + Pdfium document/core for one open PDF. Must be closed. */
class PdfDocumentHandle internal constructor(
    internal val core: PdfiumCore,
    internal val document: PdfDocument,
    private val pfd: ParcelFileDescriptor,
    val pageCount: Int
) : AutoCloseable {
    override fun close() {
        runCatching { core.closeDocument(document) }
        runCatching { pfd.close() }
    }
}
