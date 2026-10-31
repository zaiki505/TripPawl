package com.zaiki.trippawl.di

import com.zaiki.trippawl.data.repository.RiderRepositoryImpl
import com.zaiki.trippawl.data.repository.SettingsRepositoryImpl
import com.zaiki.trippawl.data.repository.TripRepositoryImpl
import com.zaiki.trippawl.domain.repository.RiderRepository
import com.zaiki.trippawl.domain.repository.SettingsRepository
import com.zaiki.trippawl.domain.repository.TripRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module binding Domain Repository interfaces to their Data Layer implementations.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRiderRepository(
        impl: RiderRepositoryImpl
    ): RiderRepository

    @Binds
    @Singleton
    abstract fun bindTripRepository(
        impl: TripRepositoryImpl
    ): TripRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        impl: SettingsRepositoryImpl
    ): SettingsRepository
}
