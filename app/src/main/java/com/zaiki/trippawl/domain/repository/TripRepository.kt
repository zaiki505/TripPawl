package com.zaiki.trippawl.domain.repository

import com.zaiki.trippawl.domain.model.DashboardMetrics
import com.zaiki.trippawl.domain.model.Trip
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for logging, querying, updating, and settling carpool trips.
 */
interface TripRepository {
    fun observeRecentTrips(limit: Int = 20): Flow<List<Trip>>
    fun observeAllTrips(): Flow<List<Trip>>
    fun observeTripsForRider(riderId: Long): Flow<List<Trip>>
    fun observeDashboardMetrics(monthStartMillis: Long, monthEndMillis: Long): Flow<DashboardMetrics>
    suspend fun getTripById(tripId: Long): Trip?
    suspend fun getTripsByGroupId(tripGroupId: String): List<Trip>
    suspend fun insertTrip(trip: Trip): Long
    suspend fun updateTrip(trip: Trip)
    suspend fun deleteTrip(tripId: Long)
    suspend fun deleteTripsByGroupId(tripGroupId: String)
    suspend fun toggleTripPaymentStatus(tripId: Long, isPaid: Boolean)
    suspend fun toggleGroupPaymentStatus(tripGroupId: String, isPaid: Boolean)
    /** Waives (true) or un-waives back to unpaid (false) a single passenger share. */
    suspend fun setTripWaived(tripId: Long, isWaived: Boolean)
    /** Waives all unpaid shares in a group (true) or reverts all waived shares to unpaid (false). */
    suspend fun setGroupWaived(tripGroupId: String, isWaived: Boolean): Int
    suspend fun settleAllUnpaidTripsForRider(riderId: Long): Int
    suspend fun processDueRecurringTrips(nowMillis: Long = System.currentTimeMillis()): Int
}
