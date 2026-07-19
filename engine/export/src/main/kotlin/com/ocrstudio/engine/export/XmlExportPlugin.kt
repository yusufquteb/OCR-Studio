package com.ocrstudio.engine.export

import android.content.Context
import com.ocrstudio.core.common.AppContext
import android.net.Uri
import android.util.Xml
import com.ocrstudio.core.common.ExportFormat
import com.ocrstudio.core.database.dao.PageRecordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class XmlExportPlugin @Inject constructor(
    @AppContext private val context: Context,
    private val pageRecordDao: PageRecordDao
) : ExportPlugin {
    override val format = ExportFormat.XML

    override suspend fun export(jobId: String, destination: Uri): Int = withContext(Dispatchers.IO) {
        val streamer = PageStreamer(pageRecordDao)
        val output = context.contentResolver.openOutputStream(destination)
            ?: error("Unable to open output stream for $destination")
        var pageCount = 0
        output.use { stream ->
            val serializer = Xml.newSerializer()
            serializer.setOutput(stream, "UTF-8")
            serializer.startDocument("UTF-8", true)
            serializer.startTag(null, "book")
            serializer.attribute(null, "jobId", jobId)

            pageCount = streamer.forEachPage(jobId) { page ->
                serializer.startTag(null, "page")
                serializer.attribute(null, "number", page.pageNumber.toString())
                serializer.attribute(null, "needsReview", page.needsReview.toString())
                serializer.attribute(null, "finalScore", page.finalScore.toString())
                serializer.attribute(null, "winningEngineId", page.winningEngineId)

                serializer.startTag(null, "rawText")
                serializer.text(page.rawText)
                serializer.endTag(null, "rawText")

                serializer.startTag(null, "correctedText")
                serializer.text(page.correctedText)
                serializer.endTag(null, "correctedText")

                serializer.endTag(null, "page")
            }

            serializer.endTag(null, "book")
            serializer.endDocument()
            serializer.flush()
        }
        pageCount
    }
}
