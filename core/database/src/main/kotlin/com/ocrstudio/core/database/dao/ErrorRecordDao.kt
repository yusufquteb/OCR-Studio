package com.ocrstudio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ocrstudio.core.database.entity.ErrorRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ErrorRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(error: ErrorRecord)

    @Query("SELECT * FROM error_records WHERE jobId = :jobId ORDER BY timestampEpochMs DESC")
    fun observeByJob(jobId: String): Flow<List<ErrorRecord>>

    @Query("SELECT COUNT(*) FROM error_records WHERE jobId = :jobId")
    suspend fun countByJob(jobId: String): Int
}
