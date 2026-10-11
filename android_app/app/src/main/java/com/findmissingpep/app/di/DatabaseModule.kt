package com.findmissingpep.app.di

import android.content.Context
import androidx.room.Room
import com.findmissingpep.app.data.local.FmpDatabase
import com.findmissingpep.app.data.local.dao.CaseDao
import com.findmissingpep.app.data.local.dao.NotificationDao
import com.findmissingpep.app.data.local.dao.SightingDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

import androidx.room.RoomDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FmpDatabase =
        Room.databaseBuilder(
            context,
            FmpDatabase::class.java,
            "fmp_database"
        )
            .setJournalMode(RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideCaseDao(database: FmpDatabase): CaseDao = database.caseDao()

    @Provides
    fun provideSightingDao(database: FmpDatabase): SightingDao = database.sightingDao()

    @Provides
    fun provideNotificationDao(database: FmpDatabase): NotificationDao = database.notificationDao()
}
