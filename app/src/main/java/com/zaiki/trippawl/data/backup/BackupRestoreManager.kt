package com.zaiki.trippawl.data.backup

import android.content.Context
import android.net.Uri
import com.zaiki.trippawl.data.local.dao.RiderDao
import com.zaiki.trippawl.data.local.dao.TripDao
import com.zaiki.trippawl.data.local.entity.RiderEntity
import com.zaiki.trippawl.data.local.entity.TripEntity
import com.zaiki.trippawl.domain.model.FuelEfficiencyUnit
import com.zaiki.trippawl.domain.model.MaintenanceMarkupMode
import com.zaiki.trippawl.domain.model.PricingSettings
import com.zaiki.trippawl.domain.model.RecurrenceUnit
import com.zaiki.trippawl.domain.model.Trip
import com.zaiki.trippawl.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Defines how a backup file should be restored into the local ledger.
 */
enum class RestoreMode(val label: String, val description: String) {
    MERGE(
        label = "Merge (Keep Existing)",
        description = "Adds missing riders & trips from backup without deleting current local records"
    ),
    REPLACE_ALL(
        label = "Replace All",
        description = "Replaces all current local riders, trips & settings with the backup snapshot"
    )
}

/**
 * Metadata for an on-device local backup snapshot stored in `filesDir/backups/`.
 */
data class LocalBackupFileUi(
    val fileName: String,
    val filePath: String,
    val createdAtMillis: Long,
    val sizeBytes: Long,
    val ridersCount: Int,
    val tripsCount: Int,
    val isAutoBackup: Boolean
)

/**
 * Summary returned after a successful backup or restore operation.
 */
data class BackupRestoreSummary(
    val ridersCount: Int,
    val tripsCount: Int,
    val timestampMillis: Long,
    val sourceLabel: String
)

/**
 * Schedule frequency for automatic backups.
 */
enum class AutoBackupFrequency(val label: String) {
    EVERY_CHANGE("On Every Trip Change"),
    DAILY("Daily"),
    WEEKLY("Weekly")
}

/**
 * Persistent preferences for local & optional Google Drive backup settings.
 */
data class BackupConfigState(
    val autoLocalBackupEnabled: Boolean = true,
    val autoBackupFrequency: AutoBackupFrequency = AutoBackupFrequency.EVERY_CHANGE,
    val googleDriveEnabled: Boolean = false,
    val googleDriveAccountLabel: String = "",
    val googleDriveAccessToken: String = "",
    val lastLocalBackupMillis: Long = 0L,
    val lastGoogleDriveBackupMillis: Long = 0L
)

/**
 * Coordinates full JSON serialization/deserialization of Riders, Grouped Trips, and Vehicle Telemetry
 * Settings, manages the on-device Local Backup Vault (`filesDir/backups/`), supports Android Storage
 * Access Framework (Local Device Files + Google Drive Document Provider), and provides optional
 * direct Google Drive REST API v3 cloud upload/download.
 */
