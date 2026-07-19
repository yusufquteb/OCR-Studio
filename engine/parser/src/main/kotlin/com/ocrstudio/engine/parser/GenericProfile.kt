package com.ocrstudio.engine.parser

import com.ocrstudio.core.common.BookContext
import com.ocrstudio.core.common.OcrPage
import com.ocrstudio.core.common.ParsedPage
import com.ocrstudio.core.common.ParserProfileIds
import com.ocrstudio.core.common.PreprocessConfig

/** Plain paragraph passthrough; no structural extraction, always fully confident. */
class GenericProfile : ParserProfile {
    override val id: String = ParserProfileIds.GENERIC
    override val preprocessDefaults: PreprocessConfig = PreprocessConfig.STANDARD

    override fun parse(page: OcrPage, ctx: BookContext): ParsedPage =
        ParsedPage(text = page.text, roots = emptyList(), references = emptyList(), parserConfidence = 1.0f)
}
