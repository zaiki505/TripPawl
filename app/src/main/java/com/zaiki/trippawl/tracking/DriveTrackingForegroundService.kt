package com.zaiki.trippawl.tracking

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.zaiki.trippawl.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

/**
 * Foreground Service that logs GPS route coordinates, total distance driven (km),
 * start time, and arrival time passively in the background without requiring the app
 * to stay open, holds a partial WakeLock so GPS continues when the screen is off,
 * and posts a high-priority heads-up notification on drive start asking:
 * "Who is riding with you today?"
 */
@AndroidEntryPoint
class DriveTrackingForegroundService : Service() {

    @Inject
    lateinit var driveTrackingManager: DriveTrackingManager

    private var locationManager: LocationManager? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var currentTriggerSource: String = "GPS Drive"

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            driveTrackingManager.onRealGpsLocation(location)
            updateOngoingNotificationProgress()
        }

        @Deprecated("Deprecated in Android API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_DRIVE -> {
                stopLocationUpdates()
                releaseWakeLock()
                if (driveTrackingManager.state.value.isDriveActive) {
                    driveTrackingManager.stopDrive()
                }
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_ARM_BT_SPEED_MONITOR -> {
                val btLabel = intent.getStringExtra(EXTRA_TRIGGER_SOURCE) ?: "Car Bluetooth"
                currentTriggerSource = btLabel
                acquireWakeLock()

                val standbyNotification = buildBluetoothStandbyNotification(btLabel)
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID_DRIVE,
                            standbyNotification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                        )
                    } else {
                        startForeground(NOTIFICATION_ID_DRIVE, standbyNotification)
                    }
                }
                startLocationUpdatesIfPermitted()
            }
            else -> {
                val triggerSource = intent?.getStringExtra(EXTRA_TRIGGER_SOURCE) ?: "GPS Drive"
                currentTriggerSource = triggerSource
                acquireWakeLock()

                val notification = buildHeadsUpDrivePromptNotification(
                    triggerSource = triggerSource,
                    distanceKm = driveTrackingManager.state.value.distanceDrivenKm,
                    speedKmh = driveTrackingManager.state.value.currentSpeedKmh,
                    isInitialPrompt = true
                )
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                            NOTIFICATION_ID_DRIVE,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                        )
                    } else {
                        startForeground(NOTIFICATION_ID_DRIVE, notification)
                    }
                }.onFailure {
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    nm?.notify(NOTIFICATION_ID_DRIVE, notification)
                }
                startLocationUpdatesIfPermitted()
            }
        }
        return START_STICKY
    }

    private fun acquireWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) return
            val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "TripPawl::DriveGpsWakeLock"
            ).apply {
                setReferenceCounted(false)
                acquire(6 * 60 * 60 * 1000L) // Up to 6 hours max safety timeout
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
            wakeLock = null
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdatesIfPermitted() {
        val hasFine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFine && !hasCoarse) return

        val lm = getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
        locationManager = lm

        // Remove any previous listener registration before re-registering
        runCatching { lm.removeUpdates(locationListener) }

        val mainLooper = Looper.getMainLooper()
        var anyProviderRegistered = false

        // 1. Register FUSED_PROVIDER ("fused") if available (combines GNSS + Wi-Fi + Cell + sensors)
        runCatching {
            val fusedProviderName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                LocationManager.FUSED_PROVIDER
            } else {
                "fused"
            }
            if (lm.allProviders.contains(fusedProviderName)) {
                lm.requestLocationUpdates(
                    fusedProviderName,
                    1_000L,
                    0f,
                    locationListener,
                    mainLooper
                )
                anyProviderRegistered = true
            }
        }

        // 2. Register hardware GPS_PROVIDER for high-precision satellite fixes
        runCatching {
            if (hasFine && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1_000L,
                    0f,
                    locationListener,
                    mainLooper
                )
                anyProviderRegistered = true
            }
        }

        // 3. Also register NETWORK_PROVIDER as fallback if GPS isn't locked yet indoors/under roof
        runCatching {
            if (!anyProviderRegistered && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    2_000L,
                    0f,
                    locationListener,
                    mainLooper
                )
            }
        }

        // Seed initial anchor point from a fresh last-known location (within last 20 seconds)
        runCatching {
            val now = System.currentTimeMillis()
            val candidates = buildList {
                if (hasFine) {
                    add(runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull())
                }
                add(runCatching { lm.getLastKnownLocation("fused") }.getOrNull())
                add(runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull())
            }.filterNotNull()

            val freshest = candidates
                .filter { now - it.time in 0..20_000L && (!it.hasAccuracy() || it.accuracy <= 50f) }
                .minByOrNull { if (it.hasAccuracy()) it.accuracy else 50f }

            if (freshest != null) {
                driveTrackingManager.onRealGpsLocation(freshest)
            }
        }
    }

    private fun updateOngoingNotificationProgress() {
        runCatching {
            val state = driveTrackingManager.state.value
            if (!state.isDriveActive) return
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val updated = buildHeadsUpDrivePromptNotification(
                triggerSource = currentTriggerSource,
                distanceKm = state.distanceDrivenKm,
                speedKmh = state.currentSpeedKmh,
                isInitialPrompt = false
            )
            nm.notify(NOTIFICATION_ID_DRIVE, updated)
        }
    }

    private fun stopLocationUpdates() {
        runCatching {
            locationManager?.removeUpdates(locationListener)
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val promptChannel = NotificationChannel(
                CHANNEL_ID_DRIVE_PROMPT,
                "TripPawl Drive & Passenger Prompt",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority heads-up prompt asking 'Who is riding with you today?' on drive start"
                enableVibration(true)
            }
            val ongoingChannel = NotificationChannel(
                CHANNEL_ID_DRIVE_ONGOING,
                "TripPawl Background GPS Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Live GPS distance and speed status while recording a drive in the background"
                enableVibration(false)
            }
            nm.createNotificationChannel(promptChannel)
            nm.createNotificationChannel(ongoingChannel)
        }
    }

    private fun buildBluetoothStandbyNotification(btLabel: String): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            100,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID_DRIVE_ONGOING)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Auto-Drive Armed · $btLabel")
            .setContentText("Monitoring GPS speed to auto-start trip recording when moving")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .build()
    }

    private fun buildHeadsUpDrivePromptNotification(
        triggerSource: String,
        distanceKm: Double,
        speedKmh: Double,
        isInitialPrompt: Boolean
    ): Notification {
        val openPromptIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_PASSENGER_PROMPT, true)
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            101,
            openPromptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, DriveTrackingForegroundService::class.java).apply {
            action = ACTION_STOP_DRIVE
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            102,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val channelId = if (isInitialPrompt) CHANNEL_ID_DRIVE_PROMPT else CHANNEL_ID_DRIVE_ONGOING
        val distText = String.format(Locale.US, "%.2f km", distanceKm)
        val speedText = String.format(Locale.US, "%.0f km/h", speedKmh)

        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(
                if (isInitialPrompt) {
                    "Who is riding with you today?"
                } else {
                    "Recording Drive · $distText ($speedText)"
                }
            )
            .setContentText(
                if (isInitialPrompt) {
                    "Drive started ($triggerSource) · Tap to select passengers & log GPS"
                } else {
                    "Background GPS active · Tap to select passengers or finish drive"
                }
            )
            .setPriority(
                if (isInitialPrompt) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW
            )
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOngoing(true)
            .setOnlyAlertOnce(!isInitialPrompt)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_myplaces,
                "Select Passengers",
                openPendingIntent
            )
            .addAction(
                android.R.drawable.ic_media_pause,
                "Finish Drive",
                stopPendingIntent
            )
            .build()
    }

    override fun onDestroy() {
        stopLocationUpdates()
        releaseWakeLock()
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        nm?.cancel(NOTIFICATION_ID_DRIVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID_DRIVE_PROMPT = "trippawl_drive_prompt_high"
        const val CHANNEL_ID_DRIVE_ONGOING = "trippawl_drive_ongoing_low"
        const val NOTIFICATION_ID_DRIVE = 4201
        const val ACTION_START_DRIVE = "com.zaiki.trippawl.action.START_DRIVE"
        const val ACTION_STOP_DRIVE = "com.zaiki.trippawl.action.STOP_DRIVE"
        const val ACTION_ARM_BT_SPEED_MONITOR = "com.zaiki.trippawl.action.ARM_BT_SPEED_MONITOR"
        const val EXTRA_TRIGGER_SOURCE = "extra_trigger_source"
        const val EXTRA_OPEN_PASSENGER_PROMPT = "extra_open_passenger_prompt"
    }
}
