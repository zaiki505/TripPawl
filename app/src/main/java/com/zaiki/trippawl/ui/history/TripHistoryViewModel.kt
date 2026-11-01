package com.zaiki.trippawl.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaiki.trippawl.domain.model.GroupedTrip
import com.zaiki.trippawl.domain.model.PricingSettings
import com.zaiki.trippawl.domain.model.Trip
import com.zaiki.trippawl.domain.model.TripFilter
import com.zaiki.trippawl.domain.model.groupByTripSession
import com.zaiki.trippawl.domain.repository.SettingsRepository
import com.zaiki.trippawl.domain.repository.TripRepository
import com.zaiki.trippawl.tracking.DriveTrackingManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TripHistoryUiState(
    val searchQuery: String = "",
    val activeFilter: TripFilter = TripFilter.ALL,
    val filteredGroups: List<GroupedTrip> = emptyList(),
    val allTripsCount: Int = 0,
    val unpaidTripsCount: Int = 0,
    val paidTripsCount: Int = 0,
    val settings: PricingSettings = PricingSettings(),
    val selectedGroupForAction: GroupedTrip? = null,
    val statusBannerMessage: String? = null
)

@HiltViewModel
class TripHistoryViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    settingsRepository: SettingsRepository,
    private val driveTrackingManager: DriveTrackingManager
) : ViewModel() {

    private val searchQueryFlow = MutableStateFlow("")
    private val filterFlow = MutableStateFlow(TripFilter.ALL)
    private val selectedGroupKeyFlow = MutableStateFlow<String?>(null)
    private val statusBannerFlow = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            tripRepository.processDueRecurringTrips()
        }
    }

    val uiState: StateFlow<TripHistoryUiState> = combine(
        tripRepository.observeAllTrips(),
        searchQueryFlow,
        filterFlow,
        settingsRepository.settingsFlow,
        selectedGroupKeyFlow
    ) { allTrips, query, filter, settings, selectedKey ->
        val allGroups = allTrips.groupByTripSession()
        val trimmedQuery = query.trim().lowercase()
        val bySearch = if (trimmedQuery.isEmpty()) {
            allGroups
        } else {
            allGroups.filter { group ->
                group.tripName.lowercase().contains(trimmedQuery) ||
                    group.passengerNames.any { it.lowercase().contains(trimmedQuery) } ||
                    group.tripNotes.lowercase().contains(trimmedQuery)
            }
        }

        val filtered = when (filter) {
            TripFilter.ALL -> bySearch
            TripFilter.UNPAID_ONLY -> bySearch.filter { !it.isAllPaid }
            TripFilter.PAID_ONLY -> bySearch.filter { it.isAllPaid }
        }

        val selectedGroup = allGroups.firstOrNull { it.groupKey == selectedKey }

        TripHistoryUiState(
            searchQuery = query,
            activeFilter = filter,
            filteredGroups = filtered,
            allTripsCount = bySearch.size,
            unpaidTripsCount = bySearch.count { !it.isAllPaid },
            paidTripsCount = bySearch.count { it.isAllPaid },
            settings = settings,
            selectedGroupForAction = selectedGroup
        )
    }.combine(statusBannerFlow) { base, banner ->
        base.copy(statusBannerMessage = banner)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TripHistoryUiState()
    )

    fun sendUnpaidAlertForAll() {
        viewModelScope.launch {
            val count = driveTrackingManager.sendUnpaidTripsNotificationAlert()
            statusBannerFlow.value = if (count > 0) {
                "🔔 Driver reminder notification sent ($count unpaid trip(s) to collect)!"
            } else {
                "All payments have been collected!"
            }
        }
    }

    fun sendUnpaidAlertForGroup(group: GroupedTrip) {
        viewModelScope.launch {
            val sent = driveTrackingManager.sendUnpaidAlertForGroup(group)
            if (sent) {
                statusBannerFlow.value = "🔔 Driver collection reminder sent for '${group.displayTitle}'!"
            }
            selectedGroupKeyFlow.value = null
        }
    }

    fun onSearchQueryChange(query: String) {
        searchQueryFlow.value = query
    }

    fun onFilterChange(filter: TripFilter) {
        filterFlow.value = filter
    }

    fun toggleGroupPaymentStatus(group: GroupedTrip) {
        viewModelScope.launch {
            val targetPaidState = !group.isAllPaid
            val groupId = group.passengerShares.firstOrNull()?.tripGroupId.orEmpty()
            if (groupId.isNotBlank()) {
                tripRepository.toggleGroupPaymentStatus(
                    tripGroupId = groupId,
                    isPaid = targetPaidState
                )
            } else {
                group.passengerShares.forEach { share ->
                    tripRepository.toggleTripPaymentStatus(
                        tripId = share.id,
                        isPaid = targetPaidState
                    )
                }
            }
        }
    }

    fun toggleSinglePassengerPaid(share: Trip) {
        viewModelScope.launch {
            tripRepository.toggleTripPaymentStatus(
                tripId = share.id,
                isPaid = !share.isPaid
            )
        }
    }

    fun toggleSinglePassengerWaived(share: Trip) {
        viewModelScope.launch {
            tripRepository.setTripWaived(
                tripId = share.id,
                isWaived = !share.isWaived
            )
        }
    }

    fun toggleGroupWaived(group: GroupedTrip) {
        viewModelScope.launch {
            val targetWaived = !group.isAllWaived
            val groupId = group.passengerShares.firstOrNull()?.tripGroupId.orEmpty()
            if (groupId.isNotBlank()) {
                tripRepository.setGroupWaived(groupId, targetWaived)
            } else {
                group.passengerShares.forEach { share ->
                    tripRepository.setTripWaived(share.id, targetWaived)
                }
            }
            selectedGroupKeyFlow.value = null
        }
    }

    fun openGroupActionsDialog(group: GroupedTrip) {
        selectedGroupKeyFlow.value = group.groupKey
    }

    fun dismissGroupActionsDialog() {
        selectedGroupKeyFlow.value = null
    }

    fun deleteGroup(group: GroupedTrip) {
        viewModelScope.launch {
            val groupId = group.passengerShares.firstOrNull()?.tripGroupId.orEmpty()
            if (groupId.isNotBlank()) {
                tripRepository.deleteTripsByGroupId(groupId)
            } else {
                group.passengerShares.forEach { share ->
                    tripRepository.deleteTrip(share.id)
                }
            }
            selectedGroupKeyFlow.value = null
        }
    }
}
