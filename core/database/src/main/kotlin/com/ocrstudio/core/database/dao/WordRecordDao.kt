package com.ocrstudio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ocrstudio.core.database.entity.WordRecord

@Dao
interface WordRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<WordRecord>)

    @Query("SELECT * FROM word_records WHERE jobId = :jobId AND pageNumber = :pageNumber ORDER BY positionInPage ASC")
    suspend fun getByJobAndPage(jobId: String, pageNumber: Int): List<WordRecord>

    @Query("DELETE FROM word_records WHERE jobId = :jobId AND pageNumber = :pageNumber")
    suspend fun deleteByJobAndPage(jobId: String, pageNumber: Int)
}
