package com.ocrstudio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ocrstudio.core.database.entity.ExportRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface ExportRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: ExportRecord)

    @Query("SELECT * FROM export_records ORDER BY createdAtEpochMs DESC")
    fun observeAll(): Flow<List<ExportRecord>>
}
