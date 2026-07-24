package com.ocrstudio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ocrstudio.core.database.entity.BookGlossaryEntry

@Dao
interface BookGlossaryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(entries: List<BookGlossaryEntry>)

    @Query("SELECT * FROM book_glossary_entries WHERE bookId = :bookId ORDER BY createdAtEpochMs ASC")
    suspend fun getByBook(bookId: String): List<BookGlossaryEntry>

    @Query("SELECT COUNT(*) FROM book_glossary_entries WHERE bookId = :bookId")
    suspend fun countForBook(bookId: String): Int

    @Query("DELETE FROM book_glossary_entries WHERE bookId = :bookId")
    suspend fun clearForBook(bookId: String)
}
