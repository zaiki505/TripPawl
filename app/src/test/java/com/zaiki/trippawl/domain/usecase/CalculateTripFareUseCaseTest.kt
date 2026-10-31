package com.zaiki.trippawl.domain.usecase

import com.zaiki.trippawl.domain.model.MaintenanceMarkupMode
import com.zaiki.trippawl.domain.model.PricingSettings
import com.zaiki.trippawl.domain.model.Rider
import com.zaiki.trippawl.domain.model.Trip
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying [CalculateTripFareUseCase] and [FormatShareSummaryUseCase]
 * across short-trip flat per-passenger tiers, long-trip formulas, multi-passenger splitting, and edge cases.
 */
class CalculateTripFareUseCaseTest {

    private lateinit var calculateTripFare: CalculateTripFareUseCase
    private lateinit var formatShareSummary: FormatShareSummaryUseCase

    private val defaultSettings = PricingSettings(
        currencySymbol = "RM",
        shortTripCutoffKm = 10.0,
        shortTripFlatRate = 3.00,
        waiveShortTripTolls = false,
        fuelPricePerLiter = 1.99,
        vehicleMileageKmPerL = 12.0,
        maintenanceMarkup = 0.10,
        maintenanceMarkupMode = MaintenanceMarkupMode.PER_KM
    )

    @Before
    fun setUp() {
        calculateTripFare = CalculateTripFareUseCase()
        formatShareSummary = FormatShareSummaryUseCase()
    }

    @Test
    fun `zero or negative distance returns zero fare safely`() {
        val zeroResult = calculateTripFare(distanceKm = 0.0, settings = defaultSettings)
        assertEquals(0.0, zeroResult.totalTripFare, 0.001)
        assertEquals(0.0, zeroResult.riderShare, 0.001)

        val negativeResult = calculateTripFare(distanceKm = -15.0, settings = defaultSettings)
        assertEquals(0.0, negativeResult.totalTripFare, 0.001)
        assertEquals(0.0, negativeResult.riderShare, 0.001)
    }

    @Test
    fun `short trip charges flat rate per passenger directly`() {
        // 8.0 km <= 10.0 km cutoff -> every passenger pays RM 3.00 flat for 1 trip
        val singlePaxResult = calculateTripFare(
            distanceKm = 8.0,
            tollAmount = 0.0,
            passengerCount = 1,
            settings = defaultSettings
        )

        assertTrue(singlePaxResult.isShortTrip)
        assertEquals(3.00, singlePaxResult.riderShare, 0.001)
        assertEquals(3.00, singlePaxResult.totalTripFare, 0.001)

        // With 3 passengers on a short trip, every passenger still pays RM 3.00 flat (total RM 9.00)
        val multiPaxResult = calculateTripFare(
            distanceKm = 8.0,
            tollAmount = 0.0,
            passengerCount = 3,
            settings = defaultSettings
        )

        assertTrue(multiPaxResult.isShortTrip)
        assertEquals(3.00, multiPaxResult.riderShare, 0.001)
        assertEquals(9.00, multiPaxResult.totalTripFare, 0.001)
    }

    @Test
    fun `short trip waives tolls when waiveShortTripTolls is enabled`() {
        val waiveSettings = defaultSettings.copy(waiveShortTripTolls = true)
        val result = calculateTripFare(
            distanceKm = 10.0, // exact cutoff boundary
            tollAmount = 2.50,
            passengerCount = 1,
            settings = waiveSettings
        )

        assertTrue(result.isShortTrip)
        assertEquals(0.0, result.effectiveTollAmount, 0.001)
        assertEquals(3.00, result.totalTripFare, 0.001)
        assertEquals(3.00, result.riderShare, 0.001)
    }

    @Test
    fun `long trip calculates fuel rate, tolls, maintenance surcharge, and multi-passenger split`() {
        // Distance = 24.0 km (> 10.0 km cutoff)
        // Rate Per KM = 1.99 / 12.0 = 0.1658333...
        // Fuel Cost = 24.0 * (1.99 / 12.0) = 3.98
        // Maintenance (Per KM 0.10) = 24.0 * 0.10 = 2.40
        // Toll = 3.62
        // Total Trip Fare = 3.98 + 2.40 + 3.62 = 10.00
        // 3 Passengers + 1 Driver = 4 occupants -> Rider Share = 10.00 / 4 = 2.50
        val result = calculateTripFare(
            distanceKm = 24.0,
            tollAmount = 3.62,
            passengerCount = 3,
            settings = defaultSettings
        )

        assertFalse(result.isShortTrip)
        assertEquals(3.98, result.baseTripCost, 0.001)
        assertEquals(2.40, result.maintenanceSurcharge, 0.001)
        assertEquals(3.62, result.effectiveTollAmount, 0.001)
        assertEquals(10.00, result.totalTripFare, 0.001)
        assertEquals(4, result.totalOccupants)
        assertEquals(2.50, result.riderShare, 0.001)
    }

    @Test
    fun `division by zero in vehicle mileage is handled gracefully`() {
        val zeroMileageSettings = defaultSettings.copy(vehicleMileageKmPerL = 0.0)
        val result = calculateTripFare(
            distanceKm = 20.0,
            tollAmount = 4.00,
            passengerCount = 1,
            settings = zeroMileageSettings
        )

        assertFalse(result.isShortTrip)
        assertEquals(0.0, result.ratePerKm, 0.001)
        assertEquals(0.0, result.baseTripCost, 0.001)
        // Only toll (4.00) + maintenance (20 * 0.10 = 2.00) = 6.00 -> / 2 = 3.00
        assertEquals(6.00, result.totalTripFare, 0.001)
        assertEquals(3.00, result.riderShare, 0.001)
    }

    @Test
    fun `share summary formatter produces expected headline and breakdown`() {
        val alex = Rider(id = 1L, name = "Alex")
        val trips = listOf(
            Trip(id = 1, riderId = 1, riderName = "Alex", distanceKm = 8.0, isShortTrip = true, tollAmount = 0.0, calculatedFare = 4.50, isPaid = false),
            Trip(id = 2, riderId = 1, riderName = "Alex", distanceKm = 22.0, isShortTrip = false, tollAmount = 2.0, calculatedFare = 10.00, isPaid = false),
            Trip(id = 3, riderId = 1, riderName = "Alex", distanceKm = 25.0, isShortTrip = false, tollAmount = 0.0, calculatedFare = 10.00, isPaid = false)
        )

        val summary = formatShareSummary(rider = alex, trips = trips, currencySymbol = "RM")
        assertTrue(
            summary.startsWith("Hi Alex, here is the carpool summary: 3 unpaid trips, Total: RM 24.50")
        )
    }
}
