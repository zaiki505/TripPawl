package com.zaiki.trippawl.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Supported recurrence units for recurring carpool trips (Every X Day / Week / Month / Year).
 */
enum class RecurrenceUnit(val label: String, val singular: String, val plural: String) {
    NONE("One-Time", "", ""),
    DAYS("Day(s)", "day", "days"),
    WEEKS("Week(s)", "week", "weeks"),
    MONTHS("Month(s)", "month", "months"),
    YEARS("Year(s)", "year", "years");

    companion object {
        fun fromNameOrNone(value: String?): RecurrenceUnit =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
    }
}

/**
 * Computes the next scheduled occurrence timestamp from [fromMillis] given [interval] and [unit].
 */
fun computeNextOccurrenceMillis(
    fromMillis: Long,
    interval: Int,
    unit: RecurrenceUnit
): Long? {
    if (unit == RecurrenceUnit.NONE || interval <= 0) return null
    val cal = Calendar.getInstance().apply { timeInMillis = fromMillis }
    val safeInterval = interval.coerceAtLeast(1)
    when (unit) {
        RecurrenceUnit.NONE -> return null
        RecurrenceUnit.DAYS -> cal.add(Calendar.DAY_OF_YEAR, safeInterval)
        RecurrenceUnit.WEEKS -> cal.add(Calendar.WEEK_OF_YEAR, safeInterval)
        RecurrenceUnit.MONTHS -> cal.add(Calendar.MONTH, safeInterval)
        RecurrenceUnit.YEARS -> cal.add(Calendar.YEAR, safeInterval)
    }
    return cal.timeInMillis
}

/**
 * Domain model representing a logged carpool trip passenger share with its associated rider details,
 * trip group identifier, custom trip name, per-trip fuel/telemetry overrides,
 * GPS telemetry timestamps/route point count, manual overrides (tolls & parking),
 * recurrence configuration, and full pricing breakdown snapshot.
 */
data class Trip(
    val id: Long = 0L,
    val tripGroupId: String = "",
    val tripName: String = "",
    val riderId: Long,
    val riderName: String,
    val riderPhone: String? = null,
    val distanceKm: Double,
    val isShortTrip: Boolean,
    val tollAmount: Double,
    val parkingAmount: Double = 0.0,
    val calculatedFare: Double,
    val isPaid: Boolean,
    /** A waived share is marked settled (`isPaid = true`) but excluded from collected totals. */
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
    val recurrenceUnit: RecurrenceUnit = RecurrenceUnit.NONE,
    val nextOccurrenceDate: Long? = null,
    val recurrenceEndDate: Long? = null
) {
    val recurrenceSummaryLabel: String?
        get() {
            if (!isRecurring || recurrenceUnit == RecurrenceUnit.NONE || recurrenceInterval <= 0) return null
            val unitWord = if (recurrenceInterval == 1) recurrenceUnit.singular else recurrenceUnit.plural
            val repeatText = "Every $recurrenceInterval $unitWord"
            return if (recurrenceEndDate != null) {
                val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(recurrenceEndDate))
                "$repeatText until $dateStr"
            } else {
                repeatText
            }
        }
}

/**
 * Represents a grouped trip session combining all passengers who rode together on the same trip.
 * Example: "Monday Class" · 3 passengers (Aiman, Sarah, Danish) · Total RM 9.00 (RM 3.00 each).
 */
