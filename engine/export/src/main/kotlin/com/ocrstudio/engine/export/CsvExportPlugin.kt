package com.ocrstudio.engine.export

import android.content.Context
import com.ocrstudio.core.common.AppContext
import android.net.Uri
import com.ocrstudio.core.common.ExportFormat
import com.ocrstudio.core.database.dao.PageRecordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import javax.inject.Inject

class CsvExportPlugin @Inject constructor(
    @AppContext private val context: Context,
    private val pageRecordDao: PageRecordDao
) : ExportPlugin {
    override val format = ExportFormat.CSV

    private val header = listOf(
        "pageNumber", "rawText", "correctedText", "ocrConfidence",
        "dictionaryHitRate", "parserConfidence", "finalScore", "needsReview", "winningEngineId"
    )

    override suspend fun export(jobId: String, destination: Uri): Int = withContext(Dispatchers.IO) {
        val streamer = PageStreamer(pageRecordDao)
        val output = context.contentResolver.openOutputStream(destination)
            ?: error("Unable to open output stream for $destination")
        var pageCount = 0
        output.use { stream ->
            BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { writer ->
                writer.write(header.joinToString(","))
                writer.newLine()
                pageCount = streamer.forEachPage(jobId) { page ->
                    val row = listOf(
                        page.pageNumber.toString(),
                        page.rawText,
                        page.correctedText,
                        page.ocrConfidence.toString(),
                        page.dictionaryHitRate.toString(),
                        page.parserConfidence.toString(),
                        page.finalScore.toString(),
                        page.needsReview.toString(),
                        page.winningEngineId
                    )
                    writer.write(row.joinToString(",") { csvEscape(it) })
                    writer.newLine()
                }
            }
        }
        pageCount
    }

    private fun csvEscape(value: String): String {
        val needsQuoting = value.contains(',') || value.contains('"') || value.contains('\n') || value.contains('\r')
        return if (needsQuoting) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }
}
