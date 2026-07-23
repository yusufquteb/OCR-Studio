package com.ocrstudio.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.ocrstudio.core.database.dao.BookDao
import com.ocrstudio.core.database.dao.BookJobDao
import com.ocrstudio.core.database.dao.CorrectionMemoryDao
import com.ocrstudio.core.database.dao.ErrorRecordDao
import com.ocrstudio.core.database.dao.ExportRecordDao
import com.ocrstudio.core.database.dao.PageRecordDao
import com.ocrstudio.core.database.dao.ReferenceEntryDao
import com.ocrstudio.core.database.dao.RootEntryDao
import com.ocrstudio.core.database.dao.SearchDao
import com.ocrstudio.core.database.dao.WordRecordDao
import com.ocrstudio.core.database.entity.Book
import com.ocrstudio.core.database.entity.BookJob
import com.ocrstudio.core.database.entity.CorrectionMemoryEntry
import com.ocrstudio.core.database.entity.ErrorRecord
import com.ocrstudio.core.database.entity.ExportRecord
import com.ocrstudio.core.database.entity.PageFts
import com.ocrstudio.core.database.entity.PageRecord
import com.ocrstudio.core.database.entity.ReferenceEntry
import com.ocrstudio.core.database.entity.RootEntry
import com.ocrstudio.core.database.entity.WordFts
import com.ocrstudio.core.database.entity.WordRecord

const val DATABASE_NAME = "ocr_studio.db"
const val DATABASE_VERSION = 4

@Database(
    entities = [
        Book::class,
        BookJob::class,
        PageRecord::class,
        WordRecord::class,
        RootEntry::class,
        ReferenceEntry::class,
        ErrorRecord::class,
        ExportRecord::class,
        PageFts::class,
        WordFts::class,
        CorrectionMemoryEntry::class
    ],
    version = DATABASE_VERSION,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun bookJobDao(): BookJobDao
    abstract fun pageRecordDao(): PageRecordDao
    abstract fun wordRecordDao(): WordRecordDao
    abstract fun rootEntryDao(): RootEntryDao
    abstract fun referenceEntryDao(): ReferenceEntryDao
    abstract fun errorRecordDao(): ErrorRecordDao
    abstract fun exportRecordDao(): ExportRecordDao
    abstract fun searchDao(): SearchDao
    abstract fun correctionMemoryDao(): CorrectionMemoryDao
}
