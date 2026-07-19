package com.ocrstudio.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Plain factory (not a Hilt module) so :core:database has no DI dependency.
 * The :app module's Hilt DatabaseModule calls this to provide the singleton.
 */
object DatabaseFactory {
    fun create(context: Context): AppDatabase =
        Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, DATABASE_NAME)
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
}
