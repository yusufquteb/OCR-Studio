package com.ocrstudio.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts5
import androidx.room.PrimaryKey

@Fts5(tokenizer = "unicode61", tokenizerArgs = ["remove_diacritics=2"])
@Entity(tableName = "word_fts")
data class WordFts(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "rowid") val rowId: Int = 0,
    val wordRecordId: String,
    val jobId: String,
    val pageNumber: Int,
    val word: String
)
