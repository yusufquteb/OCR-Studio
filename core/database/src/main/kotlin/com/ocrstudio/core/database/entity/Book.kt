package com.ocrstudio.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class Book(
    @PrimaryKey val id: String,
    val title: String,
    val pdfUri: String,
    val pageCount: Int,
    val addedAtEpochMs: Long
)
