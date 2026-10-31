package com.zaiki.trippawl.ui.logtrip

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaiki.trippawl.domain.model.FuelEfficiencyUnit
import com.zaiki.trippawl.domain.model.RecurrenceUnit
import com.zaiki.trippawl.domain.model.computeNextOccurrenceMillis
import com.zaiki.trippawl.ui.components.ZaikiBentoCard
import com.zaiki.trippawl.ui.components.ZaikiDetailPanel
import com.zaiki.trippawl.ui.components.ZaikiFilterChip
import com.zaiki.trippawl.ui.components.ZaikiFormField
import com.zaiki.trippawl.ui.components.ZaikiFormStatusBanner
import com.zaiki.trippawl.ui.components.ZaikiHairlineDivider
import com.zaiki.trippawl.ui.components.ZaikiPrimaryButton
import com.zaiki.trippawl.ui.components.ZaikiSecondaryButton
import com.zaiki.trippawl.ui.components.zaikiSquishClickable
import com.zaiki.trippawl.ui.dashboard.formatDecimal
import com.zaiki.trippawl.ui.dashboard.formatMoney
import com.zaiki.trippawl.ui.theme.ZaikiDimens
import com.zaiki.trippawl.ui.theme.ZaikiRadii
import com.zaiki.trippawl.ui.theme.ZaikiThemeTokens
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class LogTripMilestoneStep(val stepNumber: Int, val label: String) {
    DETAILS(1, "DETAILS"),
    FUEL(2, "FUEL"),
    SPLIT(3, "SPLIT"),
    REVIEW(4, "REVIEW")
}

/**
 * Slide-up Modal Bottom Sheet for Logging a New Trip or Editing an Existing Grouped Trip.
 * Uses a 4-step Milestone Stepper (`DETAILS` -> `FUEL` -> `SPLIT` -> `REVIEW`) and a
 * swipeable [HorizontalPager] gated by each step's mandatory data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogTripBottomSheet(
    tripId: Long?,
    requestKey: Long,
    viewModel: LogTripViewModel,
    onDismiss: () -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(tripId, requestKey) {
        viewModel.prepareSheet(tripId)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.sheetBg,
        contentColor = colors.textPrimary,
        scrimColor = colors.scrim
    ) {
        LogTripFormContent(
            viewModel = viewModel,
            onCloseOrSaved = onDismiss,
            isBottomSheet = true
        )
    }
}

/**
 * Standalone Log Trip Screen (retained for direct route navigation if needed).
 */
