package com.ocrstudio.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cross-page glossary for a single book: domain-specific terms (proper names, technical
 * vocabulary, parser-detected roots) that should be treated as correct even if they don't appear
 * in the base Arabic dictionary. Populated automatically from parser root output and user edits,
 * and cleared on user request from the Job Progress screen.
 */
@Entity(
    tableName = "book_glossary_entries",
    indices = [Index(value = ["bookId", "term"], unique = true)]
)
data class BookGlossaryEntry(
    @PrimaryKey val id: String,
    val bookId: String,
    val term: String,
    val source: String,           // "parser", "user", or "import"
    val firstSeenPageNumber: Int,
    val createdAtEpochMs: Long
)
