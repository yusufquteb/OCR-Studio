package com.ocrstudio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ocrstudio.core.database.entity.RootEntry

@Dao
interface RootEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(roots: List<RootEntry>)

    @Query("SELECT * FROM root_entries WHERE jobId = :jobId AND root = :root ORDER BY pageNumber ASC")
    suspend fun findByRoot(jobId: String, root: String): List<RootEntry>

    @Query("DELETE FROM root_entries WHERE jobId = :jobId AND pageNumber = :pageNumber")
    suspend fun deleteByJobAndPage(jobId: String, pageNumber: Int)
}
