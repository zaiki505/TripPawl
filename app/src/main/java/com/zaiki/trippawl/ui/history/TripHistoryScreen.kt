package com.zaiki.trippawl.ui.history

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaiki.trippawl.domain.model.GroupedTrip
import com.zaiki.trippawl.domain.model.Trip
import com.zaiki.trippawl.domain.model.TripFilter
import com.zaiki.trippawl.ui.components.ZaikiBackToTopButton
import com.zaiki.trippawl.ui.components.ZaikiBentoCard
import com.zaiki.trippawl.ui.components.ZaikiCircularAddFab
import com.zaiki.trippawl.ui.components.ZaikiDetailPanel
import com.zaiki.trippawl.ui.components.ZaikiFilterChip
import com.zaiki.trippawl.ui.components.ZaikiFormField
import com.zaiki.trippawl.ui.components.ZaikiFormStatusBanner
import com.zaiki.trippawl.ui.components.ZaikiHairlineDivider
import com.zaiki.trippawl.ui.components.ZaikiPaymentStatusChip
import com.zaiki.trippawl.ui.components.ZaikiPrimaryButton
import com.zaiki.trippawl.ui.components.ZaikiSecondaryButton
import com.zaiki.trippawl.ui.components.ZaikiSectionHeader
import com.zaiki.trippawl.ui.components.ZaikiTagCategory
import com.zaiki.trippawl.ui.components.ZaikiTagPill
import com.zaiki.trippawl.ui.components.zaikiSquishClickable
import com.zaiki.trippawl.ui.dashboard.DashboardTimeFilter
import com.zaiki.trippawl.ui.dashboard.DashboardTimeFilterPill
import com.zaiki.trippawl.ui.dashboard.formatDecimal
import com.zaiki.trippawl.ui.dashboard.formatMoney
import com.zaiki.trippawl.ui.theme.ZaikiDimens
import com.zaiki.trippawl.ui.theme.ZaikiRadii
import com.zaiki.trippawl.ui.theme.ZaikiThemeTokens
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Trip History & Ledger Screen
 * - Top Time Filter Pill (`Week | Month | All Time`) + Fuel Spent, Total Collected & Waived Carpool Fares cards.
 * - Groups trips by Week, Month, or All Time according to the selected time pill.
 * - Search bar filters by trip name, passenger name, or notes.
 * - Tap individual passenger chips or group badge to toggle payment status; tap card to manage/edit.
 */
