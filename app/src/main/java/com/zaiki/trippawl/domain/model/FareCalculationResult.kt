package com.zaiki.trippawl.domain.model

/**
 * Holds the complete breakdown of a dynamic fare calculation.
 *
 * @property distanceKm Sanitized non-negative trip distance in kilometers.
 * @property isShortTrip True if short-trip tier is enabled and distanceKm <= shortTripCutoffKm.
 * @property ratePerKm Computed fuel rate per km (fuelPricePerLiter / vehicleMileageKmPerL).
 * @property actualFuelCost Actual estimated fuel consumed for this trip (for monthly fuel tracking).
 * @property baseTripCost Either the short-trip flat fee or the distance * ratePerKm.
 * @property effectiveTollAmount Toll charges included in the total fare.
 * @property parkingAmount Optional manual override for parking charges included in the total fare.
 * @property maintenanceSurcharge Maintenance & wear surcharge added to the trip.
 * @property totalTripFare Total unsplit cost of the trip (base + toll + parking + maintenance).
 * @property passengerCount Number of riders splitting the trip (at least 1).
 * @property totalOccupants Number of passengers + 1 driver.
 * @property riderShare Final per-person share = totalTripFare / (passengerCount + 1).
 */
data class FareCalculationResult(
    val distanceKm: Double = 0.0,
    val isShortTrip: Boolean = true,
    val ratePerKm: Double = 0.0,
    val actualFuelCost: Double = 0.0,
    val baseTripCost: Double = 0.0,
    val effectiveTollAmount: Double = 0.0,
    val parkingAmount: Double = 0.0,
    val maintenanceSurcharge: Double = 0.0,
    val totalTripFare: Double = 0.0,
    val passengerCount: Int = 1,
    val totalOccupants: Int = 2,
    val riderShare: Double = 0.0
)
