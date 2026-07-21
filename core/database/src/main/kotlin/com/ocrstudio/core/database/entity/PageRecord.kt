package com.ocrstudio.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "page_records",
    indices = [Index(value = ["jobId", "pageNumber"], unique = true)]
)
data class PageRecord(
    @PrimaryKey val id: String,
    val jobId: String,
    val pageNumber: Int,
    val rawText: String,
    val correctedText: String,
    val ocrConfidence: Float,
    val dictionaryHitRate: Float,
    val parserConfidence: Float,
    val finalScore: Float,
    val needsReview: Boolean,              // finalScore < 0.80
    val winningEngineId: String,           // which OCR engine's result was kept
    val imagePath: String?,                // kept only if "keep images" setting on
    val processedAtEpochMs: Long,
    // Raw (pre-correction) OCR words + pixel-space bounding boxes, JSON-encoded via
    // OcrWordsSerializer. Used to build the searchable PDF export's invisible text layer,
    // positioned over the original page render rather than the corrected text (which can
    // differ in word count/spelling and would no longer line up with real boxes).
    val rawWordsJson: String? = null
)
