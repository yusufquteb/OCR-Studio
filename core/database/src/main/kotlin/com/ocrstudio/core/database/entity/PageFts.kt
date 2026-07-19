package com.ocrstudio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts5
import androidx.room.PrimaryKey

/**
 * Standalone FTS5 index over corrected page text, kept in sync manually by
 * PageRecordDao (rather than Room's contentEntity linkage, which requires a
 * Long-typed primary key on the source entity; PageRecord uses a UUID string).
 * unicode61 remove_diacritics=2 makes search diacritics-insensitive.
 */
@Fts5(tokenizer = "unicode61", tokenizerArgs = ["remove_diacritics=2"])
@Entity(tableName = "page_fts")
data class PageFts(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val pageRecordId: String,
    val jobId: String,
    val pageNumber: Int,
    val correctedText: String
)
