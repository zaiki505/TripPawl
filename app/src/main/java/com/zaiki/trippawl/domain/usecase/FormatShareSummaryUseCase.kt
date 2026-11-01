package com.zaiki.trippawl.domain.usecase

import com.zaiki.trippawl.domain.model.Rider
import com.zaiki.trippawl.domain.model.Trip
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Generates a pre-formatted plain-text carpool summary for a rider to copy to clipboard
 * or share directly via WhatsApp, Telegram, or SMS.
 *
 * Headline format matches specification:
 * `"Hi Alex, here is the carpool summary: 3 unpaid trips, Total: RM 24.50"`
 */
class FormatShareSummaryUseCase @Inject constructor() {

    operator fun invoke(
        rider: Rider,
        trips: List<Trip>,
        currencySymbol: String
    ): String {
        val unpaidTrips = trips.filter { !it.isPaid }.sortedBy { it.tripDate }
        val totalUnpaid = unpaidTrips.sumOf { it.calculatedFare }
        val formattedTotal = String.format(Locale.US, "%.2f", totalUnpaid)
        val tripWord = if (unpaidTrips.size == 1) "unpaid trip" else "unpaid trips"

        val header = "Hi ${rider.name}, here is the carpool summary: " +
            "${unpaidTrips.size} $tripWord, Total: $currencySymbol $formattedTotal"

        if (unpaidTrips.isEmpty()) {
            return "Hi ${rider.name}, your carpool balance is all settled! Total due: $currencySymbol 0.00"
        }

        val dateFormatter = SimpleDateFormat("dd MMM yyyy", Locale.US)
        val breakdownLines = unpaidTrips.mapIndexed { index, trip ->
            val dateStr = dateFormatter.format(Date(trip.tripDate))
            val tierLabel = if (trip.isAutoTracked) "GPS" else if (trip.isShortTrip) "Short" else "Long"
            val distStr = String.format(Locale.US, "%.1f km", trip.distanceKm)
            val fareStr = String.format(Locale.US, "%.2f", trip.calculatedFare)
            val extras = buildList {
                if (trip.tollAmount > 0.0) {
                    add(String.format(Locale.US, "%s %.2f toll", currencySymbol, trip.tollAmount))
                }
                if (trip.parkingAmount > 0.0) {
                    add(String.format(Locale.US, "%s %.2f parking", currencySymbol, trip.parkingAmount))
                }
            }
            val extrasNote = if (extras.isNotEmpty()) {
                " (incl. ${extras.joinToString(", ")})"
            } else {
                ""
            }
            val namePrefix = if (trip.tripName.isNotBlank()) "${trip.tripName} • " else ""
            val splitNote = " [${trip.passengerCount} Pax]"
            "${index + 1}. $dateStr • $namePrefix$distStr [$tierLabel]$splitNote$extrasNote -> $currencySymbol $fareStr"
        }

        return buildString {
            appendLine(header)
            appendLine()
            appendLine("Trip Breakdown:")
            breakdownLines.forEach { appendLine(it) }
            appendLine()
            append("Generated via TripPawl")
        }
    }
}
