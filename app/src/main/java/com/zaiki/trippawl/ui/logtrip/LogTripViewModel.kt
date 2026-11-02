package com.zaiki.trippawl.ui.logtrip

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaiki.trippawl.data.backup.BackupRestoreManager
import com.zaiki.trippawl.domain.model.FareCalculationResult
import com.zaiki.trippawl.domain.model.FuelEfficiencyUnit
import com.zaiki.trippawl.domain.model.PricingSettings
import com.zaiki.trippawl.domain.model.RecurrenceUnit
import com.zaiki.trippawl.domain.model.Rider
import com.zaiki.trippawl.domain.model.Trip
import com.zaiki.trippawl.domain.model.VehicleProfile
import com.zaiki.trippawl.domain.model.computeNextOccurrenceMillis
import com.zaiki.trippawl.domain.repository.RiderRepository
import com.zaiki.trippawl.domain.repository.SettingsRepository
import com.zaiki.trippawl.domain.repository.TripRepository
import com.zaiki.trippawl.domain.usecase.CalculateTripFareUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

data class LogTripFormState(
    val editingTripId: Long? = null,
    val editingGroupId: String? = null,
    val existingGroupTrips: List<Trip> = emptyList(),
    val tripNameInput: String = "",
    val selectedVehicleId: String = "",
    val selectedVehicleName: String = "",
    val selectedRiderId: Long? = null,
    val selectedRiderIds: Set<Long> = emptySet(),
    val riderNameInput: String = "",
    val riderPhoneInput: String = "",
    val isCreatingNewRider: Boolean = false,
    val distanceInput: String = "",
    val tollInput: String = "",
    val parkingInput: String = "",
    val passengerCount: Int = 1,
    val isCustomSplitOverride: Boolean = false,
    val customFarePerRiderInput: String = "",
    val customRiderFareMap: Map<Long, String> = emptyMap(),
    val isPaid: Boolean = false,
    val isWaived: Boolean = false,
    val tripDateMillis: Long = System.currentTimeMillis(),
    val customFuelConsumptionInput: String = "",
    val customFuelPriceInput: String = "",
    val customMaintenanceInput: String = "",
    val tripNotesInput: String = "",
    val isRecurring: Boolean = false,
    val recurrenceIntervalInput: String = "1",
    val recurrenceUnit: RecurrenceUnit = RecurrenceUnit.WEEKS,
    val recurrenceEndDate: Long? = null,
    val isDetailedTelemetryExpanded: Boolean = false,
    val statusMessage: String? = null,
    val isError: Boolean = false,
    val isSavedSuccess: Boolean = false
)

data class LogTripUiState(
    val form: LogTripFormState = LogTripFormState(),
    val savedRiders: List<Rider> = emptyList(),
    val recentTripNames: List<String> = emptyList(),
    val settings: PricingSettings = PricingSettings(),
    val selectedVehicleProfile: VehicleProfile = VehicleProfile(),
    val effectiveSettings: PricingSettings = PricingSettings(),
    val liveFareResult: FareCalculationResult = FareCalculationResult()
) {
    /**
     * Returns the effective payment amount for a specific [riderId], respecting custom payment overrides.
     */
    fun effectiveFareForRider(riderId: Long? = null): Double {
        if (form.isCustomSplitOverride) {
            if (riderId != null) {
                val perRiderVal = form.customRiderFareMap[riderId]?.toDoubleOrNull()
                if (perRiderVal != null && perRiderVal >= 0.0) {
                    return perRiderVal
                }
            }
            val uniformOverride = form.customFarePerRiderInput.toDoubleOrNull()
            if (uniformOverride != null && uniformOverride >= 0.0) {
                return uniformOverride
            }
        }
        return liveFareResult.riderShare
    }

    /**
     * Total collectible amount across all selected passengers for this trip (`YOU'LL COLLECT`).
     * Returns 0.0 when the trip is marked as Waived.
     */
    val totalYouWillCollect: Double
        get() {
            if (form.isWaived) return 0.0
            return if (form.selectedRiderIds.isNotEmpty() && !form.isCreatingNewRider) {
                form.selectedRiderIds.sumOf { id -> effectiveFareForRider(id) }
            } else {
                effectiveFareForRider(null) * liveFareResult.passengerCount.coerceAtLeast(1)
            }
        }

    /**
     * Estimated fuel liters consumed for the current distance and effective mileage.
     */
    val estimatedFuelLiters: Double
        get() {
            val dist = form.distanceInput.toDoubleOrNull() ?: 0.0
            val kmPerL = effectiveSettings.vehicleMileageKmPerL
            return if (dist > 0.0 && kmPerL > 0.0) dist / kmPerL else 0.0
        }
}