@Composable
fun LogTripScreen(
    viewModel: LogTripViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    LogTripFormContent(
        viewModel = viewModel,
        onCloseOrSaved = onNavigateBack,
        isBottomSheet = false,
        modifier = modifier
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogTripFormContent(
    viewModel: LogTripViewModel,
    onCloseOrSaved: () -> Unit,
    isBottomSheet: Boolean,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ZaikiThemeTokens.colors
    val currency = uiState.settings.currencySymbol
    val isEditing = uiState.form.editingTripId != null
    val coroutineScope = rememberCoroutineScope()

    // Mandatory data checks for gating forward swipes / Next button:
    // Step 1 (DETAILS): Date is always valid (> 0L).
    // Step 2 (FUEL): Requires distance > 0 km.
    // Step 3 (SPLIT): Requires at least 1 selected rider or new rider name entered.
    val isStep1FuelValid = (uiState.form.distanceInput.toDoubleOrNull() ?: 0.0) > 0.0
    val isStep2SplitValid = uiState.form.selectedRiderIds.isNotEmpty() ||
        uiState.form.riderNameInput.trim().isNotEmpty()

    val maxAllowedPageIndex = when {
        !isStep1FuelValid -> 1
        !isStep2SplitValid -> 2
        else -> 3
    }

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { maxAllowedPageIndex + 1 }
    )
    val currentStepIndex = pagerState.currentPage

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!isBottomSheet) Modifier.fillMaxSize() else Modifier)
            .imePadding()
    ) {
        // 1. Sheet Header Row: Title on Left + Rounded Square '✕' Close Button on Right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ZaikiDimens.ScreenHorizontal,
                    end = ZaikiDimens.ScreenHorizontal,
                    top = if (isBottomSheet) 2.dp else ZaikiDimens.ScreenTop,
                    bottom = 12.dp
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isEditing) "Edit trip" else "Log a trip",
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.ExtraBold
            )
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .zaikiSquishClickable(onClick = onCloseOrSaved)
                    .clip(ZaikiRadii.Button)
                    .background(colors.surfaceChip)
                    .border(BorderStroke(1.dp, colors.borderChip), ZaikiRadii.Button),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✕",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 2. "YOU'LL COLLECT" Live Banner Strip
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accentWash.copy(alpha = 0.42f))
                .border(BorderStroke(0.5.dp, colors.borderHairline))
                .padding(horizontal = ZaikiDimens.ScreenHorizontal, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "YOU'LL COLLECT",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = formatMoney(currency, uiState.totalYouWillCollect),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.available,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 3. 4-Step Milestone Progress Bar (1 DETAILS — 2 FUEL — 3 SPLIT — 4 REVIEW)
        MilestoneStepperBar(
            currentStepIndex = currentStepIndex,
            maxAllowedStepIndex = maxAllowedPageIndex,
            onStepClick = { targetIndex ->
                if (targetIndex <= maxAllowedPageIndex) {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(targetIndex)
                    }
                } else if (targetIndex == 2 && !isStep1FuelValid) {
                    viewModel.setValidationHint("Please enter trip distance (> 0 km) in FUEL first.")
                } else if (targetIndex == 3 && !isStep2SplitValid) {
                    viewModel.setValidationHint("Please select at least 1 rider in SPLIT first.")
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ZaikiDimens.ScreenHorizontal)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Swipeable Card Pager (1 Page per Milestone Step, Gated by Mandatory Data)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(min = 320.dp, max = 470.dp),
            verticalAlignment = Alignment.Top
        ) { pageIndex ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = ZaikiDimens.ScreenHorizontal, vertical = 4.dp)
            ) {
                when (pageIndex) {
                    0 -> Step1DetailsCard(uiState = uiState, viewModel = viewModel)
                    1 -> Step2FuelCard(uiState = uiState, viewModel = viewModel, currency = currency)
                    2 -> Step3SplitCard(uiState = uiState, viewModel = viewModel, currency = currency)
                    3 -> Step4ReviewCard(
                        uiState = uiState,
                        viewModel = viewModel,
                        currency = currency,
                        isEditing = isEditing,
                        onDeleted = onCloseOrSaved
                    )
                }
            }
        }

        // Validation / Status Banner
        if (!uiState.form.statusMessage.isNullOrBlank()) {
            Box(modifier = Modifier.padding(horizontal = ZaikiDimens.ScreenHorizontal)) {
                ZaikiFormStatusBanner(
                    message = uiState.form.statusMessage,
                    isError = uiState.form.isError
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        ZaikiHairlineDivider()

        // 5. Fixed Bottom Navigation Bar: [Cancel / Back] + [Next / Save trip]
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = ZaikiDimens.ScreenHorizontal,
                    end = ZaikiDimens.ScreenHorizontal,
                    top = 12.dp,
                    bottom = 20.dp
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ZaikiSecondaryButton(
                text = if (currentStepIndex == 0) "Cancel" else "Back",
                onClick = {
                    if (currentStepIndex == 0) {
                        onCloseOrSaved()
                    } else {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(currentStepIndex - 1)
                        }
                    }
                },
                modifier = Modifier.weight(0.36f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
            )

            val isLastStep = currentStepIndex == 3
            ZaikiPrimaryButton(
                text = when {
                    isLastStep && isEditing -> "Update trip"
                    isLastStep -> "Save trip"
                    else -> "Next"
                },
                onClick = {
                    when (currentStepIndex) {
                        0 -> {
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        }
                        1 -> {
                            if (isStep1FuelValid) {
                                coroutineScope.launch { pagerState.animateScrollToPage(2) }
                            } else {
                                viewModel.setValidationHint("Please enter a distance greater than 0 km to continue.")
                            }
                        }
                        2 -> {
                            if (isStep2SplitValid) {
                                coroutineScope.launch { pagerState.animateScrollToPage(3) }
                            } else {
                                viewModel.setValidationHint("Please select at least 1 passenger to continue.")
                            }
                        }
                        3 -> {
                            viewModel.saveTrip(onSuccess = onCloseOrSaved)
                        }
                    }
                },
                modifier = Modifier.weight(0.64f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp)
            )
        }
    }
}

