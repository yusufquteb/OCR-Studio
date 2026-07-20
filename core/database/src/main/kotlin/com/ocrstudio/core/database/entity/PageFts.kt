package com.ocrstudio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * Standalone FTS4 index over corrected page text, kept in sync manually by
 * PageRecordDao (rather than Room's contentEntity linkage, which requires a
 * Long-typed primary key on the source entity; PageRecord uses a UUID string).
 * unicode61 remove_diacritics=2 makes search diacritics-insensitive. FTS4 (rather than
 * FTS5) is used here since Room's KSP FTS5 processing hit a "[MissingType]" compile
 * error in this project's Room/KSP version combination; FTS4 supports the identical
 * unicode61 remove_diacritics tokenizer option, so search behavior is unaffected.
 */
@Fts4(tokenizer = "unicode61", tokenizerArgs = ["remove_diacritics=2"])
@Entity(tableName = "page_fts")
data class PageFts(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val pageRecordId: String,
    val jobId: String,
    val pageNumber: Int,
    val correctedText: String
)
