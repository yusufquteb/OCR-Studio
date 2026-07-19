package com.ocrstudio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ocrstudio.core.database.entity.PageRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface PageRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: PageRecord)

    @Query("SELECT * FROM page_records WHERE jobId = :jobId ORDER BY pageNumber ASC")
    fun observeByJob(jobId: String): Flow<List<PageRecord>>

    @Query("SELECT * FROM page_records WHERE jobId = :jobId AND pageNumber = :pageNumber LIMIT 1")
    suspend fun getByJobAndPage(jobId: String, pageNumber: Int): PageRecord?

    @Query("SELECT * FROM page_records WHERE jobId = :jobId ORDER BY pageNumber ASC")
    suspend fun getAllByJob(jobId: String): List<PageRecord>

    @Query("SELECT * FROM page_records WHERE jobId = :jobId AND needsReview = 1 ORDER BY pageNumber ASC")
    fun observeNeedsReview(jobId: String): Flow<List<PageRecord>>

    @Query("SELECT COUNT(*) FROM page_records WHERE jobId = :jobId")
    suspend fun countByJob(jobId: String): Int

    /** Paged read used by streaming exporters so an entire book is never held in memory at once. */
    @Query("SELECT * FROM page_records WHERE jobId = :jobId ORDER BY pageNumber ASC LIMIT :limit OFFSET :offset")
    suspend fun getPageBatch(jobId: String, limit: Int, offset: Int): List<PageRecord>

    @Query("DELETE FROM page_records WHERE jobId = :jobId AND pageNumber = :pageNumber")
    suspend fun deleteByJobAndPage(jobId: String, pageNumber: Int)
}
