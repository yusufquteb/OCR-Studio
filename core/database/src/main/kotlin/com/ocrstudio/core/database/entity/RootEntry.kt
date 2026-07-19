package com.ocrstudio.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "root_entries",
    indices = [Index(value = ["jobId", "root"]), Index(value = ["jobId", "pageNumber"])]
)
data class RootEntry(
    @PrimaryKey val id: String,
    val jobId: String,
    val pageNumber: Int,
    val root: String,
    val lineIndex: Int
)
