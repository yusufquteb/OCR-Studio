package com.ocrstudio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ocrstudio.core.database.entity.CorrectionMemoryEntry

@Dao
interface CorrectionMemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: CorrectionMemoryEntry)

    // Ascending by recency so a map built by folding this list (last write wins per key) reflects
    // the most recent correction when the same word was corrected more than once.
    @Query("SELECT * FROM correction_memory_entries WHERE bookId = :bookId ORDER BY createdAtEpochMs ASC")
    suspend fun getByBook(bookId: String): List<CorrectionMemoryEntry>

    @Query("DELETE FROM correction_memory_entries WHERE bookId = :bookId")
    suspend fun clearForBook(bookId: String)
}