/**
 * 4-Step Milestone Progress Bar matching the reference UI (`1 DETAILS` — `2 FUEL` — `3 SPLIT` — `4 REVIEW`).
 */
@Composable
private fun MilestoneStepperBar(
    currentStepIndex: Int,
    maxAllowedStepIndex: Int,
    onStepClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZaikiThemeTokens.colors
    val steps = LogTripMilestoneStep.entries

    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            steps.forEachIndexed { index, step ->
                val isCompleted = index < currentStepIndex
                val isCurrent = index == currentStepIndex
                val isUnlocked = index <= maxAllowedStepIndex

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .zaikiSquishClickable(onClick = { onStepClick(index) })
                        .padding(horizontal = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCompleted -> colors.highlight
                                    isCurrent -> colors.accentWash
                                    else -> colors.surfaceChip
                                }
                            )
                            .border(
                                BorderStroke(
                                    width = if (isCurrent) 1.5.dp else 1.dp,
                                    color = when {
                                        isCompleted || isCurrent -> colors.highlight
                                        isUnlocked -> colors.borderAccent
                                        else -> colors.borderChip
                                    }
                                ),
                                CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isCompleted) "✓" else step.stepNumber.toString(),
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                isCompleted -> Color.White
                                isCurrent -> colors.highlight
                                else -> colors.textMuted
                            },
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = step.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isCurrent || isCompleted) colors.highlight else colors.textMuted,
                        fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium
                    )
                }

                if (index < steps.lastIndex) {
                    val lineActive = index < currentStepIndex
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(bottom = 16.dp)
                            .height(2.dp)
                            .background(
                                if (lineActive) colors.highlight else colors.borderChip
                            )
                    )
                }
            }
        }
    }
}

