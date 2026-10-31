package com.zaiki.trippawl.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zaiki.trippawl.data.local.entity.RiderDebtSummaryTuple
import com.zaiki.trippawl.data.local.entity.RiderEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for [RiderEntity] operations and aggregated debt balance queries.
 */
@Dao
interface RiderDao {

    @Query("SELECT * FROM riders ORDER BY name COLLATE NOCASE ASC")
    fun observeAllRiders(): Flow<List<RiderEntity>>

    @Query(
        """
        SELECT 
            r.*,
            COALESCE(SUM(CASE WHEN t.isPaid = 0 THEN t.calculatedFare ELSE 0.0 END), 0.0) AS unpaidBalance,
            COUNT(CASE WHEN t.isPaid = 0 THEN 1 END) AS unpaidTripCount,
            COALESCE(SUM(CASE WHEN t.isPaid = 1 AND t.isWaived = 0 THEN t.calculatedFare ELSE 0.0 END), 0.0) AS settledAmount,
            COUNT(t.id) AS totalTripsCount
        FROM riders r
        LEFT JOIN trips t ON r.id = t.riderId
        GROUP BY r.id
        ORDER BY unpaidBalance DESC, r.name COLLATE NOCASE ASC
        """
    )
    fun observeRiderDebtSummaries(): Flow<List<RiderDebtSummaryTuple>>

    @Query("SELECT * FROM riders WHERE id = :id LIMIT 1")
    suspend fun getRiderById(id: Long): RiderEntity?

    @Query("SELECT * FROM riders WHERE LOWER(TRIM(name)) = LOWER(TRIM(:name)) LIMIT 1")
    suspend fun getRiderByNameIgnoreCase(name: String): RiderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRider(rider: RiderEntity): Long

    @Update
    suspend fun updateRider(rider: RiderEntity)

    @Query("SELECT * FROM riders ORDER BY id ASC")
    suspend fun getAllRidersOnce(): List<RiderEntity>

    @Query("DELETE FROM riders")
    suspend fun deleteAllRiders()

    @Query("DELETE FROM riders WHERE id = :riderId")
    suspend fun deleteRiderById(riderId: Long)
}
