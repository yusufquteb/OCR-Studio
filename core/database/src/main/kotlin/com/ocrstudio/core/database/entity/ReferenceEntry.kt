package com.ocrstudio.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reference_entries",
    indices = [Index(value = ["jobId", "pageNumber"])]
)
data class ReferenceEntry(
    @PrimaryKey val id: String,
    val jobId: String,
    val pageNumber: Int,
    val bookAbbreviation: String,
    val bookFullName: String,
    val number: String?,
    val lineIndex: Int
)
