package com.ocrstudio.engine.export

import android.content.Context
import android.net.Uri
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.core.common.ExportFormat
import com.ocrstudio.core.database.dao.PageRecordDao
import com.ocrstudio.core.database.entity.PageRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedWriter
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject

private const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
private const val REL_NS = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"

/**
 * Renders the corrected text as a real .docx, laid out to match the searchable-PDF export's
 * printable page: A4 with 1" margins (the same "Normal" page setup Word itself defaults to),
 * one Word page per source OCR page (a page break between them), and an auto-updating page
 * number field centered in the footer -- so opening the export shows the same margins and
 * page-by-page numbering as the PDF, just as typeset text instead of a page image. Every
 * paragraph is marked right-to-left (w:bidi / w:rtl) since the content is Arabic OCR text.
 *
 * Hand-rolled OOXML (a .docx is just a zip of XML parts) rather than a docx library: consistent
 * with every other exporter in this module (XmlExportPlugin, JsonExportPlugin, ...), and keeps
 * this module dependency-free.
 */
class WordExportPlugin @Inject constructor(
    @AppContext private val context: Context,
    private val pageRecordDao: PageRecordDao
) : ExportPlugin {
    override val format = ExportFormat.WORD

    override suspend fun export(jobId: String, destination: Uri): Int = withContext(Dispatchers.IO) {
        val streamer = PageStreamer(pageRecordDao)
        val output = context.contentResolver.openOutputStream(destination)
            ?: error("Unable to open output stream for $destination")
        var pageCount = 0
        output.use { stream ->
            ZipOutputStream(stream).use { zip ->
                writeStaticEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
                writeStaticEntry(zip, "_rels/.rels", PACKAGE_RELS_XML)
                writeStaticEntry(zip, "word/_rels/document.xml.rels", DOCUMENT_RELS_XML)
                writeStaticEntry(zip, "word/footer1.xml", FOOTER_XML)

                zip.putNextEntry(ZipEntry("word/document.xml"))
                val writer = BufferedWriter(OutputStreamWriter(zip, Charsets.UTF_8))
                writer.write(DOCUMENT_XML_HEADER)
                var isFirstPage = true
                pageCount = streamer.forEachPage(jobId) { page ->
                    if (!isFirstPage) writer.write(PAGE_BREAK_PARAGRAPH)
                    isFirstPage = false
                    writePage(writer, page)
                }
                writer.write(SECTION_PROPERTIES_XML)
                writer.write(DOCUMENT_XML_FOOTER)
                writer.flush()
                zip.closeEntry()
            }
        }
        pageCount
    }

    private fun writePage(writer: BufferedWriter, page: PageRecord) {
        if (page.needsReview) {
            writer.write(rtlParagraph(text = "⚠ Needs review (score ${"%.2f".format(page.finalScore)})", bold = true))
        }
        val lines = page.correctedText.split("\n")
        if (lines.isEmpty() || lines.all { it.isEmpty() }) {
            writer.write(rtlParagraph(text = ""))
        } else {
            lines.forEach { line -> writer.write(rtlParagraph(text = line)) }
        }
    }

    private fun rtlParagraph(text: String, bold: Boolean = false): String {
        val runProps = if (bold) "<w:rPr><w:rtl/><w:b/></w:rPr>" else "<w:rPr><w:rtl/></w:rPr>"
        val safeText = escapeXml(stripInvalidXmlChars(text))
        return "<w:p><w:pPr><w:bidi/><w:jc w:val=\"right\"/></w:pPr>" +
            "<w:r>$runProps<w:t xml:space=\"preserve\">$safeText</w:t></w:r></w:p>"
    }

    private fun writeStaticEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    /** XML 1.0 disallows most C0 control characters; OCR output can occasionally contain one. */
    private fun stripInvalidXmlChars(text: String): String =
        text.filter { it == '\t' || it == '\n' || it == '\r' || it.code >= 0x20 }

    companion object {
        private const val CONTENT_TYPES_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
<Default Extension="xml" ContentType="application/xml"/>
<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
<Override PartName="/word/footer1.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.footer+xml"/>
</Types>"""

        private const val PACKAGE_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>"""

        private const val DOCUMENT_RELS_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/footer" Target="footer1.xml"/>
</Relationships>"""

        // Centered page-number field in the footer, matching every page of the source PDF being numbered.
        private const val FOOTER_XML = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:ftr xmlns:w="$WORD_NS">
<w:p><w:pPr><w:jc w:val="center"/></w:pPr>
<w:fldSimple w:instr=" PAGE   \* MERGEFORMAT "><w:r><w:t>1</w:t></w:r></w:fldSimple>
</w:p>
</w:ftr>"""

        private const val DOCUMENT_XML_HEADER = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="$WORD_NS" xmlns:r="$REL_NS"><w:body>"""

        private const val PAGE_BREAK_PARAGRAPH = "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"

        // A4 (11906 x 16838 twips) with 1" (1440 twip) margins -- the same "Normal" page setup
        // as the searchable PDF export's printable area -- plus a right-to-left document flag
        // and the footer's page-number field, restarting numbering at 1.
        private const val SECTION_PROPERTIES_XML = """<w:sectPr>
<w:footerReference w:type="default" r:id="rId1"/>
<w:pgSz w:w="11906" w:h="16838"/>
<w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440" w:header="708" w:footer="708" w:gutter="0"/>
<w:pgNumType w:start="1"/>
<w:bidi/>
</w:sectPr>"""

        private const val DOCUMENT_XML_FOOTER = "</w:body></w:document>"
    }
}
