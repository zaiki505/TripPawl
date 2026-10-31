package com.zaiki.trippawl.ui.riders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaiki.trippawl.domain.model.PricingSettings
import com.zaiki.trippawl.domain.model.Rider
import com.zaiki.trippawl.domain.model.RiderDebtSummary
import com.zaiki.trippawl.domain.model.Trip
import com.zaiki.trippawl.domain.repository.RiderRepository
import com.zaiki.trippawl.domain.repository.SettingsRepository
import com.zaiki.trippawl.domain.repository.TripRepository
import com.zaiki.trippawl.domain.usecase.FormatShareSummaryUseCase
import com.zaiki.trippawl.tracking.DriveTrackingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Represents the state of the Add / Edit Rider Bottom Sheet.
 * When [existingRider] is null, the sheet creates a new rider; otherwise it edits [existingRider].
 */
data class RiderEditorSheetState(
    val existingRider: Rider? = null
)

data class RiderSummaryUiState(
    val riderSummaries: List<RiderDebtSummary> = emptyList(),
    val selectedRiderSummary: RiderDebtSummary? = null,
    val selectedRiderTrips: List<Trip> = emptyList(),
    val formattedShareText: String = "",
    val settings: PricingSettings = PricingSettings(),
    val statusBannerMessage: String? = null,
    val riderEditorSheet: RiderEditorSheetState? = null,
    val riderPendingDelete: Rider? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class RiderSummaryViewModel @Inject constructor(
    private val riderRepository: RiderRepository,
    private val tripRepository: TripRepository,
    settingsRepository: SettingsRepository,
    private val formatShareSummaryUseCase: FormatShareSummaryUseCase,
    private val driveTrackingManager: DriveTrackingManager
) : ViewModel() {

    private val selectedRiderIdFlow = MutableStateFlow<Long?>(null)
    private val statusBannerFlow = MutableStateFlow<String?>(null)
    private val riderEditorSheetFlow = MutableStateFlow<RiderEditorSheetState?>(null)
    private val riderPendingDeleteFlow = MutableStateFlow<Rider?>(null)

    private val selectedRiderTripsFlow = selectedRiderIdFlow.flatMapLatest { riderId ->
        if (riderId == null) flowOf(emptyList()) else tripRepository.observeTripsForRider(riderId)
    }

    private val dialogStateFlow = combine(
        statusBannerFlow,
        riderEditorSheetFlow,
        riderPendingDeleteFlow
    ) { banner, editorSheet, pendingDelete ->
        Triple(banner, editorSheet, pendingDelete)
    }

    val uiState: StateFlow<RiderSummaryUiState> = combine(
        riderRepository.observeRiderDebtSummaries(),
        selectedRiderIdFlow,
        selectedRiderTripsFlow,
        settingsRepository.settingsFlow,
        dialogStateFlow
    ) { summaries, selectedId, selectedTrips, settings, (bannerMessage, editorSheet, pendingDelete) ->
        val selectedSummary = summaries.find { it.rider.id == selectedId }
        val shareText = if (selectedSummary != null) {
            formatShareSummaryUseCase(
                rider = selectedSummary.rider,
                trips = selectedTrips,
                currencySymbol = settings.currencySymbol
            )
        } else {
            ""
        }

        RiderSummaryUiState(
            riderSummaries = summaries,
            selectedRiderSummary = selectedSummary,
            selectedRiderTrips = selectedTrips,
            formattedShareText = shareText,
            settings = settings,
            statusBannerMessage = bannerMessage,
            riderEditorSheet = editorSheet,
            riderPendingDelete = pendingDelete
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RiderSummaryUiState()
    )

    fun selectRider(riderId: Long) {
        selectedRiderIdFlow.value = riderId
        statusBannerFlow.value = null
    }

    fun closeRiderDetail() {
        selectedRiderIdFlow.value = null
        statusBannerFlow.value = null
    }

    fun openAddRiderSheet() {
        riderEditorSheetFlow.value = RiderEditorSheetState(existingRider = null)
    }

    fun openEditRiderSheet(rider: Rider) {
        riderEditorSheetFlow.value = RiderEditorSheetState(existingRider = rider)
    }

    fun dismissRiderEditorSheet() {
        riderEditorSheetFlow.value = null
    }

    fun saveRider(name: String, phone: String?) {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) return
        val cleanedPhone = phone?.trim()?.takeIf { it.isNotEmpty() }
        val editing = riderEditorSheetFlow.value?.existingRider

        viewModelScope.launch {
            if (editing != null) {
                riderRepository.updateRider(
                    editing.copy(
                        name = trimmedName,
                        phone = cleanedPhone
                    )
                )
                statusBannerFlow.value = "Updated rider details for $trimmedName! ✨"
            } else {
                riderRepository.getOrCreateRiderByName(trimmedName, cleanedPhone)
                statusBannerFlow.value = "Added $trimmedName to your carpool roster! ✨"
            }
            riderEditorSheetFlow.value = null
        }
    }

    fun requestDeleteRider(rider: Rider) {
        riderPendingDeleteFlow.value = rider
    }

    fun dismissDeleteRiderConfirmation() {
        riderPendingDeleteFlow.value = null
    }

    fun confirmDeleteRider() {
        val rider = riderPendingDeleteFlow.value ?: return
        viewModelScope.launch {
            if (selectedRiderIdFlow.value == rider.id) {
                selectedRiderIdFlow.value = null
            }
            riderPendingDeleteFlow.value = null
            riderRepository.deleteRider(rider.id)
        }
    }

    /**
     * Marks all historical unpaid trips for the given rider as "Paid" with a single action.
     */
    fun settleAllBalanceForRider(rider: Rider) {
        viewModelScope.launch {
            val settledCount = tripRepository.settleAllUnpaidTripsForRider(rider.id)
            statusBannerFlow.value = if (settledCount > 0) {
                "Settled $settledCount unpaid trip(s) for ${rider.name}! ✨"
            } else {
                "${rider.name} is already fully settled!"
            }
        }
    }

    fun toggleSingleTripPaid(trip: Trip) {
        viewModelScope.launch {
            tripRepository.toggleTripPaymentStatus(trip.id, !trip.isPaid)
        }
    }

    fun notifyCopiedToClipboard(riderName: String) {
        statusBannerFlow.value = "Copied $riderName's summary to clipboard! Ready for WhatsApp."
    }

    fun sendUnpaidAlertForAllRiders() {
        viewModelScope.launch {
            val count = driveTrackingManager.sendUnpaidTripsNotificationAlert()
            statusBannerFlow.value = if (count > 0) {
                "🔔 Driver reminder notification sent ($count unpaid trip(s) still to collect)!"
            } else {
                "All payments have been collected!"
            }
        }
    }
}
