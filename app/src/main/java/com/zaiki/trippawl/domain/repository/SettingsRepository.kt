package com.zaiki.trippawl.domain.repository

import com.zaiki.trippawl.domain.model.PricingSettings
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for reading and updating pricing rules and theme preferences.
 */
interface SettingsRepository {
    val settingsFlow: Flow<PricingSettings>
    suspend fun updateSettings(settings: PricingSettings)
    suspend fun toggleDarkTheme(isDark: Boolean)
    suspend fun resetToDefaults()
    suspend fun setLastUnpaidReminderTimestamp(timestamp: Long)
}
