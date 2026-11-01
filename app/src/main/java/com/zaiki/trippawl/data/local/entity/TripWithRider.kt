package com.zaiki.trippawl.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation
import com.zaiki.trippawl.domain.model.RecurrenceUnit
import com.zaiki.trippawl.domain.model.RiderDebtSummary
import com.zaiki.trippawl.domain.model.Trip

/**
 * Room relation POJO joining a [TripEntity] with its parent [RiderEntity].
 */
data class TripWithRider(
    @Embedded
    val trip: TripEntity,
    @Relation(
        parentColumn = "riderId",
        entityColumn = "id"
    )
    val rider: RiderEntity
) {
    fun toDomainModel(): Trip = Trip(
        id = trip.id,
        tripGroupId = trip.tripGroupId,
        tripName = trip.tripName,
        riderId = trip.riderId,
        riderName = rider.name,
        riderPhone = rider.phone,
        distanceKm = trip.distanceKm,
        isShortTrip = trip.isShortTrip,
        tollAmount = trip.tollAmount,
        parkingAmount = trip.parkingAmount,
        calculatedFare = trip.calculatedFare,
        isPaid = trip.isPaid,
        isWaived = trip.isWaived,
        tripDate = trip.tripDate,
        arrivalDate = trip.arrivalDate,
        settledDate = trip.settledDate,
        passengerCount = trip.passengerCount,
        fuelCost = trip.fuelCost,
        maintenanceAmount = trip.maintenanceAmount,
        totalTripCost = trip.totalTripCost,
        gpsPointsCount = trip.gpsPointsCount,
        isAutoTracked = trip.isAutoTracked,
        customFuelMileageKmPerL = trip.customFuelMileageKmPerL,
        customFuelPricePerLiter = trip.customFuelPricePerLiter,
        customMaintenanceMarkup = trip.customMaintenanceMarkup,
        tripNotes = trip.tripNotes,
        isRecurring = trip.isRecurring,
        recurrenceInterval = trip.recurrenceInterval,
        recurrenceUnit = RecurrenceUnit.fromNameOrNone(trip.recurrenceUnit),
        nextOccurrenceDate = trip.nextOccurrenceDate,
        recurrenceEndDate = trip.recurrenceEndDate
    )
}

/**
 * Projection POJO for aggregated rider debt SQL queries.
 */
data class RiderDebtSummaryTuple(
    @Embedded
    val rider: RiderEntity,
    val unpaidBalance: Double,
    val unpaidTripCount: Int,
    val settledAmount: Double,
    val totalTripsCount: Int
) {
    fun toDomainModel(): RiderDebtSummary = RiderDebtSummary(
        rider = rider.toDomainModel(),
        unpaidBalance = unpaidBalance,
        unpaidTripCount = unpaidTripCount,
        settledAmount = settledAmount,
        totalTripsCount = totalTripsCount
    )
}
