package com.ocrstudio.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A word-level correction the user made once in Review, remembered so the same OCR error is
 * auto-corrected (or at least suggested) on later pages of the same book without asking again --
 * e.g. correcting "إبن" to "ابن" once applies for the rest of that book.
 */
@Entity(
    tableName = "correction_memory_entries",
    indices = [Index(value = ["bookId", "original"])]
)
data class CorrectionMemoryEntry(
    @PrimaryKey val id: String,
    val bookId: String,
    val original: String,
    val corrected: String,
    val createdAtEpochMs: Long
)