/**
 * STEP 1: DETAILS
 * - Date & Time selector + max 3 quick date presets (`Now`, `Today 8AM`, `Yesterday`)
 * - Title (optional) + last 3 used trip names suggestion strip
 * - Repeats schedule selector (max 5 quick options: `One-off`, `Daily`, `Weekly`, `Biweekly`, `Monthly`)
 * - Vehicle / Carpool profile chip
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step1DetailsCard(
    uiState: LogTripUiState,
    viewModel: LogTripViewModel
) {
    val colors = ZaikiThemeTokens.colors
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("dd-MM-yyyy", Locale.US) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.US) }
    val currentTripDate = Date(uiState.form.tripDateMillis)
    val recentTripNames = uiState.recentTripNames

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Row 1: Date + Title (optional) side-by-side matching Reference Image 1
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(0.95f)) {
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zaikiSquishClickable {
                            val cal = Calendar.getInstance().apply {
                                timeInMillis = uiState.form.tripDateMillis
                            }
                            DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    viewModel.onTripDateSelected(year, month, dayOfMonth)
                                },
                                cal.get(Calendar.YEAR),
                                cal.get(Calendar.MONTH),
                                cal.get(Calendar.DAY_OF_MONTH)
                            ).show()
                        }
                        .clip(ZaikiRadii.InputField)
                        .background(colors.surfaceChip)
                        .border(BorderStroke(1.dp, colors.borderChip), ZaikiRadii.InputField)
                        .padding(horizontal = 12.dp, vertical = 13.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = dateFormat.format(currentTripDate),
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "📅",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1.05f)) {
                ZaikiFormField(
                    label = "Title (optional)",
                    value = uiState.form.tripNameInput,
                    onValueChange = viewModel::onTripNameChange,
                    placeholder = "e.g. Trip to KL"
                )
            }
        }

        // Quick Time + Date Strip (Max 4 items <= 5) & Last 3 Trip Names Strip (Max 3 items <= 5)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ZaikiFilterChip(
                label = "🕒 ${timeFormat.format(currentTripDate)}",
                selected = true,
                onClick = {
                    val cal = Calendar.getInstance().apply {
                        timeInMillis = uiState.form.tripDateMillis
                    }
                    TimePickerDialog(
                        context,
                        { _, hourOfDay, minute ->
                            viewModel.onTripTimeSelected(hourOfDay, minute)
                        },
                        cal.get(Calendar.HOUR_OF_DAY),
                        cal.get(Calendar.MINUTE),
                        true
                    ).show()
                }
            )
            ZaikiFilterChip(
                label = "Now",
                selected = false,
                onClick = { viewModel.onSetTripDatePreset(System.currentTimeMillis()) }
            )
            ZaikiFilterChip(
                label = "Yesterday",
                selected = false,
                onClick = {
                    viewModel.onSetTripDatePreset(System.currentTimeMillis() - 24L * 60L * 60L * 1000L)
                }
            )
        }

        if (recentTripNames.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Recent Trip Names",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    recentTripNames.take(3).forEach { recentName ->
                        ZaikiFilterChip(
                            label = recentName,
                            selected = uiState.form.tripNameInput.equals(recentName, ignoreCase = true),
                            onClick = { viewModel.onTripNameChange(recentName) }
                        )
                    }
                }
            }
        }

        // Repeats Section (Max 5 strip suggestions: One-off, Daily, Weekly, Biweekly, Monthly)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Repeats",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )

            val intervalVal = (uiState.form.recurrenceIntervalInput.toIntOrNull() ?: 1).coerceAtLeast(1)
            val activeUnit = uiState.form.recurrenceUnit
            val isRecurring = uiState.form.isRecurring

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ZaikiFilterChip(
                    label = "One-off",
                    selected = !isRecurring,
                    onClick = { viewModel.onToggleRecurring(false) }
                )
                ZaikiFilterChip(
                    label = "Daily",
                    selected = isRecurring && intervalVal == 1 && activeUnit == RecurrenceUnit.DAYS,
                    onClick = { viewModel.onApplyRecurrencePreset(1, RecurrenceUnit.DAYS) }
                )
                ZaikiFilterChip(
                    label = "Weekly",
                    selected = isRecurring && intervalVal == 1 && activeUnit == RecurrenceUnit.WEEKS,
                    onClick = { viewModel.onApplyRecurrencePreset(1, RecurrenceUnit.WEEKS) }
                )
                ZaikiFilterChip(
                    label = "Biweekly",
                    selected = isRecurring && intervalVal == 2 && activeUnit == RecurrenceUnit.WEEKS,
                    onClick = { viewModel.onApplyRecurrencePreset(2, RecurrenceUnit.WEEKS) }
                )
                ZaikiFilterChip(
                    label = "Monthly",
                    selected = isRecurring && intervalVal == 1 && activeUnit == RecurrenceUnit.MONTHS,
                    onClick = { viewModel.onApplyRecurrencePreset(1, RecurrenceUnit.MONTHS) }
                )
            }

            if (isRecurring) {
                Spacer(modifier = Modifier.height(4.dp))
                ZaikiDetailPanel(contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            StepperButton(
                                symbol = "−",
                                enabled = intervalVal > 1,
                                onClick = { viewModel.onStepRecurrenceInterval(-1) }
                            )
                            Text(
                                text = "Every $intervalVal",
                                style = MaterialTheme.typography.titleSmall,
                                color = colors.highlight,
                                fontWeight = FontWeight.ExtraBold
                            )
                            StepperButton(
                                symbol = "+",
                                enabled = intervalVal < 365,
                                onClick = { viewModel.onStepRecurrenceInterval(1) }
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(
                                RecurrenceUnit.DAYS,
                                RecurrenceUnit.WEEKS,
                                RecurrenceUnit.MONTHS,
                                RecurrenceUnit.YEARS
                            ).forEach { unit ->
                                ZaikiFilterChip(
                                    label = unit.label,
                                    selected = activeUnit == unit,
                                    onClick = { viewModel.onRecurrenceUnitChange(unit) }
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "A one-off trip. Pick a schedule above to repeat it automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted
                )
            }
        }

        // Which car / carpool?
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Which car / carpool?",
                style = MaterialTheme.typography.titleSmall,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ZaikiFilterChip(
                    label = "🚗 ${uiState.settings.vehicleName.uppercase()} (${uiState.settings.preferredFuelType})",
                    selected = true,
                    onClick = {}
                )
            }
        }
    }
}

/**
 * STEP 2: FUEL & DISTANCE
 * Matches Reference Image 2:
 * - Fuel price (`RM/L`) field
 * - 2x2 Interactive Box (`COST`, `LITERS`, `DISTANCE`, `FUEL EFFICIENCY`)
 * - Quick Distance Strip Suggestion (Max 5 chips: `5 km`, `10 km`, `15 km`, `25 km`, `40 km`)
 * - Collapsible Toll & Parking Override section
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step2FuelCard(
    uiState: LogTripUiState,
    viewModel: LogTripViewModel,
    currency: String
) {
    val colors = ZaikiThemeTokens.colors
    val calc = uiState.liveFareResult
    var showTollParkingOverrides by remember {
        mutableStateOf(uiState.form.tollInput.isNotBlank() || uiState.form.parkingInput.isNotBlank())
    }

    val defaultFuelPriceDisplay = formatDecimal(uiState.settings.fuelPricePerLiter)
    val defaultMileageDisplay = when (uiState.settings.fuelEfficiencyUnit) {
        FuelEfficiencyUnit.KM_PER_LITER -> formatDecimal(uiState.settings.vehicleMileageKmPerL)
        FuelEfficiencyUnit.LITERS_PER_100KM -> formatDecimal(uiState.settings.litersPer100Km)
    }

    val fuelCostText = if ((uiState.form.distanceInput.toDoubleOrNull() ?: 0.0) > 0.0) {
        formatDecimal(calc.actualFuelCost)
    } else {
        ""
    }
    val litersText = if (uiState.estimatedFuelLiters > 0.0) {
        formatDecimal(uiState.estimatedFuelLiters)
    } else {
        ""
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ZaikiFormField(
            label = "Fuel price ($currency/L)",
            value = uiState.form.customFuelPriceInput,
            onValueChange = viewModel::onCustomFuelPriceChange,
            placeholder = defaultFuelPriceDisplay,
            suffix = "$currency/L",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )

        // 2x2 Interactive Telemetry Card (COST, LITERS, DISTANCE, FUEL EFFICIENCY)
        ZaikiDetailPanel(
            borderColor = colors.borderAccent,
            contentPadding = PaddingValues(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ZaikiFormField(
                    label = "COST",
                    value = fuelCostText,
                    onValueChange = viewModel::onFuelCostFigureChange,
                    placeholder = "0",
                    suffix = currency,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                ZaikiFormField(
                    label = "LITERS",
                    value = litersText,
                    onValueChange = viewModel::onFuelLitersFigureChange,
                    placeholder = "0",
                    suffix = "L",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ZaikiFormField(
                    label = "DISTANCE *",
                    value = uiState.form.distanceInput,
                    onValueChange = viewModel::onDistanceChange,
                    placeholder = "0",
                    suffix = "km",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                ZaikiFormField(
                    label = "FUEL EFFICIENCY",
                    value = uiState.form.customFuelConsumptionInput,
                    onValueChange = viewModel::onCustomFuelConsumptionChange,
                    placeholder = defaultMileageDisplay,
                    suffix = uiState.settings.fuelEfficiencyUnit.suffix,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text(
            text = "Tap any figure to edit it — the others update automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textMuted
        )

        // Quick Distance Suggestion Strip (Max 5 options per Requirement 7)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("5", "10", "15", "25", "40").forEach { presetKm ->
                ZaikiFilterChip(
                    label = "$presetKm km",
                    selected = uiState.form.distanceInput == presetKm,
                    onClick = { viewModel.onDistanceChange(presetKm) }
                )
            }
        }

        // Expandable Toll & Parking Overrides Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zaikiSquishClickable { showTollParkingOverrides = !showTollParkingOverrides }
                .clip(ZaikiRadii.InputField)
                .background(colors.surfaceChip)
                .border(BorderStroke(1.dp, colors.borderChip), ZaikiRadii.InputField)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🛣 Add toll or parking fees (optional)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textBody,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = if (showTollParkingOverrides) "▴" else "▾",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textMuted
                )
            }
        }

        if (showTollParkingOverrides) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ZaikiFormField(
                    label = "Toll Fees",
                    value = uiState.form.tollInput,
                    onValueChange = viewModel::onTollChange,
                    placeholder = "0.00",
                    suffix = currency,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                ZaikiFormField(
                    label = "Parking Fees",
                    value = uiState.form.parkingInput,
                    onValueChange = viewModel::onParkingChange,
                    placeholder = "0.00",
                    suffix = currency,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

/**
 * STEP 3: SPLIT & PASSENGERS
 * - Select riders for this trip (full riders list + inline `+ New Rider`)
 * - Auto Split vs Custom Rider Payment Value Override (Requirement 6)
 * - Max 5 quick payment override chips (`RM 2`, `RM 3`, `RM 5`, `RM 8`, `RM 10`)
 * - Per-rider custom payment override fields when Custom Override is active
 * - Paid / Unpaid toggle
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Step3SplitCard(
    uiState: LogTripUiState,
    viewModel: LogTripViewModel,
    currency: String
) {
    val colors = ZaikiThemeTokens.colors
    val calc = uiState.liveFareResult
    val selectedRiders = uiState.savedRiders.filter { it.id in uiState.form.selectedRiderIds }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Who is riding on this trip? *",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )

        // Saved Riders List (Exception allowed by Requirement 7)
        if (uiState.savedRiders.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                uiState.savedRiders.forEach { rider ->
                    val isSelected = !uiState.form.isCreatingNewRider &&
                        (rider.id in uiState.form.selectedRiderIds)
                    ZaikiFilterChip(
                        label = (if (isSelected) "✓ " else "") + rider.name,
                        selected = isSelected,
                        onClick = { viewModel.selectExistingRider(rider) }
                    )
                }
                ZaikiFilterChip(
                    label = "+ New Rider",
                    selected = uiState.form.isCreatingNewRider,
                    onClick = { viewModel.setCreatingNewRider(true) }
                )
            }
        }

        if (uiState.form.isCreatingNewRider || uiState.savedRiders.isEmpty()) {
            ZaikiDetailPanel(contentPadding = PaddingValues(12.dp)) {
                ZaikiFormField(
                    label = "New Passenger Name *",
                    value = uiState.form.riderNameInput,
                    onValueChange = viewModel::onRiderNameChange,
                    placeholder = "e.g., Aiman"
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    ZaikiFormField(
                        label = "WhatsApp / Phone (Optional)",
                        value = uiState.form.riderPhoneInput,
                        onValueChange = viewModel::onRiderPhoneChange,
                        placeholder = "e.g., +60123456789",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.weight(1f)
                    )
                    if (uiState.savedRiders.isNotEmpty()) {
                        ZaikiSecondaryButton(
                            text = "+ Add",
                            enabled = uiState.form.riderNameInput.trim().isNotEmpty(),
                            isAccentTinted = true,
                            onClick = viewModel::addInlineNewRiderToSelection,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }

        ZaikiHairlineDivider()

        // Split Mode & Custom Payment Value Override (Requirement 6)
        Text(
            text = "Rider Payment Calculation",
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ZaikiFilterChip(
                label = "Auto Split (${formatMoney(currency, calc.riderShare)}/pax)",
                selected = !uiState.form.isCustomSplitOverride,
                onClick = { viewModel.onToggleCustomSplitOverride(false) }
            )
            ZaikiFilterChip(
                label = "Custom Payment Override",
                selected = uiState.form.isCustomSplitOverride,
                onClick = { viewModel.onToggleCustomSplitOverride(true) }
            )
        }

        if (!uiState.form.isCustomSplitOverride) {
            // Auto Split: Occupant Count Stepper
            ZaikiDetailPanel(contentPadding = PaddingValues(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Total Passengers",
                            style = MaterialTheme.typography.titleSmall,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (calc.isShortTrip) {
                                "Short trip flat: ${formatMoney(currency, uiState.effectiveSettings.shortTripFlatRate)} / rider"
                            } else {
                                "Split across ${calc.passengerCount} Pax + 1 Driver (÷${calc.totalOccupants})"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val minPassengers = uiState.form.selectedRiderIds.size.coerceAtLeast(1)
                        StepperButton(
                            symbol = "−",
                            enabled = uiState.form.passengerCount > minPassengers,
                            onClick = { viewModel.onPassengerCountChange(uiState.form.passengerCount - 1) }
                        )
                        Text(
                            text = uiState.form.passengerCount.toString(),
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.highlight,
                            fontWeight = FontWeight.ExtraBold
                        )
                        StepperButton(
                            symbol = "+",
                            enabled = uiState.form.passengerCount < 10,
                            onClick = { viewModel.onPassengerCountChange(uiState.form.passengerCount + 1) }
                        )
                    }
                }
            }
        } else {
            // Custom Rider Payment Override Section
            ZaikiDetailPanel(
                borderColor = colors.borderAccent,
                contentPadding = PaddingValues(12.dp)
            ) {
                ZaikiFormField(
                    label = "Set Payment Value per Rider ($currency)",
                    value = uiState.form.customFarePerRiderInput,
                    onValueChange = viewModel::onCustomFarePerRiderChange,
                    placeholder = formatDecimal(calc.riderShare),
                    suffix = "$currency/pax",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Custom Payment Suggestion Strip (Max 5 options per Requirement 7)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("2", "3", "5", "8", "10").forEach { presetRm ->
                        ZaikiFilterChip(
                            label = "$currency $presetRm",
                            selected = uiState.form.customFarePerRiderInput == presetRm,
                            onClick = { viewModel.onCustomFarePerRiderChange(presetRm) }
                        )
                    }
                }

                // Individual Per-Rider Payment Override Inputs when riders are selected
                if (selectedRiders.size > 1) {
                    Spacer(modifier = Modifier.height(10.dp))
                    ZaikiHairlineDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Individual Rider Payment Overrides",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.highlight
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectedRiders.forEach { rider ->
                            val individualValue = uiState.form.customRiderFareMap[rider.id]
                                ?: uiState.form.customFarePerRiderInput
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = rider.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                ZaikiFormField(
                                    label = "",
                                    value = individualValue,
                                    onValueChange = {
                                        viewModel.onCustomFareForSpecificRiderChange(rider.id, it)
                                    },
                                    placeholder = formatDecimal(calc.riderShare),
                                    suffix = currency,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Payment Status Toggle (Unpaid vs Paid)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PaymentToggleOption(
                label = "Unpaid",
                selected = !uiState.form.isPaid,
                activeColor = colors.unavailable,
                onClick = { viewModel.onPaymentStatusToggle(false) },
                modifier = Modifier.weight(1f)
            )
            PaymentToggleOption(
                label = "Paid ✓",
                selected = uiState.form.isPaid,
                activeColor = colors.available,
                onClick = { viewModel.onPaymentStatusToggle(true) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * STEP 4: REVIEW
 * Matches Reference Image 3:
 * - Clean summary card with Vehicle, Date, Repeats, 2x2 Grid (COST, DISTANCE, FUEL, PRICE), Split & Riders
 * - Highlighted `YOU'LL COLLECT` box at the bottom
 */
