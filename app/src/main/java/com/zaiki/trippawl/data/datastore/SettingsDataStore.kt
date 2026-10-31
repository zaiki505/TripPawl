package com.zaiki.trippawl.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.zaiki.trippawl.domain.model.FuelEfficiencyUnit
import com.zaiki.trippawl.domain.model.MaintenanceMarkupMode
import com.zaiki.trippawl.domain.model.PricingSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages persistent Vehicle & Fuel Telemetry configuration, pricing rules,
 * and auto-tracking settings using Jetpack DataStore Preferences.
 */
@Singleton
class SettingsDataStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val CURRENCY_SYMBOL = stringPreferencesKey("currency_symbol")
        val VEHICLE_NAME = stringPreferencesKey("vehicle_name")
        val PREFERRED_FUEL_TYPE = stringPreferencesKey("preferred_fuel_type")
        val FUEL_EFFICIENCY_UNIT = stringPreferencesKey("fuel_efficiency_unit")
        val SHORT_TRIP_CUTOFF_KM = doublePreferencesKey("short_trip_cutoff_km")
        val SHORT_TRIP_FLAT_RATE = doublePreferencesKey("short_trip_flat_rate")
        val WAIVE_SHORT_TRIP_TOLLS = booleanPreferencesKey("waive_short_trip_tolls")
        val ENABLE_SHORT_TRIP_TIER = booleanPreferencesKey("enable_short_trip_tier")
        val FUEL_PRICE_PER_LITER = doublePreferencesKey("fuel_price_per_liter")
        val VEHICLE_MILEAGE_KM_PER_L = doublePreferencesKey("vehicle_mileage_km_per_l")
        val MAINTENANCE_MARKUP = doublePreferencesKey("maintenance_markup")
        val MAINTENANCE_MARKUP_MODE = stringPreferencesKey("maintenance_markup_mode")
        val AUTO_DETECT_DRIVE_BLUETOOTH = booleanPreferencesKey("auto_detect_drive_bluetooth")
        val TRIGGER_BLUETOOTH_ADDRESSES = stringSetPreferencesKey("trigger_bluetooth_addresses")
        val TRIGGER_BLUETOOTH_NAMES = stringSetPreferencesKey("trigger_bluetooth_names")
        val AUTO_DETECT_SPEED_THRESHOLD_KMH = doublePreferencesKey("auto_detect_speed_threshold_kmh")
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
    }

    val settingsFlow: Flow<PricingSettings> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val modeName = prefs[Keys.MAINTENANCE_MARKUP_MODE] ?: MaintenanceMarkupMode.PER_KM.name
            val markupMode = runCatching { MaintenanceMarkupMode.valueOf(modeName) }
                .getOrDefault(MaintenanceMarkupMode.PER_KM)

            val unitName = prefs[Keys.FUEL_EFFICIENCY_UNIT] ?: FuelEfficiencyUnit.KM_PER_LITER.name
            val efficiencyUnit = runCatching { FuelEfficiencyUnit.valueOf(unitName) }
                .getOrDefault(FuelEfficiencyUnit.KM_PER_LITER)

            PricingSettings(
                currencySymbol = prefs[Keys.CURRENCY_SYMBOL]?.ifBlank { PricingSettings.DEFAULT_CURRENCY_SYMBOL }
                    ?: PricingSettings.DEFAULT_CURRENCY_SYMBOL,
                vehicleName = prefs[Keys.VEHICLE_NAME]?.ifBlank { PricingSettings.DEFAULT_VEHICLE_NAME }
                    ?: PricingSettings.DEFAULT_VEHICLE_NAME,
                preferredFuelType = prefs[Keys.PREFERRED_FUEL_TYPE]?.ifBlank { PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE }
                    ?: PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE,
                fuelEfficiencyUnit = efficiencyUnit,
                shortTripCutoffKm = prefs[Keys.SHORT_TRIP_CUTOFF_KM]
                    ?: PricingSettings.DEFAULT_SHORT_TRIP_CUTOFF_KM,
                shortTripFlatRate = prefs[Keys.SHORT_TRIP_FLAT_RATE]
                    ?: PricingSettings.DEFAULT_SHORT_TRIP_FLAT_RATE,
                waiveShortTripTolls = prefs[Keys.WAIVE_SHORT_TRIP_TOLLS]
                    ?: PricingSettings.DEFAULT_WAIVE_SHORT_TRIP_TOLLS,
                enableShortTripTier = prefs[Keys.ENABLE_SHORT_TRIP_TIER] ?: true,
                fuelPricePerLiter = prefs[Keys.FUEL_PRICE_PER_LITER]
                    ?: PricingSettings.DEFAULT_FUEL_PRICE_PER_LITER,
                vehicleMileageKmPerL = prefs[Keys.VEHICLE_MILEAGE_KM_PER_L]
                    ?: PricingSettings.DEFAULT_VEHICLE_MILEAGE_KM_PER_L,
                maintenanceMarkup = prefs[Keys.MAINTENANCE_MARKUP]
                    ?: PricingSettings.DEFAULT_MAINTENANCE_MARKUP,
                maintenanceMarkupMode = markupMode,
                autoDetectDriveBluetooth = prefs[Keys.AUTO_DETECT_DRIVE_BLUETOOTH] ?: true,
                triggerBluetoothAddresses = prefs[Keys.TRIGGER_BLUETOOTH_ADDRESSES] ?: emptySet(),
                triggerBluetoothNames = prefs[Keys.TRIGGER_BLUETOOTH_NAMES] ?: emptySet(),
                autoDetectSpeedThresholdKmh = prefs[Keys.AUTO_DETECT_SPEED_THRESHOLD_KMH]
                    ?: PricingSettings.DEFAULT_SPEED_THRESHOLD_KMH,
                isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true
            )
        }

    suspend fun updateSettings(settings: PricingSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.CURRENCY_SYMBOL] = settings.currencySymbol.trim().ifEmpty {
                PricingSettings.DEFAULT_CURRENCY_SYMBOL
            }
            prefs[Keys.VEHICLE_NAME] = settings.vehicleName.trim().ifEmpty {
                PricingSettings.DEFAULT_VEHICLE_NAME
            }
            prefs[Keys.PREFERRED_FUEL_TYPE] = settings.preferredFuelType.trim().ifEmpty {
                PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE
            }
            prefs[Keys.FUEL_EFFICIENCY_UNIT] = settings.fuelEfficiencyUnit.name
            prefs[Keys.SHORT_TRIP_CUTOFF_KM] = settings.shortTripCutoffKm.coerceAtLeast(0.0)
            prefs[Keys.SHORT_TRIP_FLAT_RATE] = settings.shortTripFlatRate.coerceAtLeast(0.0)
            prefs[Keys.WAIVE_SHORT_TRIP_TOLLS] = settings.waiveShortTripTolls
            prefs[Keys.ENABLE_SHORT_TRIP_TIER] = settings.enableShortTripTier
            prefs[Keys.FUEL_PRICE_PER_LITER] = settings.fuelPricePerLiter.coerceAtLeast(0.0)
            prefs[Keys.VEHICLE_MILEAGE_KM_PER_L] = settings.vehicleMileageKmPerL.coerceAtLeast(0.1)
            prefs[Keys.MAINTENANCE_MARKUP] = settings.maintenanceMarkup.coerceAtLeast(0.0)
            prefs[Keys.MAINTENANCE_MARKUP_MODE] = settings.maintenanceMarkupMode.name
            prefs[Keys.AUTO_DETECT_DRIVE_BLUETOOTH] = settings.autoDetectDriveBluetooth
            prefs[Keys.TRIGGER_BLUETOOTH_ADDRESSES] = settings.triggerBluetoothAddresses
            prefs[Keys.TRIGGER_BLUETOOTH_NAMES] = settings.triggerBluetoothNames
            prefs[Keys.AUTO_DETECT_SPEED_THRESHOLD_KMH] = settings.autoDetectSpeedThresholdKmh.coerceAtLeast(5.0)
            prefs[Keys.IS_DARK_THEME] = settings.isDarkTheme
        }
    }

    suspend fun setDarkTheme(isDark: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.IS_DARK_THEME] = isDark
        }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            val currentTheme = prefs[Keys.IS_DARK_THEME] ?: true
            prefs.clear()
            prefs[Keys.IS_DARK_THEME] = currentTheme
        }
    }
}
