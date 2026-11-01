package com.zaiki.trippawl.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import com.zaiki.trippawl.BuildConfig
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaiki.trippawl.data.backup.AutoBackupFrequency
import com.zaiki.trippawl.data.backup.LocalBackupFileUi
import com.zaiki.trippawl.data.backup.RestoreMode
import com.zaiki.trippawl.domain.model.FuelEfficiencyUnit
import com.zaiki.trippawl.domain.model.MaintenanceMarkupMode
import com.zaiki.trippawl.domain.model.UnpaidReminderInterval
import com.zaiki.trippawl.ui.components.ZaikiBackToTopButton
import com.zaiki.trippawl.ui.components.ZaikiBentoCard
import com.zaiki.trippawl.ui.components.ZaikiDetailPanel
import com.zaiki.trippawl.ui.components.ZaikiFilterChip
import com.zaiki.trippawl.ui.components.ZaikiFormField
import com.zaiki.trippawl.ui.components.ZaikiFormStatusBanner
import com.zaiki.trippawl.ui.components.ZaikiHairlineDivider
import com.zaiki.trippawl.ui.components.ZaikiPrimaryButton
import com.zaiki.trippawl.ui.components.ZaikiSecondaryButton
import com.zaiki.trippawl.ui.components.ZaikiSectionHeader
import com.zaiki.trippawl.ui.components.ZaikiTagCategory
import com.zaiki.trippawl.ui.components.ZaikiTagPill
import com.zaiki.trippawl.ui.components.ZaikiThemeToggleButton
import com.zaiki.trippawl.ui.components.zaikiSquishClickable
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
 * Settings / Configuration Screen
 * 1. Vehicle & Fuel Telemetry Config:
 *    - Vehicle Name / Profile, Preferred Fuel Type (`RON 95`, `RON 97`, `Diesel`, `EV / Hybrid`)
 *    - Fuel Consumption Rate (`km/L` or `L/100km`), Current Fuel Price per Liter
 *    - Live Cost per KM formula preview (`Fuel Price / Fuel Efficiency in km/L`)
 *    - Optional Maintenance & Wear Fee per km
 * 2. Wear & Tear Fee + Short-Trip Tier:
 *    - Every passenger pays flat `RM XX` for 1 short trip (`distance <= cutoff`)
 * 3. Auto-Drive Detection & Background Telemetry:
 *    - Car Bluetooth connection auto-trigger with a scrollable Pop-up Picker for paired & custom Bluetooth devices
 *    - Speed threshold detection
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ZaikiThemeTokens.colors
    val context = LocalContext.current
    val currency = uiState.currencySymbol.ifBlank { "RM" }
    val fuelTypes = listOf("RON 95", "RON 97", "Diesel", "Hybrid / EV")

    var isBluetoothPopupOpen by remember { mutableStateOf(false) }
    var isLocalVaultPopupOpen by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val showBackToTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 260
        }
    }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy · HH:mm", Locale.US) }
    val fileStampFormat = remember { SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US) }

    // 1. Local Device File Export Launcher (.json)
    val exportLocalDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToDocumentUri(uri, isGoogleDriveTarget = false)
        }
    }

    // 2. Local Device File Restore Launcher (.json)
    val importLocalDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreBackupFromDocumentUri(uri, sourceLabel = "Device File")
        }
    }

    // 3. Google Drive Document Export Launcher (.json)
    val exportGoogleDriveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportBackupToDocumentUri(uri, isGoogleDriveTarget = true)
        }
    }

    // 4. Google Drive Document Restore Launcher (.json)
    val importGoogleDriveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.restoreBackupFromDocumentUri(uri, sourceLabel = "Google Drive")
        }
    }

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.refreshPairedBluetoothDevices()
    }

    val scanPairedBluetoothDevices: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
                viewModel.refreshPairedBluetoothDevices()
            } else {
                bluetoothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
            }
        } else {
            viewModel.refreshPairedBluetoothDevices()
        }
    }

    LaunchedEffect(Unit) {
        val hasBtPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        if (hasBtPermission) {
            viewModel.refreshPairedBluetoothDevices()
        }
    }

    val anyPopupOpen = isBluetoothPopupOpen || isLocalVaultPopupOpen

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(if (anyPopupOpen) Modifier.blur(20.dp) else Modifier)
                .imePadding(),
            contentPadding = ZaikiDimens.screenPadding(),
            verticalArrangement = Arrangement.spacedBy(ZaikiDimens.SectionGap)
        ) {
        // 1. Header
        item {
            ZaikiSectionHeader(
                kicker = "VEHICLE TELEMETRY & PREFERENCES",
                title = "Telemetry & Config",
                trailingContent = {
                    ZaikiThemeToggleButton(
                        isDark = uiState.isDarkTheme,
                        onToggle = { viewModel.onThemeToggle(!uiState.isDarkTheme) }
                    )
                }
            )
        }

        // 2. Vehicle & Fuel Telemetry Config
        item {
            ZaikiBentoCard(isMainAccent = true) {
                Text(
                    text = "1. VEHICLE & FUEL TELEMETRY CONFIG",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.highlight
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ZaikiFormField(
                        label = "Vehicle Profile",
                        value = uiState.vehicleNameInput,
                        onValueChange = viewModel::onVehicleNameChange,
                        placeholder = "My Car",
                        modifier = Modifier.weight(1.4f)
                    )
                    ZaikiFormField(
                        label = "Currency",
                        value = uiState.currencySymbol,
                        onValueChange = viewModel::onCurrencyChange,
                        placeholder = "RM",
                        modifier = Modifier.weight(0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Preferred Fuel Type",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fuelTypes.forEach { fuelType ->
                        ZaikiFilterChip(
                            label = fuelType,
                            selected = uiState.preferredFuelType.equals(fuelType, ignoreCase = true),
                            onClick = { viewModel.onPreferredFuelTypeChange(fuelType) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Fuel Consumption Unit",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FuelEfficiencyUnit.entries.forEach { unit ->
                        ZaikiFilterChip(
                            label = unit.label,
                            selected = uiState.fuelEfficiencyUnit == unit,
                            onClick = { viewModel.onFuelEfficiencyUnitChange(unit) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ZaikiFormField(
                        label = "Fuel Price / Liter",
                        value = uiState.fuelPriceInput,
                        onValueChange = viewModel::onFuelPriceChange,
                        placeholder = "1.99",
                        suffix = "$currency/L",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    ZaikiFormField(
                        label = "Fuel Consumption",
                        value = uiState.vehicleMileageInput,
                        onValueChange = viewModel::onVehicleMileageChange,
                        placeholder = "12.0",
                        suffix = uiState.fuelEfficiencyUnit.suffix,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                ZaikiDetailPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "COST PER KM = FUEL PRICE ÷ KM/L",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.highlight
                            )
                            Text(
                                text = "Effective efficiency: ${formatDecimal(uiState.effectiveMileageKmPerL)} km/L",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted
                            )
                        }
                        Text(
                            text = "${formatMoney(currency, uiState.previewRatePerKm)}/km",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.highlight,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        }

        // 3. Maintenance & Wear Fee + Short-Trip Tier
        item {
            ZaikiBentoCard {
                Text(
                    text = "2. WEAR & TEAR FEE + TIER RULES",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.highlight
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Maintenance & Wear Fee Mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MaintenanceMarkupMode.entries.forEach { mode ->
                        ZaikiFilterChip(
                            label = mode.displayName,
                            selected = uiState.maintenanceMarkupMode == mode,
                            onClick = { viewModel.onMaintenanceModeChange(mode) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ZaikiFormField(
                    label = "Maintenance & Wear Fee (0 for none)",
                    value = uiState.maintenanceMarkupInput,
                    onValueChange = viewModel::onMaintenanceMarkupChange,
                    placeholder = "0.10",
                    suffix = when (uiState.maintenanceMarkupMode) {
                        MaintenanceMarkupMode.PER_KM -> "$currency/km"
                        MaintenanceMarkupMode.FLAT_PER_TRIP -> "$currency/trip"
                        MaintenanceMarkupMode.PERCENTAGE -> "%"
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Spacer(modifier = Modifier.height(12.dp))
                ZaikiHairlineDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Enable Short-Trip Flat Tier",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.enableShortTripTier) {
                                "Trips ≤ cutoff: every passenger pays flat $currency ${uiState.shortTripFlatRateInput.ifBlank { "3.00" }} for 1 trip"
                            } else {
                                "All trips use pure Distance × Cost/KM formula"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Switch(
                        checked = uiState.enableShortTripTier,
                        onCheckedChange = viewModel::onEnableShortTripTierChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.highlight,
                            checkedTrackColor = colors.accentWash
                        )
                    )
                }

                if (uiState.enableShortTripTier) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ZaikiFormField(
                            label = "Short-Trip Cutoff",
                            value = uiState.shortTripCutoffInput,
                            onValueChange = viewModel::onCutoffChange,
                            placeholder = "10.0",
                            suffix = "km",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                        ZaikiFormField(
                            label = "Flat / Passenger",
                            value = uiState.shortTripFlatRateInput,
                            onValueChange = viewModel::onFlatRateChange,
                            placeholder = "3.00",
                            suffix = "$currency/pax",
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // 4. Auto-Drive Detection & Background GPS Triggers
        item {
            ZaikiBentoCard {
                Text(
                    text = "3. AUTO-DRIVE DETECTION & TRIGGERS",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.highlight
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Auto-Trigger Rule: Starts ONLY when connected to your set Bluetooth device AND vehicle speed exceeds the threshold below.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Car Bluetooth + Speed Auto-Trigger",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Requires both Bluetooth connection & speed threshold to auto-start GPS logging",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    Switch(
                        checked = uiState.autoDetectDriveBluetooth,
                        onCheckedChange = viewModel::onAutoDetectBluetoothChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.highlight,
                            checkedTrackColor = colors.accentWash
                        )
                    )
                }

                if (uiState.autoDetectDriveBluetooth) {
                    Spacer(modifier = Modifier.height(12.dp))

                    val hasSelectedDevices = uiState.triggerBluetoothAddresses.isNotEmpty() ||
                        uiState.triggerBluetoothNames.isNotEmpty()
                    val selectedCount = maxOf(
                        uiState.triggerBluetoothAddresses.size,
                        uiState.triggerBluetoothNames.size
                    )
                    val selectedNamesPreview = uiState.triggerBluetoothNames
                        .ifEmpty { uiState.triggerBluetoothAddresses }
                        .joinToString(", ")

                    ZaikiDetailPanel {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "TRIGGER BLUETOOTH DEVICES",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.highlight
                                )
                                Text(
                                    text = if (hasSelectedDevices) {
                                        "Selected: $selectedNamesPreview"
                                    } else {
                                        "Default: Any Car / Audio Bluetooth device"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textBody,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Spacer(modifier = Modifier.padding(start = 8.dp))
                            ZaikiTagPill(
                                text = if (hasSelectedDevices) "$selectedCount SELECTED" else "ANY CAR AUDIO",
                                category = if (hasSelectedDevices) ZaikiTagCategory.PERSONAL else ZaikiTagCategory.SYSTEM
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        ZaikiSecondaryButton(
                            text = "Choose Bluetooth Devices →",
                            isAccentTinted = true,
                            onClick = {
                                scanPairedBluetoothDevices()
                                isBluetoothPopupOpen = true
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                ZaikiFormField(
                    label = "Auto-Start Speed Threshold",
                    value = uiState.speedThresholdInput,
                    onValueChange = viewModel::onSpeedThresholdChange,
                    placeholder = "15.0",
                    suffix = "km/h",
                    helperText = "Starts tracking once Bluetooth is connected AND speed exceeds this value",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        }

        // 4. Unpaid Trip Notification Alerts (Every 7 days / 14 days / 30 days / 14 weeks)
        item {
            ZaikiBentoCard(isMainAccent = false) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "4. UNPAID TRIP NOTIFICATIONS",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.highlight
                        )
                        Text(
                            text = "Automatic reminder schedule for uncollected carpool fares",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    ZaikiTagPill(
                        text = if (uiState.unpaidReminderEnabled) uiState.unpaidReminderInterval.label.uppercase() else "OFF",
                        category = if (uiState.unpaidReminderEnabled) ZaikiTagCategory.PERSONAL else ZaikiTagCategory.CREATIVE
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Automatic Reminder Alert",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Send polite notification alert when any trips remain unpaid",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Switch(
                        checked = uiState.unpaidReminderEnabled,
                        onCheckedChange = viewModel::onUnpaidReminderEnabledChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.highlight,
                            checkedTrackColor = colors.accentWash
                        )
                    )
                }

                if (uiState.unpaidReminderEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Alert Frequency",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UnpaidReminderInterval.entries.forEach { interval ->
                            ZaikiFilterChip(
                                label = interval.label,
                                selected = uiState.unpaidReminderInterval == interval,
                                onClick = { viewModel.onUnpaidReminderIntervalChange(interval) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    ZaikiSecondaryButton(
                        text = "Test Reminder Notification",
                        isAccentTinted = true,
                        onClick = viewModel::onTestUnpaidReminderAlert,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // 5. Simplified Data Backup & Restore (Auto Backup + Local + Google Drive)
        item {
            ZaikiBentoCard(isMainAccent = true) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "5. BACKUP & RESTORE",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.highlight
                        )
                        Text(
                            text = if (uiState.lastLocalBackupMillis > 0L) {
                                "Last backup: ${dateFormat.format(Date(uiState.lastLocalBackupMillis))}"
                            } else {
                                "Keep your riders, trips & settings safely backed up"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    ZaikiTagPill(
                        text = "${uiState.localBackupFiles.size} SAVED",
                        category = ZaikiTagCategory.PERSONAL
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 1. Auto Backup Toggle & Schedule
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto Backup",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Automatically save backup snapshots in the background",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Switch(
                        checked = uiState.autoLocalBackupEnabled,
                        onCheckedChange = viewModel::onToggleAutoLocalBackup,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.highlight,
                            checkedTrackColor = colors.accentWash
                        )
                    )
                }

                if (uiState.autoLocalBackupEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AutoBackupFrequency.entries.forEach { freq ->
                            ZaikiFilterChip(
                                label = freq.label,
                                selected = uiState.autoBackupFrequency == freq,
                                onClick = { viewModel.onAutoBackupFrequencyChange(freq) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                ZaikiHairlineDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // 2. Local Backup & Restore (Compact 2-button row)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZaikiPrimaryButton(
                        text = if (uiState.isBackupBusy) "Saving..." else "Backup Now",
                        enabled = !uiState.isBackupBusy,
                        onClick = viewModel::createLocalSnapshotNow,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    )
                    ZaikiSecondaryButton(
                        text = "Restore / Files (${uiState.localBackupFiles.size})",
                        isAccentTinted = true,
                        onClick = {
                            viewModel.refreshLocalBackups()
                            isLocalVaultPopupOpen = true
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                ZaikiHairlineDivider()
                Spacer(modifier = Modifier.height(12.dp))

                // 3. Optional Google Drive Cloud Backup
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Drive Backup",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (uiState.lastGoogleDriveBackupMillis > 0L) {
                                "Last Drive sync: ${dateFormat.format(Date(uiState.lastGoogleDriveBackupMillis))}"
                            } else {
                                "Optional cloud backup to your Google Drive"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Switch(
                        checked = uiState.googleDriveEnabled,
                        onCheckedChange = viewModel::onToggleGoogleDriveBackup,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colors.highlight,
                            checkedTrackColor = colors.accentWash
                        )
                    )
                }

                if (uiState.googleDriveEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ZaikiPrimaryButton(
                            text = "Save to Drive",
                            enabled = !uiState.isBackupBusy,
                            onClick = {
                                val driveFileName = "trippawl_gdrive_backup_${fileStampFormat.format(Date())}.json"
                                exportGoogleDriveDocumentLauncher.launch(driveFileName)
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        )
                        ZaikiSecondaryButton(
                            text = "Restore Drive",
                            enabled = !uiState.isBackupBusy,
                            isAccentTinted = true,
                            onClick = {
                                importGoogleDriveDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                            },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp)
                        )
                    }
                }

                ZaikiFormStatusBanner(
                    message = uiState.backupStatusMessage,
                    isError = uiState.isBackupStatusError
                )
            }
        }

        // 5. GitHub Updates & App Version
        item {
            ZaikiBentoCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "5. APP VERSION & UPDATES",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.highlight
                        )
                        Text(
                            text = "TripPawl v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Check GitHub releases for the latest app build & APK installer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Spacer(modifier = Modifier.padding(start = 8.dp))
                    ZaikiSecondaryButton(
                        text = if (uiState.isCheckingForUpdate) "Checking..." else "Check Updates",
                        onClick = viewModel::checkForUpdates,
                        isAccentTinted = true,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                if (uiState.updateCheckMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    ZaikiFormStatusBanner(
                        message = uiState.updateCheckMessage,
                        isError = false
                    )
                }

                val update = uiState.updateInfo
                if (update != null && update.isUpdateAvailable) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ZaikiRadii.InputField)
                            .background(colors.available.copy(alpha = 0.12f))
                            .border(BorderStroke(1.dp, colors.available.copy(alpha = 0.4f)), ZaikiRadii.InputField)
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "New Release Available: ${update.releaseTitle}",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.available,
                            fontWeight = FontWeight.Bold
                        )
                        if (update.releaseNotes.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = update.releaseNotes.take(200) + if (update.releaseNotes.length > 200) "..." else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textBody
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ZaikiPrimaryButton(
                                text = "⬇ Install APK",
                                onClick = viewModel::downloadOrInstallUpdate,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            )
                            ZaikiSecondaryButton(
                                text = "View on GitHub",
                                onClick = viewModel::openGitHubReleasesPage,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }
        }

        // 6. Save & Reset Actions
        item {
            ZaikiBentoCard {
                ZaikiFormStatusBanner(
                    message = uiState.statusMessage,
                    isError = uiState.isError
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZaikiSecondaryButton(
                        text = "Reset Defaults",
                        onClick = viewModel::resetDefaults,
                        modifier = Modifier.weight(1f)
                    )
                    ZaikiPrimaryButton(
                        text = "Save Config →",
                        onClick = viewModel::saveSettings,
                        modifier = Modifier.weight(1f)
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
                .padding(end = 24.dp, bottom = 84.dp)
        )
    }

    // Scrollable Pop-Up Dialog for Selecting Bluetooth Trigger Devices
    if (isBluetoothPopupOpen) {
        BluetoothDevicesPickerPopup(
            uiState = uiState,
            onScanPaired = scanPairedBluetoothDevices,
            onToggleDevice = viewModel::toggleBluetoothDevice,
            onAddCustomName = viewModel::addCustomBluetoothDeviceName,
            onRemoveCustomName = viewModel::removeCustomBluetoothDeviceName,
            onClearSelection = viewModel::clearSelectedBluetoothDevices,
            onDismiss = { isBluetoothPopupOpen = false }
        )
    }

    // Scrollable Pop-Up Dialog for Local Backup Snapshots Vault
    if (isLocalVaultPopupOpen) {
        LocalBackupVaultPopup(
            uiState = uiState,
            onRestoreModeChange = viewModel::onRestoreModeChange,
            onCreateSnapshot = viewModel::createLocalSnapshotNow,
            onExportJsonFile = {
                val defaultName = "trippawl_backup_${fileStampFormat.format(Date())}.json"
                exportLocalDocumentLauncher.launch(defaultName)
            },
            onImportJsonFile = {
                importLocalDocumentLauncher.launch(arrayOf("application/json", "*/*"))
                isLocalVaultPopupOpen = false
            },
            onRestoreSnapshot = { file ->
                viewModel.restoreFromLocalSnapshot(file)
                isLocalVaultPopupOpen = false
            },
            onDeleteSnapshot = viewModel::deleteLocalSnapshot,
            onDismiss = { isLocalVaultPopupOpen = false }
        )
    }
}

@Composable
private fun LocalBackupVaultPopup(
    uiState: SettingsFormUiState,
    onRestoreModeChange: (RestoreMode) -> Unit,
    onCreateSnapshot: () -> Unit,
    onExportJsonFile: () -> Unit,
    onImportJsonFile: () -> Unit,
    onRestoreSnapshot: (LocalBackupFileUi) -> Unit,
    onDeleteSnapshot: (LocalBackupFileUi) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy · HH:mm:ss", Locale.US) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 28.dp,
                        shape = ZaikiRadii.Card,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(ZaikiRadii.Card)
                    .background(colors.bgPage)
                    .background(colors.sheetBg)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.accentWash.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(BorderStroke(1.5.dp, colors.borderAccent), ZaikiRadii.Card)
                    .padding(18.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "RESTORE & MANAGE BACKUPS",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.highlight
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Saved Snapshots & Files",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    ZaikiSecondaryButton(
                        text = "✕",
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Restore Mode Selector inside Vault Popup
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RestoreMode.entries.forEach { mode ->
                        ZaikiFilterChip(
                            label = mode.label,
                            selected = uiState.restoreMode == mode,
                            onClick = { onRestoreModeChange(mode) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Export / Import .JSON file actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZaikiSecondaryButton(
                        text = "⬇ Export .JSON",
                        onClick = onExportJsonFile,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    )
                    ZaikiSecondaryButton(
                        text = "⬆ Import .JSON",
                        isAccentTinted = true,
                        onClick = onImportJsonFile,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 320.dp)
                        .clip(ZaikiRadii.Panel)
                        .background(colors.bgPage)
                        .border(BorderStroke(1.dp, colors.borderChip), ZaikiRadii.Panel)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.localBackupFiles.isEmpty()) {
                        item {
                            Text(
                                text = "No local snapshots saved yet. Tap '+ New Snapshot' below to create one.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textMuted,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    } else {
                        items(
                            items = uiState.localBackupFiles,
                            key = { it.filePath }
                        ) { file ->
                            val kb = (file.sizeBytes / 1024.0).coerceAtLeast(0.1)
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ZaikiRadii.Panel)
                                    .background(colors.surfaceChip)
                                    .border(BorderStroke(1.dp, colors.borderChip), ZaikiRadii.Panel)
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (file.isAutoBackup) "Auto-Backup Snapshot" else file.fileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    ZaikiTagPill(
                                        text = "${String.format(Locale.US, "%.1f", kb)} KB",
                                        category = ZaikiTagCategory.SYSTEM
                                    )
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "${dateFormat.format(Date(file.createdAtMillis))} · ${file.ridersCount} riders · ${file.tripsCount} trips",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    ZaikiPrimaryButton(
                                        text = "↺ Restore",
                                        onClick = { onRestoreSnapshot(file) },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                                    )
                                    ZaikiSecondaryButton(
                                        text = "Delete",
                                        onClick = { onDeleteSnapshot(file) },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ZaikiSecondaryButton(
                        text = "+ New Snapshot",
                        isAccentTinted = true,
                        onClick = onCreateSnapshot,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)
                    )
                    ZaikiPrimaryButton(
                        text = "Done",
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 11.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BluetoothDevicesPickerPopup(
    uiState: SettingsFormUiState,
    onScanPaired: () -> Unit,
    onToggleDevice: (PairedBluetoothDeviceUi) -> Unit,
    onAddCustomName: (String) -> Unit,
    onRemoveCustomName: (String) -> Unit,
    onClearSelection: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    var customBtDeviceInput by remember { mutableStateOf("") }

    val hasSelectedDevices = uiState.triggerBluetoothAddresses.isNotEmpty() ||
        uiState.triggerBluetoothNames.isNotEmpty()
    val pairedNamesLower = uiState.pairedBluetoothDevices
        .map { it.name.lowercase() }
        .toSet()
    val customNames = uiState.triggerBluetoothNames
        .filter { it.lowercase() !in pairedNamesLower }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            // Solid non-transparent card container with opaque sheetBg backing + subtle top purple glow
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 28.dp,
                        shape = ZaikiRadii.Card,
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .clip(ZaikiRadii.Card)
                    .background(colors.bgPage)
                    .background(colors.sheetBg)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.accentWash.copy(alpha = 0.55f),
                                Color.Transparent
                            )
                        )
                    )
                    .border(BorderStroke(1.5.dp, colors.borderAccent), ZaikiRadii.Card)
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "CAR BLUETOOTH AUTO-TRIGGER",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.highlight
                        )
                        Text(
                            text = "Select Trigger Devices",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    ZaikiSecondaryButton(
                        text = "Close",
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (hasSelectedDevices) {
                        "Only checked Bluetooth devices below will automatically trigger drive recording."
                    } else {
                        "No specific device checked — any Car/Audio Bluetooth connection will trigger drive recording."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action Bar: Scan Paired Devices + Reset to Any Car Audio
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ZaikiSecondaryButton(
                        text = "Scan Paired Devices",
                        isAccentTinted = true,
                        onClick = onScanPaired,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                    )
                    if (hasSelectedDevices) {
                        ZaikiSecondaryButton(
                            text = "Any Car Audio",
                            onClick = onClearSelection,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }

                if (!uiState.bluetoothScanStatus.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = uiState.bluetoothScanStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                ZaikiHairlineDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable List of Paired & Custom Bluetooth Devices
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.pairedBluetoothDevices.isEmpty() && customNames.isEmpty()) {
                        item {
                            ZaikiDetailPanel {
                                Text(
                                    text = "No paired Bluetooth devices listed yet. Tap 'Scan Paired Devices' above or add your car Bluetooth name below.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                        }
                    }

                    items(
                        items = uiState.pairedBluetoothDevices,
                        key = { "paired_${it.address}" }
                    ) { device ->
                        val isSelected = device.address in uiState.triggerBluetoothAddresses ||
                            uiState.triggerBluetoothNames.any { it.equals(device.name, ignoreCase = true) }
                        BluetoothDeviceCheckboxRow(
                            device = device,
                            selected = isSelected,
                            onToggle = { onToggleDevice(device) }
                        )
                    }

                    items(
                        items = customNames,
                        key = { "custom_$it" }
                    ) { customName ->
                        BluetoothDeviceCheckboxRow(
                            device = PairedBluetoothDeviceUi(
                                name = customName,
                                address = "Custom Device Name",
                                isAudioCar = true
                            ),
                            selected = true,
                            onToggle = { onRemoveCustomName(customName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                ZaikiHairlineDivider()
                Spacer(modifier = Modifier.height(10.dp))

                // Manual Device Name Input inside Pop-up
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ZaikiFormField(
                        label = "",
                        value = customBtDeviceInput,
                        onValueChange = { customBtDeviceInput = it },
                        placeholder = "+ Add car Bluetooth name (e.g. Myvi BT)...",
                        modifier = Modifier.weight(1f)
                    )
                    ZaikiSecondaryButton(
                        text = "Add",
                        enabled = customBtDeviceInput.trim().isNotEmpty(),
                        isAccentTinted = true,
                        onClick = {
                            onAddCustomName(customBtDeviceInput)
                            customBtDeviceInput = ""
                        },
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 11.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                ZaikiPrimaryButton(
                    text = "Done",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun BluetoothDeviceCheckboxRow(
    device: PairedBluetoothDeviceUi,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    val borderColor = if (selected) colors.highlight else colors.borderChip
    val bgColor = if (selected) colors.accentWash else colors.surfaceChip

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zaikiSquishClickable(onClick = onToggle)
            .clip(ZaikiRadii.Panel)
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), ZaikiRadii.Panel)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (device.isAudioCar) {
                    ZaikiTagPill(
                        text = "CAR / AUDIO",
                        category = ZaikiTagCategory.PERSONAL
                    )
                }
            }
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textMuted
            )
        }

        Spacer(modifier = Modifier.padding(start = 8.dp))

        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(ZaikiRadii.Button)
                .background(if (selected) colors.highlight else Color.Transparent)
                .border(
                    BorderStroke(1.5.dp, if (selected) colors.highlight else colors.textMuted),
                    ZaikiRadii.Button
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Text(
                    text = "OK",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
