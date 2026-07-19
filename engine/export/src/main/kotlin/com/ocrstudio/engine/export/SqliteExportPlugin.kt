package com.ocrstudio.engine.export

import android.content.Context
import com.ocrstudio.core.common.AppContext
import android.net.Uri
import com.ocrstudio.core.common.ExportFormat
import com.ocrstudio.core.database.AppDatabase
import com.ocrstudio.core.database.DATABASE_NAME
import com.ocrstudio.core.database.dao.PageRecordDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Copies the live Room database file (checkpointing WAL first for a consistent snapshot). */
class SqliteExportPlugin @Inject constructor(
    @AppContext private val context: Context,
    private val database: AppDatabase,
    private val pageRecordDao: PageRecordDao
) : ExportPlugin {
    override val format = ExportFormat.SQLITE

    override suspend fun export(jobId: String, destination: Uri): Int = withContext(Dispatchers.IO) {
        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }

        val dbFile = context.getDatabasePath(DATABASE_NAME)
        val output = context.contentResolver.openOutputStream(destination)
            ?: error("Unable to open output stream for $destination")
        output.use { stream ->
            dbFile.inputStream().use { input -> input.copyTo(stream) }
        }

        pageRecordDao.countByJob(jobId)
    }
}
