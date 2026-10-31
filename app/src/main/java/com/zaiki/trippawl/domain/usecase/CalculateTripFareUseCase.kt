package com.zaiki.trippawl.domain.usecase

import com.zaiki.trippawl.domain.model.FareCalculationResult
import com.zaiki.trippawl.domain.model.MaintenanceMarkupMode
import com.zaiki.trippawl.domain.model.PricingSettings
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Pure domain UseCase that calculates carpool trip fares and per-passenger splits:
 *
 * 1. **Short-Trip Tier** (`enableShortTripTier && distanceKm <= shortTripCutoffKm`):
 *    - **Every passenger pays the flat rate (`shortTripFlatRate`, e.g., RM 3.00) for 1 trip.**
 *    - If non-waived tolls or parking overrides are added, those extra charges are split across occupants.
 *
 * 2. **Long-Trip / Distance Formula Tier**:
 *    - `Cost per KM = Fuel Price per Liter / Fuel Efficiency in KM per Liter`
 *    - `Total Trip Cost = (Distance Driven (km) * Cost per KM) + Maintenance + Tolls + Parking`
 *    - `Share per Person = Total Trip Cost / Total Occupants (Selected Passengers + 1 Driver)`
 */
class CalculateTripFareUseCase @Inject constructor() {

    operator fun invoke(
        distanceKm: Double,
        tollAmount: Double = 0.0,
        passengerCount: Int = 1,
        settings: PricingSettings = PricingSettings(),
        parkingAmount: Double = 0.0
    ): FareCalculationResult {
        val safeDistance = if (distanceKm.isNaN() || distanceKm.isInfinite()) 0.0 else distanceKm.coerceAtLeast(0.0)
        val safeToll = if (tollAmount.isNaN() || tollAmount.isInfinite()) 0.0 else tollAmount.coerceAtLeast(0.0)
        val safeParking = if (parkingAmount.isNaN() || parkingAmount.isInfinite()) 0.0 else parkingAmount.coerceAtLeast(0.0)
        val safePassengers = passengerCount.coerceAtLeast(1)
        val totalOccupants = safePassengers + 1 // Number of Passengers + 1 Driver

        val safeFuelPrice = settings.fuelPricePerLiter.coerceAtLeast(0.0)
        val safeMileage = settings.vehicleMileageKmPerL
        val safeCutoff = settings.shortTripCutoffKm.coerceAtLeast(0.0)
        val safeFlatRate = settings.shortTripFlatRate.coerceAtLeast(0.0)
        val safeMarkup = settings.maintenanceMarkup.coerceAtLeast(0.0)

        // Cost per KM = Fuel Price per Liter / Fuel Efficiency in KM per Liter
        val rawRatePerKm = if (safeMileage > 0.0) {
            safeFuelPrice / safeMileage
        } else {
            0.0
        }

        if (safeDistance <= 0.0) {
            return FareCalculationResult(
                distanceKm = 0.0,
                isShortTrip = settings.enableShortTripTier,
                ratePerKm = roundCurrency(rawRatePerKm),
                actualFuelCost = 0.0,
                baseTripCost = 0.0,
                effectiveTollAmount = 0.0,
                parkingAmount = 0.0,
                maintenanceSurcharge = 0.0,
                totalTripFare = 0.0,
                passengerCount = safePassengers,
                totalOccupants = totalOccupants,
                riderShare = 0.0
            )
        }

        val rawActualFuelCost = safeDistance * rawRatePerKm
        val isShortTrip = settings.enableShortTripTier && (safeDistance <= safeCutoff)

        val baseTripCost: Double
        val effectiveToll: Double
        val maintenanceSurcharge: Double
        val rawTotalTripFare: Double
        val rawRiderShare: Double

        if (isShortTrip) {
            // Short-Trip Flat Tier: Every passenger pays RM XX (safeFlatRate) for 1 trip
            effectiveToll = if (settings.waiveShortTripTolls) 0.0 else safeToll
            maintenanceSurcharge = 0.0
            baseTripCost = safeFlatRate * safePassengers
            val extraOverridesPerPerson = (effectiveToll + safeParking) / totalOccupants.toDouble()
            rawRiderShare = safeFlatRate + extraOverridesPerPerson
            rawTotalTripFare = baseTripCost + effectiveToll + safeParking
        } else {
            // Distance Formula Tier: (Distance * Cost/KM + Maintenance + Tolls + Parking) / Total Occupants
            baseTripCost = rawActualFuelCost
            effectiveToll = safeToll
            maintenanceSurcharge = when (settings.maintenanceMarkupMode) {
                MaintenanceMarkupMode.PER_KM -> safeDistance * safeMarkup
                MaintenanceMarkupMode.FLAT_PER_TRIP -> safeMarkup
                MaintenanceMarkupMode.PERCENTAGE -> baseTripCost * (safeMarkup / 100.0)
            }
            rawTotalTripFare = baseTripCost + effectiveToll + safeParking + maintenanceSurcharge
            rawRiderShare = rawTotalTripFare / totalOccupants.toDouble()
        }

        return FareCalculationResult(
            distanceKm = roundCurrency(safeDistance),
            isShortTrip = isShortTrip,
            ratePerKm = roundCurrency(rawRatePerKm),
            actualFuelCost = roundCurrency(rawActualFuelCost),
            baseTripCost = roundCurrency(baseTripCost),
            effectiveTollAmount = roundCurrency(effectiveToll),
            parkingAmount = roundCurrency(safeParking),
            maintenanceSurcharge = roundCurrency(maintenanceSurcharge),
            totalTripFare = roundCurrency(rawTotalTripFare),
            passengerCount = safePassengers,
            totalOccupants = totalOccupants,
            riderShare = roundCurrency(rawRiderShare)
        )
    }

    private fun roundCurrency(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal.valueOf(value)
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
    }
}
