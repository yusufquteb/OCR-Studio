package com.ocrstudio.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ocrstudio.core.common.JobStatus
import com.ocrstudio.core.database.entity.BookJob
import kotlinx.coroutines.flow.Flow

@Dao
interface BookJobDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: BookJob)

    @Update
    suspend fun update(job: BookJob)

    @Query("SELECT * FROM book_jobs WHERE id = :id")
    suspend fun getById(id: String): BookJob?

    @Query("SELECT * FROM book_jobs ORDER BY updatedAtEpochMs DESC")
    fun observeAll(): Flow<List<BookJob>>

    @Query("SELECT * FROM book_jobs WHERE id = :id")
    fun observeById(id: String): Flow<BookJob?>

    @Query("SELECT * FROM book_jobs WHERE status = :status")
    suspend fun getByStatus(status: JobStatus): List<BookJob>

    @Query("UPDATE book_jobs SET currentPage = :currentPage, updatedAtEpochMs = :updatedAt WHERE id = :jobId")
    suspend fun checkpoint(jobId: String, currentPage: Int, updatedAt: Long)

    @Query("UPDATE book_jobs SET status = :status, updatedAtEpochMs = :updatedAt WHERE id = :jobId")
    suspend fun updateStatus(jobId: String, status: JobStatus, updatedAt: Long)

    @Query("UPDATE book_jobs SET errorCount = errorCount + 1, updatedAtEpochMs = :updatedAt WHERE id = :jobId")
    suspend fun incrementErrorCount(jobId: String, updatedAt: Long)
}