data class GroupedTrip(
    val groupKey: String,
    val primaryTripId: Long,
    val tripName: String,
    val passengerShares: List<Trip>,
    val distanceKm: Double,
    val isShortTrip: Boolean,
    val isAutoTracked: Boolean,
    val tripDate: Long,
    val arrivalDate: Long?,
    val tollAmount: Double,
    val parkingAmount: Double,
    val fuelCost: Double,
    val maintenanceAmount: Double,
    val sharePerPerson: Double,
    val totalPassengersFare: Double,
    val totalTripCost: Double,
    val gpsPointsCount: Int,
    val customFuelMileageKmPerL: Double?,
    val customFuelPricePerLiter: Double?,
    val customMaintenanceMarkup: Double?,
    val tripNotes: String,
    val isRecurring: Boolean = false,
    val recurrenceInterval: Int = 1,
    val recurrenceUnit: RecurrenceUnit = RecurrenceUnit.NONE,
    val nextOccurrenceDate: Long? = null,
    val recurrenceEndDate: Long? = null
) {
    val passengerCount: Int
        get() = maxOf(passengerShares.size, passengerShares.firstOrNull()?.passengerCount ?: 1)

    val passengerNames: List<String>
        get() = passengerShares.map { it.riderName }.distinct()

    val passengerNamesSummary: String
        get() = passengerNames.joinToString(", ")

    /**
     * Primary title shown on cards: uses the user's Trip Name (e.g. "Monday Class") if provided,
     * otherwise falls back to the grouped passenger names.
     */
    val displayTitle: String
        get() = tripName.trim().ifEmpty { passengerNamesSummary }

    val isAllPaid: Boolean
        get() = passengerShares.isNotEmpty() && passengerShares.all { it.isPaid }

    val paidCount: Int
        get() = passengerShares.count { it.isPaid }

    val unpaidCount: Int
        get() = passengerShares.count { !it.isPaid }

    val unpaidTotalFare: Double
        get() = passengerShares.filter { !it.isPaid }.sumOf { it.calculatedFare }

    val waivedCount: Int
        get() = passengerShares.count { it.isWaived }

    val waivedTotalFare: Double
        get() = passengerShares.filter { it.isWaived }.sumOf { it.calculatedFare }

    val isAllWaived: Boolean
        get() = passengerShares.isNotEmpty() && passengerShares.all { it.isWaived }

    val recurrenceSummaryLabel: String?
        get() {
            if (!isRecurring || recurrenceUnit == RecurrenceUnit.NONE || recurrenceInterval <= 0) return null
            val unitWord = if (recurrenceInterval == 1) recurrenceUnit.singular else recurrenceUnit.plural
            val repeatText = "Every $recurrenceInterval $unitWord"
            return if (recurrenceEndDate != null) {
                val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(recurrenceEndDate))
                "$repeatText until $dateStr"
            } else {
                repeatText
            }
        }
}

/**
 * Groups individual passenger trip records that share the same [Trip.tripGroupId]
 * (or legacy timestamp + distance) into unified [GroupedTrip] entries.
 */
fun List<Trip>.groupByTripSession(): List<GroupedTrip> {
    if (isEmpty()) return emptyList()
    return this
        .groupBy { trip ->
            trip.tripGroupId.takeIf { it.isNotBlank() }
                ?: "legacy_${trip.tripDate}_${trip.distanceKm}"
        }
        .map { (key, shares) ->
            val sortedShares = shares.sortedBy { it.id }
            val first = sortedShares.first()
            val totalPaxFare = sortedShares.sumOf { it.calculatedFare }
            GroupedTrip(
                groupKey = key,
                primaryTripId = first.id,
                tripName = first.tripName,
                passengerShares = sortedShares,
                distanceKm = first.distanceKm,
                isShortTrip = first.isShortTrip,
                isAutoTracked = first.isAutoTracked,
                tripDate = first.tripDate,
                arrivalDate = first.arrivalDate,
                tollAmount = first.tollAmount,
                parkingAmount = first.parkingAmount,
                fuelCost = first.fuelCost,
                maintenanceAmount = first.maintenanceAmount,
                sharePerPerson = first.calculatedFare,
                totalPassengersFare = totalPaxFare,
                totalTripCost = if (sortedShares.size > 1) totalPaxFare else first.totalTripCost,
                gpsPointsCount = first.gpsPointsCount,
                customFuelMileageKmPerL = first.customFuelMileageKmPerL,
                customFuelPricePerLiter = first.customFuelPricePerLiter,
                customMaintenanceMarkup = first.customMaintenanceMarkup,
                tripNotes = first.tripNotes,
                isRecurring = first.isRecurring,
                recurrenceInterval = first.recurrenceInterval,
                recurrenceUnit = first.recurrenceUnit,
                nextOccurrenceDate = first.nextOccurrenceDate,
                recurrenceEndDate = first.recurrenceEndDate
            )
        }
        .sortedByDescending { it.tripDate }
}

/**
 * Filter states for the Trip History & Ledger screen.
 */
enum class TripFilter(val label: String) {
    ALL("All"),
    UNPAID_ONLY("Unpaid Only"),
    PAID_ONLY("Paid Only")
}
