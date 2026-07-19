package com.ocrstudio.core.common

/** A dictionary root/headword detected on a page by a ParserProfile. */
data class RootMatch(
    val root: String,
    val lineIndex: Int
)

/** A hadith/book reference detected on a page, e.g. "خ 1234" -> Bukhari #1234. */
data class ReferenceMatch(
    val bookAbbreviation: String,
    val bookFullName: String,
    val number: String?,
    val lineIndex: Int
)

data class ParsedPage(
    val text: String,
    val roots: List<RootMatch> = emptyList(),
    val references: List<ReferenceMatch> = emptyList(),
    val parserConfidence: Float = 1.0f
)

/** Context passed to a ParserProfile so it can use book-level state (e.g. previous root). */
data class BookContext(
    val bookId: String,
    val jobId: String,
    val pageNumber: Int,
    val lastKnownRoot: String? = null
)
