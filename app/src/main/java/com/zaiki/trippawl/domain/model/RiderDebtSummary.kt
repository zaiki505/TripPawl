package com.zaiki.trippawl.domain.model

/**
 * Aggregated debt and trip summary for a single rider.
 */
data class RiderDebtSummary(
    val rider: Rider,
    val unpaidBalance: Double,
    val unpaidTripCount: Int,
    val settledAmount: Double,
    val totalTripsCount: Int
)
