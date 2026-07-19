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

class MarkdownExportPlugin @Inject constructor(
    @AppContext private val context: Context,
    private val pageRecordDao: PageRecordDao
) : ExportPlugin {
    override val format = ExportFormat.MARKDOWN

    override suspend fun export(jobId: String, destination: Uri): Int = withContext(Dispatchers.IO) {
        val streamer = PageStreamer(pageRecordDao)
        val output = context.contentResolver.openOutputStream(destination)
            ?: error("Unable to open output stream for $destination")
        var pageCount = 0
        output.use { stream ->
            BufferedWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { writer ->
                pageCount = streamer.forEachPage(jobId) { page ->
                    writer.write("## Page ${page.pageNumber}")
                    writer.newLine()
                    writer.newLine()
                    if (page.needsReview) {
                        writer.write("> ⚠️ Needs review (score ${"%.2f".format(page.finalScore)})")
                        writer.newLine()
                        writer.newLine()
                    }
                    writer.write(page.correctedText)
                    writer.newLine()
                    writer.newLine()
                }
            }
        }
        pageCount
    }
}
