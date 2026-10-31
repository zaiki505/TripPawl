package com.zaiki.trippawl.data.repository

import com.zaiki.trippawl.data.datastore.SettingsDataStore
import com.zaiki.trippawl.domain.model.PricingSettings
import com.zaiki.trippawl.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [SettingsRepository] backed by [SettingsDataStore].
 */
@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val settingsDataStore: SettingsDataStore
) : SettingsRepository {

    override val settingsFlow: Flow<PricingSettings> = settingsDataStore.settingsFlow

    override suspend fun updateSettings(settings: PricingSettings) {
        settingsDataStore.updateSettings(settings)
    }

    override suspend fun toggleDarkTheme(isDark: Boolean) {
        settingsDataStore.setDarkTheme(isDark)
    }

    override suspend fun resetToDefaults() {
        settingsDataStore.resetToDefaults()
    }
}