@HiltViewModel
class LogTripViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val riderRepository: RiderRepository,
    private val tripRepository: TripRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateTripFareUseCase: CalculateTripFareUseCase,
    private val backupRestoreManager: BackupRestoreManager
) : ViewModel() {

    private val editingTripIdArg: Long? = savedStateHandle.get<Long>("tripId")?.takeIf { it > 0L }

    private val formState = MutableStateFlow(LogTripFormState(editingTripId = editingTripIdArg))

    val uiState: StateFlow<LogTripUiState> = combine(
        formState,
        riderRepository.observeAllRiders(),
        tripRepository.observeRecentTrips(limit = 60),
        settingsRepository.settingsFlow
    ) { form, riders, recentTrips, settings ->
        val lastThreeTripNames = recentTrips
            .map { it.tripName.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase(Locale.US) }
            .take(3)

        val vehicles = settings.effectiveVehicles
        val selectedProfile = vehicles.firstOrNull { it.id == form.selectedVehicleId }
            ?: vehicles.firstOrNull {
                form.selectedVehicleName.isNotBlank() && it.name.equals(form.selectedVehicleName, ignoreCase = true)
            }
            ?: settings.activeVehicleProfile

        val baseSettings = settings.withVehicleProfile(selectedProfile)

        val distanceKm = form.distanceInput.toDoubleOrNull() ?: 0.0
        val tollAmount = form.tollInput.toDoubleOrNull() ?: 0.0
        val parkingAmount = form.parkingInput.toDoubleOrNull() ?: 0.0
        val effectivePassengers = form.selectedRiderIds.size.coerceAtLeast(1)

        val customMileageKmPerL = parseCustomMileageKmPerL(
            rawInput = form.customFuelConsumptionInput,
            unit = baseSettings.fuelEfficiencyUnit
        )
        val customFuelPrice = form.customFuelPriceInput.toDoubleOrNull()?.takeIf { it >= 0.0 }
        val customMaintenance = form.customMaintenanceInput.toDoubleOrNull()?.takeIf { it >= 0.0 }

        val effectiveSettings = baseSettings.copy(
            vehicleMileageKmPerL = customMileageKmPerL ?: baseSettings.vehicleMileageKmPerL,
            fuelPricePerLiter = customFuelPrice ?: baseSettings.fuelPricePerLiter,
            maintenanceMarkup = customMaintenance ?: baseSettings.maintenanceMarkup
        )

        val livePreview = calculateTripFareUseCase(
            distanceKm = distanceKm,
            tollAmount = tollAmount,
            passengerCount = effectivePassengers,
            settings = effectiveSettings,
            parkingAmount = parkingAmount
        )

        LogTripUiState(
            form = form.copy(
                passengerCount = effectivePassengers,
                selectedVehicleId = selectedProfile.id,
                selectedVehicleName = selectedProfile.name
            ),
            savedRiders = riders,
            recentTripNames = lastThreeTripNames,
            settings = settings,
            selectedVehicleProfile = selectedProfile,
            effectiveSettings = effectiveSettings,
            liveFareResult = livePreview
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LogTripUiState()
    )

    init {
        if (editingTripIdArg != null) {
            loadExistingTrip(editingTripIdArg)
        }
    }

    /**
     * Prepares the form when opening the slide-up Log/Edit Trip Bottom Sheet.
     */
    fun prepareSheet(tripId: Long?) {
        val validId = tripId?.takeIf { it > 0L }
        if (validId == null) {
            formState.value = LogTripFormState(tripDateMillis = System.currentTimeMillis())
        } else {
            formState.value = LogTripFormState(editingTripId = validId)
            loadExistingTrip(validId)
        }
    }

    private fun loadExistingTrip(tripId: Long) {
        viewModelScope.launch {
            val existing = tripRepository.getTripById(tripId) ?: return@launch
            val settings = settingsRepository.settingsFlow.first()
            val groupTrips = if (existing.tripGroupId.isNotBlank()) {
                tripRepository.getTripsByGroupId(existing.tripGroupId).ifEmpty { listOf(existing) }
            } else {
                listOf(existing)
            }

            val vehicles = settings.effectiveVehicles
            val matchedProfile = vehicles.firstOrNull {
                existing.vehicleName.isNotBlank() && it.name.equals(existing.vehicleName, ignoreCase = true)
            } ?: settings.activeVehicleProfile
            val baseSettings = settings.withVehicleProfile(matchedProfile)

            val riderIds = groupTrips.map { it.riderId }.toSet()
            val customMileageDisplay = existing.customFuelMileageKmPerL?.let { kmPerL ->
                when (baseSettings.fuelEfficiencyUnit) {
                    FuelEfficiencyUnit.KM_PER_LITER -> trimTrailingZeros(kmPerL)
                    FuelEfficiencyUnit.LITERS_PER_100KM ->
                        if (kmPerL > 0.0) trimTrailingZeros(100.0 / kmPerL) else ""
                }
            }.orEmpty()

            val customFuelPriceDisplay = existing.customFuelPricePerLiter?.let { trimTrailingZeros(it) }.orEmpty()
            val customMaintenanceDisplay = existing.customMaintenanceMarkup?.let { trimTrailingZeros(it) }.orEmpty()

            val effectiveSettings = baseSettings.copy(
                vehicleMileageKmPerL = existing.customFuelMileageKmPerL ?: baseSettings.vehicleMileageKmPerL,
                fuelPricePerLiter = existing.customFuelPricePerLiter ?: baseSettings.fuelPricePerLiter,
                maintenanceMarkup = existing.customMaintenanceMarkup ?: baseSettings.maintenanceMarkup
            )
            val autoCalc = calculateTripFareUseCase(
                distanceKm = existing.distanceKm,
                tollAmount = existing.tollAmount,
                passengerCount = riderIds.size.coerceAtLeast(1),
                settings = effectiveSettings,
                parkingAmount = existing.parkingAmount
            )

            val hasCustomFare = groupTrips.any { abs(it.calculatedFare - autoCalc.riderShare) > 0.01 }
            val perRiderMap = if (hasCustomFare) {
                groupTrips.associate { it.riderId to trimTrailingZeros(it.calculatedFare) }
            } else {
                emptyMap()
            }

            val allWaived = groupTrips.isNotEmpty() && groupTrips.all { t -> t.isWaived }
            val allPaid = groupTrips.isNotEmpty() && groupTrips.all { t -> t.isPaid }

            formState.update {
                it.copy(
                    editingTripId = existing.id,
                    editingGroupId = existing.tripGroupId,
                    existingGroupTrips = groupTrips,
                    tripNameInput = existing.tripName,
                    selectedVehicleId = matchedProfile.id,
                    selectedVehicleName = matchedProfile.name,
                    selectedRiderId = existing.riderId,
                    selectedRiderIds = riderIds,
                    riderNameInput = existing.riderName,
                    riderPhoneInput = existing.riderPhone.orEmpty(),
                    isCreatingNewRider = false,
                    distanceInput = trimTrailingZeros(existing.distanceKm),
                    tollInput = if (existing.tollAmount > 0.0) trimTrailingZeros(existing.tollAmount) else "",
                    parkingInput = if (existing.parkingAmount > 0.0) trimTrailingZeros(existing.parkingAmount) else "",
                    passengerCount = riderIds.size.coerceAtLeast(1),
                    isCustomSplitOverride = hasCustomFare,
                    customFarePerRiderInput = if (hasCustomFare) trimTrailingZeros(existing.calculatedFare) else "",
                    customRiderFareMap = perRiderMap,
                    isPaid = allPaid,
                    isWaived = allWaived,
                    tripDateMillis = existing.tripDate,
                    customFuelConsumptionInput = customMileageDisplay,
                    customFuelPriceInput = customFuelPriceDisplay,
                    customMaintenanceInput = customMaintenanceDisplay,
                    tripNotesInput = existing.tripNotes,
                    isRecurring = existing.isRecurring && existing.recurrenceUnit != RecurrenceUnit.NONE,
                    recurrenceIntervalInput = existing.recurrenceInterval.coerceAtLeast(1).toString(),
                    recurrenceUnit = if (existing.recurrenceUnit == RecurrenceUnit.NONE) {
                        RecurrenceUnit.WEEKS
                    } else {
                        existing.recurrenceUnit
                    },
                    recurrenceEndDate = existing.recurrenceEndDate,
                    isDetailedTelemetryExpanded = true
                )
            }
        }
    }

    fun onSelectVehicleProfile(profile: VehicleProfile) {
        formState.update {
            it.copy(
                selectedVehicleId = profile.id,
                selectedVehicleName = profile.name,
                customFuelConsumptionInput = "",
                customFuelPriceInput = "",
                customMaintenanceInput = "",
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onTripNameChange(name: String) {
        formState.update {
            it.copy(tripNameInput = name, statusMessage = null, isError = false)
        }
    }

    /**
     * Multi-passenger toggling for both new trips and editing grouped trips.
     */
    fun selectExistingRider(rider: Rider) {
        formState.update { current ->
            val updatedIds = current.selectedRiderIds.toMutableSet()
            if (!updatedIds.add(rider.id)) {
                updatedIds.remove(rider.id)
            }
            val primaryId = updatedIds.lastOrNull()
            val newCount = updatedIds.size.coerceAtLeast(1)
            current.copy(
                selectedRiderId = primaryId,
                selectedRiderIds = updatedIds,
                riderNameInput = if (primaryId == rider.id) rider.name else current.riderNameInput,
                riderPhoneInput = if (primaryId == rider.id) rider.phone.orEmpty() else current.riderPhoneInput,
                passengerCount = newCount,
                isCreatingNewRider = false,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun setCreatingNewRider(isNew: Boolean) {
        formState.update {
            it.copy(
                isCreatingNewRider = isNew,
                selectedRiderId = if (isNew) null else it.selectedRiderId,
                selectedRiderIds = if (isNew) emptySet() else it.selectedRiderIds,
                riderNameInput = if (isNew) "" else it.riderNameInput,
                riderPhoneInput = if (isNew) "" else it.riderPhoneInput,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onRiderNameChange(name: String) {
        formState.update {
            it.copy(
                riderNameInput = name,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onRiderPhoneChange(phone: String) {
        formState.update {
            it.copy(riderPhoneInput = phone, statusMessage = null, isError = false)
        }
    }

    fun addInlineNewRiderToSelection() {
        val name = formState.value.riderNameInput.trim()
        val phone = formState.value.riderPhoneInput.trim().takeIf { it.isNotEmpty() }
        if (name.isEmpty()) return
        viewModelScope.launch {
            val created = riderRepository.getOrCreateRiderByName(name, phone)
            formState.update { current ->
                val nextIds = current.selectedRiderIds + created.id
                current.copy(
                    selectedRiderId = created.id,
                    selectedRiderIds = nextIds,
                    passengerCount = nextIds.size.coerceAtLeast(1),
                    riderNameInput = "",
                    riderPhoneInput = "",
                    isCreatingNewRider = false,
                    statusMessage = "Added ${created.name} to this trip!",
                    isError = false
                )
            }
        }
    }

    fun deleteSelectedRider() {
        val riderId = formState.value.selectedRiderId ?: return
        viewModelScope.launch {
            riderRepository.deleteRider(riderId)
            formState.update {
                it.copy(
                    selectedRiderId = null,
                    selectedRiderIds = it.selectedRiderIds - riderId,
                    riderNameInput = "",
                    riderPhoneInput = "",
                    statusMessage = "Rider removed from roster.",
                    isError = false
                )
            }
        }
    }

    fun onDistanceChange(value: String) {
        val filtered = sanitizeDecimalInput(value)
        formState.update {
            it.copy(distanceInput = filtered, statusMessage = null, isError = false)
        }
    }

    /**
     * Allows editing the Fuel Cost figure directly in the Step 2 Fuel grid (updates distance automatically).
     */
    fun onFuelCostFigureChange(costInput: String) {
        val cleaned = sanitizeDecimalInput(costInput)
        val costVal = cleaned.toDoubleOrNull() ?: 0.0
        val effective = uiState.value.effectiveSettings
        val fuelPrice = effective.fuelPricePerLiter
        val kmPerL = effective.vehicleMileageKmPerL
        if (costVal > 0.0 && fuelPrice > 0.0 && kmPerL > 0.0) {
            val liters = costVal / fuelPrice
            val derivedDistanceKm = liters * kmPerL
            formState.update {
                it.copy(
                    distanceInput = trimTrailingZeros(derivedDistanceKm),
                    statusMessage = null,
                    isError = false
                )
            }
        } else if (cleaned.isEmpty()) {
            formState.update { it.copy(distanceInput = "", statusMessage = null, isError = false) }
        }
    }

    /**
     * Allows editing the Liters figure directly in the Step 2 Fuel grid (updates distance automatically).
     */
    fun onFuelLitersFigureChange(litersInput: String) {
        val cleaned = sanitizeDecimalInput(litersInput)
        val litersVal = cleaned.toDoubleOrNull() ?: 0.0
        val kmPerL = uiState.value.effectiveSettings.vehicleMileageKmPerL
        if (litersVal > 0.0 && kmPerL > 0.0) {
            val derivedDistanceKm = litersVal * kmPerL
            formState.update {
                it.copy(
                    distanceInput = trimTrailingZeros(derivedDistanceKm),
                    statusMessage = null,
                    isError = false
                )
            }
        } else if (cleaned.isEmpty()) {
            formState.update { it.copy(distanceInput = "", statusMessage = null, isError = false) }
        }
    }

    fun onTollChange(value: String) {
        val filtered = sanitizeDecimalInput(value)
        formState.update {
            it.copy(tollInput = filtered, statusMessage = null, isError = false)
        }
    }

    fun onParkingChange(value: String) {
        val filtered = sanitizeDecimalInput(value)
        formState.update {
            it.copy(parkingInput = filtered, statusMessage = null, isError = false)
        }
    }

    fun onToggleCustomSplitOverride(enabled: Boolean) {
        formState.update { current ->
            val defaultFareStr = if (enabled && current.customFarePerRiderInput.isBlank()) {
                val share = uiState.value.liveFareResult.riderShare
                if (share > 0.0) trimTrailingZeros(share) else ""
            } else {
                current.customFarePerRiderInput
            }
            current.copy(
                isCustomSplitOverride = enabled,
                customFarePerRiderInput = defaultFareStr,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onCustomFarePerRiderChange(value: String) {
        val cleaned = sanitizeDecimalInput(value)
        formState.update { current ->
            val updatedMap = current.selectedRiderIds.associateWith { cleaned }
            current.copy(
                isCustomSplitOverride = true,
                customFarePerRiderInput = cleaned,
                customRiderFareMap = updatedMap,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onCustomFareForSpecificRiderChange(riderId: Long, value: String) {
        val cleaned = sanitizeDecimalInput(value)
        formState.update { current ->
            val nextMap = current.customRiderFareMap.toMutableMap()
            if (cleaned.isBlank()) {
                nextMap.remove(riderId)
            } else {
                nextMap[riderId] = cleaned
            }
            current.copy(
                isCustomSplitOverride = true,
                customRiderFareMap = nextMap,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onToggleDetailedTelemetry() {
        formState.update {
            it.copy(isDetailedTelemetryExpanded = !it.isDetailedTelemetryExpanded)
        }
    }

    fun onTripDateSelected(year: Int, monthZeroBased: Int, dayOfMonth: Int) {
        formState.update { current ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = current.tripDateMillis
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, monthZeroBased)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            current.copy(tripDateMillis = cal.timeInMillis, statusMessage = null, isError = false)
        }
    }

    fun onTripTimeSelected(hourOfDay: Int, minute: Int) {
        formState.update { current ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = current.tripDateMillis
                set(Calendar.HOUR_OF_DAY, hourOfDay)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            current.copy(tripDateMillis = cal.timeInMillis, statusMessage = null, isError = false)
        }
    }

    fun onSetTripDatePreset(millis: Long) {
        formState.update {
            it.copy(tripDateMillis = millis, statusMessage = null, isError = false)
        }
    }

    fun onCustomFuelConsumptionChange(value: String) {
        formState.update {
            it.copy(
                customFuelConsumptionInput = sanitizeDecimalInput(value),
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onCustomFuelPriceChange(value: String) {
        formState.update {
            it.copy(
                customFuelPriceInput = sanitizeDecimalInput(value),
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onCustomMaintenanceChange(value: String) {
        formState.update {
            it.copy(
                customMaintenanceInput = sanitizeDecimalInput(value),
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onTripNotesChange(notes: String) {
        formState.update {
            it.copy(tripNotesInput = notes, statusMessage = null, isError = false)
        }
    }

    fun onToggleRecurring(enabled: Boolean) {
        formState.update { current ->
            val validUnit = if (current.recurrenceUnit == RecurrenceUnit.NONE) {
                RecurrenceUnit.WEEKS
            } else {
                current.recurrenceUnit
            }
            current.copy(
                isRecurring = enabled,
                recurrenceUnit = if (enabled) validUnit else current.recurrenceUnit,
                recurrenceIntervalInput = current.recurrenceIntervalInput.ifBlank { "1" },
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onRecurrenceIntervalChange(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(3)
        formState.update {
            it.copy(recurrenceIntervalInput = digits, statusMessage = null, isError = false)
        }
    }

    fun onStepRecurrenceInterval(delta: Int) {
        formState.update { current ->
            val currentVal = current.recurrenceIntervalInput.toIntOrNull() ?: 1
            val nextVal = (currentVal + delta).coerceIn(1, 365)
            current.copy(
                recurrenceIntervalInput = nextVal.toString(),
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onRecurrenceUnitChange(unit: RecurrenceUnit) {
        formState.update {
            it.copy(
                isRecurring = unit != RecurrenceUnit.NONE,
                recurrenceUnit = unit,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onRecurrenceEndDateChange(endDate: Long?) {
        formState.update {
            it.copy(recurrenceEndDate = endDate, statusMessage = null, isError = false)
        }
    }

    fun onApplyRecurrencePreset(interval: Int, unit: RecurrenceUnit) {
        formState.update {
            it.copy(
                isRecurring = unit != RecurrenceUnit.NONE,
                recurrenceIntervalInput = interval.coerceAtLeast(1).toString(),
                recurrenceUnit = unit,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onPaymentStatusSelect(isPaid: Boolean, isWaived: Boolean) {
        formState.update {
            it.copy(
                isPaid = isPaid || isWaived,
                isWaived = isWaived,
                statusMessage = null,
                isError = false
            )
        }
    }

    fun onPaymentStatusToggle(isPaid: Boolean) {
        onPaymentStatusSelect(isPaid = isPaid, isWaived = false)
    }

    fun setValidationHint(message: String) {
        formState.update {
            it.copy(statusMessage = message, isError = true)
        }
    }

    fun deleteEditingTrip(onDeleted: () -> Unit) {
        val tripId = formState.value.editingTripId ?: return
        val groupId = formState.value.editingGroupId
        viewModelScope.launch {
            if (!groupId.isNullOrBlank()) {
                tripRepository.deleteTripsByGroupId(groupId)
            } else {
                tripRepository.deleteTrip(tripId)
            }
            backupRestoreManager.triggerAutoBackupIfEnabled(isDataChangedEvent = true)
            onDeleted()
        }
    }

    fun saveTrip(onSuccess: () -> Unit) {
        val current = uiState.value
        val riderName = current.form.riderNameInput.trim()
        val riderPhone = current.form.riderPhoneInput.trim().takeIf { it.isNotEmpty() }
        val distanceKm = current.form.distanceInput.toDoubleOrNull()

        if (current.form.selectedRiderIds.isEmpty() && riderName.isBlank()) {
            formState.update {
                it.copy(
                    statusMessage = "Please select one or more passengers or enter a passenger name.",
                    isError = true
                )
            }
            return
        }

        if (distanceKm == null || distanceKm <= 0.0) {
            formState.update {
                it.copy(
                    statusMessage = "Please enter a valid trip distance greater than 0 km.",
                    isError = true
                )
            }
            return
        }

        viewModelScope.launch {
            val calc = current.liveFareResult
            val now = System.currentTimeMillis()
            val tripDate = current.form.tripDateMillis
            val effectiveIsWaived = current.form.isWaived
            val effectiveIsPaid = current.form.isPaid || effectiveIsWaived
            val settledDate = if (effectiveIsPaid) now else null
            val cleanTripName = current.form.tripNameInput.trim()
            val cleanNotes = current.form.tripNotesInput.trim()
            val selectedVehicle = current.selectedVehicleProfile

            val customMileageKmPerL = parseCustomMileageKmPerL(
                rawInput = current.form.customFuelConsumptionInput,
                unit = selectedVehicle.fuelEfficiencyUnit
            )
            val customFuelPrice = current.form.customFuelPriceInput.toDoubleOrNull()?.takeIf { it >= 0.0 }
            val customMaintenance = current.form.customMaintenanceInput.toDoubleOrNull()?.takeIf { it >= 0.0 }

            val isRecurring = current.form.isRecurring && current.form.recurrenceUnit != RecurrenceUnit.NONE
            val recurrenceInterval = (current.form.recurrenceIntervalInput.toIntOrNull() ?: 1).coerceIn(1, 365)
            val recurrenceUnit = if (isRecurring) current.form.recurrenceUnit else RecurrenceUnit.NONE
            val recurrenceEndDate = if (isRecurring) current.form.recurrenceEndDate else null
            val nextOccurrenceDate = if (isRecurring) {
                val nextMillis = computeNextOccurrenceMillis(
                    fromMillis = tripDate,
                    interval = recurrenceInterval,
                    unit = recurrenceUnit
                )
                if (recurrenceEndDate != null && nextMillis != null && nextMillis > recurrenceEndDate) {
                    null
                } else {
                    nextMillis
                }
            } else {
                null
            }

            // Resolve the target passenger list for this grouped trip
            val targetRiders: List<Rider> = if (current.form.selectedRiderIds.isNotEmpty() && !current.form.isCreatingNewRider) {
                current.form.selectedRiderIds.mapNotNull { id -> riderRepository.getRiderById(id) }
            } else {
                listOf(riderRepository.getOrCreateRiderByName(riderName, riderPhone))
            }

            if (targetRiders.isEmpty()) {
                formState.update {
                    it.copy(
                        statusMessage = "Please select at least one valid passenger.",
                        isError = true
                    )
                }
                return@launch
            }

            val groupId = current.form.editingGroupId?.takeIf { it.isNotBlank() }
                ?: "grp_${tripDate}_${System.nanoTime()}"

            if (current.form.editingTripId != null) {
                val existingByRiderId = current.form.existingGroupTrips.associateBy { it.riderId }
                val targetRiderIdSet = targetRiders.map { it.id }.toSet()

                // Remove any passenger rows that were unchecked from the group
                current.form.existingGroupTrips
                    .filter { it.riderId !in targetRiderIdSet }
                    .forEach { removedTrip ->
                        tripRepository.deleteTrip(removedTrip.id)
                    }

                // Update existing passenger rows or insert newly added passengers in the group
                targetRiders.forEach { rider ->
                    val existingRow = existingByRiderId[rider.id]
                    val effectiveRiderFare = current.effectiveFareForRider(rider.id)
                    val rowTrip = Trip(
                        id = existingRow?.id ?: 0L,
                        tripGroupId = groupId,
                        tripName = cleanTripName,
                        riderId = rider.id,
                        riderName = rider.name,
                        riderPhone = rider.phone,
                        distanceKm = calc.distanceKm,
                        isShortTrip = calc.isShortTrip,
                        tollAmount = calc.effectiveTollAmount,
                        parkingAmount = calc.parkingAmount,
                        calculatedFare = effectiveRiderFare,
                        isPaid = effectiveIsPaid,
                        isWaived = effectiveIsWaived,
                        tripDate = tripDate,
                        arrivalDate = existingRow?.arrivalDate,
                        settledDate = if (effectiveIsPaid) (existingRow?.settledDate ?: settledDate) else null,
                        passengerCount = targetRiders.size,
                        fuelCost = calc.actualFuelCost,
                        maintenanceAmount = calc.maintenanceSurcharge,
                        totalTripCost = calc.totalTripFare,
                        gpsPointsCount = existingRow?.gpsPointsCount ?: 0,
                        isAutoTracked = existingRow?.isAutoTracked ?: false,
                        customFuelMileageKmPerL = customMileageKmPerL ?: selectedVehicle.vehicleMileageKmPerL,
                        customFuelPricePerLiter = customFuelPrice ?: selectedVehicle.fuelPricePerLiter,
                        customMaintenanceMarkup = customMaintenance ?: selectedVehicle.maintenanceMarkup,
                        tripNotes = cleanNotes,
                        isRecurring = isRecurring,
                        recurrenceInterval = recurrenceInterval,
                        recurrenceUnit = recurrenceUnit,
                        nextOccurrenceDate = nextOccurrenceDate,
                        recurrenceEndDate = recurrenceEndDate,
                        vehicleName = selectedVehicle.name
                    )
                    if (existingRow != null) {
                        tripRepository.updateTrip(rowTrip)
                    } else {
                        tripRepository.insertTrip(rowTrip)
                    }
                }
            } else {
                // Multi-Passenger Grouped Trip Logging: all passengers share the same tripGroupId and tripName
                targetRiders.forEach { rider ->
                    val effectiveRiderFare = current.effectiveFareForRider(rider.id)
                    val newTrip = Trip(
                        tripGroupId = groupId,
                        tripName = cleanTripName,
                        riderId = rider.id,
                        riderName = rider.name,
                        riderPhone = rider.phone,
                        distanceKm = calc.distanceKm,
                        isShortTrip = calc.isShortTrip,
                        tollAmount = calc.effectiveTollAmount,
                        parkingAmount = calc.parkingAmount,
                        calculatedFare = effectiveRiderFare,
                        isPaid = effectiveIsPaid,
                        isWaived = effectiveIsWaived,
                        tripDate = tripDate,
                        settledDate = settledDate,
                        passengerCount = targetRiders.size,
                        fuelCost = calc.actualFuelCost,
                        maintenanceAmount = calc.maintenanceSurcharge,
                        totalTripCost = calc.totalTripFare,
                        customFuelMileageKmPerL = customMileageKmPerL ?: selectedVehicle.vehicleMileageKmPerL,
                        customFuelPricePerLiter = customFuelPrice ?: selectedVehicle.fuelPricePerLiter,
                        customMaintenanceMarkup = customMaintenance ?: selectedVehicle.maintenanceMarkup,
                        tripNotes = cleanNotes,
                        isRecurring = isRecurring,
                        recurrenceInterval = recurrenceInterval,
                        recurrenceUnit = recurrenceUnit,
                        nextOccurrenceDate = nextOccurrenceDate,
                        recurrenceEndDate = recurrenceEndDate,
                        vehicleName = selectedVehicle.name
                    )
                    tripRepository.insertTrip(newTrip)
                }
            }

            if (isRecurring) {
                tripRepository.processDueRecurringTrips()
            }

            backupRestoreManager.triggerAutoBackupIfEnabled(isDataChangedEvent = true)
            onSuccess()
        }
    }

    private fun parseCustomMileageKmPerL(rawInput: String, unit: FuelEfficiencyUnit): Double? {
        val raw = rawInput.toDoubleOrNull() ?: return null
        if (raw <= 0.0) return null
        return when (unit) {
            FuelEfficiencyUnit.KM_PER_LITER -> raw
            FuelEfficiencyUnit.LITERS_PER_100KM -> 100.0 / raw
        }
    }

    private fun trimTrailingZeros(value: Double): String {
        return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }

    private fun sanitizeDecimalInput(raw: String): String {
        val cleaned = raw.filter { it.isDigit() || it == '.' }
        val firstDot = cleaned.indexOf('.')
        return if (firstDot == -1) {
            cleaned
        } else {
            val beforeDot = cleaned.substring(0, firstDot + 1)
            val afterDot = cleaned.substring(firstDot + 1).replace(".", "")
            beforeDot + afterDot
        }
    }
}
