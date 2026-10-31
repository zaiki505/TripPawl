package com.zaiki.trippawl.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.zaiki.trippawl.data.local.entity.TripEntity
import com.zaiki.trippawl.data.local.entity.TripWithRider
import com.zaiki.trippawl.domain.model.DashboardMetrics
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [TripEntity] operations, grouped trip queries, and dashboard aggregations.
 */
@Dao
interface TripDao {

    @Transaction
    @Query("SELECT * FROM trips ORDER BY tripDate DESC, id DESC LIMIT :limit")
    fun observeRecentTripsWithRider(limit: Int = 20): Flow<List<TripWithRider>>

    @Transaction
    @Query("SELECT * FROM trips ORDER BY tripDate DESC, id DESC")
    fun observeAllTripsWithRider(): Flow<List<TripWithRider>>

    @Transaction
    @Query("SELECT * FROM trips WHERE riderId = :riderId ORDER BY tripDate DESC, id DESC")
    fun observeTripsForRider(riderId: Long): Flow<List<TripWithRider>>

    @Query(
        """
        SELECT 
            COALESCE(SUM(CASE WHEN isPaid = 0 THEN calculatedFare ELSE 0.0 END), 0.0) AS totalUncollectedDebt,
            COALESCE(
                (SELECT SUM(groupFuel) FROM (
                    SELECT MAX(fuelCost) AS groupFuel
                    FROM trips
                    WHERE tripDate >= :monthStartMillis AND tripDate <= :monthEndMillis
                    GROUP BY CASE WHEN tripGroupId != '' THEN tripGroupId ELSE CAST(id AS TEXT) END
                )),
                0.0
            ) AS totalFuelSpentCurrentMonth,
            COALESCE(SUM(CASE WHEN isPaid = 1 AND isWaived = 0 THEN calculatedFare ELSE 0.0 END), 0.0) AS totalSettledPayments,
            COUNT(CASE WHEN isPaid = 0 THEN 1 END) AS unpaidTripCount,
            COUNT(DISTINCT CASE WHEN tripDate >= :monthStartMillis AND tripDate <= :monthEndMillis THEN (CASE WHEN tripGroupId != '' THEN tripGroupId ELSE CAST(id AS TEXT) END) END) AS currentMonthTripCount,
            COUNT(CASE WHEN isPaid = 1 AND isWaived = 0 THEN 1 END) AS settledTripCount,
            COALESCE(SUM(CASE WHEN isWaived = 1 THEN calculatedFare ELSE 0.0 END), 0.0) AS totalWaivedAmount,
            COUNT(CASE WHEN isWaived = 1 THEN 1 END) AS waivedTripCount
        FROM trips
        """
    )
    fun observeDashboardMetrics(monthStartMillis: Long, monthEndMillis: Long): Flow<DashboardMetrics>

    @Transaction
    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun getTripWithRiderById(tripId: Long): TripWithRider?

    @Transaction
    @Query("SELECT * FROM trips WHERE tripGroupId = :tripGroupId ORDER BY id ASC")
    suspend fun getTripsWithRiderByGroupId(tripGroupId: String): List<TripWithRider>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Query("DELETE FROM trips WHERE id = :tripId")
    suspend fun deleteTripById(tripId: Long)

    @Query("DELETE FROM trips WHERE tripGroupId = :tripGroupId")
    suspend fun deleteTripsByGroupId(tripGroupId: String)

    @Query("UPDATE trips SET isPaid = :isPaid, isWaived = 0, settledDate = :settledDate WHERE id = :tripId")
    suspend fun updateTripPaymentStatus(tripId: Long, isPaid: Boolean, settledDate: Long?)

    @Query("UPDATE trips SET isPaid = :isPaid, isWaived = 0, settledDate = :settledDate WHERE tripGroupId = :tripGroupId")
    suspend fun updateGroupPaymentStatus(tripGroupId: String, isPaid: Boolean, settledDate: Long?)

    /** Waives (or un-waives back to unpaid) a single passenger share. */
    @Query("UPDATE trips SET isWaived = :isWaived, isPaid = :isWaived, settledDate = :settledDate WHERE id = :tripId")
    suspend fun updateTripWaivedStatus(tripId: Long, isWaived: Boolean, settledDate: Long?)

    /** Waives every still-unpaid share in a group (already-collected shares are left untouched). */
    @Query("UPDATE trips SET isWaived = 1, isPaid = 1, settledDate = :settledDate WHERE tripGroupId = :tripGroupId AND isPaid = 0")
    suspend fun waiveUnpaidSharesInGroup(tripGroupId: String, settledDate: Long): Int

    /** Reverts every waived share in a group back to unpaid. */
    @Query("UPDATE trips SET isWaived = 0, isPaid = 0, settledDate = NULL WHERE tripGroupId = :tripGroupId AND isWaived = 1")
    suspend fun unwaiveSharesInGroup(tripGroupId: String): Int

    @Query("UPDATE trips SET isPaid = 1, settledDate = :settledDate WHERE riderId = :riderId AND isPaid = 0")
    suspend fun settleAllUnpaidTripsForRider(riderId: Long, settledDate: Long): Int

    @Transaction
    @Query("SELECT * FROM trips WHERE isRecurring = 1 AND nextOccurrenceDate IS NOT NULL AND nextOccurrenceDate <= :nowMillis ORDER BY nextOccurrenceDate ASC, id ASC")
    suspend fun getDueRecurringTripsWithRider(nowMillis: Long): List<TripWithRider>

    @Transaction
    @Query("SELECT * FROM trips ORDER BY id ASC")
    suspend fun getAllTripsWithRiderOnce(): List<TripWithRider>

    @Query("DELETE FROM trips")
    suspend fun deleteAllTrips()

    @Query("UPDATE trips SET isRecurring = :isRecurring, nextOccurrenceDate = :nextOccurrenceDate WHERE id = :tripId")
    suspend fun updateTripRecurrenceNextDate(tripId: Long, isRecurring: Boolean, nextOccurrenceDate: Long?)
}
