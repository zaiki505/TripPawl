package com.zaiki.trippawl.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zaiki.trippawl.data.local.dao.DriveLogDao
import com.zaiki.trippawl.data.local.entity.DriveLogEntity
import com.zaiki.trippawl.domain.model.DashboardMetrics
import com.zaiki.trippawl.domain.model.FareCalculationResult
import com.zaiki.trippawl.domain.model.GroupedTrip
import com.zaiki.trippawl.domain.model.PricingSettings
import com.zaiki.trippawl.domain.model.Rider
import com.zaiki.trippawl.domain.model.Trip
import com.zaiki.trippawl.domain.model.groupByTripSession
import com.zaiki.trippawl.domain.repository.RiderRepository
import com.zaiki.trippawl.domain.repository.SettingsRepository
import com.zaiki.trippawl.domain.repository.TripRepository
import com.zaiki.trippawl.domain.usecase.CalculateTripFareUseCase
import com.zaiki.trippawl.tracking.DriveTrackingManager
import com.zaiki.trippawl.tracking.DriveTrackingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class DashboardTimeFilter(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    ALL_TIME("All Time")
}

data class DashboardUiState(
    val metrics: DashboardMetrics = DashboardMetrics(),
    val timeFilter: DashboardTimeFilter = DashboardTimeFilter.MONTH,
    val recentTrips: List<Trip> = emptyList(),
    val recentGroupedTrips: List<GroupedTrip> = emptyList(),
    val recentTripNames: List<String> = emptyList(),
    val recentDriveLogs: List<DriveLogEntity> = emptyList(),
    val savedRiders: List<Rider> = emptyList(),
    val settings: PricingSettings = PricingSettings(),
    val driveState: DriveTrackingState = DriveTrackingState(),
    val liveDriveFare: FareCalculationResult = FareCalculationResult(),
    val statusBannerMessage: String? = null,
    val isLoading: Boolean = true
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val riderRepository: RiderRepository,
    private val settingsRepository: SettingsRepository,
    private val calculateTripFareUseCase: CalculateTripFareUseCase,
    private val driveTrackingManager: DriveTrackingManager,
    private val driveLogDao: DriveLogDao
) : ViewModel() {

    private val selectedTimeFilterFlow = MutableStateFlow(DashboardTimeFilter.MONTH)
    private val statusBannerFlow = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val metricsWithFilterFlow = selectedTimeFilterFlow.flatMapLatest { filter ->
        val bounds = when (filter) {
            DashboardTimeFilter.WEEK -> computeCurrentWeekBounds()
            DashboardTimeFilter.MONTH -> computeCurrentMonthBounds()
            DashboardTimeFilter.ALL_TIME -> 0L to Long.MAX_VALUE
        }
        tripRepository.observeDashboardMetrics(bounds.first, bounds.second).map { metrics ->
            metrics to filter
        }
    }

    init {
        viewModelScope.launch {
            driveTrackingManager.ensureSpeedThresholdMonitorRunning()
            val spawned = tripRepository.processDueRecurringTrips()
            if (spawned > 0) {
                statusBannerFlow.value = "Auto-logged $spawned due recurring trip(s)."
            }
            driveTrackingManager.checkAndPostAutomaticDriverCollectionReminder()
        }
    }

    fun refreshAutoDriveMonitor() {
        driveTrackingManager.ensureSpeedThresholdMonitorRunning()
    }

    fun onTimeFilterSelected(filter: DashboardTimeFilter) {
        selectedTimeFilterFlow.value = filter
    }

    val uiState: StateFlow<DashboardUiState> = combine(
        metricsWithFilterFlow,
        tripRepository.observeRecentTrips(limit = 25),
        riderRepository.observeAllRiders(),
        settingsRepository.settingsFlow,
        driveTrackingManager.state
    ) { metricsAndFilter, recentTrips, riders, settings, driveState ->
        val (metrics, timeFilter) = metricsAndFilter
        val activePassengerCount = driveState.selectedPassengerIds.size.coerceAtLeast(1)
        val liveFare = calculateTripFareUseCase(
            distanceKm = driveState.distanceDrivenKm,
            tollAmount = 0.0,
            passengerCount = activePassengerCount,
            settings = settings,
            parkingAmount = 0.0
        )
        val groupedRecent = recentTrips.groupByTripSession().take(5)
        val lastThreeTripNames = recentTrips
            .map { it.tripName.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .take(3)

        DashboardUiState(
            metrics = metrics,
            timeFilter = timeFilter,
            recentTrips = recentTrips,
            recentGroupedTrips = groupedRecent,
            recentTripNames = lastThreeTripNames,
            savedRiders = riders,
            settings = settings,
            driveState = driveState,
            liveDriveFare = liveFare,
            isLoading = false
        )
    }.combine(driveLogDao.observeRecentLogs(limit = 10)) { base, logs ->
        base.copy(recentDriveLogs = logs)
    }.combine(statusBannerFlow) { base, banner ->
        base.copy(statusBannerMessage = banner)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DashboardUiState()
    )

    fun sendUnpaidTripsAlert() {
        viewModelScope.launch {
            val count = driveTrackingManager.sendUnpaidTripsNotificationAlert()
            statusBannerFlow.value = if (count > 0) {
                "Driver reminder notification sent ($count unpaid trip(s) still to collect)."
            } else {
                "All payments have been collected!"
            }
        }
    }

    fun clearStatusBanner() {
        statusBannerFlow.value = null
    }

    fun startRealGpsDrive() {
        driveTrackingManager.startDrive(
            triggerSource = "GPS Drive",
            simulated = false,
            showPromptImmediately = true
        )
    }

    fun stopActiveDrive() {
        driveTrackingManager.stopDrive()
    }

    fun togglePassengerForDrive(riderId: Long) {
        driveTrackingManager.togglePassengerSelection(riderId)
    }

    fun openPassengerPrompt() {
        driveTrackingManager.openPassengerPrompt()
    }

    fun dismissPassengerPrompt() {
        driveTrackingManager.dismissPassengerPrompt()
    }

    fun quickAddPassenger(name: String, phone: String?) {
        driveTrackingManager.quickAddPassengerDuringDrive(name, phone)
    }

    fun computeFarePreview(
        distanceKm: Double,
        tollAmount: Double,
        parkingAmount: Double,
        passengerCount: Int
    ): FareCalculationResult {
        return calculateTripFareUseCase(
            distanceKm = distanceKm,
            tollAmount = tollAmount,
            parkingAmount = parkingAmount,
            passengerCount = passengerCount.coerceAtLeast(1),
            settings = uiState.value.settings
        )
    }

    fun saveCompletedDrive(
        tripName: String = "",
        distanceOverrideKm: Double?,
        tollAmount: Double,
        parkingAmount: Double,
        selectedPassengerIds: Set<Long>,
        customPaymentPerRider: Double? = null,
        isPaid: Boolean,
        isWaived: Boolean = false
    ) {
        driveTrackingManager.saveCompletedDriveToLedger(
            tripName = tripName,
            distanceOverrideKm = distanceOverrideKm,
            tollAmount = tollAmount,
            parkingAmount = parkingAmount,
            selectedPassengerIds = selectedPassengerIds,
            customPaymentPerRider = customPaymentPerRider,
            isPaid = isPaid,
            isWaived = isWaived,
            onSaved = {}
        )
    }

    fun dismissCompletedDrive() {
        driveTrackingManager.dismissCompletedDriveSummary()
    }

    /**
     * Quick single-tap action on the Dashboard to mark a grouped trip as paid (or toggle status).
     */
    fun toggleGroupedTripPayment(group: GroupedTrip) {
        viewModelScope.launch {
            val targetPaidState = !group.isAllPaid
            val firstGroupId = group.passengerShares.firstOrNull()?.tripGroupId.orEmpty()
            if (firstGroupId.isNotBlank()) {
                tripRepository.toggleGroupPaymentStatus(firstGroupId, targetPaidState)
            } else {
                group.passengerShares.forEach { share ->
                    tripRepository.toggleTripPaymentStatus(share.id, targetPaidState)
                }
            }
        }
    }

    /**
     * Quick action to waive or un-waive a single passenger share.
     */
    fun toggleSinglePassengerWaived(share: Trip) {
        viewModelScope.launch {
            tripRepository.setTripWaived(share.id, !share.isWaived)
        }
    }

    /**
     * Quick action to waive or un-waive an entire grouped trip.
     */
    fun toggleGroupedTripWaived(group: GroupedTrip) {
        viewModelScope.launch {
            val targetWaived = !group.isAllWaived
            val firstGroupId = group.passengerShares.firstOrNull()?.tripGroupId.orEmpty()
            if (firstGroupId.isNotBlank()) {
                tripRepository.setGroupWaived(firstGroupId, targetWaived)
            } else {
                group.passengerShares.forEach { share ->
                    tripRepository.setTripWaived(share.id, targetWaived)
                }
            }
        }
    }

    fun reopenDriveLog(log: DriveLogEntity) {
        driveTrackingManager.reopenDriveLog(log)
    }

    fun deleteDriveLog(logId: Long) {
        driveTrackingManager.deleteDriveLog(logId)
    }

    fun clearAllDriveLogs() {
        driveTrackingManager.clearAllDriveLogs()
    }

    fun toggleTheme() {
        viewModelScope.launch {
            val currentDark = uiState.value.settings.isDarkTheme
            settingsRepository.toggleDarkTheme(!currentDark)
        }
    }

    private fun computeCurrentWeekBounds(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = if (dayOfWeek == Calendar.SUNDAY) 6 else dayOfWeek - Calendar.MONDAY
        cal.add(Calendar.DAY_OF_MONTH, -daysFromMonday)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.add(Calendar.DAY_OF_MONTH, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    private fun computeCurrentMonthBounds(): Pair<Long, Long> {
        val startCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endCal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, getActualMaximum(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
        return startCal.timeInMillis to endCal.timeInMillis
    }
}
