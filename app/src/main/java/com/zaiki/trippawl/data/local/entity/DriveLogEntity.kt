package com.zaiki.trippawl.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Status of a GPS-tracked drive log after the drive ended.
 */
enum class DriveLogStatus(val label: String) {
    PENDING("Pending Review"),
    SAVED("Saved to Ledger"),
    DISCARDED("Discarded");

    companion object {
        fun fromName(value: String?): DriveLogStatus =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
    }
}

/**
 * Persistent log entry for every GPS-tracked drive (manual or auto-triggered), kept even when the
 * resulting trip is discarded so the driver can review or re-log it later from the Dashboard.
 */
@Entity(
    tableName = "drive_logs",
    indices = [Index(value = ["startTimeMillis"])]
)
data class DriveLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val startTimeMillis: Long,
    val endTimeMillis: Long,
    val distanceKm: Double,
    val gpsPointsCount: Int,
    val triggerSource: String,
    /** Comma-separated rider ids selected during the drive. */
    val passengerIdsCsv: String,
    val status: String,
    val savedTripGroupId: String
) {
    val passengerIds: Set<Long>
        get() = passengerIdsCsv.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()

    val logStatus: DriveLogStatus
        get() = DriveLogStatus.fromName(status)

    val durationMillis: Long
        get() = (endTimeMillis - startTimeMillis).coerceAtLeast(0L)

    val averageSpeedKmh: Double
        get() {
            val hours = durationMillis / 3_600_000.0
            return if (hours > 0.0) distanceKm / hours else 0.0
        }
}
