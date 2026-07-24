package com.ocrstudio.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.common.TashkeelMode

@Entity(
    tableName = "book_jobs",
    indices = [Index(value = ["bookId"])]
)
data class BookJob(
    @PrimaryKey val id: String,            // UUID
    val bookId: String,
    val pdfUri: String,                    // SAF persisted uri
    val title: String,
    val pageCount: Int,
    val currentPage: Int = 0,              // checkpoint (last COMPLETED page)
    val batchSize: Int = 20,               // user-configurable 10..50
    val dpi: Int = 300,                    // 300 default, 400 "old book" preset, 600 advanced
    val profileId: String = "generic",     // parser profile
    val ocrEngineId: String = "tesseract",
    val llmModelId: String? = null,        // null = rule-based correction only
    val preprocessConfigJson: String,      // serialized PreprocessConfig
    val tashkeelMode: TashkeelMode = TashkeelMode.NORMAL,
    val correctionScopeJson: String = "{}",   // serialized CorrectionScope; defaults to standard
    val status: JobStatus = JobStatus.QUEUED,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val errorCount: Int = 0
)