@Composable
fun TripHistoryScreen(
    viewModel: TripHistoryViewModel,
    onEditTrip: (Long) -> Unit,
    onLogNewTripClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ZaikiThemeTokens.colors
    val currency = uiState.settings.currencySymbol
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 260
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = ZaikiDimens.screenPadding(
                bottom = ZaikiDimens.NavPillClearance + ZaikiDimens.FabClearance
            ),
            verticalArrangement = Arrangement.spacedBy(ZaikiDimens.SectionGap)
        ) {
            // 1. Header
            item {
                ZaikiSectionHeader(
                    kicker = "TRIP HISTORY & LEDGER",
                    title = "All Logged Trips",
                    trailingContent = {
                        ZaikiPrimaryButton(
                            text = "+ Log Trip",
                            onClick = onLogNewTripClick,
                            contentPadding = PaddingValues(horizontal = 15.dp, vertical = 9.dp)
                        )
                    }
                )
            }

            // 1.5 Time Period Option Pill (Week | Month | All Time)
            item {
                DashboardTimeFilterPill(
                    selectedFilter = uiState.timeFilter,
                    onFilterSelected = viewModel::onTimeFilterSelected,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            // 1.6 Top Summary Cards: Fuel Spent & Total Collected
            item {
                val timeFilterLabel = uiState.timeFilter.label
                val isAllTime = uiState.timeFilter == DashboardTimeFilter.ALL_TIME
                val periodQualifier = if (isAllTime) "all-time" else "this ${timeFilterLabel.lowercase()}"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ZaikiBentoCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(ZaikiDimens.CardPaddingCompact)
                    ) {
                        Text(
                            text = "FUEL SPENT (${timeFilterLabel.uppercase()})",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.tagWebText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatMoney(currency, uiState.metrics.totalFuelSpentCurrentMonth),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.metrics.currentMonthTripCount} trip(s) $periodQualifier",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    ZaikiBentoCard(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentPadding = PaddingValues(ZaikiDimens.CardPaddingCompact)
                    ) {
                        Text(
                            text = if (isAllTime) "TOTAL COLLECTED" else "COLLECTED (${timeFilterLabel.uppercase()})",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.available,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatMoney(currency, uiState.metrics.totalSettledPayments),
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.available,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${uiState.metrics.settledTripCount} paid share(s) $periodQualifier",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 1.7 Top Summary Panel: Waived Carpool Fares
            item {
                val isAllTime = uiState.timeFilter == DashboardTimeFilter.ALL_TIME
                val waivedPeriodQualifier = if (isAllTime) "" else " this ${uiState.timeFilter.label.lowercase()}"

                ZaikiDetailPanel(
                    contentPadding = PaddingValues(horizontal = 15.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAllTime) "WAIVED CARPOOL FARES" else "WAIVED FARES (${uiState.timeFilter.label.uppercase()})",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.highlight
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Total Waived: ${formatMoney(currency, uiState.metrics.totalWaivedAmount)}",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Carpool shares forgiven for friends & colleagues (${uiState.metrics.waivedTripCount} waived$waivedPeriodQualifier)",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        ZaikiTagPill(
                            text = "${uiState.metrics.waivedTripCount} Waived",
                            category = ZaikiTagCategory.PERSONAL
                        )
                    }
                }
            }

            // 2. Search Bar & Filter Chips ("All", "Unpaid Only", "Paid Only", "Alert Unpaid")
            item {
                ZaikiBentoCard(contentPadding = PaddingValues(ZaikiDimens.CardPaddingCompact)) {
                    ZaikiFormField(
                        label = "",
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChange,
                        placeholder = "Search by trip name or pax...",
                        suffix = if (uiState.searchQuery.isNotEmpty()) "ACTIVE" else null
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ZaikiFilterChip(
                            label = TripFilter.ALL.label,
                            selected = uiState.activeFilter == TripFilter.ALL,
                            badgeCount = uiState.allTripsCount,
                            onClick = { viewModel.onFilterChange(TripFilter.ALL) }
                        )
                        ZaikiFilterChip(
                            label = TripFilter.UNPAID_ONLY.label,
                            selected = uiState.activeFilter == TripFilter.UNPAID_ONLY,
                            badgeCount = uiState.unpaidTripsCount,
                            onClick = { viewModel.onFilterChange(TripFilter.UNPAID_ONLY) }
                        )
                        ZaikiFilterChip(
                            label = TripFilter.PAID_ONLY.label,
                            selected = uiState.activeFilter == TripFilter.PAID_ONLY,
                            badgeCount = uiState.paidTripsCount,
                            onClick = { viewModel.onFilterChange(TripFilter.PAID_ONLY) }
                        )
                        if (uiState.unpaidTripsCount > 0) {
                            ZaikiFilterChip(
                                label = "Remind to Collect",
                                selected = false,
                                onClick = viewModel::sendUnpaidAlertForAll
                            )
                        }
                    }
                    ZaikiFormStatusBanner(
                        message = uiState.statusBannerMessage,
                        isError = false
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Grouped by ${uiState.timeFilter.label} · Tap badge to toggle Paid/Unpaid · Tap card to Edit",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textFaint
                    )
                }
            }

            // 3. Filtered & Time-Grouped Trips List (Week / Month / All Time sections)
            if (uiState.groupedSections.isEmpty()) {
                item {
                    ZaikiBentoCard {
                        Text(
                            text = "No matching trips found",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) {
                                "No trips matched '${uiState.searchQuery}'."
                            } else {
                                "No trips recorded under the '${uiState.activeFilter.label}' filter yet."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                }
            } else {
                uiState.groupedSections.forEach { section ->
                    item(key = "header_${section.key}") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = colors.highlight,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = section.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            ZaikiTagPill(
                                text = "Total ${formatMoney(currency, section.totalFare)}",
                                category = ZaikiTagCategory.PERSONAL
                            )
                        }
                    }

                    items(
                        items = section.groups,
                        key = { "${section.key}_${it.groupKey}" }
                    ) { group ->
                        GroupedTripLedgerDetailCard(
                            group = group,
                            currencySymbol = currency,
                            onToggleGroupPaid = { viewModel.toggleGroupPaymentStatus(group) },
                            onToggleSinglePassengerPaid = viewModel::toggleSinglePassengerPaid,
                            onOpenActions = { viewModel.openGroupActionsDialog(group) }
                        )
                    }
                }
            }
        }

        // Back-to-Top Arrow Button when scrolled down
        ZaikiBackToTopButton(
            visible = showBackToTop,
            onClick = {
                coroutineScope.launch {
                    listState.animateScrollToItem(0)
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 27.dp, bottom = 148.dp)
        )

        // Circular '+' FAB above the floating navigation bar
        ZaikiCircularAddFab(
            onClick = onLogNewTripClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 82.dp)
        )
    }

    // Tap / Long-press Edit or Delete Modal Dialog
    val selectedGroup = uiState.selectedGroupForAction
    if (selectedGroup != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissGroupActionsDialog,
            containerColor = colors.glassFillStrong,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textBody,
            title = {
                Column {
                    Text(
                        text = "MANAGE TRIP",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.highlight
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${selectedGroup.displayTitle} · ${selectedGroup.passengerCount} Pax",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Pax: ${selectedGroup.passengerNamesSummary}\n" +
                            "Total: ${formatMoney(currency, selectedGroup.totalPassengersFare)} " +
                            "(${formatMoney(currency, selectedGroup.sharePerPerson)} / pax)" +
                            (if (selectedGroup.vehicleName.isNotBlank()) "\nCar: ${selectedGroup.vehicleName}" else "") +
                            (if (selectedGroup.tripNotes.isNotBlank()) "\nNotes: ${selectedGroup.tripNotes}" else "") +
                            (if (selectedGroup.waivedCount > 0) "\nWaived: ${selectedGroup.waivedCount} share(s) (${formatMoney(currency, selectedGroup.waivedTotalFare)})" else "") +
                            (selectedGroup.recurrenceSummaryLabel?.let { "\nRecurrence: $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textBody
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ZaikiPrimaryButton(
                        text = "Edit Trip Details (Date, Fuel, Riders)",
                        onClick = {
                            val id = selectedGroup.primaryTripId
                            viewModel.dismissGroupActionsDialog()
                            onEditTrip(id)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!selectedGroup.isAllPaid) {
                        ZaikiSecondaryButton(
                            text = "Remind Me to Collect Payment",
                            onClick = { viewModel.sendUnpaidAlertForGroup(selectedGroup) },
                            isAccentTinted = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ZaikiSecondaryButton(
                        text = if (selectedGroup.isAllPaid) "Mark All Unpaid" else "Mark All Paid",
                        onClick = {
                            viewModel.toggleGroupPaymentStatus(selectedGroup)
                            viewModel.dismissGroupActionsDialog()
                        },
                        isAccentTinted = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    ZaikiSecondaryButton(
                        text = if (selectedGroup.isAllWaived) "Revert Waived Fares to Unpaid" else "Waive All Fares (Free Ride)",
                        onClick = {
                            viewModel.toggleGroupWaived(selectedGroup)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (selectedGroup.passengerShares.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "PASSENGER WAIVER & PAYMENT STATUS",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.highlight
                        )
                        selectedGroup.passengerShares.forEach { share ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ZaikiRadii.InputField)
                                    .background(colors.surfaceChip)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = share.riderName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = formatMoney(currency, share.calculatedFare),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.textMuted
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ZaikiTagPill(
                                        text = if (share.isWaived) "WAIVED" else "Waive",
                                        category = if (share.isWaived) ZaikiTagCategory.PERSONAL else ZaikiTagCategory.SYSTEM,
                                        onClick = { viewModel.toggleSinglePassengerWaived(share) }
                                    )
                                    ZaikiTagPill(
                                        text = if (share.isPaid) "PAID" else "Unpaid",
                                        category = if (share.isPaid) ZaikiTagCategory.WEB else ZaikiTagCategory.SYSTEM,
                                        onClick = { viewModel.toggleSinglePassengerPaid(share) }
                                    )
                                }
                            }
                        }
                    }
                    ZaikiSecondaryButton(
                        text = "Delete Trip",
                        onClick = { viewModel.deleteGroup(selectedGroup) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                ZaikiSecondaryButton(
                    text = "Close",
                    onClick = viewModel::dismissGroupActionsDialog,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GroupedTripLedgerDetailCard(
    group: GroupedTrip,
    currencySymbol: String,
    onToggleGroupPaid: () -> Unit,
    onToggleSinglePassengerPaid: (Trip) -> Unit,
    onOpenActions: (() -> Unit)? = null
) {
    val colors = ZaikiThemeTokens.colors
    val dateFormat = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.US)

    // Solid opaque backing (`colors.bgPage`) behind the glass card prevents the SwipeToDismissBox
    // green/red action track from ever bleeding through the card during a swipe gesture.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZaikiRadii.Card)
            .background(colors.bgPage)
    ) {
        ZaikiBentoCard(
            contentPadding = PaddingValues(16.dp),
            onLongClick = onOpenActions,
            onClick = onOpenActions
        ) {
            // Top Row: Trip Name (e.g. "Monday Class") + Passenger Count Badge + Tier Tag + Payment Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = group.displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        ZaikiTagPill(
                            text = "${group.passengerCount} Pax",
                            category = ZaikiTagCategory.PERSONAL
                        )
                        ZaikiTagPill(
                            text = if (group.isAutoTracked) "GPS" else if (group.isShortTrip) "Short" else "Long",
                            category = if (group.isAutoTracked) {
                                ZaikiTagCategory.PERSONAL
                            } else if (group.isShortTrip) {
                                ZaikiTagCategory.WEB
                            } else {
                                ZaikiTagCategory.SYSTEM
                            }
                        )
                        group.recurrenceSummaryLabel?.let { recLabel ->
                            ZaikiTagPill(
                                text = recLabel,
                                category = ZaikiTagCategory.WEB
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val paxSubtitle = buildString {
                        if (group.tripName.isNotBlank()) {
                            append(group.passengerNamesSummary)
                        } else {
                            append("Pax: ${group.passengerNamesSummary}")
                        }
                        if (group.vehicleName.isNotBlank()) {
                            append(" · ${group.vehicleName}")
                        }
                    }
                    Text(
                        text = paxSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textBody,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateFormat.format(Date(group.tripDate)) +
                            if (group.customFuelMileageKmPerL != null) {
                                " · ${formatDecimal(group.customFuelMileageKmPerL)} km/L"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                    if (group.tripNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Note: ${group.tripNotes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.highlight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.padding(start = 8.dp))

                ZaikiPaymentStatusChip(
                    isPaid = group.isAllPaid,
                    isWaived = group.isAllWaived,
                    onClick = onToggleGroupPaid
                )
            }

            // Passenger Payment Pills rendered consistently for both 1-pax and multi-pax trips
            if (group.passengerShares.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    group.passengerShares.forEach { share ->
                        val chipColor = when {
                            share.isWaived -> colors.highlight
                            share.isPaid -> colors.available
                            else -> colors.unavailable
                        }
                        val symbol = when {
                            share.isWaived -> "WAIVED"
                            share.isPaid -> "✓"
                            else -> "●"
                        }
                        Box(
                            modifier = Modifier
                                .zaikiSquishClickable { onToggleSinglePassengerPaid(share) }
                                .clip(ZaikiRadii.Pill)
                                .background(chipColor.copy(alpha = 0.14f))
                                .border(BorderStroke(1.dp, chipColor.copy(alpha = 0.45f)), ZaikiRadii.Pill)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$symbol ${share.riderName} (${formatMoney(currencySymbol, share.calculatedFare)})",
                                style = MaterialTheme.typography.labelSmall,
                                color = chipColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            ZaikiHairlineDivider()
            Spacer(modifier = Modifier.height(10.dp))

            // Breakdown Grid: Distance, Toll/Park, Share/Pax, Total Passengers RM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LedgerMetricColumn(
                    label = "DISTANCE",
                    value = "${formatDecimal(group.distanceKm)} km",
                    modifier = Modifier.weight(1f)
                )
                LedgerMetricColumn(
                    label = if (group.parkingAmount > 0.0) "TOLL/PARK" else "TOLL",
                    value = formatMoney(currencySymbol, group.tollAmount + group.parkingAmount),
                    modifier = Modifier.weight(1f)
                )
                LedgerMetricColumn(
                    label = "PER PAX",
                    value = formatMoney(currencySymbol, group.sharePerPerson),
                    modifier = Modifier.weight(1f)
                )
                LedgerMetricColumn(
                    label = "TOTAL RM",
                    value = formatMoney(currencySymbol, group.totalPassengersFare),
                    modifier = Modifier.weight(1f),
                    alignEnd = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Row: Fuel Cost Note & Highlighted Group Total
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ZaikiRadii.Button)
                    .background(colors.surfaceChip)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${group.passengerCount} pax · ${formatMoney(currencySymbol, group.sharePerPerson)} each",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.padding(start = 8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "TOTAL:",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                    Text(
                        text = formatMoney(currencySymbol, group.totalPassengersFare),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (group.isAllPaid) colors.available else colors.highlight,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
internal fun TripLedgerDetailCard(
    trip: Trip,
    currencySymbol: String,
    onTogglePaid: () -> Unit,
    onLongPress: (() -> Unit)? = null
) {
    val colors = ZaikiThemeTokens.colors
    val dateFormat = SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.US)
    val cardTitle = trip.tripName.trim().ifEmpty { trip.riderName }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ZaikiRadii.Card)
            .background(colors.bgPage)
    ) {
        ZaikiBentoCard(
            contentPadding = PaddingValues(16.dp),
            onLongClick = onLongPress,
            onClick = onLongPress
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = cardTitle,
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        ZaikiTagPill(
                            text = "${trip.passengerCount.coerceAtLeast(1)} Pax",
                            category = ZaikiTagCategory.PERSONAL
                        )
                        ZaikiTagPill(
                            text = if (trip.isAutoTracked) "GPS" else if (trip.isShortTrip) "Short" else "Long",
                            category = if (trip.isAutoTracked) {
                                ZaikiTagCategory.PERSONAL
                            } else if (trip.isShortTrip) {
                                ZaikiTagCategory.WEB
                            } else {
                                ZaikiTagCategory.SYSTEM
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = buildString {
                            append("${trip.riderName} · ${dateFormat.format(Date(trip.tripDate))}")
                            if (trip.vehicleName.isNotBlank()) {
                                append(" · ${trip.vehicleName}")
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                    if (trip.tripNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Note: ${trip.tripNotes}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.highlight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.padding(start = 8.dp))

                ZaikiPaymentStatusChip(
                    isPaid = trip.isPaid,
                    isWaived = trip.isWaived,
                    onClick = onTogglePaid
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            ZaikiHairlineDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LedgerMetricColumn(
                    label = "DISTANCE",
                    value = "${formatDecimal(trip.distanceKm)} km",
                    modifier = Modifier.weight(1f)
                )
                LedgerMetricColumn(
                    label = if (trip.parkingAmount > 0.0) "TOLL/PARK" else "TOLL",
                    value = formatMoney(currencySymbol, trip.tollAmount + trip.parkingAmount),
                    modifier = Modifier.weight(1f)
                )
                LedgerMetricColumn(
                    label = "TOTAL",
                    value = formatMoney(currencySymbol, trip.totalTripCost),
                    modifier = Modifier.weight(1f)
                )
                LedgerMetricColumn(
                    label = "SHARE",
                    value = formatMoney(currencySymbol, trip.calculatedFare),
                    modifier = Modifier.weight(1f),
                    alignEnd = true
                )
            }
        }
    }
}

@Composable
private fun LedgerMetricColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    val colors = ZaikiThemeTokens.colors
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textFaint,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
