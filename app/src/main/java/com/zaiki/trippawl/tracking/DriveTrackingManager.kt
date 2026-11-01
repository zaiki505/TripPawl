package com.zaiki.trippawl.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zaiki.trippawl.MainActivity
import com.zaiki.trippawl.data.backup.BackupRestoreManager
import com.zaiki.trippawl.data.local.dao.DriveLogDao
import com.zaiki.trippawl.data.local.entity.DriveLogEntity
import com.zaiki.trippawl.data.local.entity.DriveLogStatus
import com.zaiki.trippawl.domain.model.GroupedTrip
import com.zaiki.trippawl.domain.model.PricingSettings
import com.zaiki.trippawl.domain.model.Rider
import com.zaiki.trippawl.domain.model.Trip
import com.zaiki.trippawl.domain.model.groupByTripSession
import com.zaiki.trippawl.domain.repository.RiderRepository
import com.zaiki.trippawl.domain.repository.SettingsRepository
import com.zaiki.trippawl.domain.repository.TripRepository
import com.zaiki.trippawl.domain.usecase.CalculateTripFareUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sin

data class GpsCoordinate(
    val latitude: Double,
    val longitude: Double,
    val timestampMillis: Long
)

data class CompletedDriveSummary(
    val startTimeMillis: Long,
    val arrivalTimeMillis: Long,
    val distanceDrivenKm: Double,
    val gpsPointsCount: Int,
    val selectedPassengerIds: Set<Long>,
    val triggerSource: String,
    val logId: Long = 0L
)

data class DriveTrackingState(
    val isDriveActive: Boolean = false,
    val isSimulated: Boolean = false,
    val isSpeedMonitorActive: Boolean = false,
    val isCarBluetoothConnected: Boolean = false,
    val connectedBluetoothDeviceLabel: String? = null,
    val triggerSource: String = "Manual",
    val startTimeMillis: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val distanceDrivenKm: Double = 0.0,
    val currentSpeedKmh: Double = 0.0,
    val gpsPointsCount: Int = 0,
    val routePoints: List<GpsCoordinate> = emptyList(),
    val selectedPassengerIds: Set<Long> = emptySet(),
    val isPassengerPromptOpen: Boolean = false,
    val completedDriveSummary: CompletedDriveSummary? = null
)

/**
 * Singleton manager coordinating automated background GPS trip logging,
 * combined Bluetooth + Speed threshold auto-start detection,
 * the "Who is riding with you today?" passenger prompt, trip-ended notifications,
 * owner/driver payment collection reminder notifications, and multi-passenger fare splitting.
 */
