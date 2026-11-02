package com.zaiki.trippawl.tracking

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.zaiki.trippawl.domain.repository.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * BroadcastReceiver that monitors Car Bluetooth connection state for automated drive tracking:
 * - On Connect: marks the target Bluetooth device as connected and arms the speed monitor.
 *   Auto-drive starts ONLY when BOTH conditions are met: connected to the set Bluetooth device
 *   AND vehicle speed exceeds the configured speed threshold.
 * - On Disconnect: clears Bluetooth connection state and stops active drive recording.
 */
@AndroidEntryPoint
class CarBluetoothReceiver : BroadcastReceiver() {

    @Inject
    lateinit var driveTrackingManager: DriveTrackingManager

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val pendingResult = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = settingsRepository.settingsFlow.first()
                if (!settings.autoDetectDriveBluetooth) return@launch

                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val deviceAddress = runCatching { device?.address }.getOrNull()
                val deviceName = runCatching { device?.name }.getOrNull()

                val hasExplicitSelection =
                    settings.triggerBluetoothAddresses.isNotEmpty() || settings.triggerBluetoothNames.isNotEmpty()

                val shouldTrigger = if (hasExplicitSelection) {
                    val matchesAddress = deviceAddress != null &&
                        settings.triggerBluetoothAddresses.any { it.equals(deviceAddress, ignoreCase = true) }
                    val matchesName = !deviceName.isNullOrBlank() &&
                        settings.triggerBluetoothNames.any { it.equals(deviceName, ignoreCase = true) }
                    matchesAddress || matchesName
                } else {
                    runCatching {
                        val majorClass = device?.bluetoothClass?.majorDeviceClass
                        majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO || majorClass == null
                    }.getOrDefault(true)
                }

                if (!shouldTrigger) return@launch

                val label = deviceName?.takeIf { it.isNotBlank() } ?: deviceAddress ?: "Car Bluetooth"

                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        driveTrackingManager.onCarBluetoothConnected(
                            deviceLabel = label,
                            deviceAddress = deviceAddress,
                            deviceName = deviceName
                        )
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        driveTrackingManager.onCarBluetoothDisconnected()
                    }
                    BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED,
                    BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED -> {
                        val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
                        if (state == BluetoothProfile.STATE_CONNECTED) {
                            driveTrackingManager.onCarBluetoothConnected(
                                deviceLabel = label,
                                deviceAddress = deviceAddress,
                                deviceName = deviceName
                            )
                        } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                            driveTrackingManager.onCarBluetoothDisconnected()
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
