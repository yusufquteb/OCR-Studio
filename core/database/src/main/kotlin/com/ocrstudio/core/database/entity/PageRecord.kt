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
    // Whether an LLM corrector (offline or online) actually accepted a rewrite for at least one
    // chunk of this page -- false means the corrected text is rule-engine output only, even if
    // the job has a correction chain configured, so Review can show the user what really ran.
    val aiCorrectionApplied: Boolean = false,
    // Raw (pre-correction) OCR words + pixel-space bounding boxes, JSON-encoded via
    // OcrWordsSerializer. Used to build the searchable PDF export's invisible text layer,
    // positioned over the original page render rather than the corrected text (which can
    // differ in word count/spelling and would no longer line up with real boxes).
    val rawWordsJson: String? = null,
    // Set only under TashkeelMode.SMART, when a corrector ended up adding diacritics the raw
    // OCR text didn't have -- Review shows a "completed by AI" indicator when true.
    val tashkeelAiCompleted: Boolean = false,
    // Populated when the user runs ReviewType.TRANSLATION on this page; null otherwise.
    // Stored separately so the Arabic correctedText is never overwritten.
    val translatedText: String? = null
)
