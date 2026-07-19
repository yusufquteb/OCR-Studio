package com.ocrstudio.engine.parser

import com.ocrstudio.core.common.BookContext
import com.ocrstudio.core.common.OcrPage
import com.ocrstudio.core.common.ParsedPage
import com.ocrstudio.core.common.ParserProfileIds
import com.ocrstudio.core.common.PreprocessConfig

/**
 * Registered-but-simple profile for Quranic exegesis works. Passes text through; reserved
 * for future ayah-boundary detection.
 */
class TafsirProfile : ParserProfile {
    override val id: String = ParserProfileIds.TAFSIR
    override val preprocessDefaults: PreprocessConfig = PreprocessConfig.STANDARD

    override fun parse(page: OcrPage, ctx: BookContext): ParsedPage =
        ParsedPage(text = page.text, roots = emptyList(), references = emptyList(), parserConfidence = 0.9f)
}
