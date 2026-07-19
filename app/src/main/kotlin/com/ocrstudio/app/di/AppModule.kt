package com.ocrstudio.app.di

import android.content.Context
import androidx.work.WorkManager
import com.ocrstudio.core.common.AppContext
import com.ocrstudio.engine.correction.Dictionary
import com.ocrstudio.engine.correction.RuleEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @AppContext
    fun provideAppContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideRuleEngine(@ApplicationContext context: Context): RuleEngine =
        RuleEngine(Dictionary.load(context))
}
