package com.ocrstudio.engine.export

import android.content.Context
import com.ocrstudio.core.common.AppContext
import android.net.Uri
import android.util.JsonWriter
import com.ocrstudio.core.common.ExportFormat
import com.ocrstudio.core.database.dao.PageRecordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStreamWriter
import javax.inject.Inject

class JsonExportPlugin @Inject constructor(
    @AppContext private val context: Context,
    private val pageRecordDao: PageRecordDao
) : ExportPlugin {
    override val format = ExportFormat.JSON

    override suspend fun export(jobId: String, destination: Uri): Int = withContext(Dispatchers.IO) {
        val streamer = PageStreamer(pageRecordDao)
        val output = context.contentResolver.openOutputStream(destination)
            ?: error("Unable to open output stream for $destination")
        var pageCount = 0
        output.use { stream ->
            JsonWriter(OutputStreamWriter(stream, Charsets.UTF_8)).use { writer ->
                writer.setIndent("  ")
                writer.beginArray()
                pageCount = streamer.forEachPage(jobId) { page ->
                    writer.beginObject()
                    writer.name("pageNumber").value(page.pageNumber)
                    writer.name("rawText").value(page.rawText)
                    writer.name("correctedText").value(page.correctedText)
                    writer.name("ocrConfidence").value(page.ocrConfidence.toDouble())
                    writer.name("dictionaryHitRate").value(page.dictionaryHitRate.toDouble())
                    writer.name("parserConfidence").value(page.parserConfidence.toDouble())
                    writer.name("finalScore").value(page.finalScore.toDouble())
                    writer.name("needsReview").value(page.needsReview)
                    writer.name("winningEngineId").value(page.winningEngineId)
                    writer.endObject()
                }
                writer.endArray()
            }
        }
        pageCount
    }
}
