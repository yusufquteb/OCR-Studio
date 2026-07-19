package com.ocrstudio.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ocrstudio.core.common.ExportFormat

@Entity(
    tableName = "export_records",
    indices = [Index(value = ["jobId"])]
)
data class ExportRecord(
    @PrimaryKey val id: String,
    val jobId: String,
    val format: ExportFormat,
    val destinationUri: String,
    val pageCount: Int,
    val createdAtEpochMs: Long
)
