package com.zaiki.trippawl.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zaiki.trippawl.data.local.dao.DriveLogDao
import com.zaiki.trippawl.data.local.dao.RiderDao
import com.zaiki.trippawl.data.local.dao.TripDao
import com.zaiki.trippawl.data.local.entity.DriveLogEntity
import com.zaiki.trippawl.data.local.entity.RiderEntity
import com.zaiki.trippawl.data.local.entity.TripEntity

/**
 * Room Database definition for TripPawl.
 */
@Database(
    entities = [
        RiderEntity::class,
        TripEntity::class,
        DriveLogEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class TripPawlDatabase : RoomDatabase() {
    abstract fun riderDao(): RiderDao
    abstract fun tripDao(): TripDao
    abstract fun driveLogDao(): DriveLogDao

    companion object {
        const val DATABASE_NAME = "trippawl_ledger.db"

        /**
         * v4 -> v5 (non-destructive): adds `trips.isWaived` and the `drive_logs` table,
         * preserving all existing riders and trips.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `trips` ADD COLUMN `isWaived` INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `drive_logs` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`startTimeMillis` INTEGER NOT NULL, " +
                        "`endTimeMillis` INTEGER NOT NULL, " +
                        "`distanceKm` REAL NOT NULL, " +
                        "`gpsPointsCount` INTEGER NOT NULL, " +
                        "`triggerSource` TEXT NOT NULL, " +
                        "`passengerIdsCsv` TEXT NOT NULL, " +
                        "`status` TEXT NOT NULL, " +
                        "`savedTripGroupId` TEXT NOT NULL)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_drive_logs_startTimeMillis` " +
                        "ON `drive_logs` (`startTimeMillis`)"
                )
            }
        }
    }
}
