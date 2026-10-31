package com.zaiki.trippawl.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.zaiki.trippawl.data.local.entity.DriveLogEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for GPS drive logs shown on the Dashboard.
 */
@Dao
interface DriveLogDao {

    @Query("SELECT * FROM drive_logs ORDER BY startTimeMillis DESC, id DESC LIMIT :limit")
    fun observeRecentLogs(limit: Int): Flow<List<DriveLogEntity>>

    @Query("SELECT * FROM drive_logs WHERE id = :id LIMIT 1")
    suspend fun getLogById(id: Long): DriveLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: DriveLogEntity): Long

    @Query("UPDATE drive_logs SET status = :status, savedTripGroupId = :savedTripGroupId WHERE id = :id")
    suspend fun updateLogStatus(id: Long, status: String, savedTripGroupId: String)

    @Query("DELETE FROM drive_logs WHERE id = :id")
    suspend fun deleteLog(id: Long)

    @Query("DELETE FROM drive_logs")
    suspend fun deleteAllLogs()
}
