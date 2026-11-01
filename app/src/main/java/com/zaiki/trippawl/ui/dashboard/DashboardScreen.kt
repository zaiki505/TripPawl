package com.zaiki.trippawl.ui.dashboard

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.zaiki.trippawl.data.local.entity.DriveLogEntity
import com.zaiki.trippawl.data.local.entity.DriveLogStatus
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaiki.trippawl.domain.model.FuelEfficiencyUnit
import com.zaiki.trippawl.domain.model.GroupedTrip
import com.zaiki.trippawl.domain.model.Rider
import com.zaiki.trippawl.tracking.CompletedDriveSummary
import com.zaiki.trippawl.tracking.DriveTrackingState
import com.zaiki.trippawl.ui.components.CyberCatMascot
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
import com.zaiki.trippawl.ui.components.ZaikiThemeToggleButton
import com.zaiki.trippawl.ui.components.zaikiSquishClickable
import com.zaiki.trippawl.ui.theme.ZaikiDimens
import com.zaiki.trippawl.ui.theme.ZaikiRadii
import com.zaiki.trippawl.ui.theme.ZaikiThemeTokens
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dashboard (Home Screen)
 * - Live Background GPS Drive Tracker & Auto-Detection Card ("Start GPS Drive")
 * - Immediate Low-Friction Drive Start Prompt: "Who is riding with you today?" with passenger checkboxes
 * - Drive Stop Automatic Cost & Split Summary with Trip Name + Toll & Parking Manual Overrides
 * - Metric Cards: Total Uncollected Debt, Total Fuel Spent (Month), Total Settled Payments
 * - Vehicle & Fuel Telemetry Snapshot
 * - Recent Grouped Trips List (5 most recent grouped trips)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onLogNewTripClick: () -> Unit,
    onEditTripClick: (Long) -> Unit,
    onViewAllTripsClick: () -> Unit,
    onViewRidersClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ZaikiThemeTokens.colors
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currency = uiState.settings.currencySymbol

    var hasForegroundLocation by remember { mutableStateOf(checkForegroundLocationPermission(context)) }
    var hasBackgroundLocation by remember { mutableStateOf(checkBackgroundLocationPermission(context)) }
    var showBackgroundPermissionDialog by remember { mutableStateOf(false) }
    var pendingStartDriveAfterBgPrompt by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasForegroundLocation = checkForegroundLocationPermission(context)
                hasBackgroundLocation = checkBackgroundLocationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasBackgroundLocation = granted || checkBackgroundLocationPermission(context)
        if (!hasBackgroundLocation) {
            openAppLocationPermissionSettings(context)
        }
        if (pendingStartDriveAfterBgPrompt) {
            pendingStartDriveAfterBgPrompt = false
            viewModel.startRealGpsDrive()
        }
    }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        hasForegroundLocation = checkForegroundLocationPermission(context)
        hasBackgroundLocation = checkBackgroundLocationPermission(context)
        if (hasForegroundLocation && !hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            showBackgroundPermissionDialog = true
        } else if (hasForegroundLocation) {
            viewModel.startRealGpsDrive()
        }
    }

    val requestBackgroundAlwaysAllow: () -> Unit = {
        if (!checkForegroundLocationPermission(context)) {
            pendingStartDriveAfterBgPrompt = false
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            foregroundPermissionLauncher.launch(perms.toTypedArray())
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !checkBackgroundLocationPermission(context)) {
            showBackgroundPermissionDialog = true
        }
    }

    val requestPermissionsAndStartDrive: () -> Unit = {
        hasForegroundLocation = checkForegroundLocationPermission(context)
        hasBackgroundLocation = checkBackgroundLocationPermission(context)
        if (!hasForegroundLocation) {
            pendingStartDriveAfterBgPrompt = true
            val perms = buildList {
                add(Manifest.permission.ACCESS_FINE_LOCATION)
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    add(Manifest.permission.BLUETOOTH_CONNECT)
                }
            }
            foregroundPermissionLauncher.launch(perms.toTypedArray())
        } else if (!hasBackgroundLocation && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            pendingStartDriveAfterBgPrompt = true
            showBackgroundPermissionDialog = true
        } else {
            viewModel.startRealGpsDrive()
        }
    }

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
            // 1. Hero Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "HI THERE, WELCOME TO",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.highlight,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "TripPawl",
                            style = MaterialTheme.typography.displayMedium.copy(
                                brush = Brush.linearGradient(
                                    colors = listOf(colors.highlight, colors.accentSoft)
                                )
                            ),
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${uiState.settings.vehicleName} · ${uiState.settings.preferredFuelType} · Auto-Track & Split",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    ZaikiThemeToggleButton(
                        isDark = uiState.settings.isDarkTheme,
                        onToggle = viewModel::toggleTheme
                    )
                }
            }

            // 2. Automated Background GPS Drive Telemetry Card
            item {
                LiveDriveTelemetryCard(
                    driveState = uiState.driveState,
                    savedRiders = uiState.savedRiders,
                    currencySymbol = currency,
                    speedThresholdKmh = uiState.settings.autoDetectSpeedThresholdKmh,
                    liveSharePerPerson = uiState.liveDriveFare.riderShare,
                    liveTotalTripCost = uiState.liveDriveFare.totalTripFare,
                    hasBackgroundLocationPermission = hasBackgroundLocation,
                    onRequestBackgroundPermission = requestBackgroundAlwaysAllow,
                    onStartRealDrive = requestPermissionsAndStartDrive,
                    onOpenPassengerPrompt = viewModel::openPassengerPrompt,
                    onStopDrive = viewModel::stopActiveDrive
                )
            }

            // 3. Primary Bento Metric Card: Total Uncollected Debt + Unpaid Alert Trigger
            item {
                ZaikiBentoCard(
                    isMainAccent = true,
                    onClick = onViewRidersClick
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL UNCOLLECTED DEBT",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.highlight
                        )
                        ZaikiTagPill(
                            text = "${uiState.metrics.unpaidTripCount} UNPAID",
                            category = if (uiState.metrics.unpaidTripCount > 0) {
                                ZaikiTagCategory.CREATIVE
                            } else {
                                ZaikiTagCategory.PERSONAL
                            }
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = formatMoney(currency, uiState.metrics.totalUncollectedDebt),
                        style = MaterialTheme.typography.displayLarge,
                        color = if (uiState.metrics.totalUncollectedDebt > 0.0) {
                            colors.unavailable
                        } else {
                            colors.available
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (uiState.metrics.unpaidTripCount > 0) {
                            "Tap to inspect passenger balances & export to WhatsApp / Telegram →"
                        } else {
                            "Everyone is settled up! Clean carpool slate."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )

                    if (uiState.metrics.unpaidTripCount > 0) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ZaikiSecondaryButton(
                                text = "Remind Me to Collect",
                                isAccentTinted = true,
                                onClick = viewModel::sendUnpaidTripsAlert,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                            )
                            ZaikiSecondaryButton(
                                text = "View Riders →",
                                onClick = onViewRidersClick,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                            )
                        }
                    }

                    ZaikiFormStatusBanner(
                        message = uiState.statusBannerMessage,
                        isError = false
                    )
                }
            }

            // 4. Secondary Bento Metric Cards Row: Fuel Spent (Month) & Settled Payments
            item {
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
                        contentPadding = PaddingValues(ZaikiDimens.CardPaddingCompact),
                        onClick = onViewAllTripsClick
                    ) {
                        Text(
                            text = "FUEL SPENT (MONTH)",
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
                            text = "${uiState.metrics.currentMonthTripCount} trip(s) this month",
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
                        contentPadding = PaddingValues(ZaikiDimens.CardPaddingCompact),
                        onClick = onViewAllTripsClick
                    ) {
                        Text(
                            text = "TOTAL COLLECTED",
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
                            text = "${uiState.metrics.settledTripCount} paid share(s)",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 5. Waived Carpool Fares Summary Panel (Replaces Vehicle & Fuel Telemetry)
            item {
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
                                text = "WAIVED CARPOOL FARES",
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
                                text = "Carpool shares forgiven for friends & colleagues (${uiState.metrics.waivedTripCount} waived)",
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

            // 6. GPS Drive Logs (Section Header & List)
            item {
                ZaikiSectionHeader(
                    kicker = "GPS DRIVE SENSORS",
                    title = "GPS Drive Logs",
                    trailingContent = {
                        if (uiState.recentDriveLogs.isNotEmpty()) {
                            ZaikiTagPill(
                                text = "${uiState.recentDriveLogs.size} logs",
                                category = ZaikiTagCategory.SYSTEM
                            )
                        }
                    }
                )
            }

            if (uiState.recentDriveLogs.isEmpty()) {
                item {
                    ZaikiBentoCard(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "No GPS-recorded drives logged yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textMuted
                        )
                    }
                }
            } else {
                items(
                    items = uiState.recentDriveLogs,
                    key = { "drivelog_${it.id}" }
                ) { log ->
                    DriveLogDashboardCard(
                        log = log,
                        onReopen = { viewModel.reopenDriveLog(log) },
                        onDelete = { viewModel.deleteDriveLog(log.id) }
                    )
                }
            }

            // 7. Recent Trips Section Header
            item {
                ZaikiSectionHeader(
                    kicker = "RECENT ACTIVITY",
                    title = "Recent Trips",
                    trailingContent = {
                        if (uiState.recentGroupedTrips.isNotEmpty()) {
                            ZaikiSecondaryButton(
                                text = "View All →",
                                onClick = onViewAllTripsClick,
                                isAccentTinted = true,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                )
            }

            // 7. Recent Grouped Trips List or Empty State
            if (uiState.recentGroupedTrips.isEmpty() && !uiState.isLoading) {
                item {
                    ZaikiBentoCard {
                        Text(
                            text = "No carpool trips logged yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Start a live GPS drive above or tap '+' below to log a grouped carpool trip.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        ZaikiPrimaryButton(
                            text = "+ Log Trip",
                            onClick = onLogNewTripClick,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
                        )
                    }
                }
            } else {
                items(
                    items = uiState.recentGroupedTrips,
                    key = { it.groupKey }
                ) { group ->
                    RecentGroupedTripDashboardCard(
                        group = group,
                        currencySymbol = currency,
                        onTogglePaid = { viewModel.toggleGroupedTripPayment(group) },
                        onCardClick = { onEditTripClick(group.primaryTripId) }
                    )
                }
            }

            // 8. Zaiki Brand Easter Egg — Interactive CyberCat ("Pawl")
            item {
                ZaikiDetailPanel(
                    contentPadding = PaddingValues(vertical = 14.dp, horizontal = 16.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CyberCatMascot(
                            unpaidTripCount = uiState.metrics.unpaidTripCount
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

        // Floating Action Button (FAB): Circular purple '+' positioned right above the floating NavPill
        ZaikiCircularAddFab(
            onClick = onLogNewTripClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 82.dp)
        )
    }

    // Modal 1: On Drive Start — Immediate Low-Friction Prompt: "Who is riding with you today?"
    if (uiState.driveState.isPassengerPromptOpen) {
        val promptSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var quickRiderName by remember { mutableStateOf("") }
        var quickRiderPhone by remember { mutableStateOf("") }

        ModalBottomSheet(
            onDismissRequest = viewModel::dismissPassengerPrompt,
            sheetState = promptSheetState,
            containerColor = colors.sheetBg,
            contentColor = colors.textPrimary,
            scrimColor = colors.scrim
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .padding(
                        start = ZaikiDimens.ScreenHorizontal,
                        end = ZaikiDimens.ScreenHorizontal,
                        top = 4.dp,
                        bottom = 36.dp
                    ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DRIVE STARTED · ${uiState.driveState.triggerSource.uppercase()}",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.available
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Who is riding with you today?",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    ZaikiTagPill(
                        text = "${uiState.driveState.selectedPassengerIds.size.coerceAtLeast(1)} PAX",
                        category = ZaikiTagCategory.PERSONAL
                    )
                }

                Text(
                    text = "Check the passengers in your car. GPS distance is being logged automatically in the background.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )

                // Quick-Select Checkboxes for Saved Passengers
                ZaikiBentoCard {
                    if (uiState.savedRiders.isEmpty()) {
                        Text(
                            text = "No saved passengers yet. Add one quickly below:",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            uiState.savedRiders.forEach { rider ->
                                val isChecked = rider.id in uiState.driveState.selectedPassengerIds
                                PassengerCheckboxRow(
                                    rider = rider,
                                    checked = isChecked,
                                    onToggle = { viewModel.togglePassengerForDrive(rider.id) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    ZaikiHairlineDivider()
                    Spacer(modifier = Modifier.height(10.dp))

                    // Inline Quick-Add New Passenger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ZaikiFormField(
                            label = "",
                            value = quickRiderName,
                            onValueChange = { quickRiderName = it },
                            placeholder = "+ Quick add passenger name...",
                            modifier = Modifier.weight(1f)
                        )
                        ZaikiSecondaryButton(
                            text = "Add",
                            enabled = quickRiderName.trim().isNotEmpty(),
                            isAccentTinted = true,
                            onClick = {
                                viewModel.quickAddPassenger(quickRiderName, quickRiderPhone)
                                quickRiderName = ""
                                quickRiderPhone = ""
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
                        )
                    }
                }

                val selectedCount = uiState.driveState.selectedPassengerIds.size
                ZaikiPrimaryButton(
                    text = "Confirm $selectedCount Passenger(s) & Drive →",
                    onClick = viewModel::dismissPassengerPrompt,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    // Modal 2: On Drive Stop — Automatic Cost & Split Summary + Trip Name + Toll / Parking Manual Overrides
    val completedSummary = uiState.driveState.completedDriveSummary
    if (completedSummary != null) {
        DriveCompletedSplitSheet(
            summary = completedSummary,
            savedRiders = uiState.savedRiders,
            recentTripNames = uiState.recentTripNames,
            currencySymbol = currency,
            viewModel = viewModel
        )
    }

    // Modal 3: Prompt to Grant "Allow all the time" Background GPS Permission (Android 10+)
    if (showBackgroundPermissionDialog && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        AlertDialog(
            onDismissRequest = {
                showBackgroundPermissionDialog = false
                if (pendingStartDriveAfterBgPrompt) {
                    pendingStartDriveAfterBgPrompt = false
                    viewModel.startRealGpsDrive()
                }
            },
            containerColor = colors.glassFillStrong,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textBody,
            title = {
                Column {
                    Text(
                        text = "BACKGROUND GPS PERMISSION",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.highlight
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Allow GPS 'All the Time'",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "To track real GPS distance accurately when TripPawl is in the background, minimized, or when your screen is locked, Android requires Location permission set to \"Allow all the time\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textBody
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    ZaikiPrimaryButton(
                        text = "Select 'Allow all the time' →",
                        onClick = {
                            showBackgroundPermissionDialog = false
                            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    ZaikiSecondaryButton(
                        text = if (pendingStartDriveAfterBgPrompt) "Start Drive Anyway" else "Later",
                        onClick = {
                            showBackgroundPermissionDialog = false
                            if (pendingStartDriveAfterBgPrompt) {
                                pendingStartDriveAfterBgPrompt = false
                                viewModel.startRealGpsDrive()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
private fun LiveDriveTelemetryCard(
    driveState: DriveTrackingState,
    savedRiders: List<Rider>,
    currencySymbol: String,
    speedThresholdKmh: Double,
    liveSharePerPerson: Double,
    liveTotalTripCost: Double,
    hasBackgroundLocationPermission: Boolean,
    onRequestBackgroundPermission: () -> Unit,
    onStartRealDrive: () -> Unit,
    onOpenPassengerPrompt: () -> Unit,
    onStopDrive: () -> Unit
) {
    val colors = ZaikiThemeTokens.colors

    if (!driveState.isDriveActive) {
        ZaikiBentoCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(colors.available)
                    )
                    Text(
                        text = "AUTO DRIVE & GPS TELEMETRY",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.available
                    )
                }
                ZaikiTagPill(
                    text = if (hasBackgroundLocationPermission) "BG GPS: ALWAYS ✓" else "BG GPS NEEDED",
                    category = if (hasBackgroundLocationPermission) {
                        ZaikiTagCategory.PERSONAL
                    } else {
                        ZaikiTagCategory.CREATIVE
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Hands-Free Trip Logging",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Auto-starts ONLY when connected to your set Car Bluetooth device AND speed exceeds ${speedThresholdKmh.toInt()} km/h.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ZaikiTagPill(
                    text = if (driveState.isCarBluetoothConnected) {
                        "BT: ${driveState.connectedBluetoothDeviceLabel ?: "Connected"} ✓"
                    } else {
                        "BT: Waiting"
                    },
                    category = if (driveState.isCarBluetoothConnected) {
                        ZaikiTagCategory.PERSONAL
                    } else {
                        ZaikiTagCategory.SYSTEM
                    }
                )
                ZaikiTagPill(
                    text = "Speed ≥ ${speedThresholdKmh.toInt()} km/h (${String.format(Locale.US, "%.0f", driveState.currentSpeedKmh)} km/h)",
                    category = if (driveState.currentSpeedKmh >= speedThresholdKmh) {
                        ZaikiTagCategory.PERSONAL
                    } else {
                        ZaikiTagCategory.WEB
                    }
                )
            }

            if (!hasBackgroundLocationPermission) {
                Spacer(modifier = Modifier.height(10.dp))
                ZaikiDetailPanel(
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    borderColor = colors.unavailable.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "BACKGROUND GPS PERMISSION",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.unavailable
                            )
                            Text(
                                text = "Set Location to 'Allow all the time' so GPS works in the background.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textBody
                            )
                        }
                        Spacer(modifier = Modifier.padding(start = 8.dp))
                        ZaikiSecondaryButton(
                            text = "Allow Always →",
                            isAccentTinted = true,
                            onClick = onRequestBackgroundPermission,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            ZaikiPrimaryButton(
                text = "▶ Start GPS Drive",
                onClick = onStartRealDrive,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    } else {
        val selectedNames = savedRiders
            .filter { it.id in driveState.selectedPassengerIds }
            .joinToString(", ") { it.name }
            .ifEmpty { "Tap 'Passengers' to select" }
        val mins = driveState.elapsedSeconds / 60
        val secs = driveState.elapsedSeconds % 60
        val durationStr = String.format(Locale.US, "%02d:%02d", mins, secs)

        ZaikiBentoCard(
            isMainAccent = true,
            accentBorderColor = colors.available.copy(alpha = 0.65f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(colors.available)
                    )
                    Text(
                        text = "● RECORDING DRIVE LIVE",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.available,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                ZaikiTagPill(
                    text = "$durationStr · ${driveState.gpsPointsCount} GPS PTS",
                    category = ZaikiTagCategory.PERSONAL
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Live Telemetry Metrics Row: Distance, Speed, Live Share/Person
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DISTANCE DRIVEN",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                    Text(
                        text = "${String.format(Locale.US, "%.2f", driveState.distanceDrivenKm)} km",
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Speed: ${String.format(Locale.US, "%.0f", driveState.currentSpeedKmh)} km/h",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.available
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "SHARE / PERSON (${driveState.selectedPassengerIds.size.coerceAtLeast(1)} PAX)",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.highlight
                    )
                    Text(
                        text = formatMoney(currencySymbol, liveSharePerPerson),
                        style = MaterialTheme.typography.displaySmall,
                        color = colors.highlight,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "Total: ${formatMoney(currencySymbol, liveTotalTripCost)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            ZaikiHairlineDivider()
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Riding today: $selectedNames",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ZaikiSecondaryButton(
                    text = "Passengers (${driveState.selectedPassengerIds.size})",
                    onClick = onOpenPassengerPrompt,
                    isAccentTinted = true,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 11.dp)
                )
                ZaikiPrimaryButton(
                    text = "■ Finish Drive",
                    onClick = onStopDrive,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)
                )
            }
        }
    }
}

@Composable
internal fun PassengerCheckboxRow(
    rider: Rider,
    checked: Boolean,
    onToggle: () -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    val borderColor = if (checked) colors.highlight else colors.borderChip
    val bgColor = if (checked) colors.accentWash else colors.surfaceChip

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zaikiSquishClickable(onClick = onToggle)
            .clip(ZaikiRadii.Panel)
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), ZaikiRadii.Panel)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = rider.name,
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            if (!rider.phone.isNullOrBlank()) {
                Text(
                    text = rider.phone,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(ZaikiRadii.Button)
                .background(if (checked) colors.highlight else Color.Transparent)
                .border(
                    BorderStroke(1.5.dp, if (checked) colors.highlight else colors.textMuted),
                    ZaikiRadii.Button
                ),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DriveCompletedSplitSheet(
    summary: CompletedDriveSummary,
    savedRiders: List<Rider>,
    recentTripNames: List<String>,
    currencySymbol: String,
    viewModel: DashboardViewModel
) {
    val colors = ZaikiThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    var tripNameInput by remember(summary) { mutableStateOf("") }
    var selectedIds by remember(summary) {
        mutableStateOf(
            summary.selectedPassengerIds.ifEmpty {
                savedRiders.take(1).map { it.id }.toSet()
            }
        )
    }
    var distanceInput by remember(summary) {
        mutableStateOf(String.format(Locale.US, "%.2f", summary.distanceDrivenKm))
    }
    var tollInput by remember(summary) { mutableStateOf("") }
    var parkingInput by remember(summary) { mutableStateOf("") }
    var customRiderFareInput by remember(summary) { mutableStateOf("") }
    var isPaid by remember(summary) { mutableStateOf(false) }

    val parsedDistance = distanceInput.toDoubleOrNull() ?: summary.distanceDrivenKm
    val parsedToll = tollInput.toDoubleOrNull() ?: 0.0
    val parsedParking = parkingInput.toDoubleOrNull() ?: 0.0
    val parsedCustomRiderFare = customRiderFareInput.toDoubleOrNull()?.takeIf { it >= 0.0 }
    val preview = viewModel.computeFarePreview(
        distanceKm = parsedDistance,
        tollAmount = parsedToll,
        parkingAmount = parsedParking,
        passengerCount = selectedIds.size.coerceAtLeast(1)
    )
    val effectiveSharePerPerson = parsedCustomRiderFare ?: preview.riderShare

    ModalBottomSheet(
        onDismissRequest = viewModel::dismissCompletedDrive,
        sheetState = sheetState,
        containerColor = colors.sheetBg,
        contentColor = colors.textPrimary,
        scrimColor = colors.scrim
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding(),
            contentPadding = PaddingValues(
                start = ZaikiDimens.ScreenHorizontal,
                end = ZaikiDimens.ScreenHorizontal,
                top = 4.dp,
                bottom = 40.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "DRIVE COMPLETED · ${summary.gpsPointsCount} GPS COORDS",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.available
                        )
                        Text(
                            text = "Confirm Split & Overrides",
                            style = MaterialTheme.typography.headlineSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Start: ${timeFormat.format(Date(summary.startTimeMillis))} → Arrival: ${timeFormat.format(Date(summary.arrivalTimeMillis))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    ZaikiSecondaryButton(
                        text = "Discard",
                        onClick = viewModel::dismissCompletedDrive,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }

            // 1. Trip Name, Distance & Manual Overrides (Toll Fees & Parking Fees)
            item {
                ZaikiBentoCard {
                    Text(
                        text = "1. TRIP NAME, GPS DISTANCE & OVERRIDES",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.highlight
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ZaikiFormField(
                        label = "Trip Name / Label",
                        value = tripNameInput,
                        onValueChange = { tripNameInput = it },
                        placeholder = "e.g., Monday Class, Campus Run..."
                    )
                    if (recentTripNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            recentTripNames.forEach { recentName ->
                                ZaikiFilterChip(
                                    label = recentName,
                                    selected = tripNameInput.equals(recentName, ignoreCase = true),
                                    onClick = { tripNameInput = recentName }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    ZaikiFormField(
                        label = "GPS Distance Driven",
                        value = distanceInput,
                        onValueChange = { distanceInput = it },
                        suffix = "km",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ZaikiFormField(
                            label = "Toll Fees (Override)",
                            value = tollInput,
                            onValueChange = { tollInput = it },
                            placeholder = "0.00",
                            suffix = currencySymbol,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        ZaikiFormField(
                            label = "Parking Fees (Override)",
                            value = parkingInput,
                            onValueChange = { parkingInput = it },
                            placeholder = "0.00",
                            suffix = currencySymbol,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    ZaikiFormField(
                        label = "Custom Payment Override per Rider (Optional)",
                        value = customRiderFareInput,
                        onValueChange = { customRiderFareInput = it },
                        placeholder = "Auto (${String.format(Locale.US, "%.2f", preview.riderShare)})",
                        suffix = "$currencySymbol/pax",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            }

            // 2. Passengers on this Drive
            item {
                ZaikiBentoCard {
                    Text(
                        text = "2. GROUPED PASSENGERS (${selectedIds.size} SELECTED)",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.highlight
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (savedRiders.isEmpty()) {
                        Text(
                            text = "Will be logged under default 'Carpool Passenger' profile.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            savedRiders.forEach { rider ->
                                val isChecked = rider.id in selectedIds
                                PassengerCheckboxRow(
                                    rider = rider,
                                    checked = isChecked,
                                    onToggle = {
                                        val next = selectedIds.toMutableSet()
                                        if (!next.add(rider.id)) next.remove(rider.id)
                                        selectedIds = next
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // 3. Automatic Cost & Split Formula Breakdown
            item {
                ZaikiDetailPanel(
                    title = "AUTOMATIC COST & SPLIT CALCULATION",
                    borderColor = colors.borderAccent
                ) {
                    Text(
                        text = "Total Trip Cost = ${formatMoney(currencySymbol, preview.totalTripFare)} " +
                            "(Fuel/Base: ${formatMoney(currencySymbol, preview.baseTripCost)} + " +
                            "Wear: ${formatMoney(currencySymbol, preview.maintenanceSurcharge)} + " +
                            "Toll/Parking: ${formatMoney(currencySymbol, preview.effectiveTollAmount + preview.parkingAmount)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textBody
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (preview.isShortTrip) {
                            "Short-Trip Rule: Every passenger pays flat ${formatMoney(currencySymbol, preview.riderShare)} for 1 trip (${preview.passengerCount} passenger(s))"
                        } else {
                            "Split Formula: ${formatMoney(currencySymbol, preview.totalTripFare)} ÷ ${preview.totalOccupants} occupants (${preview.passengerCount} passenger(s) + 1 driver)"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "SHARE PER PERSON:",
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.highlight,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = formatMoney(currencySymbol, effectiveSharePerPerson),
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.highlight,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            item {
                ZaikiPrimaryButton(
                    text = "Save Group (${selectedIds.size.coerceAtLeast(1)} Pax · Total ${formatMoney(currencySymbol, effectiveSharePerPerson * preview.passengerCount)}) →",
                    onClick = {
                        viewModel.saveCompletedDrive(
                            tripName = tripNameInput,
                            distanceOverrideKm = parsedDistance,
                            tollAmount = parsedToll,
                            parkingAmount = parsedParking,
                            selectedPassengerIds = selectedIds,
                            customPaymentPerRider = parsedCustomRiderFare,
                            isPaid = isPaid
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun RecentGroupedTripDashboardCard(
    group: GroupedTrip,
    currencySymbol: String,
    onTogglePaid: () -> Unit,
    onCardClick: () -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    val dateFormat = SimpleDateFormat("dd MMM · HH:mm", Locale.US)

    ZaikiBentoCard(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        accentBorderColor = if (!group.isAllPaid) {
            colors.unavailable.copy(alpha = 0.35f)
        } else {
            null
        },
        onClick = onCardClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
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

                Text(
                    text = group.passengerNamesSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textBody,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${dateFormat.format(Date(group.tripDate))} · ${formatDecimal(group.distanceKm)} km" +
                        if (group.passengerCount > 1) " · ${formatMoney(currencySymbol, group.sharePerPerson)}/pax" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.padding(start = 10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMoney(currencySymbol, group.totalPassengersFare),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (group.isAllPaid) colors.available else colors.highlight,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(5.dp))
                ZaikiPaymentStatusChip(
                    isPaid = group.isAllPaid,
                    isWaived = group.isAllWaived,
                    onClick = onTogglePaid,
                    showQuickActionHint = !group.isAllPaid && !group.isAllWaived
                )
            }
        }
    }
}

@Composable
private fun DriveLogDashboardCard(
    log: DriveLogEntity,
    onReopen: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    val dateFormat = remember { SimpleDateFormat("dd MMM · HH:mm", Locale.US) }
    val durationMins = (log.durationMillis / 60_000).coerceAtLeast(1)

    ZaikiBentoCard(
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "GPS Drive · ${formatDecimal(log.distanceKm)} km",
                        style = MaterialTheme.typography.titleSmall,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    ZaikiTagPill(
                        text = log.logStatus.label,
                        category = when (log.logStatus) {
                            DriveLogStatus.SAVED -> ZaikiTagCategory.WEB
                            DriveLogStatus.PENDING -> ZaikiTagCategory.PERSONAL
                            DriveLogStatus.DISCARDED -> ZaikiTagCategory.SYSTEM
                        }
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${dateFormat.format(Date(log.startTimeMillis))} · ${durationMins}m · " +
                        "${formatDecimal(log.averageSpeedKmh)} km/h avg · ${log.gpsPointsCount} pts",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }

            Spacer(modifier = Modifier.padding(start = 8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (log.logStatus != DriveLogStatus.SAVED) {
                    ZaikiTagPill(
                        text = "Review",
                        category = ZaikiTagCategory.PERSONAL,
                        onClick = onReopen
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Log",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

internal fun formatMoney(currencySymbol: String, amount: Double): String {
    return "$currencySymbol ${String.format(Locale.US, "%.2f", amount)}"
}

internal fun formatDecimal(value: Double): String {
    return if (value % 1.0 == 0.0) {
        String.format(Locale.US, "%.1f", value)
    } else {
        String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
    }
}

internal fun checkForegroundLocationPermission(context: Context): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    val coarse = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

internal fun checkBackgroundLocationPermission(context: Context): Boolean {
    if (!checkForegroundLocationPermission(context)) return false
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }
}

internal fun openAppLocationPermissionSettings(context: Context) {
    runCatching {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
