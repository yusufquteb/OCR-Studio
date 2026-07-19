package com.ocrstudio.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "word_records",
    indices = [Index(value = ["jobId", "pageNumber"]), Index(value = ["word"])]
)
data class WordRecord(
    @PrimaryKey val id: String,
    val jobId: String,
    val pageNumber: Int,
    val word: String,
    val root: String?,
    val positionInPage: Int,
    val confidence: Float
)