@Composable
private fun Step4ReviewCard(
    uiState: LogTripUiState,
    viewModel: LogTripViewModel,
    currency: String,
    isEditing: Boolean,
    onDeleted: () -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    val calc = uiState.liveFareResult
    val dateFormat = remember { SimpleDateFormat("dd-MM-yyyy", Locale.US) }
    val selectedRiders = uiState.savedRiders.filter { it.id in uiState.form.selectedRiderIds }
    val ridersSummary = selectedRiders.joinToString(", ") { it.name }
        .ifBlank { uiState.form.riderNameInput.trim().ifBlank { "1 Passenger" } }

    val repeatsLabel = if (!uiState.form.isRecurring) {
        "One-off"
    } else {
        val interval = (uiState.form.recurrenceIntervalInput.toIntOrNull() ?: 1).coerceAtLeast(1)
        val unitWord = if (interval == 1) uiState.form.recurrenceUnit.singular else uiState.form.recurrenceUnit.plural
        "Every $interval $unitWord"
    }

    val splitSummaryLabel = if (uiState.form.isCustomSplitOverride) {
        "Custom (${formatMoney(currency, uiState.effectiveFareForRider(selectedRiders.firstOrNull()?.id))}/pax)"
    } else {
        "Auto (${formatMoney(currency, calc.riderShare)}/pax)"
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ZaikiBentoCard(contentPadding = PaddingValues(16.dp)) {
            ReviewKeyValueRow(
                label = "Vehicle",
                value = uiState.settings.vehicleName.uppercase()
            )
            if (uiState.form.tripNameInput.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                ReviewKeyValueRow(
                    label = "Title",
                    value = uiState.form.tripNameInput.trim()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ReviewKeyValueRow(
                label = "Date",
                value = dateFormat.format(Date(uiState.form.tripDateMillis))
            )
            Spacer(modifier = Modifier.height(8.dp))
            ReviewKeyValueRow(
                label = "Repeats",
                value = repeatsLabel
            )

            Spacer(modifier = Modifier.height(12.dp))
            ZaikiHairlineDivider()
            Spacer(modifier = Modifier.height(12.dp))

            // 2x2 Telemetry Summary Grid (COST, DISTANCE, FUEL, PRICE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "COST",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatMoney(currency, calc.totalTripFare),
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DISTANCE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatDecimal(calc.distanceKm)} km",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "FUEL",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${String.format(Locale.US, "%.2f", uiState.estimatedFuelLiters)} L",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "PRICE",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${formatMoney(currency, uiState.effectiveSettings.fuelPricePerLiter)}/L",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            ZaikiHairlineDivider()
            Spacer(modifier = Modifier.height(12.dp))

            ReviewKeyValueRow(
                label = "Riders (${calc.passengerCount})",
                value = ridersSummary
            )
            Spacer(modifier = Modifier.height(8.dp))
            ReviewKeyValueRow(
                label = "Split",
                value = splitSummaryLabel
            )
            Spacer(modifier = Modifier.height(8.dp))
            ReviewKeyValueRow(
                label = "Status",
                value = if (uiState.form.isPaid) "Paid ✓" else "Unpaid"
            )
        }

        // Bottom Highlight Card: YOU'LL COLLECT
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(ZaikiRadii.Card)
                .background(colors.accentWash)
                .border(BorderStroke(1.dp, colors.borderAccent), ZaikiRadii.Card)
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "YOU'LL COLLECT",
                    style = MaterialTheme.typography.labelLarge,
                    color = colors.textMuted,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = formatMoney(currency, uiState.totalYouWillCollect),
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.available,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        if (isEditing) {
            ZaikiSecondaryButton(
                text = "🗑 Delete Grouped Trip",
                onClick = { viewModel.deleteEditingTrip(onDeleted = onDeleted) },
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 11.dp)
            )
        }
    }
}

