package com.ocrstudio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ocrstudio.core.database.entity.PageFts
import com.ocrstudio.core.database.entity.WordFts

data class PageSearchHit(
    val pageRecordId: String,
    val jobId: String,
    val pageNumber: Int,
    val correctedText: String
)

@Dao
interface SearchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPageFts(entry: PageFts)

    @Query("DELETE FROM page_fts WHERE pageRecordId = :pageRecordId")
    suspend fun deletePageFts(pageRecordId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWordFts(entries: List<WordFts>)

    @Query("DELETE FROM word_fts WHERE jobId = :jobId AND pageNumber = :pageNumber")
    suspend fun deleteWordFts(jobId: String, pageNumber: Int)

    /**
     * Diacritics-insensitive full text search over corrected page text.
     * unicode61 remove_diacritics=2 normalizes both the indexed text and the
     * query string, so a diacritized query matches undiacritized text and
     * vice versa.
     */
    @Query(
        """
        SELECT pageRecordId, jobId, pageNumber, correctedText
        FROM page_fts
        WHERE page_fts MATCH :query
        ORDER BY jobId, pageNumber
        LIMIT :limit
        """
    )
    suspend fun searchPages(query: String, limit: Int = 200): List<PageSearchHit>

    @Query(
        """
        SELECT pageRecordId, jobId, pageNumber, correctedText
        FROM page_fts
        WHERE page_fts MATCH :query AND jobId = :jobId
        ORDER BY pageNumber
        LIMIT :limit
        """
    )
    suspend fun searchPagesInJob(query: String, jobId: String, limit: Int = 200): List<PageSearchHit>
}
