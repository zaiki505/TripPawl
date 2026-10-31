package com.zaiki.trippawl.di

import android.content.Context
import androidx.room.Room
import com.zaiki.trippawl.data.local.TripPawlDatabase
import com.zaiki.trippawl.data.local.dao.DriveLogDao
import com.zaiki.trippawl.data.local.dao.RiderDao
import com.zaiki.trippawl.data.local.dao.TripDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Room [TripPawlDatabase] instance and DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideTripPawlDatabase(
        @ApplicationContext context: Context
    ): TripPawlDatabase {
        return Room.databaseBuilder(
            context,
            TripPawlDatabase::class.java,
            TripPawlDatabase.DATABASE_NAME
        )
            // Explicit migrations preserve user ledger data; destructive fallback only on downgrade.
            .addMigrations(TripPawlDatabase.MIGRATION_4_5)
            .fallbackToDestructiveMigrationFrom(1, 2, 3)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    @Singleton
    fun provideDriveLogDao(database: TripPawlDatabase): DriveLogDao = database.driveLogDao()

    @Provides
    @Singleton
    fun provideRiderDao(database: TripPawlDatabase): RiderDao = database.riderDao()

    @Provides
    @Singleton
    fun provideTripDao(database: TripPawlDatabase): TripDao = database.tripDao()
}
