package com.ocrstudio.engine.parser

import com.ocrstudio.core.common.BookContext
import com.ocrstudio.core.common.OcrPage
import com.ocrstudio.core.common.ParsedPage
import com.ocrstudio.core.common.ParserProfileIds
import com.ocrstudio.core.common.PreprocessConfig

/**
 * Registered-but-simple profile for straight hadith collections (no mu'jam-style headword
 * structure). Passes text through; reserved for future chain-of-narration (isnad) extraction.
 */
class HadithProfile : ParserProfile {
    override val id: String = ParserProfileIds.HADITH
    override val preprocessDefaults: PreprocessConfig = PreprocessConfig.STANDARD

    override fun parse(page: OcrPage, ctx: BookContext): ParsedPage =
        ParsedPage(text = page.text, roots = emptyList(), references = emptyList(), parserConfidence = 0.9f)
}
