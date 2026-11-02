package com.zaiki.trippawl.domain.model

import kotlin.math.roundToInt

/**
 * Defines how the maintenance surcharge is applied on trip calculations.
 */
enum class MaintenanceMarkupMode(val displayName: String, val unitHint: String) {
    PER_KM("Per KM", "per km"),
    FLAT_PER_TRIP("Flat / Trip", "per trip"),
    PERCENTAGE("Percentage (%)", "% of fuel")
}

/**
 * Defines whether the user enters vehicle fuel consumption in `km/L` or `L/100km`.
 */
enum class FuelEfficiencyUnit(val label: String, val suffix: String) {
    KM_PER_LITER("km / Liter", "km/L"),
    LITERS_PER_100KM("L / 100km", "L/100km")
}

/**
 * Defines the interval for unpaid trip reminder alerts.
 */
enum class UnpaidReminderInterval(val days: Int, val label: String) {
    DAYS_7(7, "7 Days"),
    DAYS_14(14, "14 Days"),
    DAYS_30(30, "30 Days"),
    WEEKS_14(98, "14 Weeks");

    val millis: Long get() = days.toLong() * 24L * 60L * 60L * 1000L

    companion object {
        fun fromDays(days: Int): UnpaidReminderInterval =
            entries.firstOrNull { it.days == days } ?: DAYS_7
    }
}

/**
 * Represents an individual car / vehicle profile configured by the driver in Settings.
 */
data class VehicleProfile(
    val id: String = DEFAULT_VEHICLE_ID,
    val name: String = PricingSettings.DEFAULT_VEHICLE_NAME,
    val preferredFuelType: String = PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE,
    val fuelEfficiencyUnit: FuelEfficiencyUnit = FuelEfficiencyUnit.KM_PER_LITER,
    val fuelPricePerLiter: Double = PricingSettings.DEFAULT_FUEL_PRICE_PER_LITER,
    val vehicleMileageKmPerL: Double = PricingSettings.DEFAULT_VEHICLE_MILEAGE_KM_PER_L,
    val maintenanceMarkup: Double = PricingSettings.DEFAULT_MAINTENANCE_MARKUP,
    val maintenanceMarkupMode: MaintenanceMarkupMode = MaintenanceMarkupMode.PER_KM
) {
    val ratePerKm: Double
        get() = if (vehicleMileageKmPerL > 0.0 && fuelPricePerLiter >= 0.0) {
            fuelPricePerLiter / vehicleMileageKmPerL
        } else {
            0.0
        }

    val litersPer100Km: Double
        get() = if (vehicleMileageKmPerL > 0.0) {
            ((100.0 / vehicleMileageKmPerL) * 100.0).roundToInt() / 100.0
        } else {
            0.0
        }

    companion object {
        const val DEFAULT_VEHICLE_ID = "car_default"
    }
}

/**
 * User-configurable Vehicle & Fuel Telemetry, pricing, and auto-tracking preferences stored in DataStore.
 */
