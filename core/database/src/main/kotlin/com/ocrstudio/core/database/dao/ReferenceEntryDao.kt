package com.ocrstudio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ocrstudio.core.database.entity.ReferenceEntry

@Dao
interface ReferenceEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(references: List<ReferenceEntry>)

    @Query("SELECT * FROM reference_entries WHERE jobId = :jobId ORDER BY pageNumber ASC")
    suspend fun getAllByJob(jobId: String): List<ReferenceEntry>

    @Query("DELETE FROM reference_entries WHERE jobId = :jobId AND pageNumber = :pageNumber")
    suspend fun deleteByJobAndPage(jobId: String, pageNumber: Int)
}
