package com.zaiki.trippawl.ui.settings

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaiki.trippawl.data.backup.AutoBackupFrequency
import com.zaiki.trippawl.data.backup.BackupRestoreManager
import com.zaiki.trippawl.data.backup.LocalBackupFileUi
import com.zaiki.trippawl.data.backup.RestoreMode
import com.zaiki.trippawl.data.update.AppUpdateInfo
import com.zaiki.trippawl.data.update.AppUpdateManager
import com.zaiki.trippawl.domain.model.FuelEfficiencyUnit
import com.zaiki.trippawl.domain.model.MaintenanceMarkupMode
import com.zaiki.trippawl.domain.model.PricingSettings
import com.zaiki.trippawl.domain.model.UnpaidReminderInterval
import com.zaiki.trippawl.domain.model.VehicleProfile
import com.zaiki.trippawl.domain.repository.SettingsRepository
import com.zaiki.trippawl.tracking.DriveTrackingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class PairedBluetoothDeviceUi(
    val name: String,
    val address: String,
    val isAudioCar: Boolean
)

data class SettingsFormUiState(
    val currencySymbol: String = PricingSettings.DEFAULT_CURRENCY_SYMBOL,
    val vehicleNameInput: String = PricingSettings.DEFAULT_VEHICLE_NAME,
    val preferredFuelType: String = PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE,
    val fuelEfficiencyUnit: FuelEfficiencyUnit = FuelEfficiencyUnit.KM_PER_LITER,
    val shortTripCutoffInput: String = PricingSettings.DEFAULT_SHORT_TRIP_CUTOFF_KM.toString(),
    val shortTripFlatRateInput: String = String.format(Locale.US, "%.2f", PricingSettings.DEFAULT_SHORT_TRIP_FLAT_RATE),
    val waiveShortTripTolls: Boolean = PricingSettings.DEFAULT_WAIVE_SHORT_TRIP_TOLLS,
    val enableShortTripTier: Boolean = true,
    val fuelPriceInput: String = PricingSettings.DEFAULT_FUEL_PRICE_PER_LITER.toString(),
    val vehicleMileageInput: String = PricingSettings.DEFAULT_VEHICLE_MILEAGE_KM_PER_L.toString(),
    val maintenanceMarkupInput: String = PricingSettings.DEFAULT_MAINTENANCE_MARKUP.toString(),
    val maintenanceMarkupMode: MaintenanceMarkupMode = MaintenanceMarkupMode.PER_KM,
    val savedVehicles: List<VehicleProfile> = listOf(VehicleProfile()),
    val activeVehicleId: String = VehicleProfile.DEFAULT_VEHICLE_ID,
    val autoDetectDriveBluetooth: Boolean = true,
    val triggerBluetoothAddresses: Set<String> = emptySet(),
    val triggerBluetoothNames: Set<String> = emptySet(),
    val pairedBluetoothDevices: List<PairedBluetoothDeviceUi> = emptyList(),
    val bluetoothScanStatus: String? = null,
    val speedThresholdInput: String = PricingSettings.DEFAULT_SPEED_THRESHOLD_KMH.toString(),
    val isDarkTheme: Boolean = true,
    val unpaidReminderEnabled: Boolean = true,
    val unpaidReminderInterval: UnpaidReminderInterval = UnpaidReminderInterval.DAYS_7,
    val isInitializedFromStore: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    // Backup & Restore (Local + Optional Google Drive)
    val restoreMode: RestoreMode = RestoreMode.MERGE,
    val autoLocalBackupEnabled: Boolean = true,
    val autoBackupFrequency: AutoBackupFrequency = AutoBackupFrequency.EVERY_CHANGE,
    val googleDriveEnabled: Boolean = false,
    val googleDriveAccountLabel: String = "",
    val googleDriveAccessToken: String = "",
    val lastLocalBackupMillis: Long = 0L,
    val lastGoogleDriveBackupMillis: Long = 0L,
    val localBackupFiles: List<LocalBackupFileUi> = emptyList(),
    val isBackupBusy: Boolean = false,
    val backupStatusMessage: String? = null,
    val isBackupStatusError: Boolean = false,
    // GitHub App Updates
    val updateInfo: AppUpdateInfo? = null,
    val isCheckingForUpdate: Boolean = false,
    val updateCheckMessage: String? = null
) {
    /**
     * Converts the user's mileage input (`km/L` or `L/100km`) into `km/L`.
     */
    val effectiveMileageKmPerL: Double
        get() {
            val raw = vehicleMileageInput.toDoubleOrNull() ?: 0.0
            if (raw <= 0.0) return 0.0
            return when (fuelEfficiencyUnit) {
                FuelEfficiencyUnit.KM_PER_LITER -> raw
                FuelEfficiencyUnit.LITERS_PER_100KM -> 100.0 / raw
            }
        }

    /**
     * Cost per KM = Fuel Price per Liter / Fuel Efficiency in KM per Liter
     */
    val previewRatePerKm: Double
        get() {
            val fuel = fuelPriceInput.toDoubleOrNull() ?: 0.0
            val kmPerL = effectiveMileageKmPerL
            return if (kmPerL > 0.0 && fuel >= 0.0) fuel / kmPerL else 0.0
        }

    fun currentActiveProfileFromFields(): VehicleProfile {
        val id = activeVehicleId.ifBlank { VehicleProfile.DEFAULT_VEHICLE_ID }
        return VehicleProfile(
            id = id,
            name = vehicleNameInput.trim().ifEmpty { PricingSettings.DEFAULT_VEHICLE_NAME },
            preferredFuelType = preferredFuelType.trim().ifEmpty { PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE },
            fuelEfficiencyUnit = fuelEfficiencyUnit,
            fuelPricePerLiter = (fuelPriceInput.toDoubleOrNull() ?: PricingSettings.DEFAULT_FUEL_PRICE_PER_LITER).coerceAtLeast(0.0),
            vehicleMileageKmPerL = effectiveMileageKmPerL.takeIf { it > 0.0 } ?: PricingSettings.DEFAULT_VEHICLE_MILEAGE_KM_PER_L,
            maintenanceMarkup = (maintenanceMarkupInput.toDoubleOrNull() ?: PricingSettings.DEFAULT_MAINTENANCE_MARKUP).coerceAtLeast(0.0),
            maintenanceMarkupMode = maintenanceMarkupMode
        )
    }

    fun updatedSavedVehiclesWithCurrentFields(): List<VehicleProfile> {
        val currentProfile = currentActiveProfileFromFields()
        if (savedVehicles.isEmpty()) return listOf(currentProfile)
        val exists = savedVehicles.any { it.id == currentProfile.id }
        return if (exists) {
            savedVehicles.map { if (it.id == currentProfile.id) currentProfile else it }
        } else {
            savedVehicles + currentProfile
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val settingsRepository: SettingsRepository,
    private val backupRestoreManager: BackupRestoreManager,
    private val appUpdateManager: AppUpdateManager,
    private val driveTrackingManager: DriveTrackingManager
) : ViewModel() {

    private val localEdits = MutableStateFlow(SettingsFormUiState())

    val uiState: StateFlow<SettingsFormUiState> = combine(
        settingsRepository.settingsFlow,
        localEdits
    ) { persisted, form ->
        if (!form.isInitializedFromStore) {
            val backupCfg = backupRestoreManager.getBackupConfig()
            val synced = persisted.toFormState(
                pairedDevices = loadBondedBluetoothDevices().first,
                scanStatus = null
            ).copy(
                autoLocalBackupEnabled = backupCfg.autoLocalBackupEnabled,
                autoBackupFrequency = backupCfg.autoBackupFrequency,
                googleDriveEnabled = backupCfg.googleDriveEnabled,
                googleDriveAccountLabel = backupCfg.googleDriveAccountLabel,
                googleDriveAccessToken = backupCfg.googleDriveAccessToken,
                lastLocalBackupMillis = backupCfg.lastLocalBackupMillis,
                lastGoogleDriveBackupMillis = backupCfg.lastGoogleDriveBackupMillis
            )
            localEdits.value = synced
            synced
        } else {
            form.copy(isDarkTheme = persisted.isDarkTheme)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsFormUiState()
    )

    init {
        refreshLocalBackups()
    }

    fun selectVehicleProfile(vehicleId: String) {
        val current = localEdits.value
        if (current.activeVehicleId == vehicleId) return
        val syncedVehicles = current.updatedSavedVehiclesWithCurrentFields()
        val target = syncedVehicles.firstOrNull { it.id == vehicleId } ?: return
        val displayedMileage = when (target.fuelEfficiencyUnit) {
            FuelEfficiencyUnit.KM_PER_LITER -> target.vehicleMileageKmPerL.toString()
            FuelEfficiencyUnit.LITERS_PER_100KM -> target.litersPer100Km.toString()
        }
        val nextState = current.copy(
            activeVehicleId = target.id,
            savedVehicles = syncedVehicles,
            vehicleNameInput = target.name,
            preferredFuelType = target.preferredFuelType,
            fuelEfficiencyUnit = target.fuelEfficiencyUnit,
            fuelPriceInput = target.fuelPricePerLiter.toString(),
            vehicleMileageInput = displayedMileage,
            maintenanceMarkupInput = target.maintenanceMarkup.toString(),
            maintenanceMarkupMode = target.maintenanceMarkupMode,
            statusMessage = "Switched active car profile to '${target.name}'.",
            isError = false
        )
        localEdits.value = nextState
        viewModelScope.launch {
            buildPricingSettingsOrNull(nextState)?.let { settingsRepository.updateSettings(it) }
        }
    }

    fun addNewVehicleProfile() {
        val current = localEdits.value
        val syncedVehicles = current.updatedSavedVehiclesWithCurrentFields()
        val nextIndex = syncedVehicles.size + 1
        val newProfile = VehicleProfile(
            id = "car_${System.currentTimeMillis()}",
            name = "Car $nextIndex",
            preferredFuelType = current.preferredFuelType.ifBlank { PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE },
            fuelEfficiencyUnit = current.fuelEfficiencyUnit,
            fuelPricePerLiter = (current.fuelPriceInput.toDoubleOrNull() ?: PricingSettings.DEFAULT_FUEL_PRICE_PER_LITER).coerceAtLeast(0.0),
            vehicleMileageKmPerL = current.effectiveMileageKmPerL.takeIf { it > 0.0 } ?: PricingSettings.DEFAULT_VEHICLE_MILEAGE_KM_PER_L,
            maintenanceMarkup = (current.maintenanceMarkupInput.toDoubleOrNull() ?: PricingSettings.DEFAULT_MAINTENANCE_MARKUP).coerceAtLeast(0.0),
            maintenanceMarkupMode = current.maintenanceMarkupMode
        )
        val updatedList = syncedVehicles + newProfile
        val displayedMileage = when (newProfile.fuelEfficiencyUnit) {
            FuelEfficiencyUnit.KM_PER_LITER -> newProfile.vehicleMileageKmPerL.toString()
            FuelEfficiencyUnit.LITERS_PER_100KM -> newProfile.litersPer100Km.toString()
        }
        val nextState = current.copy(
            activeVehicleId = newProfile.id,
            savedVehicles = updatedList,
            vehicleNameInput = newProfile.name,
            preferredFuelType = newProfile.preferredFuelType,
            fuelEfficiencyUnit = newProfile.fuelEfficiencyUnit,
            fuelPriceInput = newProfile.fuelPricePerLiter.toString(),
            vehicleMileageInput = displayedMileage,
            maintenanceMarkupInput = newProfile.maintenanceMarkup.toString(),
            maintenanceMarkupMode = newProfile.maintenanceMarkupMode,
            statusMessage = "Added new car profile '${newProfile.name}'. Customize its telemetry below and tap Save Config.",
            isError = false
        )
        localEdits.value = nextState
        viewModelScope.launch {
            buildPricingSettingsOrNull(nextState)?.let { settingsRepository.updateSettings(it) }
        }
    }

    fun deleteActiveVehicleProfile() {
        val current = localEdits.value
        val syncedVehicles = current.updatedSavedVehiclesWithCurrentFields()
        if (syncedVehicles.size <= 1) return
        val remaining = syncedVehicles.filterNot { it.id == current.activeVehicleId }
        val fallback = remaining.first()
        val displayedMileage = when (fallback.fuelEfficiencyUnit) {
            FuelEfficiencyUnit.KM_PER_LITER -> fallback.vehicleMileageKmPerL.toString()
            FuelEfficiencyUnit.LITERS_PER_100KM -> fallback.litersPer100Km.toString()
        }
        val nextState = current.copy(
            activeVehicleId = fallback.id,
            savedVehicles = remaining,
            vehicleNameInput = fallback.name,
            preferredFuelType = fallback.preferredFuelType,
            fuelEfficiencyUnit = fallback.fuelEfficiencyUnit,
            fuelPriceInput = fallback.fuelPricePerLiter.toString(),
            vehicleMileageInput = displayedMileage,
            maintenanceMarkupInput = fallback.maintenanceMarkup.toString(),
            maintenanceMarkupMode = fallback.maintenanceMarkupMode,
            statusMessage = "Removed car profile. Active profile is now '${fallback.name}'.",
            isError = false
        )
        localEdits.value = nextState
        viewModelScope.launch {
            buildPricingSettingsOrNull(nextState)?.let { settingsRepository.updateSettings(it) }
        }
    }

    fun refreshLocalBackups() {
        viewModelScope.launch {
            val files = backupRestoreManager.listLocalBackups()
            val cfg = backupRestoreManager.getBackupConfig()
            localEdits.update {
                it.copy(
                    localBackupFiles = files,
                    autoLocalBackupEnabled = cfg.autoLocalBackupEnabled,
                    autoBackupFrequency = cfg.autoBackupFrequency,
                    googleDriveEnabled = cfg.googleDriveEnabled,
                    googleDriveAccountLabel = cfg.googleDriveAccountLabel,
                    googleDriveAccessToken = cfg.googleDriveAccessToken,
                    lastLocalBackupMillis = cfg.lastLocalBackupMillis,
                    lastGoogleDriveBackupMillis = cfg.lastGoogleDriveBackupMillis
                )
            }
        }
    }

    fun onRestoreModeChange(mode: RestoreMode) {
        localEdits.update {
            it.copy(restoreMode = mode, backupStatusMessage = null, isBackupStatusError = false)
        }
    }

    fun onAutoBackupFrequencyChange(frequency: AutoBackupFrequency) {
        val updated = backupRestoreManager.updateBackupConfig(autoBackupFrequency = frequency)
        localEdits.update {
            it.copy(
                autoBackupFrequency = updated.autoBackupFrequency,
                backupStatusMessage = "Auto-backup schedule set to '${frequency.label}'.",
                isBackupStatusError = false
            )
        }
    }

    fun onToggleAutoLocalBackup(enabled: Boolean) {
        val updated = backupRestoreManager.updateBackupConfig(autoLocalBackupEnabled = enabled)
        localEdits.update {
            it.copy(
                autoLocalBackupEnabled = updated.autoLocalBackupEnabled,
                backupStatusMessage = if (enabled) {
                    "Automatic local backup snapshots enabled."
                } else {
                    "Automatic local backup snapshots paused."
                },
                isBackupStatusError = false
            )
        }
        if (enabled) {
            viewModelScope.launch {
                backupRestoreManager.createLocalSnapshotBackup(isAutoBackup = true)
                refreshLocalBackups()
            }
        }
    }

    fun onToggleGoogleDriveBackup(enabled: Boolean) {
        val updated = backupRestoreManager.updateBackupConfig(googleDriveEnabled = enabled)
        localEdits.update {
            it.copy(
                googleDriveEnabled = updated.googleDriveEnabled,
                backupStatusMessage = null,
                isBackupStatusError = false
            )
        }
    }

    fun onGoogleDriveAccountLabelChange(value: String) {
        backupRestoreManager.updateBackupConfig(googleDriveAccountLabel = value)
        localEdits.update {
            it.copy(googleDriveAccountLabel = value, backupStatusMessage = null, isBackupStatusError = false)
        }
    }

    fun onGoogleDriveAccessTokenChange(value: String) {
        backupRestoreManager.updateBackupConfig(googleDriveAccessToken = value)
        localEdits.update {
            it.copy(googleDriveAccessToken = value, backupStatusMessage = null, isBackupStatusError = false)
        }
    }

    fun createLocalSnapshotNow() {
        viewModelScope.launch {
            localEdits.update { it.copy(isBackupBusy = true, backupStatusMessage = null, isBackupStatusError = false) }
            val result = backupRestoreManager.createLocalSnapshotBackup(isAutoBackup = false)
            val files = backupRestoreManager.listLocalBackups()
            val cfg = backupRestoreManager.getBackupConfig()
            result.fold(
                onSuccess = { created ->
                    localEdits.update {
                        it.copy(
                            isBackupBusy = false,
                            localBackupFiles = files,
                            lastLocalBackupMillis = cfg.lastLocalBackupMillis,
                            backupStatusMessage = "Saved local snapshot '${created.fileName}' (${created.ridersCount} riders, ${created.tripsCount} trips)!",
                            isBackupStatusError = false
                        )
                    }
                },
                onFailure = { err ->
                    localEdits.update {
                        it.copy(
                            isBackupBusy = false,
                            backupStatusMessage = "Failed to create local backup: ${err.message}",
                            isBackupStatusError = true
                        )
                    }
                }
            )
        }
    }

    fun restoreFromLocalSnapshot(file: LocalBackupFileUi) {
        val mode = localEdits.value.restoreMode
        viewModelScope.launch {
            localEdits.update { it.copy(isBackupBusy = true, backupStatusMessage = null, isBackupStatusError = false) }
            val result = backupRestoreManager.restoreFromLocalFile(file.filePath, mode)
            result.fold(
                onSuccess = { summary ->
                    reloadFormFromPersistedSettings(
                        backupBanner = "Restored ${summary.ridersCount} rider(s) & ${summary.tripsCount} trip(s) from '${file.fileName}' (${mode.label})!",
                        isError = false
                    )
                },
                onFailure = { err ->
                    localEdits.update {
                        it.copy(
                            isBackupBusy = false,
                            backupStatusMessage = "Restore failed: ${err.message}",
                            isBackupStatusError = true
                        )
                    }
                }
            )
        }
    }

    fun deleteLocalSnapshot(file: LocalBackupFileUi) {
        viewModelScope.launch {
            backupRestoreManager.deleteLocalBackupFile(file.filePath)
            val files = backupRestoreManager.listLocalBackups()
            localEdits.update {
                it.copy(
                    localBackupFiles = files,
                    backupStatusMessage = "Deleted local backup '${file.fileName}'.",
                    isBackupStatusError = false
                )
            }
        }
    }

    fun exportBackupToDocumentUri(uri: Uri, isGoogleDriveTarget: Boolean) {
        viewModelScope.launch {
            localEdits.update { it.copy(isBackupBusy = true, backupStatusMessage = null, isBackupStatusError = false) }
            val result = backupRestoreManager.exportBackupToUri(uri, isGoogleDriveTarget = isGoogleDriveTarget)
            val files = backupRestoreManager.listLocalBackups()
            val cfg = backupRestoreManager.getBackupConfig()
            result.fold(
                onSuccess = { summary ->
                    localEdits.update {
                        it.copy(
                            isBackupBusy = false,
                            localBackupFiles = files,
                            lastLocalBackupMillis = cfg.lastLocalBackupMillis,
                            lastGoogleDriveBackupMillis = cfg.lastGoogleDriveBackupMillis,
                            backupStatusMessage = "Backed up ${summary.ridersCount} rider(s) & ${summary.tripsCount} trip(s) to ${summary.sourceLabel}!",
                            isBackupStatusError = false
                        )
                    }
                },
                onFailure = { err ->
                    localEdits.update {
                        it.copy(
                            isBackupBusy = false,
                            backupStatusMessage = "Backup export failed: ${err.message}",
                            isBackupStatusError = true
                        )
                    }
                }
            )
        }
    }

    fun restoreBackupFromDocumentUri(uri: Uri, sourceLabel: String) {
        val mode = localEdits.value.restoreMode
        viewModelScope.launch {
            localEdits.update { it.copy(isBackupBusy = true, backupStatusMessage = null, isBackupStatusError = false) }
            val result = backupRestoreManager.restoreBackupFromUri(uri, mode = mode, sourceLabel = sourceLabel)
            result.fold(
                onSuccess = { summary ->
                    reloadFormFromPersistedSettings(
                        backupBanner = "Restored ${summary.ridersCount} rider(s) & ${summary.tripsCount} trip(s) from ${summary.sourceLabel} (${mode.label})!",
                        isError = false
                    )
                },
                onFailure = { err ->
                    localEdits.update {
                        it.copy(
                            isBackupBusy = false,
                            backupStatusMessage = "Restore failed: ${err.message}",
                            isBackupStatusError = true
                        )
                    }
                }
            )
        }
    }

    fun uploadToGoogleDriveCloudApi() {
        val token = localEdits.value.googleDriveAccessToken
        viewModelScope.launch {
            localEdits.update { it.copy(isBackupBusy = true, backupStatusMessage = null, isBackupStatusError = false) }
            val result = backupRestoreManager.uploadBackupToGoogleDriveCloudApi(token)
            val cfg = backupRestoreManager.getBackupConfig()
            result.fold(
                onSuccess = { summary ->
                    localEdits.update {
                        it.copy(
                            isBackupBusy = false,
                            lastGoogleDriveBackupMillis = cfg.lastGoogleDriveBackupMillis,
                            backupStatusMessage = "Synced ${summary.ridersCount} rider(s) & ${summary.tripsCount} trip(s) to Google Drive Cloud!",
                            isBackupStatusError = false
                        )
                    }
                },
                onFailure = { err ->
                    localEdits.update {
                        it.copy(
                            isBackupBusy = false,
                            backupStatusMessage = err.message ?: "Google Drive Cloud upload failed.",
                            isBackupStatusError = true
                        )
                    }
                }
            )
        }
    }

    fun restoreFromGoogleDriveCloudApi() {
        val token = localEdits.value.googleDriveAccessToken
        val mode = localEdits.value.restoreMode
        viewModelScope.launch {
            localEdits.update { it.copy(isBackupBusy = true, backupStatusMessage = null, isBackupStatusError = false) }
            val result = backupRestoreManager.restoreFromGoogleDriveCloudApi(token, mode)
            result.fold(
                onSuccess = { summary ->
                    reloadFormFromPersistedSettings(
                        backupBanner = "Restored ${summary.ridersCount} rider(s) & ${summary.tripsCount} trip(s) from Google Drive Cloud (${mode.label})!",
                        isError = false
                    )
                },
                onFailure = { err ->
                    localEdits.update {
                        it.copy(
                            isBackupBusy = false,
                            backupStatusMessage = err.message ?: "Google Drive Cloud restore failed.",
                            isBackupStatusError = true
                        )
                    }
                }
            )
        }
    }

    private suspend fun reloadFormFromPersistedSettings(
        backupBanner: String,
        isError: Boolean
    ) {
        val persisted = settingsRepository.settingsFlow.first()
        val (paired, status) = loadBondedBluetoothDevices()
        val files = backupRestoreManager.listLocalBackups()
        val cfg = backupRestoreManager.getBackupConfig()
        val currentMode = localEdits.value.restoreMode

        localEdits.value = persisted.toFormState(
            pairedDevices = paired,
            scanStatus = status
        ).copy(
            restoreMode = currentMode,
            autoLocalBackupEnabled = cfg.autoLocalBackupEnabled,
            autoBackupFrequency = cfg.autoBackupFrequency,
            googleDriveEnabled = cfg.googleDriveEnabled,
            googleDriveAccountLabel = cfg.googleDriveAccountLabel,
            googleDriveAccessToken = cfg.googleDriveAccessToken,
            lastLocalBackupMillis = cfg.lastLocalBackupMillis,
            lastGoogleDriveBackupMillis = cfg.lastGoogleDriveBackupMillis,
            localBackupFiles = files,
            isBackupBusy = false,
            backupStatusMessage = backupBanner,
            isBackupStatusError = isError
        )
    }

    fun onCurrencyChange(value: String) {
        localEdits.update { it.copy(currencySymbol = value, statusMessage = null, isError = false) }
    }

    fun onVehicleNameChange(value: String) {
        localEdits.update { current ->
            val updatedVehicles = current.savedVehicles.map { v ->
                if (v.id == current.activeVehicleId) {
                    v.copy(name = value.trim().ifEmpty { PricingSettings.DEFAULT_VEHICLE_NAME })
                } else {
                    v
                }
            }
            current.copy(
                vehicleNameInput = value,
                savedVehicles = updatedVehicles,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onPreferredFuelTypeChange(fuelType: String) {
        localEdits.update { it.copy(preferredFuelType = fuelType, statusMessage = null, isError = false) }
    }

    fun onFuelEfficiencyUnitChange(newUnit: FuelEfficiencyUnit) {
        localEdits.update { current ->
            if (current.fuelEfficiencyUnit == newUnit) return@update current
            val currentVal = current.vehicleMileageInput.toDoubleOrNull() ?: 0.0
            val convertedStr = if (currentVal > 0.0) {
                String.format(Locale.US, "%.2f", 100.0 / currentVal).trimEnd('0').trimEnd('.')
            } else {
                current.vehicleMileageInput
            }
            current.copy(
                fuelEfficiencyUnit = newUnit,
                vehicleMileageInput = convertedStr,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onCutoffChange(value: String) {
        localEdits.update { it.copy(shortTripCutoffInput = filterDecimal(value), statusMessage = null, isError = false) }
    }

    fun onFlatRateChange(value: String) {
        localEdits.update { it.copy(shortTripFlatRateInput = filterDecimal(value), statusMessage = null, isError = false) }
    }

    fun onWaiveShortTripTollsChange(waive: Boolean) {
        localEdits.update { it.copy(waiveShortTripTolls = waive, statusMessage = null, isError = false) }
    }

    fun onEnableShortTripTierChange(enabled: Boolean) {
        localEdits.update { it.copy(enableShortTripTier = enabled, statusMessage = null, isError = false) }
    }

    fun onFuelPriceChange(value: String) {
        localEdits.update { it.copy(fuelPriceInput = filterDecimal(value), statusMessage = null, isError = false) }
    }

    fun onVehicleMileageChange(value: String) {
        localEdits.update { it.copy(vehicleMileageInput = filterDecimal(value), statusMessage = null, isError = false) }
    }

    fun onMaintenanceMarkupChange(value: String) {
        localEdits.update { it.copy(maintenanceMarkupInput = filterDecimal(value), statusMessage = null, isError = false) }
    }

    fun onMaintenanceModeChange(mode: MaintenanceMarkupMode) {
        localEdits.update { it.copy(maintenanceMarkupMode = mode, statusMessage = null, isError = false) }
    }

    fun onAutoDetectBluetoothChange(enabled: Boolean) {
        localEdits.update { it.copy(autoDetectDriveBluetooth = enabled, statusMessage = null, isError = false) }
    }

    fun refreshPairedBluetoothDevices() {
        val (devices, status) = loadBondedBluetoothDevices()
        localEdits.update {
            it.copy(
                pairedBluetoothDevices = devices,
                bluetoothScanStatus = status
            )
        }
    }

    fun toggleBluetoothDevice(device: PairedBluetoothDeviceUi) {
        localEdits.update { current ->
            val nextAddresses = current.triggerBluetoothAddresses.toMutableSet()
            val nextNames = current.triggerBluetoothNames.toMutableSet()
            val isCurrentlySelected = device.address in nextAddresses ||
                nextNames.any { it.equals(device.name, ignoreCase = true) }

            if (isCurrentlySelected) {
                nextAddresses.remove(device.address)
                nextNames.removeAll { it.equals(device.name, ignoreCase = true) }
            } else {
                nextAddresses.add(device.address)
                if (device.name.isNotBlank()) {
                    nextNames.add(device.name)
                }
            }
            current.copy(
                triggerBluetoothAddresses = nextAddresses,
                triggerBluetoothNames = nextNames,
                statusMessage = null,
                isError = false
            )
        }
        persistBluetoothTriggersOnly()
    }

    fun addCustomBluetoothDeviceName(rawName: String) {
        val trimmed = rawName.trim()
        if (trimmed.isEmpty()) return
        localEdits.update { current ->
            val nextNames = current.triggerBluetoothNames.toMutableSet()
            nextNames.add(trimmed)
            current.copy(
                triggerBluetoothNames = nextNames,
                statusMessage = null,
                isError = false
            )
        }
        persistBluetoothTriggersOnly()
    }

    fun removeCustomBluetoothDeviceName(name: String) {
        localEdits.update { current ->
            val nextNames = current.triggerBluetoothNames.toMutableSet()
            nextNames.removeAll { it.equals(name, ignoreCase = true) }
            val matchingAddresses = current.pairedBluetoothDevices
                .filter { it.name.equals(name, ignoreCase = true) }
                .map { it.address }
                .toSet()
            val nextAddresses = current.triggerBluetoothAddresses - matchingAddresses
            current.copy(
                triggerBluetoothAddresses = nextAddresses,
                triggerBluetoothNames = nextNames,
                statusMessage = null,
                isError = false
            )
        }
        persistBluetoothTriggersOnly()
    }

    fun clearSelectedBluetoothDevices() {
        localEdits.update { current ->
            current.copy(
                triggerBluetoothAddresses = emptySet(),
                triggerBluetoothNames = emptySet(),
                statusMessage = null,
                isError = false
            )
        }
        persistBluetoothTriggersOnly()
    }

    private fun persistBluetoothTriggersOnly() {
        val snapshot = localEdits.value
        viewModelScope.launch {
            val currentSettings = buildPricingSettingsOrNull(snapshot) ?: return@launch
            settingsRepository.updateSettings(currentSettings)
        }
    }

    @SuppressLint("MissingPermission")
    private fun loadBondedBluetoothDevices(): Pair<List<PairedBluetoothDeviceUi>, String?> {
        return try {
            val btManager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            val adapter = btManager?.adapter
            if (adapter == null) {
                emptyList<PairedBluetoothDeviceUi>() to "Bluetooth is not supported on this device."
            } else if (!adapter.isEnabled) {
                emptyList<PairedBluetoothDeviceUi>() to "Bluetooth is currently turned off. Turn on Bluetooth or add a device name manually below."
            } else {
                val bonded = adapter.bondedDevices.orEmpty().map { device ->
                    val majorClass = runCatching { device.bluetoothClass?.majorDeviceClass }.getOrNull()
                    val isAudioCar = majorClass == BluetoothClass.Device.Major.AUDIO_VIDEO
                    val safeName = runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() }
                        ?: "Bluetooth (${device.address})"
                    PairedBluetoothDeviceUi(
                        name = safeName,
                        address = device.address,
                        isAudioCar = isAudioCar
                    )
                }.sortedWith(
                    compareByDescending<PairedBluetoothDeviceUi> { it.isAudioCar }
                        .thenBy { it.name.lowercase(Locale.US) }
                )
                val statusMsg = if (bonded.isEmpty()) {
                    "No paired Bluetooth devices found in Android settings."
                } else {
                    "Found ${bonded.size} paired Bluetooth device(s)."
                }
                bonded to statusMsg
            }
        } catch (_: SecurityException) {
            emptyList<PairedBluetoothDeviceUi>() to "Tap 'Scan Paired Devices' to grant Bluetooth permission and list your paired car stereos."
        } catch (e: Exception) {
            emptyList<PairedBluetoothDeviceUi>() to null
        }
    }

    fun onSpeedThresholdChange(value: String) {
        localEdits.update { it.copy(speedThresholdInput = filterDecimal(value), statusMessage = null, isError = false) }
    }

    fun onThemeToggle(isDark: Boolean) {
        viewModelScope.launch {
            settingsRepository.toggleDarkTheme(isDark)
        }
    }

    private fun buildPricingSettingsOrNull(current: SettingsFormUiState): PricingSettings? {
        val cutoff = current.shortTripCutoffInput.toDoubleOrNull()
        val flatRate = current.shortTripFlatRateInput.toDoubleOrNull()
        val fuelPrice = current.fuelPriceInput.toDoubleOrNull()
        val mileageKmPerL = current.effectiveMileageKmPerL
        val markup = current.maintenanceMarkupInput.toDoubleOrNull()
        val speedThreshold = current.speedThresholdInput.toDoubleOrNull() ?: PricingSettings.DEFAULT_SPEED_THRESHOLD_KMH

        if (cutoff == null || cutoff < 0.0 ||
            flatRate == null || flatRate < 0.0 ||
            fuelPrice == null || fuelPrice < 0.0 ||
            mileageKmPerL <= 0.0 ||
            markup == null || markup < 0.0
        ) {
            return null
        }

        val syncedVehicles = current.updatedSavedVehiclesWithCurrentFields()
        val activeId = current.activeVehicleId.ifBlank { VehicleProfile.DEFAULT_VEHICLE_ID }

        return PricingSettings(
            currencySymbol = current.currencySymbol.trim().ifEmpty { PricingSettings.DEFAULT_CURRENCY_SYMBOL },
            vehicleName = current.vehicleNameInput.trim().ifEmpty { PricingSettings.DEFAULT_VEHICLE_NAME },
            preferredFuelType = current.preferredFuelType.trim().ifEmpty { PricingSettings.DEFAULT_PREFERRED_FUEL_TYPE },
            fuelEfficiencyUnit = current.fuelEfficiencyUnit,
            shortTripCutoffKm = cutoff,
            shortTripFlatRate = flatRate,
            waiveShortTripTolls = current.waiveShortTripTolls,
            enableShortTripTier = current.enableShortTripTier,
            fuelPricePerLiter = fuelPrice,
            vehicleMileageKmPerL = mileageKmPerL,
            maintenanceMarkup = markup,
            maintenanceMarkupMode = current.maintenanceMarkupMode,
            savedVehicles = syncedVehicles,
            activeVehicleId = activeId,
            autoDetectDriveBluetooth = current.autoDetectDriveBluetooth,
            triggerBluetoothAddresses = current.triggerBluetoothAddresses,
            triggerBluetoothNames = current.triggerBluetoothNames,
            autoDetectSpeedThresholdKmh = speedThreshold.coerceAtLeast(5.0),
            isDarkTheme = current.isDarkTheme,
            unpaidReminderEnabled = current.unpaidReminderEnabled,
            unpaidReminderInterval = current.unpaidReminderInterval
        )
    }

    fun onUnpaidReminderEnabledChange(enabled: Boolean) {
        localEdits.update { it.copy(unpaidReminderEnabled = enabled, statusMessage = null) }
    }

    fun onUnpaidReminderIntervalChange(interval: UnpaidReminderInterval) {
        localEdits.update { it.copy(unpaidReminderInterval = interval, statusMessage = null) }
    }

    fun onTestUnpaidReminderAlert() {
        viewModelScope.launch {
            val count = driveTrackingManager.sendUnpaidTripsNotificationAlert()
            localEdits.update {
                it.copy(
                    statusMessage = if (count > 0) {
                        "Test reminder sent for $count unpaid trip group(s)."
                    } else {
                        "No unpaid trips to remind about (all trips settled)."
                    },
                    isError = false
                )
            }
        }
    }

    fun saveSettings() {
        val current = localEdits.value
        val newSettings = buildPricingSettingsOrNull(current)
        if (newSettings == null) {
            localEdits.update {
                it.copy(
                    statusMessage = "Please enter valid numbers (Fuel efficiency must be > 0).",
                    isError = true
                )
            }
            return
        }

        viewModelScope.launch {
            settingsRepository.updateSettings(newSettings)
            localEdits.update {
                it.copy(
                    savedVehicles = newSettings.savedVehicles,
                    activeVehicleId = newSettings.activeVehicleId,
                    statusMessage = "Vehicle telemetry & pricing rules saved.",
                    isError = false
                )
            }
        }
    }

    fun resetDefaults() {
        viewModelScope.launch {
            settingsRepository.resetToDefaults()
            val (paired, status) = loadBondedBluetoothDevices()
            val defaults = PricingSettings(isDarkTheme = uiState.value.isDarkTheme).toFormState(
                pairedDevices = paired,
                scanStatus = status
            )
            localEdits.value = defaults.copy(
                statusMessage = "Restored default Vehicle & Pricing telemetry configuration.",
                isError = false
            )
        }
    }

    private fun PricingSettings.toFormState(
        pairedDevices: List<PairedBluetoothDeviceUi>,
        scanStatus: String?
    ): SettingsFormUiState {
        val displayedMileage = when (fuelEfficiencyUnit) {
            FuelEfficiencyUnit.KM_PER_LITER -> vehicleMileageKmPerL.toString()
            FuelEfficiencyUnit.LITERS_PER_100KM -> litersPer100Km.toString()
        }
        return SettingsFormUiState(
            currencySymbol = currencySymbol,
            vehicleNameInput = vehicleName,
            preferredFuelType = preferredFuelType,
            fuelEfficiencyUnit = fuelEfficiencyUnit,
            shortTripCutoffInput = shortTripCutoffKm.toString(),
            shortTripFlatRateInput = String.format(Locale.US, "%.2f", shortTripFlatRate),
            waiveShortTripTolls = waiveShortTripTolls,
            enableShortTripTier = enableShortTripTier,
            fuelPriceInput = fuelPricePerLiter.toString(),
            vehicleMileageInput = displayedMileage,
            maintenanceMarkupInput = maintenanceMarkup.toString(),
            maintenanceMarkupMode = maintenanceMarkupMode,
            savedVehicles = effectiveVehicles,
            activeVehicleId = activeVehicleProfile.id,
            autoDetectDriveBluetooth = autoDetectDriveBluetooth,
            triggerBluetoothAddresses = triggerBluetoothAddresses,
            triggerBluetoothNames = triggerBluetoothNames,
            pairedBluetoothDevices = pairedDevices,
            bluetoothScanStatus = scanStatus,
            speedThresholdInput = autoDetectSpeedThresholdKmh.toString(),
            isDarkTheme = isDarkTheme,
            unpaidReminderEnabled = unpaidReminderEnabled,
            unpaidReminderInterval = unpaidReminderInterval,
            isInitializedFromStore = true
        )
    }

    private fun filterDecimal(raw: String): String {
        val cleaned = raw.filter { it.isDigit() || it == '.' }
        val firstDot = cleaned.indexOf('.')
        return if (firstDot == -1) cleaned
        else cleaned.substring(0, firstDot + 1) + cleaned.substring(firstDot + 1).replace(".", "")
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            localEdits.update { it.copy(isCheckingForUpdate = true, updateCheckMessage = "Checking GitHub Releases...") }
            appUpdateManager.checkForUpdates().fold(
                onSuccess = { info ->
                    val msg = if (info.isUpdateAvailable) {
                        "New version v${info.latestVersion} available!"
                    } else {
                        "You have the latest version (v${info.currentVersion})"
                    }
                    localEdits.update { it.copy(isCheckingForUpdate = false, updateInfo = info, updateCheckMessage = msg) }
                },
                onFailure = { err ->
                    localEdits.update {
                        it.copy(
                            isCheckingForUpdate = false,
                            updateCheckMessage = "Could not check updates: ${err.localizedMessage ?: "Network error"}"
                        )
                    }
                }
            )
        }
    }

    fun downloadOrInstallUpdate() {
        val info = localEdits.value.updateInfo ?: return
        val apk = info.apkDownloadUrl
        if (!apk.isNullOrBlank()) {
            runCatching {
                appUpdateManager.startDownloadViaManager(apk, info.latestVersion)
                localEdits.update { it.copy(updateCheckMessage = "Downloading APK to Downloads folder...") }
            }.onFailure {
                appUpdateManager.openReleaseOrDownloadInBrowser(apk)
            }
        } else {
            appUpdateManager.openReleaseOrDownloadInBrowser(info.releasePageUrl)
        }
    }

    fun openGitHubReleasesPage() {
        val url = localEdits.value.updateInfo?.releasePageUrl ?: AppUpdateManager.REPO_HTML_URL
        appUpdateManager.openReleaseOrDownloadInBrowser(url)
    }
}
