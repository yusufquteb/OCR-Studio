package com.ocrstudio.engine.parser

import com.ocrstudio.core.common.BookContext
import com.ocrstudio.core.common.OcrPage
import com.ocrstudio.core.common.ParsedPage
import com.ocrstudio.core.common.PreprocessConfig

interface ParserProfile {
    val id: String
    val preprocessDefaults: PreprocessConfig

    /** Extracts structure (roots, references) from a single page's OCR output. */
    fun parse(page: OcrPage, ctx: BookContext): ParsedPage
}
