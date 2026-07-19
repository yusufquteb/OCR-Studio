package com.ocrstudio.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class ErrorStage {
    RENDER, PREPROCESS, OCR, PARSE, CORRECT, VALIDATE, PERSIST
}

@Entity(
    tableName = "error_records",
    indices = [Index(value = ["jobId"])]
)
data class ErrorRecord(
    @PrimaryKey val id: String,
    val jobId: String,
    val pageNumber: Int,
    val stage: ErrorStage,
    val message: String,
    val timestampEpochMs: Long
)