@Singleton
class BackupRestoreManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val riderDao: RiderDao,
    private val tripDao: TripDao,
    private val settingsRepository: SettingsRepository
) {
    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val backupDir: File
        get() = File(appContext.filesDir, "backups").apply {
            if (!exists()) mkdirs()
        }

    fun getBackupConfig(): BackupConfigState {
        val freqName = prefs.getString(KEY_AUTO_BACKUP_FREQUENCY, AutoBackupFrequency.EVERY_CHANGE.name)
        val freq = runCatching {
            AutoBackupFrequency.valueOf(freqName ?: AutoBackupFrequency.EVERY_CHANGE.name)
        }.getOrDefault(AutoBackupFrequency.EVERY_CHANGE)

        return BackupConfigState(
            autoLocalBackupEnabled = prefs.getBoolean(KEY_AUTO_LOCAL_BACKUP, true),
            autoBackupFrequency = freq,
            googleDriveEnabled = prefs.getBoolean(KEY_GDRIVE_ENABLED, false),
            googleDriveAccountLabel = prefs.getString(KEY_GDRIVE_ACCOUNT_LABEL, "").orEmpty(),
            googleDriveAccessToken = prefs.getString(KEY_GDRIVE_ACCESS_TOKEN, "").orEmpty(),
            lastLocalBackupMillis = prefs.getLong(KEY_LAST_LOCAL_BACKUP_MILLIS, 0L),
            lastGoogleDriveBackupMillis = prefs.getLong(KEY_LAST_GDRIVE_BACKUP_MILLIS, 0L)
        )
    }

    fun updateBackupConfig(
        autoLocalBackupEnabled: Boolean? = null,
        autoBackupFrequency: AutoBackupFrequency? = null,
        googleDriveEnabled: Boolean? = null,
        googleDriveAccountLabel: String? = null,
        googleDriveAccessToken: String? = null
    ): BackupConfigState {
        prefs.edit().apply {
            autoLocalBackupEnabled?.let { putBoolean(KEY_AUTO_LOCAL_BACKUP, it) }
            autoBackupFrequency?.let { putString(KEY_AUTO_BACKUP_FREQUENCY, it.name) }
            googleDriveEnabled?.let { putBoolean(KEY_GDRIVE_ENABLED, it) }
            googleDriveAccountLabel?.let { putString(KEY_GDRIVE_ACCOUNT_LABEL, it.trim()) }
            googleDriveAccessToken?.let { putString(KEY_GDRIVE_ACCESS_TOKEN, it.trim()) }
        }.apply()
        return getBackupConfig()
    }

    /**
     * Automatically creates an on-device backup snapshot when Auto Backup is enabled and due.
     */
    suspend fun triggerAutoBackupIfEnabled(isDataChangedEvent: Boolean = true) {
        val cfg = getBackupConfig()
        if (!cfg.autoLocalBackupEnabled) return
        val now = System.currentTimeMillis()
        val elapsed = now - cfg.lastLocalBackupMillis

        val shouldRun = when (cfg.autoBackupFrequency) {
            AutoBackupFrequency.EVERY_CHANGE -> isDataChangedEvent || cfg.lastLocalBackupMillis == 0L
            AutoBackupFrequency.DAILY -> elapsed >= 24L * 60L * 60L * 1000L
            AutoBackupFrequency.WEEKLY -> elapsed >= 7L * 24L * 60L * 60L * 1000L
        }

        if (shouldRun) {
            createLocalSnapshotBackup(isAutoBackup = true)
            if (cfg.googleDriveEnabled && cfg.googleDriveAccessToken.isNotBlank()) {
                runCatching { uploadBackupToGoogleDriveCloudApi(cfg.googleDriveAccessToken) }
            }
        }
    }

    fun recordGoogleDriveBackupSuccess() {
        prefs.edit().putLong(KEY_LAST_GDRIVE_BACKUP_MILLIS, System.currentTimeMillis()).apply()
    }

    /**
     * Builds the full JSON backup string containing all Settings, Riders, and Trips.
     */
    suspend fun buildBackupJsonString(): Pair<String, BackupRestoreSummary> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val settings = settingsRepository.settingsFlow.first()
        val riders = riderDao.getAllRidersOnce()
        val trips = tripDao.getAllTripsWithRiderOnce().map { it.toDomainModel() }

        val root = JSONObject().apply {
            put("backupVersion", BACKUP_SCHEMA_VERSION)
            put("appName", "TripPawl")
            put("exportedAtMillis", now)
            put("ridersCount", riders.size)
            put("tripsCount", trips.size)
            put("settings", serializeSettings(settings))
            put("riders", serializeRiders(riders))
            put("trips", serializeTrips(trips))
        }

        val summary = BackupRestoreSummary(
            ridersCount = riders.size,
            tripsCount = trips.size,
            timestampMillis = now,
            sourceLabel = "TripPawl Backup"
        )
        root.toString(2) to summary
    }

    /**
     * Creates an on-device local backup snapshot in `filesDir/backups/`.
     */
    suspend fun createLocalSnapshotBackup(isAutoBackup: Boolean = false): Result<LocalBackupFileUi> =
        withContext(Dispatchers.IO) {
            runCatching {
                val (json, summary) = buildBackupJsonString()
                val stampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                val fileName = if (isAutoBackup) {
                    AUTO_BACKUP_FILENAME
                } else {
                    "trippawl_backup_${stampFormat.format(Date(summary.timestampMillis))}.json"
                }

                val targetFile = File(backupDir, fileName)
                targetFile.writeText(json, Charsets.UTF_8)

                prefs.edit().putLong(KEY_LAST_LOCAL_BACKUP_MILLIS, summary.timestampMillis).apply()
                pruneOldManualSnapshots(maxKeep = 20)

                LocalBackupFileUi(
                    fileName = targetFile.name,
                    filePath = targetFile.absolutePath,
                    createdAtMillis = summary.timestampMillis,
                    sizeBytes = targetFile.length(),
                    ridersCount = summary.ridersCount,
                    tripsCount = summary.tripsCount,
                    isAutoBackup = isAutoBackup
                )
            }
        }

    /**
     * Lists all local backup snapshots stored in `filesDir/backups/`, newest first.
     */
    suspend fun listLocalBackups(): List<LocalBackupFileUi> = withContext(Dispatchers.IO) {
        val files = backupDir.listFiles { file ->
            file.isFile && file.name.endsWith(".json", ignoreCase = true)
        }?.toList().orEmpty()

        files.mapNotNull { file ->
            runCatching {
                val text = file.readText(Charsets.UTF_8)
                val root = JSONObject(text)
                val exportedAt = root.optLong("exportedAtMillis", file.lastModified())
                val ridersCount = root.optInt(
                    "ridersCount",
                    root.optJSONArray("riders")?.length() ?: 0
                )
                val tripsCount = root.optInt(
                    "tripsCount",
                    root.optJSONArray("trips")?.length() ?: 0
                )
                LocalBackupFileUi(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    createdAtMillis = exportedAt,
                    sizeBytes = file.length(),
                    ridersCount = ridersCount,
                    tripsCount = tripsCount,
                    isAutoBackup = file.name == AUTO_BACKUP_FILENAME
                )
            }.getOrNull()
        }.sortedByDescending { it.createdAtMillis }
    }

    /**
     * Restores data from a local snapshot file in `filesDir/backups/`.
     */
    suspend fun restoreFromLocalFile(
        filePath: String,
        mode: RestoreMode
    ): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(filePath)
            require(file.exists() && file.isFile) { "Backup snapshot file not found." }
            val json = file.readText(Charsets.UTF_8)
            restoreFromJsonString(json, mode, sourceLabel = file.name)
        }
    }

    /**
     * Deletes a local backup snapshot from `filesDir/backups/`.
     */
    suspend fun deleteLocalBackupFile(filePath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(filePath)
            if (file.exists()) file.delete() else false
        }.getOrDefault(false)
    }

    /**
     * Writes the full JSON backup to any Android Document Provider [Uri]
     * (Local storage Downloads/Documents OR Google Drive Document Provider).
     */
    suspend fun exportBackupToUri(
        uri: Uri,
        isGoogleDriveTarget: Boolean = false
    ): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val (json, summary) = buildBackupJsonString()
            val outputStream = appContext.contentResolver.openOutputStream(uri, "wt")
                ?: appContext.contentResolver.openOutputStream(uri)
                ?: error("Unable to open destination file for writing.")

            outputStream.use { stream ->
                OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                    writer.write(json)
                    writer.flush()
                }
            }

            val now = System.currentTimeMillis()
            if (isGoogleDriveTarget) {
                prefs.edit().putLong(KEY_LAST_GDRIVE_BACKUP_MILLIS, now).apply()
            } else {
                prefs.edit().putLong(KEY_LAST_LOCAL_BACKUP_MILLIS, now).apply()
            }

            // Also keep the on-device auto-backup snapshot fresh
            if (getBackupConfig().autoLocalBackupEnabled) {
                runCatching { createLocalSnapshotBackup(isAutoBackup = true) }
            }

            summary.copy(
                sourceLabel = if (isGoogleDriveTarget) "Google Drive" else "Device Storage"
            )
        }
    }

    /**
     * Reads and restores a JSON backup from any Android Document Provider [Uri]
     * (Local storage OR Google Drive Document Provider).
     */
    suspend fun restoreBackupFromUri(
        uri: Uri,
        mode: RestoreMode,
        sourceLabel: String = "Selected Backup File"
    ): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val inputStream = appContext.contentResolver.openInputStream(uri)
                ?: error("Unable to open selected backup file.")
            val json = inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            }
            restoreFromJsonString(json, mode, sourceLabel = sourceLabel)
        }
    }

    /**
     * Uploads `trippawl_cloud_backup.json` directly to Google Drive REST API v3
     * (`https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart`)
     * when the user provides a Google Drive OAuth Bearer access token.
     */
    suspend fun uploadBackupToGoogleDriveCloudApi(
        accessToken: String
    ): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = accessToken.trim().removePrefix("Bearer ").trim()
            require(cleanToken.isNotEmpty()) {
                "Google Drive access token is empty. Use 'Save to Google Drive' to pick your Drive folder directly, or paste an OAuth token."
            }

            val (json, summary) = buildBackupJsonString()
            val existingFileId = findExistingGoogleDriveBackupFileId(cleanToken)

            val boundary = "===TripPawlDriveBoundary${System.currentTimeMillis()}==="
            val endpoint = if (existingFileId != null) {
                "https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=multipart"
            } else {
                "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"
            }
            val method = if (existingFileId != null) "PATCH" else "POST"

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Authorization", "Bearer $cleanToken")
                setRequestProperty("Content-Type", "multipart/related; boundary=$boundary")
            }

            val metadataJson = JSONObject().apply {
                put("name", GDRIVE_CLOUD_FILENAME)
                put("mimeType", "application/json")
                put("description", "TripPawl Carpool Ledger & Vehicle Telemetry Cloud Backup")
            }.toString()

            conn.outputStream.use { out ->
                OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                    writer.write("--$boundary\r\n")
                    writer.write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    writer.write(metadataJson)
                    writer.write("\r\n--$boundary\r\n")
                    writer.write("Content-Type: application/json; charset=UTF-8\r\n\r\n")
                    writer.write(json)
                    writer.write("\r\n--$boundary--\r\n")
                    writer.flush()
                }
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errBody = runCatching {
                    conn.errorStream?.bufferedReader(Charsets.UTF_8)?.readText()
                }.getOrNull().orEmpty()
                error("Google Drive API returned HTTP $code. ${errBody.take(160)}")
            }

            val now = System.currentTimeMillis()
            prefs.edit().putLong(KEY_LAST_GDRIVE_BACKUP_MILLIS, now).apply()

            summary.copy(
                timestampMillis = now,
                sourceLabel = "Google Drive Cloud API"
            )
        }
    }

    /**
     * Downloads and restores `trippawl_cloud_backup.json` from Google Drive REST API v3.
     */
    suspend fun restoreFromGoogleDriveCloudApi(
        accessToken: String,
        mode: RestoreMode
    ): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val cleanToken = accessToken.trim().removePrefix("Bearer ").trim()
            require(cleanToken.isNotEmpty()) {
                "Google Drive access token is empty. Use 'Restore from Google Drive' to pick a file from your Drive app, or paste an OAuth token."
            }

            val fileId = findExistingGoogleDriveBackupFileId(cleanToken)
                ?: error("No '$GDRIVE_CLOUD_FILENAME' backup found in your Google Drive.")

            val downloadUrl = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"
            val conn = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Authorization", "Bearer $cleanToken")
            }

            val code = conn.responseCode
            if (code !in 200..299) {
                error("Failed to download backup from Google Drive (HTTP $code).")
            }

            val json = conn.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).readText()
            }

            restoreFromJsonString(json, mode, sourceLabel = "Google Drive Cloud API")
        }
    }

    private fun findExistingGoogleDriveBackupFileId(cleanToken: String): String? {
        val query = URLEncoder.encode(
            "name = '$GDRIVE_CLOUD_FILENAME' and trashed = false",
            "UTF-8"
        )
        val listUrl = "https://www.googleapis.com/drive/v3/files?q=$query&spaces=drive&fields=files(id,name,modifiedTime)&pageSize=1"
        val conn = (URL(listUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Authorization", "Bearer $cleanToken")
        }
        if (conn.responseCode !in 200..299) return null
        val body = conn.inputStream.use { it.bufferedReader(Charsets.UTF_8).readText() }
        val files = JSONObject(body).optJSONArray("files") ?: return null
        if (files.length() == 0) return null
        return files.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
    }

    /**
     * Parses a TripPawl JSON backup string and restores Settings, Riders, and Trips
     * according to [mode] ([RestoreMode.MERGE] or [RestoreMode.REPLACE_ALL]).
     */
    suspend fun restoreFromJsonString(
        jsonString: String,
        mode: RestoreMode,
        sourceLabel: String = "Backup"
    ): BackupRestoreSummary = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonString.trim())
        val exportedAt = root.optLong("exportedAtMillis", System.currentTimeMillis())

        // 1. Restore Settings if present
        root.optJSONObject("settings")?.let { settingsObj ->
            val currentSettings = settingsRepository.settingsFlow.first()
            val restoredSettings = deserializeSettings(settingsObj, currentSettings)
            settingsRepository.updateSettings(restoredSettings)
        }

        val ridersArray = root.optJSONArray("riders") ?: JSONArray()
        val tripsArray = root.optJSONArray("trips") ?: JSONArray()

        if (mode == RestoreMode.REPLACE_ALL) {
            tripDao.deleteAllTrips()
            riderDao.deleteAllRiders()
        }

        // 2. Restore Riders and build oldRiderId -> localRiderId mapping
        val riderIdMap = mutableMapOf<Long, Long>()
        val riderNameMap = mutableMapOf<String, Long>()

        // Pre-index existing local riders when merging
        riderDao.getAllRidersOnce().forEach { existing ->
            riderNameMap[existing.name.trim().lowercase(Locale.US)] = existing.id
        }

        var restoredRidersCount = 0
        for (i in 0 until ridersArray.length()) {
            val obj = ridersArray.optJSONObject(i) ?: continue
            val oldId = obj.optLong("id", 0L)
            val name = obj.optString("name", "").trim()
            if (name.isEmpty()) continue
            val phone = obj.optString("phone", "").trim().takeIf { it.isNotEmpty() && it != "null" }
            val createdAt = obj.optLong("createdAt", System.currentTimeMillis())
            val nameKey = name.lowercase(Locale.US)

            val resolvedId = if (mode == RestoreMode.MERGE && riderNameMap.containsKey(nameKey)) {
                val existingId = riderNameMap.getValue(nameKey)
                val existingEntity = riderDao.getRiderById(existingId)
                if (existingEntity != null && existingEntity.phone.isNullOrBlank() && !phone.isNullOrBlank()) {
                    riderDao.updateRider(existingEntity.copy(phone = phone))
                }
                existingId
            } else {
                val insertedId = riderDao.insertRider(
                    RiderEntity(
                        id = 0L,
                        name = name,
                        phone = phone,
                        createdAt = createdAt
                    )
                )
                riderNameMap[nameKey] = insertedId
                restoredRidersCount++
                insertedId
            }

            if (oldId > 0L) {
                riderIdMap[oldId] = resolvedId
            }
        }

        // 3. Build duplicate-detection signatures when merging
        val existingTripSignatures = if (mode == RestoreMode.MERGE) {
            tripDao.getAllTripsWithRiderOnce().map { existing ->
                buildTripSignature(
                    tripGroupId = existing.trip.tripGroupId,
                    riderId = existing.trip.riderId,
                    tripDate = existing.trip.tripDate,
                    distanceKm = existing.trip.distanceKm,
                    calculatedFare = existing.trip.calculatedFare
                )
            }.toMutableSet()
        } else {
            mutableSetOf()
        }

        // 4. Restore Trips
        var restoredTripsCount = 0
        for (i in 0 until tripsArray.length()) {
            val obj = tripsArray.optJSONObject(i) ?: continue
            val oldRiderId = obj.optLong("riderId", 0L)
            val riderName = obj.optString("riderName", "Carpool Passenger").trim().ifEmpty { "Carpool Passenger" }
            val riderPhone = obj.optString("riderPhone", "").trim().takeIf { it.isNotEmpty() && it != "null" }
            val nameKey = riderName.lowercase(Locale.US)

            val targetRiderId = riderIdMap[oldRiderId]
                ?: riderNameMap[nameKey]
                ?: run {
                    val createdId = riderDao.insertRider(
                        RiderEntity(
                            id = 0L,
                            name = riderName,
                            phone = riderPhone,
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    riderNameMap[nameKey] = createdId
                    if (oldRiderId > 0L) riderIdMap[oldRiderId] = createdId
                    restoredRidersCount++
                    createdId
                }

            val tripGroupId = obj.optString("tripGroupId", "")
            val tripDate = obj.optLong("tripDate", System.currentTimeMillis())
            val distanceKm = obj.optDouble("distanceKm", 0.0)
            val calculatedFare = obj.optDouble("calculatedFare", 0.0)

            val signature = buildTripSignature(
                tripGroupId = tripGroupId,
                riderId = targetRiderId,
                tripDate = tripDate,
                distanceKm = distanceKm,
                calculatedFare = calculatedFare
            )

            if (mode == RestoreMode.MERGE && signature in existingTripSignatures) {
                continue
            }
            existingTripSignatures.add(signature)

            val entity = TripEntity(
                id = 0L,
                tripGroupId = tripGroupId,
                tripName = obj.optString("tripName", ""),
                riderId = targetRiderId,
                distanceKm = distanceKm,
                isShortTrip = obj.optBoolean("isShortTrip", false),
                tollAmount = obj.optDouble("tollAmount", 0.0),
                parkingAmount = obj.optDouble("parkingAmount", 0.0),
                calculatedFare = calculatedFare,
                isPaid = obj.optBoolean("isPaid", false),
                isWaived = obj.optBoolean("isWaived", false),
                tripDate = tripDate,
                arrivalDate = obj.optLongOrNull("arrivalDate"),
                settledDate = obj.optLongOrNull("settledDate"),
                passengerCount = obj.optInt("passengerCount", 1).coerceAtLeast(1),
                fuelCost = obj.optDouble("fuelCost", 0.0),
                maintenanceAmount = obj.optDouble("maintenanceAmount", 0.0),
                totalTripCost = obj.optDouble("totalTripCost", calculatedFare),
                gpsPointsCount = obj.optInt("gpsPointsCount", 0),
                isAutoTracked = obj.optBoolean("isAutoTracked", false),
                customFuelMileageKmPerL = obj.optDoubleOrNull("customFuelMileageKmPerL"),
                customFuelPricePerLiter = obj.optDoubleOrNull("customFuelPricePerLiter"),
                customMaintenanceMarkup = obj.optDoubleOrNull("customMaintenanceMarkup"),
                tripNotes = obj.optString("tripNotes", ""),
                isRecurring = obj.optBoolean("isRecurring", false),
                recurrenceInterval = obj.optInt("recurrenceInterval", 1).coerceAtLeast(1),
                recurrenceUnit = obj.optString("recurrenceUnit", RecurrenceUnit.NONE.name),
                nextOccurrenceDate = obj.optLongOrNull("nextOccurrenceDate"),
                recurrenceEndDate = obj.optLongOrNull("recurrenceEndDate")
            )
            tripDao.insertTrip(entity)
            restoredTripsCount++
        }

        BackupRestoreSummary(
            ridersCount = if (mode == RestoreMode.REPLACE_ALL) ridersArray.length() else restoredRidersCount,
            tripsCount = restoredTripsCount,
            timestampMillis = exportedAt,
            sourceLabel = sourceLabel
        )
    }

    private fun pruneOldManualSnapshots(maxKeep: Int) {
        val manualFiles = backupDir.listFiles { file ->
            file.isFile &&
                file.name.endsWith(".json", ignoreCase = true) &&
                file.name != AUTO_BACKUP_FILENAME
        }?.sortedByDescending { it.lastModified() }.orEmpty()

        if (manualFiles.size > maxKeep) {
            manualFiles.drop(maxKeep).forEach { oldFile ->
                runCatching { oldFile.delete() }
            }
        }
    }

    private fun buildTripSignature(
        tripGroupId: String,
        riderId: Long,
        tripDate: Long,
        distanceKm: Double,
        calculatedFare: Double
    ): String {
        val roundedDist = String.format(Locale.US, "%.2f", distanceKm)
        val roundedFare = String.format(Locale.US, "%.2f", calculatedFare)
        return if (tripGroupId.isNotBlank()) {
            "grp:$tripGroupId|r:$riderId"
        } else {
            "legacy:$riderId|t:$tripDate|d:$roundedDist|f:$roundedFare"
        }
    }

    private fun serializeSettings(settings: PricingSettings): JSONObject = JSONObject().apply {
        put("currencySymbol", settings.currencySymbol)
        put("vehicleName", settings.vehicleName)
        put("preferredFuelType", settings.preferredFuelType)
        put("fuelEfficiencyUnit", settings.fuelEfficiencyUnit.name)
        put("shortTripCutoffKm", settings.shortTripCutoffKm)
        put("shortTripFlatRate", settings.shortTripFlatRate)
        put("waiveShortTripTolls", settings.waiveShortTripTolls)
        put("enableShortTripTier", settings.enableShortTripTier)
        put("fuelPricePerLiter", settings.fuelPricePerLiter)
        put("vehicleMileageKmPerL", settings.vehicleMileageKmPerL)
        put("maintenanceMarkup", settings.maintenanceMarkup)
        put("maintenanceMarkupMode", settings.maintenanceMarkupMode.name)
        put("autoDetectDriveBluetooth", settings.autoDetectDriveBluetooth)
        put("triggerBluetoothAddresses", JSONArray(settings.triggerBluetoothAddresses.toList()))
        put("triggerBluetoothNames", JSONArray(settings.triggerBluetoothNames.toList()))
        put("autoDetectSpeedThresholdKmh", settings.autoDetectSpeedThresholdKmh)
        put("isDarkTheme", settings.isDarkTheme)
    }

    private fun deserializeSettings(obj: JSONObject, fallback: PricingSettings): PricingSettings {
        val unitName = obj.optString("fuelEfficiencyUnit", fallback.fuelEfficiencyUnit.name)
        val efficiencyUnit = runCatching { FuelEfficiencyUnit.valueOf(unitName) }
            .getOrDefault(fallback.fuelEfficiencyUnit)

        val modeName = obj.optString("maintenanceMarkupMode", fallback.maintenanceMarkupMode.name)
        val markupMode = runCatching { MaintenanceMarkupMode.valueOf(modeName) }
            .getOrDefault(fallback.maintenanceMarkupMode)

        val addresses = mutableSetOf<String>()
        obj.optJSONArray("triggerBluetoothAddresses")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i)?.takeIf { it.isNotBlank() }?.let { addresses.add(it) }
            }
        }

        val names = mutableSetOf<String>()
        obj.optJSONArray("triggerBluetoothNames")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optString(i)?.takeIf { it.isNotBlank() }?.let { names.add(it) }
            }
        }

        return PricingSettings(
            currencySymbol = obj.optString("currencySymbol", fallback.currencySymbol),
            vehicleName = obj.optString("vehicleName", fallback.vehicleName),
            preferredFuelType = obj.optString("preferredFuelType", fallback.preferredFuelType),
            fuelEfficiencyUnit = efficiencyUnit,
            shortTripCutoffKm = obj.optDouble("shortTripCutoffKm", fallback.shortTripCutoffKm),
            shortTripFlatRate = obj.optDouble("shortTripFlatRate", fallback.shortTripFlatRate),
            waiveShortTripTolls = obj.optBoolean("waiveShortTripTolls", fallback.waiveShortTripTolls),
            enableShortTripTier = obj.optBoolean("enableShortTripTier", fallback.enableShortTripTier),
            fuelPricePerLiter = obj.optDouble("fuelPricePerLiter", fallback.fuelPricePerLiter),
            vehicleMileageKmPerL = obj.optDouble("vehicleMileageKmPerL", fallback.vehicleMileageKmPerL),
            maintenanceMarkup = obj.optDouble("maintenanceMarkup", fallback.maintenanceMarkup),
            maintenanceMarkupMode = markupMode,
            autoDetectDriveBluetooth = obj.optBoolean("autoDetectDriveBluetooth", fallback.autoDetectDriveBluetooth),
            triggerBluetoothAddresses = addresses,
            triggerBluetoothNames = names,
            autoDetectSpeedThresholdKmh = obj.optDouble(
                "autoDetectSpeedThresholdKmh",
                fallback.autoDetectSpeedThresholdKmh
            ),
            isDarkTheme = obj.optBoolean("isDarkTheme", fallback.isDarkTheme)
        )
    }

    private fun serializeRiders(riders: List<RiderEntity>): JSONArray = JSONArray().apply {
        riders.forEach { rider ->
            put(
                JSONObject().apply {
                    put("id", rider.id)
                    put("name", rider.name)
                    put("phone", rider.phone ?: JSONObject.NULL)
                    put("createdAt", rider.createdAt)
                }
            )
        }
    }

    private fun serializeTrips(trips: List<Trip>): JSONArray = JSONArray().apply {
        trips.forEach { trip ->
            put(
                JSONObject().apply {
                    put("id", trip.id)
                    put("tripGroupId", trip.tripGroupId)
                    put("tripName", trip.tripName)
                    put("riderId", trip.riderId)
                    put("riderName", trip.riderName)
                    put("riderPhone", trip.riderPhone ?: JSONObject.NULL)
                    put("distanceKm", trip.distanceKm)
                    put("isShortTrip", trip.isShortTrip)
                    put("tollAmount", trip.tollAmount)
                    put("parkingAmount", trip.parkingAmount)
                    put("calculatedFare", trip.calculatedFare)
                    put("isPaid", trip.isPaid)
                    put("isWaived", trip.isWaived)
                    put("tripDate", trip.tripDate)
                    put("arrivalDate", trip.arrivalDate ?: JSONObject.NULL)
                    put("settledDate", trip.settledDate ?: JSONObject.NULL)
                    put("passengerCount", trip.passengerCount)
                    put("fuelCost", trip.fuelCost)
                    put("maintenanceAmount", trip.maintenanceAmount)
                    put("totalTripCost", trip.totalTripCost)
                    put("gpsPointsCount", trip.gpsPointsCount)
                    put("isAutoTracked", trip.isAutoTracked)
                    put("customFuelMileageKmPerL", trip.customFuelMileageKmPerL ?: JSONObject.NULL)
                    put("customFuelPricePerLiter", trip.customFuelPricePerLiter ?: JSONObject.NULL)
                    put("customMaintenanceMarkup", trip.customMaintenanceMarkup ?: JSONObject.NULL)
                    put("tripNotes", trip.tripNotes)
                    put("isRecurring", trip.isRecurring)
                    put("recurrenceInterval", trip.recurrenceInterval)
                    put("recurrenceUnit", trip.recurrenceUnit.name)
                    put("nextOccurrenceDate", trip.nextOccurrenceDate ?: JSONObject.NULL)
                    put("recurrenceEndDate", trip.recurrenceEndDate ?: JSONObject.NULL)
                }
            )
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (isNull(key) || !has(key)) null else optLong(key)

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key).takeIf { !it.isNaN() }

    companion object {
        const val BACKUP_SCHEMA_VERSION = 1
        const val AUTO_BACKUP_FILENAME = "trippawl_auto_backup_latest.json"
        const val GDRIVE_CLOUD_FILENAME = "trippawl_cloud_backup.json"
        private const val PREFS_NAME = "trippawl_backup_prefs"
        private const val KEY_AUTO_LOCAL_BACKUP = "auto_local_backup_enabled"
        private const val KEY_AUTO_BACKUP_FREQUENCY = "auto_backup_frequency"
        private const val KEY_GDRIVE_ENABLED = "gdrive_backup_enabled"
        private const val KEY_GDRIVE_ACCOUNT_LABEL = "gdrive_account_label"
        private const val KEY_GDRIVE_ACCESS_TOKEN = "gdrive_access_token"
        private const val KEY_LAST_LOCAL_BACKUP_MILLIS = "last_local_backup_millis"
        private const val KEY_LAST_GDRIVE_BACKUP_MILLIS = "last_gdrive_backup_millis"
    }
}
