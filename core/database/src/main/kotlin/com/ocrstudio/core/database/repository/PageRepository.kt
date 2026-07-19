package com.ocrstudio.core.database.repository

import androidx.room.withTransaction
import com.ocrstudio.core.database.AppDatabase
import com.ocrstudio.core.database.entity.PageFts
import com.ocrstudio.core.database.entity.PageRecord
import com.ocrstudio.core.database.entity.ReferenceEntry
import com.ocrstudio.core.database.entity.RootEntry
import com.ocrstudio.core.database.entity.WordFts
import com.ocrstudio.core.database.entity.WordRecord
import javax.inject.Inject

/**
 * Persists one fully-processed page (record + words + roots + references + FTS index rows)
 * in a single Room transaction, per the "one transaction per page" pipeline rule.
 */
class PageRepository @Inject constructor(
    private val db: AppDatabase
) {
    suspend fun persistPage(
        page: PageRecord,
        words: List<WordRecord>,
        roots: List<RootEntry>,
        references: List<ReferenceEntry>
    ) {
        db.withTransaction {
            db.pageRecordDao().deleteByJobAndPage(page.jobId, page.pageNumber)
            db.wordRecordDao().deleteByJobAndPage(page.jobId, page.pageNumber)
            db.rootEntryDao().deleteByJobAndPage(page.jobId, page.pageNumber)
            db.referenceEntryDao().deleteByJobAndPage(page.jobId, page.pageNumber)
            db.searchDao().deletePageFts(page.id)
            db.searchDao().deleteWordFts(page.jobId, page.pageNumber)

            db.pageRecordDao().insert(page)
            if (words.isNotEmpty()) db.wordRecordDao().insertAll(words)
            if (roots.isNotEmpty()) db.rootEntryDao().insertAll(roots)
            if (references.isNotEmpty()) db.referenceEntryDao().insertAll(references)

            db.searchDao().insertPageFts(
                PageFts(
                    pageRecordId = page.id,
                    jobId = page.jobId,
                    pageNumber = page.pageNumber,
                    correctedText = page.correctedText
                )
            )
            if (words.isNotEmpty()) {
                db.searchDao().insertWordFts(
                    words.map {
                        WordFts(
                            wordRecordId = it.id,
                            jobId = it.jobId,
                            pageNumber = it.pageNumber,
                            word = it.word
                        )
                    }
                )
            }
        }
    }

    /** Advances the job checkpoint after a page transaction commits successfully. */
    suspend fun checkpoint(jobId: String, pageNumber: Int) {
        db.bookJobDao().checkpoint(jobId, pageNumber, System.currentTimeMillis())
    }
}