@Singleton
class DriveTrackingManager @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val riderRepository: RiderRepository,
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateTripFareUseCase: CalculateTripFareUseCase,
    private val backupRestoreManager: BackupRestoreManager,
    private val driveLogDao: DriveLogDao
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var timerJob: Job? = null
    private var lastLocation: Location? = null
    private var lastIdleMonitorLocation: Location? = null
    private var consecutiveSpeedThresholdHits: Int = 0
    private var lastDriveStopTimeMillis: Long = 0L
    private var lastAutoReminderMillis: Long = 0L
    private var passiveSpeedListenerRegistered: Boolean = false

    private val _state = MutableStateFlow(DriveTrackingState())
    val state: StateFlow<DriveTrackingState> = _state.asStateFlow()

    private val passiveSpeedLocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            // Only feed from the idle monitor when the foreground drive service is not already active
            if (!_state.value.isDriveActive) {
                onRealGpsLocation(location)
            }
        }

        @Deprecated("Deprecated in Android API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    init {
        ensureAlertNotificationChannel()
        ensureSpeedThresholdMonitorRunning()
        refreshBluetoothConnectionStateAsync()
    }

    private fun refreshBluetoothConnectionStateAsync() {
        scope.launch {
            val settings = runCatching { settingsRepository.settingsFlow.first() }.getOrNull() ?: return@launch
            resolveConnectedTriggerBluetoothDevice(settings)
        }
    }

    /**
     * Called when [CarBluetoothReceiver] detects `ACTION_ACL_CONNECTED` for a matching trigger Bluetooth device.
     * Arms the auto-trigger and starts the drive ONLY if the speed threshold is also already exceeded;
     * otherwise waits for speed to exceed `autoDetectSpeedThresholdKmh`.
     */
    fun onCarBluetoothConnected(deviceLabel: String) {
        _state.update {
            it.copy(
                isCarBluetoothConnected = true,
                connectedBluetoothDeviceLabel = deviceLabel
            )
        }
        ensureSpeedThresholdMonitorRunning()

        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            if (!settings.autoDetectDriveBluetooth) return@launch
            val threshold = settings.autoDetectSpeedThresholdKmh
            val currentSpeed = _state.value.currentSpeedKmh
            val outsideCooldown = (System.currentTimeMillis() - lastDriveStopTimeMillis) > 45_000L

            if (!_state.value.isDriveActive && outsideCooldown && threshold > 0.0 && currentSpeed >= threshold) {
                startDrive(
                    triggerSource = "Auto: BT ($deviceLabel) + ${currentSpeed.toInt()} km/h",
                    simulated = false,
                    showPromptImmediately = true
                )
            }
        }
    }

    /**
     * Called when [CarBluetoothReceiver] detects `ACTION_ACL_DISCONNECTED` for a matching Bluetooth device.
     */
    fun onCarBluetoothDisconnected() {
        _state.update {
            it.copy(
                isCarBluetoothConnected = false,
                connectedBluetoothDeviceLabel = null
            )
        }
        if (_state.value.isDriveActive) {
            stopDrive()
        }
    }

    /**
     * Checks whether the phone is currently connected to a qualifying trigger Bluetooth device
     * (either from [CarBluetoothReceiver] state or by inspecting currently connected bonded devices).
     */
    @SuppressLint("MissingPermission")
    private fun resolveConnectedTriggerBluetoothDevice(settings: PricingSettings): String? {
        if (!settings.autoDetectDriveBluetooth) return null
        val currentState = _state.value
        if (currentState.isCarBluetoothConnected && !currentState.connectedBluetoothDeviceLabel.isNullOrBlank()) {
            return currentState.connectedBluetoothDeviceLabel
        }

        val hasBtConnectPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (!hasBtConnectPerm) return null

        val btManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        val adapter = btManager?.adapter ?: return null
        if (!adapter.isEnabled) return null

        val bonded = runCatching { adapter.bondedDevices }.getOrNull().orEmpty()
        val hasExplicitSelection =
            settings.triggerBluetoothAddresses.isNotEmpty() || settings.triggerBluetoothNames.isNotEmpty()

        for (device in bonded) {
            val address = runCatching { device.address }.getOrNull()
            val name = runCatching { device.name }.getOrNull()

            val matchesSelection = if (hasExplicitSelection) {
                val matchesAddress = address != null &&
                    settings.triggerBluetoothAddresses.any { it.equals(address, ignoreCase = true) }
                val matchesName = !name.isNullOrBlank() &&
                    settings.triggerBluetoothNames.any { it.equals(name, ignoreCase = true) }
                matchesAddress || matchesName
            } else {
                runCatching {
                    val majorClass = device.bluetoothClass?.majorDeviceClass
                    majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO || majorClass == null
                }.getOrDefault(false)
            }

            if (!matchesSelection) continue

            val isConnectedAcl = runCatching {
                val method = device.javaClass.getMethod("isConnected")
                method.invoke(device) as? Boolean ?: false
            }.getOrDefault(false)

            if (isConnectedAcl) {
                val label = name?.takeIf { it.isNotBlank() } ?: address ?: "Car Bluetooth"
                _state.update {
                    it.copy(
                        isCarBluetoothConnected = true,
                        connectedBluetoothDeviceLabel = label
                    )
                }
                return label
            }
        }

        return null
    }

    /**
     * Ensures passive/background location updates are registered even when no drive is active,
     * so that exceeding `autoDetectSpeedThresholdKmh` while connected to the target Bluetooth device
     * automatically starts a drive.
     */
    @SuppressLint("MissingPermission")
    fun ensureSpeedThresholdMonitorRunning() {
        val hasFine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) {
            _state.update { it.copy(isSpeedMonitorActive = false) }
            return
        }

        val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        if (passiveSpeedListenerRegistered) {
            _state.update { it.copy(isSpeedMonitorActive = true) }
            return
        }

        val mainLooper = Looper.getMainLooper()
        var registeredAny = false

        runCatching {
            val fusedProviderName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                LocationManager.FUSED_PROVIDER
            } else {
                "fused"
            }
            if (lm.allProviders.contains(fusedProviderName)) {
                lm.requestLocationUpdates(
                    fusedProviderName,
                    2_500L,
                    2f,
                    passiveSpeedLocationListener,
                    mainLooper
                )
                registeredAny = true
            }
        }

        runCatching {
            if (hasFine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    2_500L,
                    2f,
                    passiveSpeedLocationListener,
                    mainLooper
                )
                registeredAny = true
            }
        }

        runCatching {
            if (lm.allProviders.contains(LocationManager.PASSIVE_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    2_000L,
                    0f,
                    passiveSpeedLocationListener,
                    mainLooper
                )
                registeredAny = true
            }
        }

        passiveSpeedListenerRegistered = registeredAny
        _state.update { it.copy(isSpeedMonitorActive = registeredAny) }
    }

    /**
     * Initiates background drive recording and displays the immediate low-friction
     * "Who is riding with you today?" prompt & heads-up notification.
     */
    fun startDrive(
        triggerSource: String = "Manual Start",
        simulated: Boolean = false,
        showPromptImmediately: Boolean = true
    ) {
        if (_state.value.isDriveActive) {
            if (showPromptImmediately) {
                _state.update { it.copy(isPassengerPromptOpen = true) }
            }
            return
        }

        val now = System.currentTimeMillis()
        lastLocation = null
        lastIdleMonitorLocation = null
        consecutiveSpeedThresholdHits = 0
        cancelCompletedDriveNotification()

        scope.launch {
            val savedRiders = riderRepository.observeAllRiders().first()
            val initialSelected = _state.value.selectedPassengerIds.ifEmpty {
                savedRiders.take(1).map { it.id }.toSet()
            }

            _state.value = DriveTrackingState(
                isDriveActive = true,
                isSimulated = simulated,
                isSpeedMonitorActive = passiveSpeedListenerRegistered,
                isCarBluetoothConnected = _state.value.isCarBluetoothConnected,
                connectedBluetoothDeviceLabel = _state.value.connectedBluetoothDeviceLabel,
                triggerSource = triggerSource,
                startTimeMillis = now,
                elapsedSeconds = 0L,
                distanceDrivenKm = 0.0,
                currentSpeedKmh = if (simulated) 42.0 else _state.value.currentSpeedKmh,
                gpsPointsCount = if (simulated) 1 else 0,
                routePoints = if (simulated) {
                    listOf(GpsCoordinate(3.1390, 101.6869, now))
                } else {
                    emptyList()
                },
                selectedPassengerIds = initialSelected,
                isPassengerPromptOpen = showPromptImmediately,
                completedDriveSummary = null
            )

            // Launch foreground service for background GPS logging & heads-up notification
            runCatching {
                val serviceIntent = Intent(appContext, DriveTrackingForegroundService::class.java).apply {
                    action = DriveTrackingForegroundService.ACTION_START_DRIVE
                    putExtra(DriveTrackingForegroundService.EXTRA_TRIGGER_SOURCE, triggerSource)
                }
                ContextCompat.startForegroundService(appContext, serviceIntent)
            }

            startTelemetryTicker(simulated)
        }
    }

    private fun startTelemetryTicker(simulated: Boolean) {
        timerJob?.cancel()
        timerJob = scope.launch {
            var tick = 0
            while (isActive && _state.value.isDriveActive) {
                delay(1_000L)
                tick++
                _state.update { current ->
                    if (!current.isDriveActive) return@update current
                    if (simulated) {
                        val simulatedSpeedKmh = 44.0 + 10.0 * sin(tick * 0.35)
                        val deltaKm = 0.16
                        val nextLat = (current.routePoints.lastOrNull()?.latitude ?: 3.1390) + 0.0012
                        val nextLon = (current.routePoints.lastOrNull()?.longitude ?: 101.6869) + 0.0010
                        val newPoint = GpsCoordinate(nextLat, nextLon, System.currentTimeMillis())
                        current.copy(
                            elapsedSeconds = current.elapsedSeconds + 1,
                            distanceDrivenKm = current.distanceDrivenKm + deltaKm,
                            currentSpeedKmh = simulatedSpeedKmh,
                            gpsPointsCount = current.gpsPointsCount + 1,
                            routePoints = (current.routePoints + newPoint).takeLast(500)
                        )
                    } else {
                        current.copy(elapsedSeconds = current.elapsedSeconds + 1)
                    }
                }
            }
        }
    }

    /**
     * Called by [DriveTrackingForegroundService] or the passive speed monitor when a real GPS fix arrives.
     * Supports BOTH:
     * 1. Combined Bluetooth + Speed threshold auto-start detection when idle (triggers ONLY when connected
     *    to the configured Bluetooth device AND exceeding `autoDetectSpeedThresholdKmh`).
     * 2. Active anchor-point distance accumulation during a drive.
     */
    fun onRealGpsLocation(location: Location) {
        val rawSpeedKmh = if (location.hasSpeed() && location.speed > 0f) (location.speed * 3.6) else 0.0

        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val now = System.currentTimeMillis()

            // 1. Combined Bluetooth + Speed threshold auto-start check when idle
            if (!_state.value.isDriveActive) {
                // Ignore stale cached locations older than 15s
                if (location.time > 0L && now - location.time > 15_000L) {
                    return@launch
                }
                // Ignore inaccurate fixes (> 40m) for speed auto-detection
                if (location.hasAccuracy() && location.accuracy > 40f) {
                    return@launch
                }

                var derivedIdleSpeedKmh = 0.0
                val prevIdle = lastIdleMonitorLocation
                if (prevIdle != null) {
                    val dtSec = ((location.time - prevIdle.time).coerceAtLeast(500L)) / 1000.0
                    val distMeters = prevIdle.distanceTo(location)
                    if (dtSec in 0.8..30.0 && distMeters in 4.0f..1000.0f) {
                        derivedIdleSpeedKmh = ((distMeters / dtSec) * 3.6).coerceIn(0.0, 220.0)
                    }
                }
                lastIdleMonitorLocation = location

                val effectiveIdleSpeedKmh = if (rawSpeedKmh > 1.0) rawSpeedKmh else derivedIdleSpeedKmh
                val connectedBtLabel = resolveConnectedTriggerBluetoothDevice(settings)

                _state.update {
                    it.copy(
                        currentSpeedKmh = if (effectiveIdleSpeedKmh < 1.5) 0.0 else effectiveIdleSpeedKmh,
                        isSpeedMonitorActive = true,
                        isCarBluetoothConnected = connectedBtLabel != null,
                        connectedBluetoothDeviceLabel = connectedBtLabel
                    )
                }

                // Respect a 45-second cooldown after stopping a drive so stopping while moving doesn't immediately re-trigger
                val outsideCooldown = (now - lastDriveStopTimeMillis) > 45_000L
                val threshold = settings.autoDetectSpeedThresholdKmh

                // Auto-trigger ONLY when BOTH conditions are met:
                // (A) Connected to the set Bluetooth device, AND (B) Speed >= threshold
                val isBluetoothConditionMet = settings.autoDetectDriveBluetooth && connectedBtLabel != null
                val isSpeedConditionMet = threshold > 0.0 && effectiveIdleSpeedKmh >= threshold

                if (outsideCooldown && isBluetoothConditionMet && isSpeedConditionMet) {
                    consecutiveSpeedThresholdHits++
                    // Trigger immediately if hardware Doppler speed is confident, or after 2 consecutive derived fixes
                    if (rawSpeedKmh >= threshold || consecutiveSpeedThresholdHits >= 2) {
                        consecutiveSpeedThresholdHits = 0
                        startDrive(
                            triggerSource = "Auto: BT ($connectedBtLabel) + ${effectiveIdleSpeedKmh.toInt()} km/h",
                            simulated = false,
                            showPromptImmediately = true
                        )
                        return@launch
                    }
                } else {
                    consecutiveSpeedThresholdHits = 0
                }
            }

            // 2. Active drive distance & route accumulation
            if (_state.value.isDriveActive && !_state.value.isSimulated) {
                val startMillis = _state.value.startTimeMillis
                // Ignore stale cached locations older than 20s before drive start
                if (location.time > 0L && location.time < startMillis - 20_000L) {
                    return@launch
                }

                val prev = lastLocation
                if (prev == null) {
                    // Accept first anchor point if accuracy is reasonable (<= 65m)
                    if (location.hasAccuracy() && location.accuracy > 65f) {
                        return@launch
                    }
                    lastLocation = location
                    val firstPoint = GpsCoordinate(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestampMillis = if (location.time > 0L) location.time else System.currentTimeMillis()
                    )
                    _state.update { current ->
                        current.copy(
                            currentSpeedKmh = rawSpeedKmh,
                            gpsPointsCount = current.gpsPointsCount.coerceAtLeast(1),
                            routePoints = listOf(firstPoint)
                        )
                    }
                    return@launch
                }

                // Filter out noisy outlier fixes (> 45m horizontal inaccuracy)
                if (location.hasAccuracy() && location.accuracy > 45f) {
                    return@launch
                }

                val deltaMeters = prev.distanceTo(location)
                val deltaTimeSec = ((location.time - prev.time).coerceAtLeast(500L)) / 1000.0
                val minStepMeters = if (location.hasAccuracy()) {
                    (location.accuracy * 0.25f).coerceIn(1.5f, 4.5f)
                } else {
                    2.0f
                }

                if (deltaMeters >= minStepMeters && deltaMeters < 3_000f) {
                    // Advance anchor point ONLY when movement exceeds the jitter threshold
                    lastLocation = location
                    val validDeltaKm = deltaMeters / 1000.0
                    val derivedSpeedKmh = if (rawSpeedKmh > 0.5) {
                        rawSpeedKmh
                    } else {
                        ((deltaMeters / deltaTimeSec) * 3.6).coerceIn(0.0, 220.0)
                    }

                    val point = GpsCoordinate(
                        latitude = location.latitude,
                        longitude = location.longitude,
                        timestampMillis = if (location.time > 0L) location.time else System.currentTimeMillis()
                    )

                    _state.update { current ->
                        current.copy(
                            distanceDrivenKm = current.distanceDrivenKm + validDeltaKm,
                            currentSpeedKmh = derivedSpeedKmh,
                            gpsPointsCount = current.gpsPointsCount + 1,
                            routePoints = (current.routePoints + point).takeLast(500)
                        )
                    }
                } else {
                    // Stationary or sub-step: keep lastLocation anchor intact, update speed display
                    _state.update { current ->
                        current.copy(
                            currentSpeedKmh = if (rawSpeedKmh < 1.5) 0.0 else rawSpeedKmh
                        )
                    }
                }
            }
        }
    }

    fun togglePassengerSelection(riderId: Long) {
        _state.update { current ->
            val updated = current.selectedPassengerIds.toMutableSet()
            if (!updated.add(riderId)) {
                updated.remove(riderId)
            }
            current.copy(selectedPassengerIds = updated)
        }
    }

    fun setSelectedPassengers(riderIds: Set<Long>) {
        _state.update { it.copy(selectedPassengerIds = riderIds) }
    }

    fun openPassengerPrompt() {
        _state.update { it.copy(isPassengerPromptOpen = true) }
    }

    fun dismissPassengerPrompt() {
        _state.update { it.copy(isPassengerPromptOpen = false) }
    }

    fun quickAddPassengerDuringDrive(name: String, phone: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        scope.launch {
            val rider = riderRepository.getOrCreateRiderByName(trimmed, phone)
            _state.update { current ->
                current.copy(selectedPassengerIds = current.selectedPassengerIds + rider.id)
            }
        }
    }

    /**
     * Stops active drive recording (triggered manually, via notification, or when Bluetooth disconnects),
     * opens the completed drive confirmation & manual override summary, and posts a system notification
     * summarizing the completed trip.
     */
    fun stopDrive() {
        val current = _state.value
        if (!current.isDriveActive) return

        timerJob?.cancel()
        timerJob = null
        lastLocation = null
        lastIdleMonitorLocation = null
        consecutiveSpeedThresholdHits = 0
        lastDriveStopTimeMillis = System.currentTimeMillis()

        runCatching {
            val stopIntent = Intent(appContext, DriveTrackingForegroundService::class.java).apply {
                action = DriveTrackingForegroundService.ACTION_STOP_DRIVE
            }
            appContext.stopService(stopIntent)
        }

        val effectiveDistance = current.distanceDrivenKm
        val summary = CompletedDriveSummary(
            startTimeMillis = current.startTimeMillis,
            arrivalTimeMillis = lastDriveStopTimeMillis,
            distanceDrivenKm = effectiveDistance,
            gpsPointsCount = current.gpsPointsCount,
            selectedPassengerIds = current.selectedPassengerIds,
            triggerSource = current.triggerSource,
            logId = 0L
        )

        _state.value = DriveTrackingState(
            isDriveActive = false,
            isSpeedMonitorActive = passiveSpeedListenerRegistered,
            isCarBluetoothConnected = current.isCarBluetoothConnected,
            connectedBluetoothDeviceLabel = current.connectedBluetoothDeviceLabel,
            selectedPassengerIds = current.selectedPassengerIds,
            completedDriveSummary = summary
        )

        // Persist GPS drive log to database so driver can review/re-log from Dashboard at any time
        val logEntity = DriveLogEntity(
            startTimeMillis = current.startTimeMillis,
            endTimeMillis = lastDriveStopTimeMillis,
            distanceKm = effectiveDistance,
            gpsPointsCount = current.gpsPointsCount,
            triggerSource = current.triggerSource,
            passengerIdsCsv = current.selectedPassengerIds.joinToString(","),
            status = DriveLogStatus.PENDING.name,
            savedTripGroupId = ""
        )
        scope.launch {
            val insertedLogId = driveLogDao.insertLog(logEntity)
            _state.update { state ->
                val activeSummary = state.completedDriveSummary
                if (activeSummary != null && activeSummary.startTimeMillis == current.startTimeMillis) {
                    state.copy(completedDriveSummary = activeSummary.copy(logId = insertedLogId))
                } else {
                    state
                }
            }
        }

        // Post trip-ended system notification with distance and estimated split
        postTripCompletedNotification(summary)

        // Ensure passive speed monitor continues listening for future drives
        ensureSpeedThresholdMonitorRunning()
    }

    fun dismissCompletedDriveSummary() {
        val currentSummary = _state.value.completedDriveSummary
        if (currentSummary != null && currentSummary.logId != 0L) {
            scope.launch {
                driveLogDao.updateLogStatus(currentSummary.logId, DriveLogStatus.DISCARDED.name, "")
            }
        }
        cancelCompletedDriveNotification()
        _state.update { it.copy(completedDriveSummary = null) }
    }

    /**
     * Reopens a previously recorded drive log for review and saving.
     */
    fun reopenDriveLog(log: DriveLogEntity) {
        val summary = CompletedDriveSummary(
            startTimeMillis = log.startTimeMillis,
            arrivalTimeMillis = log.endTimeMillis,
            distanceDrivenKm = log.distanceKm,
            gpsPointsCount = log.gpsPointsCount,
            selectedPassengerIds = log.passengerIds,
            triggerSource = log.triggerSource,
            logId = log.id
        )
        _state.update { it.copy(completedDriveSummary = summary) }
    }

    fun deleteDriveLog(logId: Long) {
        scope.launch {
            driveLogDao.deleteLog(logId)
        }
    }

    fun clearAllDriveLogs() {
        scope.launch {
            driveLogDao.deleteAllLogs()
        }
    }

    /**
     * Finalizes a completed GPS drive, applying any manual Toll, Parking, or Custom Rider Payment overrides,
     * and charging each selected passenger their share in the ledger.
     */
    fun saveCompletedDriveToLedger(
        tripName: String = "",
        distanceOverrideKm: Double?,
        tollAmount: Double,
        parkingAmount: Double,
        selectedPassengerIds: Set<Long>,
        customPaymentPerRider: Double? = null,
        isPaid: Boolean,
        onSaved: () -> Unit
    ) {
        val summary = _state.value.completedDriveSummary ?: return
        val finalDistance = (distanceOverrideKm ?: summary.distanceDrivenKm).coerceAtLeast(0.01)

        scope.launch {
            val settings = settingsRepository.settingsFlow.first()
            val allRiders = riderRepository.observeAllRiders().first()
            val chosenRiders: List<Rider> = allRiders.filter { it.id in selectedPassengerIds }.ifEmpty {
                listOf(riderRepository.getOrCreateRiderByName("Carpool Passenger"))
            }

            val calc = calculateTripFareUseCase(
                distanceKm = finalDistance,
                tollAmount = tollAmount,
                passengerCount = chosenRiders.size,
                settings = settings,
                parkingAmount = parkingAmount
            )

            val effectiveRiderFare = customPaymentPerRider?.takeIf { it >= 0.0 } ?: calc.riderShare
            val settledDate = if (isPaid) summary.arrivalTimeMillis else null
            val sharedGroupId = "grp_${summary.startTimeMillis}_${System.nanoTime()}"
            val cleanTripName = tripName.trim()

            chosenRiders.forEach { rider ->
                val trip = Trip(
                    tripGroupId = sharedGroupId,
                    tripName = cleanTripName,
                    riderId = rider.id,
                    riderName = rider.name,
                    riderPhone = rider.phone,
                    distanceKm = calc.distanceKm,
                    isShortTrip = calc.isShortTrip,
                    tollAmount = calc.effectiveTollAmount,
                    parkingAmount = calc.parkingAmount,
                    calculatedFare = effectiveRiderFare,
                    isPaid = isPaid,
                    tripDate = summary.startTimeMillis,
                    arrivalDate = summary.arrivalTimeMillis,
                    settledDate = settledDate,
                    passengerCount = chosenRiders.size,
                    fuelCost = calc.actualFuelCost,
                    maintenanceAmount = calc.maintenanceSurcharge,
                    totalTripCost = calc.totalTripFare,
                    gpsPointsCount = summary.gpsPointsCount,
                    isAutoTracked = true
                )
                tripRepository.insertTrip(trip)
            }

            if (summary.logId != 0L) {
                driveLogDao.updateLogStatus(summary.logId, DriveLogStatus.SAVED.name, sharedGroupId)
            }

            backupRestoreManager.triggerAutoBackupIfEnabled()
            cancelCompletedDriveNotification()
            _state.update { it.copy(completedDriveSummary = null) }
            onSaved()
        }
    }

    private fun ensureAlertNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val channel = NotificationChannel(
                CHANNEL_ID_DRIVE_ALERTS,
                "TripPawl Driver Reminders & Trip Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications when a trip ends and reminders for the driver to collect pending carpool payments"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun postTripCompletedNotification(summary: CompletedDriveSummary) {
        scope.launch {
            runCatching {
                ensureAlertNotificationChannel()
                val settings = settingsRepository.settingsFlow.first()
                val paxCount = summary.selectedPassengerIds.size.coerceAtLeast(1)
                val calc = calculateTripFareUseCase(
                    distanceKm = summary.distanceDrivenKm,
                    tollAmount = 0.0,
                    passengerCount = paxCount,
                    settings = settings,
                    parkingAmount = 0.0
                )

                val openIntent = Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_OPEN_COMPLETED_SUMMARY, true)
                }
                val pendingIntent = PendingIntent.getActivity(
                    appContext,
                    201,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val distText = String.format(Locale.US, "%.2f km", summary.distanceDrivenKm)
                val shareText = "${settings.currencySymbol} ${String.format(Locale.US, "%.2f", calc.riderShare)}"
                val totalText = "${settings.currencySymbol} ${String.format(Locale.US, "%.2f", calc.totalTripFare)}"
                val durationMin = ((summary.arrivalTimeMillis - summary.startTimeMillis).coerceAtLeast(1_000L) / 60_000L)

                val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_DRIVE_ALERTS)
                    .setSmallIcon(android.R.drawable.ic_dialog_map)
                    .setContentTitle("Trip Ended · $distText Driven")
                    .setContentText("Est. $shareText/pax ($paxCount pax · Total $totalText) · Tap to review & save")
                    .setStyle(
                        NotificationCompat.BigTextStyle().bigText(
                            "Distance: $distText (${summary.gpsPointsCount} GPS pts · ${durationMin}m)\n" +
                                "Estimated Share: $shareText per passenger ($paxCount pax)\n" +
                                "Total Trip Cost: $totalText · Tap to add tolls/parking & save to ledger."
                        )
                    )
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_STATUS)
                    .setAutoCancel(true)
                    .setContentIntent(pendingIntent)
                    .addAction(
                        android.R.drawable.ic_menu_save,
                        "Review & Save Trip",
                        pendingIntent
                    )
                    .build()

                val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(NOTIFICATION_ID_DRIVE_COMPLETED, notification)
            }
        }
    }

    private fun cancelCompletedDriveNotification() {
        runCatching {
            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.cancel(NOTIFICATION_ID_DRIVE_COMPLETED)
        }
    }

    /**
     * Automatically checks if the owner/driver has pending uncollected payments and posts a polite
     * driver reminder notification (throttled to at most once every 6 hours so it is never spammy).
     */
    suspend fun checkAndPostAutomaticDriverCollectionReminder() {
        val settings = settingsRepository.settingsFlow.first()
        if (!settings.unpaidReminderEnabled) return

        val now = System.currentTimeMillis()
        val intervalMillis = settings.unpaidReminderInterval.millis
        if (now - settings.lastUnpaidReminderTimestamp < intervalMillis) return

        val allTrips = tripRepository.observeAllTrips().first()
        val hasUnpaid = allTrips.any { !it.isPaid && !it.isWaived }
        if (hasUnpaid) {
            val count = sendUnpaidTripsNotificationAlert()
            if (count > 0) {
                settingsRepository.setLastUnpaidReminderTimestamp(now)
            }
        }
    }

    /**
     * Posts a high-priority notification reminder for the OWNER / DRIVER reminding them that there are
     * still pending carpool payments to collect across their trips.
     */
    suspend fun sendUnpaidTripsNotificationAlert(): Int {
        ensureAlertNotificationChannel()
        val settings = settingsRepository.settingsFlow.first()
        val allTrips = tripRepository.observeAllTrips().first()
        val unpaidTrips = allTrips.filter { !it.isPaid }
        if (unpaidTrips.isEmpty()) return 0

        val unpaidGroups = unpaidTrips.groupByTripSession()
        val totalOutstanding = unpaidTrips.sumOf { it.calculatedFare }
        val totalText = "${settings.currencySymbol} ${String.format(Locale.US, "%.2f", totalOutstanding)}"

        val riderTotals = unpaidTrips
            .groupBy { it.riderName }
            .mapValues { (_, list) -> list.sumOf { it.calculatedFare } }
            .entries
            .sortedByDescending { it.value }

        val shortSummary = "You still have $totalText to collect from ${unpaidGroups.size} trip(s) (${riderTotals.size} rider(s))."

        val detailedLines = buildString {
            appendLine("Driver Reminder: You still have $totalText uncollected across ${unpaidGroups.size} trip(s):")
            riderTotals.take(5).forEach { (name, amt) ->
                appendLine("• Collect ${settings.currencySymbol} ${String.format(Locale.US, "%.2f", amt)} from $name")
            }
            if (riderTotals.size > 5) {
                appendLine("• +${riderTotals.size - 5} more rider(s)")
            }
            append("Tap to review your ledger and mark collected payments as Paid.")
        }.trim()

        runCatching {
            val openIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                202,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_DRIVE_ALERTS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Payment Collection Reminder · $totalText")
                .setContentText(shortSummary)
                .setStyle(NotificationCompat.BigTextStyle().bigText(detailedLines))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID_UNPAID_ALERT, notification)
        }

        return unpaidGroups.size
    }

    /**
     * Posts a high-priority driver reminder notification for a specific unpaid [GroupedTrip]
     * reminding the owner/driver that payment for this trip still needs to be collected.
     */
    suspend fun sendUnpaidAlertForGroup(group: GroupedTrip): Boolean {
        val unpaidShares = group.passengerShares.filter { !it.isPaid }
        if (unpaidShares.isEmpty()) return false

        ensureAlertNotificationChannel()
        val settings = settingsRepository.settingsFlow.first()
        val totalDue = unpaidShares.sumOf { it.calculatedFare }
        val totalText = "${settings.currencySymbol} ${String.format(Locale.US, "%.2f", totalDue)}"
        val ridersOwing = unpaidShares.joinToString(", ") {
            "${it.riderName} (${settings.currencySymbol} ${String.format(Locale.US, "%.2f", it.calculatedFare)})"
        }

        runCatching {
            val openIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                203,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(appContext, CHANNEL_ID_DRIVE_ALERTS)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle("Collect $totalText · ${group.displayTitle}")
                .setContentText("Reminder: Payment still to collect for ${group.displayTitle}")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "Driver Collection Reminder for '${group.displayTitle}':\n" +
                            "You still have $totalText to collect from: $ridersOwing\n" +
                            "Tap to open TripPawl and mark collected when paid."
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            val nm = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.notify(NOTIFICATION_ID_UNPAID_ALERT + group.primaryTripId.toInt(), notification)
        }
        return true
    }

    companion object {
        const val CHANNEL_ID_DRIVE_ALERTS = "trippawl_drive_alerts_high"
        const val NOTIFICATION_ID_DRIVE_COMPLETED = 4202
        const val NOTIFICATION_ID_UNPAID_ALERT = 4203
        const val EXTRA_OPEN_COMPLETED_SUMMARY = "extra_open_completed_summary"
    }
}
