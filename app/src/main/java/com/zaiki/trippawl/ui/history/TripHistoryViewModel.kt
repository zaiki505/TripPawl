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
import com.zaiki.trippawl.ui.dashboard.DashboardTimeFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class TripHistoryTimeGroup(
    val key: String,
    val title: String,
    val subtitle: String,
    val groups: List<GroupedTrip>,
    val paidTotal: Double,
    val unpaidTotal: Double,
    val waivedTotal: Double,
    val totalFuel: Double
)

data class TripHistoryUiState(
    val searchQuery: String = "",
    val activeFilter: TripFilter = TripFilter.ALL,
    val timeFilter: DashboardTimeFilter = DashboardTimeFilter.MONTH,
    val filteredGroups: List<GroupedTrip> = emptyList(),
    val groupedSections: List<TripHistoryTimeGroup> = emptyList(),
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
    private val selectedTimeFilterFlow = MutableStateFlow(DashboardTimeFilter.MONTH)
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
        selectedTimeFilterFlow
    ) { allTrips, query, filter, settings, timeFilter ->
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

        val sections = buildTimeGroupedSections(
            groups = filtered,
            timeFilter = timeFilter,
            currencySymbol = settings.currencySymbol
        )

        Triple(
            TripHistoryUiState(
                searchQuery = query,
                activeFilter = filter,
                timeFilter = timeFilter,
                filteredGroups = filtered,
                groupedSections = sections,
                allTripsCount = bySearch.size,
                unpaidTripsCount = bySearch.count { !it.isAllPaid },
                paidTripsCount = bySearch.count { it.isAllPaid },
                settings = settings
            ),
            allGroups,
            Unit
        )
    }.combine(selectedGroupKeyFlow) { (baseState, allGroups, _), selectedKey ->
        val selectedGroup = allGroups.firstOrNull { it.groupKey == selectedKey }
        baseState.copy(selectedGroupForAction = selectedGroup)
    }.combine(statusBannerFlow) { base, banner ->
        base.copy(statusBannerMessage = banner)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TripHistoryUiState()
    )

    fun onTimeFilterSelected(filter: DashboardTimeFilter) {
        selectedTimeFilterFlow.value = filter
    }

    fun sendUnpaidAlertForAll() {
        viewModelScope.launch {
            val count = driveTrackingManager.sendUnpaidTripsNotificationAlert()
            statusBannerFlow.value = if (count > 0) {
                "Driver reminder notification sent ($count unpaid trip(s) to collect)."
            } else {
                "All payments have been collected!"
            }
        }
    }

    fun sendUnpaidAlertForGroup(group: GroupedTrip) {
        viewModelScope.launch {
            val sent = driveTrackingManager.sendUnpaidAlertForGroup(group)
            if (sent) {
                statusBannerFlow.value = "Driver collection reminder sent for '${group.displayTitle}'."
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

    private fun buildTimeGroupedSections(
        groups: List<GroupedTrip>,
        timeFilter: DashboardTimeFilter,
        currencySymbol: String
    ): List<TripHistoryTimeGroup> {
        if (groups.isEmpty()) return emptyList()

        return when (timeFilter) {
            DashboardTimeFilter.WEEK -> {
                val (currentWeekStart, _) = computeCurrentWeekBounds()
                val shortFmt = SimpleDateFormat("dd MMM", Locale.US)
                val yearFmt = SimpleDateFormat("dd MMM yyyy", Locale.US)
                groups
                    .groupBy { computeWeekBoundsForTimestamp(it.tripDate).first }
                    .entries
                    .sortedByDescending { it.key }
                    .map { (weekStart, bucket) ->
                        val (_, weekEnd) = computeWeekBoundsForTimestamp(weekStart)
                        val title = if (weekStart == currentWeekStart) {
                            "THIS WEEK (${shortFmt.format(Date(weekStart))} – ${shortFmt.format(Date(weekEnd))})".uppercase(Locale.US)
                        } else {
                            "WEEK OF ${shortFmt.format(Date(weekStart))} – ${yearFmt.format(Date(weekEnd))}".uppercase(Locale.US)
                        }
                        buildSectionGroup(
                            key = "week_$weekStart",
                            title = title,
                            bucket = bucket,
                            currencySymbol = currencySymbol
                        )
                    }
            }

            DashboardTimeFilter.MONTH -> {
                val (currentMonthStart, _) = computeCurrentMonthBounds()
                val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.US)
                groups
                    .groupBy { computeMonthStartForTimestamp(it.tripDate) }
                    .entries
                    .sortedByDescending { it.key }
                    .map { (monthStart, bucket) ->
                        val monthLabel = monthFmt.format(Date(monthStart)).uppercase(Locale.US)
                        val title = if (monthStart == currentMonthStart) {
                            "THIS MONTH · $monthLabel"
                        } else {
                            monthLabel
                        }
                        buildSectionGroup(
                            key = "month_$monthStart",
                            title = title,
                            bucket = bucket,
                            currencySymbol = currencySymbol
                        )
                    }
            }

            DashboardTimeFilter.ALL_TIME -> {
                listOf(
                    buildSectionGroup(
                        key = "all_time",
                        title = "ALL TIME TRIPS",
                        bucket = groups,
                        currencySymbol = currencySymbol
                    )
                )
            }
        }
    }

    private fun buildSectionGroup(
        key: String,
        title: String,
        bucket: List<GroupedTrip>,
        currencySymbol: String
    ): TripHistoryTimeGroup {
        val shares = bucket.flatMap { it.passengerShares }
        val paidTotal = shares.filter { it.isPaid && !it.isWaived }.sumOf { it.calculatedFare }
        val unpaidTotal = shares.filter { !it.isPaid && !it.isWaived }.sumOf { it.calculatedFare }
        val waivedTotal = shares.filter { it.isWaived }.sumOf { it.calculatedFare }
        val totalFuel = bucket.sumOf { it.fuelCost }
        return TripHistoryTimeGroup(
            key = key,
            title = title,
            subtitle = "${bucket.size} trip(s) · Fuel $currencySymbol ${String.format(Locale.US, "%.2f", totalFuel)}",
            groups = bucket,
            paidTotal = paidTotal,
            unpaidTotal = unpaidTotal,
            waivedTotal = waivedTotal,
            totalFuel = totalFuel
        )
    }

    private fun computeWeekBoundsForTimestamp(timestampMillis: Long): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = timestampMillis }
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

    private fun computeMonthStartForTimestamp(timestampMillis: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestampMillis
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun computeCurrentWeekBounds(): Pair<Long, Long> {
        return computeWeekBoundsForTimestamp(System.currentTimeMillis())
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
