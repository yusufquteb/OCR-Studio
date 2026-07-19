package com.ocrstudio.app.di

import android.content.Context
import com.ocrstudio.core.database.AppDatabase
import com.ocrstudio.core.database.DatabaseFactory
import com.ocrstudio.core.database.dao.BookDao
import com.ocrstudio.core.database.dao.BookJobDao
import com.ocrstudio.core.database.dao.ErrorRecordDao
import com.ocrstudio.core.database.dao.ExportRecordDao
import com.ocrstudio.core.database.dao.PageRecordDao
import com.ocrstudio.core.database.dao.ReferenceEntryDao
import com.ocrstudio.core.database.dao.RootEntryDao
import com.ocrstudio.core.database.dao.SearchDao
import com.ocrstudio.core.database.dao.WordRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        DatabaseFactory.create(context)

    @Provides fun provideBookDao(db: AppDatabase): BookDao = db.bookDao()
    @Provides fun provideBookJobDao(db: AppDatabase): BookJobDao = db.bookJobDao()
    @Provides fun providePageRecordDao(db: AppDatabase): PageRecordDao = db.pageRecordDao()
    @Provides fun provideWordRecordDao(db: AppDatabase): WordRecordDao = db.wordRecordDao()
    @Provides fun provideRootEntryDao(db: AppDatabase): RootEntryDao = db.rootEntryDao()
    @Provides fun provideReferenceEntryDao(db: AppDatabase): ReferenceEntryDao = db.referenceEntryDao()
    @Provides fun provideErrorRecordDao(db: AppDatabase): ErrorRecordDao = db.errorRecordDao()
    @Provides fun provideExportRecordDao(db: AppDatabase): ExportRecordDao = db.exportRecordDao()
    @Provides fun provideSearchDao(db: AppDatabase): SearchDao = db.searchDao()
}
