package com.ocrstudio.core.common

enum class ExportFormat(val extension: String, val mimeType: String) {
    SQLITE("db", "application/x-sqlite3"),
    JSON("json", "application/json"),
    TXT("txt", "text/plain"),
    MARKDOWN("md", "text/markdown"),
    CSV("csv", "text/csv"),
    XML("xml", "application/xml")
}
