package com.zaiki.trippawl.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zaiki.trippawl.domain.model.Trip

/**
 * Room Entity representing a carpool trip passenger share linked to a [RiderEntity].
 *
 * Stores core fare & split fields alongside trip group identifier, custom trip name,
 * per-trip fuel/telemetry overrides, GPS telemetry (`arrivalDate`, `gpsPointsCount`,
 * `isAutoTracked`) and manual overrides (`tollAmount`, `parkingAmount`).
 */
@Entity(
    tableName = "trips",
    foreignKeys = [
        ForeignKey(
            entity = RiderEntity::class,
            parentColumns = ["id"],
            childColumns = ["riderId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["riderId"]),
        Index(value = ["tripDate"]),
        Index(value = ["isPaid"]),
        Index(value = ["tripGroupId"])
    ]
)
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val tripGroupId: String = "",
    val tripName: String = "",
    val riderId: Long,
    val distanceKm: Double,
    val isShortTrip: Boolean,
    val tollAmount: Double,
    val parkingAmount: Double = 0.0,
    val calculatedFare: Double,
    val isPaid: Boolean,
    @ColumnInfo(defaultValue = "0")
    val isWaived: Boolean = false,
    val tripDate: Long = System.currentTimeMillis(),
    val arrivalDate: Long? = null,
    val settledDate: Long? = null,
    val passengerCount: Int = 1,
    val fuelCost: Double = 0.0,
    val maintenanceAmount: Double = 0.0,
    val totalTripCost: Double = calculatedFare,
    val gpsPointsCount: Int = 0,
    val isAutoTracked: Boolean = false,
    val customFuelMileageKmPerL: Double? = null,
    val customFuelPricePerLiter: Double? = null,
    val customMaintenanceMarkup: Double? = null,
    val tripNotes: String = "",
    val isRecurring: Boolean = false,
    val recurrenceInterval: Int = 1,
    val recurrenceUnit: String = "NONE",
    val nextOccurrenceDate: Long? = null,
    @ColumnInfo(defaultValue = "NULL")
    val recurrenceEndDate: Long? = null
) {
    companion object {
        fun fromDomainModel(trip: Trip): TripEntity = TripEntity(
            id = trip.id,
            tripGroupId = trip.tripGroupId,
            tripName = trip.tripName,
            riderId = trip.riderId,
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
            recurrenceUnit = trip.recurrenceUnit.name,
            nextOccurrenceDate = trip.nextOccurrenceDate,
            recurrenceEndDate = trip.recurrenceEndDate
        )
    }
}
