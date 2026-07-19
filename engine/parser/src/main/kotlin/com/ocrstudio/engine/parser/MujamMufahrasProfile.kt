package com.ocrstudio.engine.parser

import com.ocrstudio.core.common.BookContext
import com.ocrstudio.core.common.OcrPage
import com.ocrstudio.core.common.ParsedPage
import com.ocrstudio.core.common.ParserProfileIds
import com.ocrstudio.core.common.PreprocessConfig
import com.ocrstudio.core.common.ReferenceMatch
import com.ocrstudio.core.common.RootMatch

/**
 * Profile for indexed-dictionary works (mu'jam mufahras style): a root headword line
 * (short, isolated Arabic word/phrase) is followed by derivations, then hadith text, then
 * a reference line naming a book abbreviation + number (e.g. "خ 1234" -> Bukhari #1234).
 */
class MujamMufahrasProfile(
    private val hadithAbbreviations: Map<String, String>,
    private val knownRoots: Set<String>
) : ParserProfile {

    override val id: String = ParserProfileIds.MUJAM_MUFAHRAS
    override val preprocessDefaults: PreprocessConfig = PreprocessConfig.STANDARD

    private val arabicOnlyRegex = Regex("^[\\u0621-\\u064A\\u0670-\\u06D3\\s]+$")

    override fun parse(page: OcrPage, ctx: BookContext): ParsedPage {
        val lines = page.text.split("\n")
        val roots = mutableListOf<RootMatch>()
        val references = mutableListOf<ReferenceMatch>()
        var structuralHits = 0

        lines.forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed

            if (isHeadwordLine(line)) {
                roots.add(RootMatch(root = line, lineIndex = index))
                structuralHits++
                return@forEachIndexed
            }

            val reference = findReference(line, index)
            if (reference != null) {
                references.add(reference)
                structuralHits++
            }
        }

        val nonEmptyLines = lines.count { it.isNotBlank() }
        val parserConfidence = if (nonEmptyLines == 0) {
            0.5f
        } else {
            (0.6f + 0.4f * (structuralHits.toFloat() / nonEmptyLines)).coerceIn(0.6f, 1.0f)
        }

        return ParsedPage(
            text = page.text,
            roots = roots,
            references = references,
            parserConfidence = parserConfidence
        )
    }

    private fun isHeadwordLine(line: String): Boolean {
        val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty() || tokens.size > 2) return false
        if (line.length > 12) return false
        if (!arabicOnlyRegex.matches(line)) return false
        return knownRoots.isEmpty() || tokens.any { it in knownRoots } || tokens.size == 1
    }

    private fun findReference(line: String, lineIndex: Int): ReferenceMatch? {
        for ((abbreviation, fullName) in hadithAbbreviations) {
            val pattern = Regex("(?:^|\\s)${Regex.escape(abbreviation)}[\\s]*([0-9\\u0660-\\u0669]*)")
            val match = pattern.find(line) ?: continue
            val number = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() }
            return ReferenceMatch(
                bookAbbreviation = abbreviation,
                bookFullName = fullName,
                number = number,
                lineIndex = lineIndex
            )
        }
        return null
    }
}
