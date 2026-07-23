package com.ocrstudio.engine.export

import com.ocrstudio.core.common.ExportFormat
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExportRegistry @Inject constructor(
    sqliteExportPlugin: SqliteExportPlugin,
    jsonExportPlugin: JsonExportPlugin,
    txtExportPlugin: TxtExportPlugin,
    markdownExportPlugin: MarkdownExportPlugin,
    csvExportPlugin: CsvExportPlugin,
    xmlExportPlugin: XmlExportPlugin,
    searchablePdfExportPlugin: SearchablePdfExportPlugin,
    wordExportPlugin: WordExportPlugin
) {
    private val plugins: Map<ExportFormat, ExportPlugin> = listOf(
        sqliteExportPlugin, jsonExportPlugin, txtExportPlugin,
        markdownExportPlugin, csvExportPlugin, xmlExportPlugin, searchablePdfExportPlugin,
        wordExportPlugin
    ).associateBy { it.format }

    fun pluginFor(format: ExportFormat): ExportPlugin =
        plugins[format] ?: error("No export plugin registered for $format")
}
