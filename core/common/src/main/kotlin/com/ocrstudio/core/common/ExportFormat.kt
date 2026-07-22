package com.ocrstudio.core.common

enum class ExportFormat(val extension: String, val mimeType: String) {
    SQLITE("db", "application/x-sqlite3"),
    JSON("json", "application/json"),
    TXT("txt", "text/plain"),
    MARKDOWN("md", "text/markdown"),
    CSV("csv", "text/csv"),
    XML("xml", "application/xml"),
    // Original scanned page image with an invisible, positioned text layer on top (same trick
    // OCRmyPDF uses): the page keeps its exact original margins, page numbers, and layout
    // because the visible content *is* the original render, not a reconstruction.
    SEARCHABLE_PDF("pdf", "application/pdf")
}