data class PricingSettings(
    val currencySymbol: String = DEFAULT_CURRENCY_SYMBOL,
    val vehicleName: String = DEFAULT_VEHICLE_NAME,
    val preferredFuelType: String = DEFAULT_PREFERRED_FUEL_TYPE,
    val fuelEfficiencyUnit: FuelEfficiencyUnit = FuelEfficiencyUnit.KM_PER_LITER,
    val shortTripCutoffKm: Double = DEFAULT_SHORT_TRIP_CUTOFF_KM,
    val shortTripFlatRate: Double = DEFAULT_SHORT_TRIP_FLAT_RATE,
    val waiveShortTripTolls: Boolean = DEFAULT_WAIVE_SHORT_TRIP_TOLLS,
    val enableShortTripTier: Boolean = true,
    val fuelPricePerLiter: Double = DEFAULT_FUEL_PRICE_PER_LITER,
    val vehicleMileageKmPerL: Double = DEFAULT_VEHICLE_MILEAGE_KM_PER_L,
    val maintenanceMarkup: Double = DEFAULT_MAINTENANCE_MARKUP,
    val maintenanceMarkupMode: MaintenanceMarkupMode = MaintenanceMarkupMode.PER_KM,
    val savedVehicles: List<VehicleProfile> = emptyList(),
    val activeVehicleId: String = VehicleProfile.DEFAULT_VEHICLE_ID,
    val autoDetectDriveBluetooth: Boolean = true,
    val triggerBluetoothAddresses: Set<String> = emptySet(),
    val triggerBluetoothNames: Set<String> = emptySet(),
    val autoDetectSpeedThresholdKmh: Double = DEFAULT_SPEED_THRESHOLD_KMH,
    val isDarkTheme: Boolean = true,
    val unpaidReminderEnabled: Boolean = true,
    val unpaidReminderInterval: UnpaidReminderInterval = UnpaidReminderInterval.DAYS_7,
    val lastUnpaidReminderTimestamp: Long = 0L
) {
    /**
     * Guaranteed non-empty list of configured car profiles.
     */
    val effectiveVehicles: List<VehicleProfile>
        get() = savedVehicles.ifEmpty {
            listOf(
                VehicleProfile(
                    id = activeVehicleId.ifBlank { VehicleProfile.DEFAULT_VEHICLE_ID },
                    name = vehicleName.ifBlank { DEFAULT_VEHICLE_NAME },
                    preferredFuelType = preferredFuelType.ifBlank { DEFAULT_PREFERRED_FUEL_TYPE },
                    fuelEfficiencyUnit = fuelEfficiencyUnit,
                    fuelPricePerLiter = fuelPricePerLiter,
                    vehicleMileageKmPerL = vehicleMileageKmPerL,
                    maintenanceMarkup = maintenanceMarkup,
                    maintenanceMarkupMode = maintenanceMarkupMode
                )
            )
        }

    /**
     * Currently active car profile.
     */
    val activeVehicleProfile: VehicleProfile
        get() = effectiveVehicles.firstOrNull { it.id == activeVehicleId } ?: effectiveVehicles.first()

    /**
     * Returns a copy of [PricingSettings] with the telemetry parameters of [profile] applied.
     */
    fun withVehicleProfile(profile: VehicleProfile): PricingSettings = copy(
        vehicleName = profile.name,
        preferredFuelType = profile.preferredFuelType,
        fuelEfficiencyUnit = profile.fuelEfficiencyUnit,
        fuelPricePerLiter = profile.fuelPricePerLiter,
        vehicleMileageKmPerL = profile.vehicleMileageKmPerL,
        maintenanceMarkup = profile.maintenanceMarkup,
        maintenanceMarkupMode = profile.maintenanceMarkupMode,
        activeVehicleId = profile.id
    )

    /**
     * Effective fuel cost rate per kilometer:
     * Cost per KM = Fuel Price per Liter / Fuel Efficiency in KM per Liter.
     * Guards against division by zero or negative fuel economy.
     */
    val ratePerKm: Double
        get() = if (vehicleMileageKmPerL > 0.0 && fuelPricePerLiter >= 0.0) {
            fuelPricePerLiter / vehicleMileageKmPerL
        } else {
            0.0
        }

    val ratePerKmRounded: Double
        get() = (ratePerKm * 100.0).roundToInt() / 100.0

    /**
     * Fuel consumption expressed in L/100km for display when [FuelEfficiencyUnit.LITERS_PER_100KM] is selected.
     */
    val litersPer100Km: Double
        get() = if (vehicleMileageKmPerL > 0.0) {
            ((100.0 / vehicleMileageKmPerL) * 100.0).roundToInt() / 100.0
        } else {
            0.0
        }

    companion object {
        const val DEFAULT_CURRENCY_SYMBOL = "RM"
        const val DEFAULT_VEHICLE_NAME = "My Car"
        const val DEFAULT_PREFERRED_FUEL_TYPE = "RON 95"
        const val DEFAULT_SHORT_TRIP_CUTOFF_KM = 10.0
        const val DEFAULT_SHORT_TRIP_FLAT_RATE = 3.00
        const val DEFAULT_WAIVE_SHORT_TRIP_TOLLS = false
        const val DEFAULT_FUEL_PRICE_PER_LITER = 1.99
        const val DEFAULT_VEHICLE_MILEAGE_KM_PER_L = 12.0
        const val DEFAULT_MAINTENANCE_MARKUP = 0.10
        const val DEFAULT_SPEED_THRESHOLD_KMH = 15.0
    }
}