@Composable
private fun ReviewKeyValueRow(
    label: String,
    value: String
) {
    val colors = ZaikiThemeTokens.colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textMuted
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textPrimary,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    Box(
        modifier = Modifier
            .size(34.dp)
            .zaikiSquishClickable(enabled = enabled, onClick = onClick)
            .clip(ZaikiRadii.Button)
            .background(if (enabled) colors.accentWash else colors.surfaceChip)
            .border(
                BorderStroke(
                    1.dp,
                    if (enabled) colors.borderAccent else colors.borderChip
                ),
                ZaikiRadii.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleMedium,
            color = if (enabled) colors.highlight else colors.textFaint,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PaymentToggleOption(
    label: String,
    selected: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = ZaikiThemeTokens.colors
    val borderColor = if (selected) activeColor else colors.borderChip
    val bgColor = if (selected) activeColor.copy(alpha = 0.14f) else colors.surfaceChip
    val textColor = if (selected) activeColor else colors.textMuted

    Box(
        modifier = modifier
            .zaikiSquishClickable(onClick = onClick)
            .clip(ZaikiRadii.Button)
            .background(bgColor)
            .border(BorderStroke(1.dp, borderColor), ZaikiRadii.Button)
            .padding(vertical = 11.dp, horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = FontWeight.Bold
        )
    }
}
