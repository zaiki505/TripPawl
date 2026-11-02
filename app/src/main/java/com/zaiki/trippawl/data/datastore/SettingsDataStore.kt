package com.zaiki.trippawl.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.zaiki.trippawl.domain.model.FuelEfficiencyUnit
import com.zaiki.trippawl.domain.model.MaintenanceMarkupMode
import com.zaiki.trippawl.domain.model.PricingSettings
import com.zaiki.trippawl.domain.model.UnpaidReminderInterval
import com.zaiki.trippawl.domain.model.VehicleProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
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
        val SAVED_VEHICLES_JSON = stringPreferencesKey("saved_vehicles_json")
        val ACTIVE_VEHICLE_ID = stringPreferencesKey("active_vehicle_id")
        val AUTO_DETECT_DRIVE_BLUETOOTH = booleanPreferencesKey("auto_detect_drive_bluetooth")
        val TRIGGER_BLUETOOTH_ADDRESSES = stringSetPreferencesKey("trigger_bluetooth_addresses")
        val TRIGGER_BLUETOOTH_NAMES = stringSetPreferencesKey("trigger_bluetooth_names")
        val AUTO_DETECT_SPEED_THRESHOLD_KMH = doublePreferencesKey("auto_detect_speed_threshold_kmh")
        val IS_DARK_THEME = booleanPreferencesKey("is_dark_theme")
        val UNPAID_REMINDER_ENABLED = booleanPreferencesKey("unpaid_reminder_enabled")
        val UNPAID_REMINDER_INTERVAL_DAYS = intPreferencesKey("unpaid_reminder_interval_days")
        val LAST_UNPAID_REMINDER_TIMESTAMP = longPreferencesKey("last_unpaid_reminder_timestamp")
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

            val reminderDays = prefs[Keys.UNPAID_REMINDER_INTERVAL_DAYS] ?: 7
            val reminderInterval = UnpaidReminderInterval.fromDays(reminderDays)

            val vehicleName = prefs[Keys.VEHICLE_NAME]?.ifBlank { PricingSettings.DEFAULT_VEHICLE_NAME }
                ?: PricingSettings.DEFAULT_VEHICLE_NAME
            val preferredFuelType = prefs[Keys.PREFERRED_FUEL_TYPE]?.ifBlank { PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE }
                ?: PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE
            val fuelPricePerLiter = prefs[Keys.FUEL_PRICE_PER_LITER]
                ?: PricingSettings.DEFAULT_FUEL_PRICE_PER_LITER
            val vehicleMileageKmPerL = prefs[Keys.VEHICLE_MILEAGE_KM_PER_L]
                ?: PricingSettings.DEFAULT_VEHICLE_MILEAGE_KM_PER_L
            val maintenanceMarkup = prefs[Keys.MAINTENANCE_MARKUP]
                ?: PricingSettings.DEFAULT_MAINTENANCE_MARKUP

            val parsedVehicles = deserializeVehicles(prefs[Keys.SAVED_VEHICLES_JSON])
            val activeVehicleIdPref = prefs[Keys.ACTIVE_VEHICLE_ID]?.ifBlank { VehicleProfile.DEFAULT_VEHICLE_ID }
                ?: VehicleProfile.DEFAULT_VEHICLE_ID

            val effectiveVehicles = parsedVehicles.ifEmpty {
                listOf(
                    VehicleProfile(
                        id = activeVehicleIdPref,
                        name = vehicleName,
                        preferredFuelType = preferredFuelType,
                        fuelEfficiencyUnit = efficiencyUnit,
                        fuelPricePerLiter = fuelPricePerLiter,
                        vehicleMileageKmPerL = vehicleMileageKmPerL,
                        maintenanceMarkup = maintenanceMarkup,
                        maintenanceMarkupMode = markupMode
                    )
                )
            }
            val activeProfile = effectiveVehicles.firstOrNull { it.id == activeVehicleIdPref }
                ?: effectiveVehicles.first()

            PricingSettings(
                currencySymbol = prefs[Keys.CURRENCY_SYMBOL]?.ifBlank { PricingSettings.DEFAULT_CURRENCY_SYMBOL }
                    ?: PricingSettings.DEFAULT_CURRENCY_SYMBOL,
                vehicleName = activeProfile.name,
                preferredFuelType = activeProfile.preferredFuelType,
                fuelEfficiencyUnit = activeProfile.fuelEfficiencyUnit,
                shortTripCutoffKm = prefs[Keys.SHORT_TRIP_CUTOFF_KM]
                    ?: PricingSettings.DEFAULT_SHORT_TRIP_CUTOFF_KM,
                shortTripFlatRate = prefs[Keys.SHORT_TRIP_FLAT_RATE]
                    ?: PricingSettings.DEFAULT_SHORT_TRIP_FLAT_RATE,
                waiveShortTripTolls = prefs[Keys.WAIVE_SHORT_TRIP_TOLLS]
                    ?: PricingSettings.DEFAULT_WAIVE_SHORT_TRIP_TOLLS,
                enableShortTripTier = prefs[Keys.ENABLE_SHORT_TRIP_TIER] ?: true,
                fuelPricePerLiter = activeProfile.fuelPricePerLiter,
                vehicleMileageKmPerL = activeProfile.vehicleMileageKmPerL,
                maintenanceMarkup = activeProfile.maintenanceMarkup,
                maintenanceMarkupMode = activeProfile.maintenanceMarkupMode,
                savedVehicles = effectiveVehicles,
                activeVehicleId = activeProfile.id,
                autoDetectDriveBluetooth = prefs[Keys.AUTO_DETECT_DRIVE_BLUETOOTH] ?: true,
                triggerBluetoothAddresses = prefs[Keys.TRIGGER_BLUETOOTH_ADDRESSES] ?: emptySet(),
                triggerBluetoothNames = prefs[Keys.TRIGGER_BLUETOOTH_NAMES] ?: emptySet(),
                autoDetectSpeedThresholdKmh = prefs[Keys.AUTO_DETECT_SPEED_THRESHOLD_KMH]
                    ?: PricingSettings.DEFAULT_SPEED_THRESHOLD_KMH,
                isDarkTheme = prefs[Keys.IS_DARK_THEME] ?: true,
                unpaidReminderEnabled = prefs[Keys.UNPAID_REMINDER_ENABLED] ?: true,
                unpaidReminderInterval = reminderInterval,
                lastUnpaidReminderTimestamp = prefs[Keys.LAST_UNPAID_REMINDER_TIMESTAMP] ?: 0L
            )
        }

    suspend fun updateSettings(settings: PricingSettings) {
        dataStore.edit { prefs ->
            val sanitizedVehicleName = settings.vehicleName.trim().ifEmpty {
                PricingSettings.DEFAULT_VEHICLE_NAME
            }
            val sanitizedFuelType = settings.preferredFuelType.trim().ifEmpty {
                PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE
            }
            val sanitizedFuelPrice = settings.fuelPricePerLiter.coerceAtLeast(0.0)
            val sanitizedMileage = settings.vehicleMileageKmPerL.coerceAtLeast(0.1)
            val sanitizedMarkup = settings.maintenanceMarkup.coerceAtLeast(0.0)
            val activeId = settings.activeVehicleId.ifBlank { VehicleProfile.DEFAULT_VEHICLE_ID }

            val updatedProfile = VehicleProfile(
                id = activeId,
                name = sanitizedVehicleName,
                preferredFuelType = sanitizedFuelType,
                fuelEfficiencyUnit = settings.fuelEfficiencyUnit,
                fuelPricePerLiter = sanitizedFuelPrice,
                vehicleMileageKmPerL = sanitizedMileage,
                maintenanceMarkup = sanitizedMarkup,
                maintenanceMarkupMode = settings.maintenanceMarkupMode
            )

            val vehiclesList = if (settings.savedVehicles.isEmpty()) {
                listOf(updatedProfile)
            } else {
                val exists = settings.savedVehicles.any { it.id == activeId }
                if (exists) {
                    settings.savedVehicles.map { if (it.id == activeId) updatedProfile else it }
                } else {
                    settings.savedVehicles + updatedProfile
                }
            }

            prefs[Keys.CURRENCY_SYMBOL] = settings.currencySymbol.trim().ifEmpty {
                PricingSettings.DEFAULT_CURRENCY_SYMBOL
            }
            prefs[Keys.VEHICLE_NAME] = sanitizedVehicleName
            prefs[Keys.PREFERRED_FUEL_TYPE] = sanitizedFuelType
            prefs[Keys.FUEL_EFFICIENCY_UNIT] = settings.fuelEfficiencyUnit.name
            prefs[Keys.SHORT_TRIP_CUTOFF_KM] = settings.shortTripCutoffKm.coerceAtLeast(0.0)
            prefs[Keys.SHORT_TRIP_FLAT_RATE] = settings.shortTripFlatRate.coerceAtLeast(0.0)
            prefs[Keys.WAIVE_SHORT_TRIP_TOLLS] = settings.waiveShortTripTolls
            prefs[Keys.ENABLE_SHORT_TRIP_TIER] = settings.enableShortTripTier
            prefs[Keys.FUEL_PRICE_PER_LITER] = sanitizedFuelPrice
            prefs[Keys.VEHICLE_MILEAGE_KM_PER_L] = sanitizedMileage
            prefs[Keys.MAINTENANCE_MARKUP] = sanitizedMarkup
            prefs[Keys.MAINTENANCE_MARKUP_MODE] = settings.maintenanceMarkupMode.name
            prefs[Keys.SAVED_VEHICLES_JSON] = serializeVehicles(vehiclesList)
            prefs[Keys.ACTIVE_VEHICLE_ID] = activeId
            prefs[Keys.AUTO_DETECT_DRIVE_BLUETOOTH] = settings.autoDetectDriveBluetooth
            prefs[Keys.TRIGGER_BLUETOOTH_ADDRESSES] = settings.triggerBluetoothAddresses
            prefs[Keys.TRIGGER_BLUETOOTH_NAMES] = settings.triggerBluetoothNames
            prefs[Keys.AUTO_DETECT_SPEED_THRESHOLD_KMH] = settings.autoDetectSpeedThresholdKmh.coerceAtLeast(5.0)
            prefs[Keys.IS_DARK_THEME] = settings.isDarkTheme
            prefs[Keys.UNPAID_REMINDER_ENABLED] = settings.unpaidReminderEnabled
            prefs[Keys.UNPAID_REMINDER_INTERVAL_DAYS] = settings.unpaidReminderInterval.days
        }
    }

    suspend fun setLastUnpaidReminderTimestamp(timestamp: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_UNPAID_REMINDER_TIMESTAMP] = timestamp
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

    private fun serializeVehicles(vehicles: List<VehicleProfile>): String {
        val array = JSONArray()
        for (v in vehicles) {
            val obj = JSONObject().apply {
                put("id", v.id)
                put("name", v.name)
                put("preferredFuelType", v.preferredFuelType)
                put("fuelEfficiencyUnit", v.fuelEfficiencyUnit.name)
                put("fuelPricePerLiter", v.fuelPricePerLiter)
                put("vehicleMileageKmPerL", v.vehicleMileageKmPerL)
                put("maintenanceMarkup", v.maintenanceMarkup)
                put("maintenanceMarkupMode", v.maintenanceMarkupMode.name)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun deserializeVehicles(json: String?): List<VehicleProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    val id = obj.optString("id").ifBlank { "car_$i" }
                    val name = obj.optString("name").ifBlank { PricingSettings.DEFAULT_VEHICLE_NAME }
                    val fuelType = obj.optString("preferredFuelType").ifBlank { PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE }
                    val unitName = obj.optString("fuelEfficiencyUnit", FuelEfficiencyUnit.KM_PER_LITER.name)
                    val unit = runCatching { FuelEfficiencyUnit.valueOf(unitName) }
                        .getOrDefault(FuelEfficiencyUnit.KM_PER_LITER)
                    val fuelPrice = obj.optDouble("fuelPricePerLiter", PricingSettings.DEFAULT_FUEL_PRICE_PER_LITER)
                    val mileage = obj.optDouble("vehicleMileageKmPerL", PricingSettings.DEFAULT_VEHICLE_MILEAGE_KM_PER_L)
                    val markup = obj.optDouble("maintenanceMarkup", PricingSettings.DEFAULT_MAINTENANCE_MARKUP)
                    val modeName = obj.optString("maintenanceMarkupMode", MaintenanceMarkupMode.PER_KM.name)
                    val mode = runCatching { MaintenanceMarkupMode.valueOf(modeName) }
                        .getOrDefault(MaintenanceMarkupMode.PER_KM)

                    add(
                        VehicleProfile(
                            id = id,
                            name = name,
                            preferredFuelType = fuelType,
                            fuelEfficiencyUnit = unit,
                            fuelPricePerLiter = fuelPrice.coerceAtLeast(0.0),
                            vehicleMileageKmPerL = mileage.coerceAtLeast(0.1),
                            maintenanceMarkup = markup.coerceAtLeast(0.0),
                            maintenanceMarkupMode = mode
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
