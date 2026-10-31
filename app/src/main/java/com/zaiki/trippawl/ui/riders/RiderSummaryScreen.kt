package com.zaiki.trippawl.ui.riders

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zaiki.trippawl.domain.model.RiderDebtSummary
import com.zaiki.trippawl.ui.components.ZaikiBackToTopButton
import com.zaiki.trippawl.ui.components.ZaikiBentoCard
import com.zaiki.trippawl.ui.components.ZaikiCircularAddFab
import com.zaiki.trippawl.ui.components.ZaikiDetailPanel
import com.zaiki.trippawl.ui.components.ZaikiFormField
import com.zaiki.trippawl.ui.components.ZaikiFormStatusBanner
import com.zaiki.trippawl.ui.components.ZaikiHairlineDivider
import com.zaiki.trippawl.ui.components.ZaikiPrimaryButton
import com.zaiki.trippawl.ui.components.ZaikiSecondaryButton
import com.zaiki.trippawl.ui.components.ZaikiSectionHeader
import com.zaiki.trippawl.ui.components.ZaikiTagCategory
import com.zaiki.trippawl.ui.components.ZaikiTagPill
import com.zaiki.trippawl.ui.components.zaikiSquishClickable
import com.zaiki.trippawl.ui.dashboard.formatMoney
import com.zaiki.trippawl.ui.history.TripLedgerDetailCard
import com.zaiki.trippawl.ui.theme.ZaikiDimens
import com.zaiki.trippawl.ui.theme.ZaikiRadii
import com.zaiki.trippawl.ui.theme.ZaikiThemeTokens
import kotlinx.coroutines.launch

/**
 * Rider Debt Summary Screen
 * - Aggregated list of all riders with running unpaid balance per rider.
 * - Full CRUD for Riders:
 *   - Add new rider via "+ Add Rider" header button or slide-up Rider Editor Bottom Sheet.
 *   - Edit rider name & phone directly from any rider card or inside their ledger sheet.
 *   - Delete rider (with confirmation) from any rider card or inside their ledger sheet.
 * - Tap a rider to view:
 *   - Detailed trip ledger for that specific person.
 *   - "Settle Balance" button that marks all historical trips for that rider as "Paid".
 *   - "Share Summary" action: Generates a pre-formatted plain text breakdown for WhatsApp/clipboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderSummaryScreen(
    viewModel: RiderSummaryViewModel,
    onLogNewTripClick: () -> Unit,
    onEditTripClick: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = ZaikiThemeTokens.colors
    val currency = uiState.settings.currencySymbol
    val context = LocalContext.current
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
            item {
                ZaikiSectionHeader(
                    kicker = "CARPOOL ROSTER & BALANCES",
                    title = "Rider Debt Summary",
                    trailingContent = {
                        ZaikiPrimaryButton(
                            text = "+ Add Rider",
                            onClick = viewModel::openAddRiderSheet,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }
                )
            }

            // Top Overview Card
            item {
                val totalUnpaidAllRiders = uiState.riderSummaries.sumOf { it.unpaidBalance }
                val ridersWithDebt = uiState.riderSummaries.count { it.unpaidBalance > 0.0 }

                ZaikiBentoCard(isMainAccent = true) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "OUTSTANDING RIDER BALANCES",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.highlight
                        )
                        ZaikiTagPill(
                            text = "$ridersWithDebt / ${uiState.riderSummaries.size} OWING",
                            category = if (ridersWithDebt > 0) ZaikiTagCategory.CREATIVE else ZaikiTagCategory.PERSONAL
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = formatMoney(currency, totalUnpaidAllRiders),
                        style = MaterialTheme.typography.displayMedium,
                        color = if (totalUnpaidAllRiders > 0.0) colors.unavailable else colors.available,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap any rider card to view their ledger, settle balances, or share summary.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted
                    )

                    if (ridersWithDebt > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        ZaikiSecondaryButton(
                            text = "🔔 Driver Reminder: Payment Still to Collect",
                            isAccentTinted = true,
                            onClick = viewModel::sendUnpaidAlertForAllRiders,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)
                        )
                    }

                    if (uiState.selectedRiderSummary == null) {
                        ZaikiFormStatusBanner(
                            message = uiState.statusBannerMessage,
                            isError = false
                        )
                    }
                }
            }

            if (uiState.riderSummaries.isEmpty()) {
                item {
                    ZaikiBentoCard {
                        Text(
                            text = "No riders saved yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add a rider to your carpool roster or log a new trip to start tracking balances.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ZaikiPrimaryButton(
                                text = "+ Add Rider",
                                onClick = viewModel::openAddRiderSheet
                            )
                            ZaikiSecondaryButton(
                                text = "+ Log a Trip",
                                onClick = onLogNewTripClick,
                                isAccentTinted = true
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.riderSummaries,
                    key = { it.rider.id }
                ) { summary ->
                    RiderSummaryCard(
                        summary = summary,
                        currencySymbol = currency,
                        onClick = { viewModel.selectRider(summary.rider.id) },
                        onEditRider = { viewModel.openEditRiderSheet(summary.rider) },
                        onDeleteRider = { viewModel.requestDeleteRider(summary.rider) }
                    )
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

        // Floating '+' button to log a new trip right from the Riders screen
        ZaikiCircularAddFab(
            onClick = onLogNewTripClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 22.dp, bottom = 82.dp)
        )
    }

    // 1. Rider Detail Ledger Bottom Sheet
    val selectedSummary = uiState.selectedRiderSummary
    if (selectedSummary != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::closeRiderDetail,
            sheetState = sheetState,
            containerColor = colors.sheetBg,
            contentColor = colors.textPrimary,
            scrimColor = colors.scrim
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = ZaikiDimens.ScreenHorizontal,
                    end = ZaikiDimens.ScreenHorizontal,
                    top = 8.dp,
                    bottom = 48.dp
                ),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Rider Header, Unpaid Balance, Edit/Delete Rider & Settle/Share Actions
                item {
                    ZaikiBentoCard(isMainAccent = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "RIDER LEDGER",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = colors.highlight
                                )
                                Text(
                                    text = selectedSummary.rider.name,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = selectedSummary.rider.phone?.takeIf { it.isNotBlank() }
                                        ?: "No phone number set",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "UNPAID BALANCE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textMuted
                                )
                                Text(
                                    text = formatMoney(currency, selectedSummary.unpaidBalance),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = if (selectedSummary.unpaidBalance > 0.0) {
                                        colors.unavailable
                                    } else {
                                        colors.available
                                    },
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Text(
                                    text = "${selectedSummary.unpaidTripCount} unpaid · ${selectedSummary.totalTripsCount} total",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.textMuted
                                )
                            }
                        }

                        ZaikiFormStatusBanner(
                            message = uiState.statusBannerMessage,
                            isError = false
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Primary Actions: Settle Balance & Share Summary
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ZaikiPrimaryButton(
                                text = "✓ Settle Balance",
                                enabled = selectedSummary.unpaidTripCount > 0,
                                onClick = { viewModel.settleAllBalanceForRider(selectedSummary.rider) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            )
                            ZaikiSecondaryButton(
                                text = "Share Summary ↗",
                                isAccentTinted = true,
                                onClick = {
                                    copyAndShareSummary(
                                        context = context,
                                        summaryText = uiState.formattedShareText
                                    )
                                    viewModel.notifyCopiedToClipboard(selectedSummary.rider.name)
                                },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Rider Management Row: Edit Rider & Delete Rider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ZaikiSecondaryButton(
                                text = "✎ Edit Rider",
                                onClick = { viewModel.openEditRiderSheet(selectedSummary.rider) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            )
                            ZaikiSecondaryButton(
                                text = "🗑 Delete Rider",
                                onClick = { viewModel.requestDeleteRider(selectedSummary.rider) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                            )
                        }
                    }
                }

                // Pre-formatted WhatsApp / Clipboard Summary Preview
                item {
                    ZaikiDetailPanel(title = "PRE-FORMATTED WHATSAPP / CLIPBOARD SUMMARY") {
                        Text(
                            text = uiState.formattedShareText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textBody
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        ZaikiSecondaryButton(
                            text = "📋 Copy Summary Text",
                            onClick = {
                                copyToClipboard(context, uiState.formattedShareText)
                                viewModel.notifyCopiedToClipboard(selectedSummary.rider.name)
                            },
                            isAccentTinted = true,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }

                // Detailed Trip Ledger for this Rider
                item {
                    Text(
                        text = "TRIP HISTORY FOR ${selectedSummary.rider.name.uppercase()} (${uiState.selectedRiderTrips.size})",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.highlight
                    )
                }

                items(
                    items = uiState.selectedRiderTrips,
                    key = { it.id }
                ) { trip ->
                    TripLedgerDetailCard(
                        trip = trip,
                        currencySymbol = currency,
                        onTogglePaid = { viewModel.toggleSingleTripPaid(trip) },
                        onLongPress = { onEditTripClick(trip.id) }
                    )
                }
            }
        }
    }

    // 2. Slide-Up Add / Edit Rider Bottom Sheet
    val editorSheet = uiState.riderEditorSheet
    if (editorSheet != null) {
        val isEditing = editorSheet.existingRider != null
        val editorSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var nameInput by remember(editorSheet) {
            mutableStateOf(editorSheet.existingRider?.name.orEmpty())
        }
        var phoneInput by remember(editorSheet) {
            mutableStateOf(editorSheet.existingRider?.phone.orEmpty())
        }

        ModalBottomSheet(
            onDismissRequest = viewModel::dismissRiderEditorSheet,
            sheetState = editorSheetState,
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
                    Column {
                        Text(
                            text = if (isEditing) "UPDATE RIDER PROFILE" else "NEW CARPOOL RIDER",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.highlight
                        )
                        Text(
                            text = if (isEditing) "Edit Rider" else "Add New Rider",
                            style = MaterialTheme.typography.headlineMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    ZaikiSecondaryButton(
                        text = "Close",
                        onClick = viewModel::dismissRiderEditorSheet,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    )
                }

                ZaikiBentoCard {
                    ZaikiFormField(
                        label = "Rider Name",
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        placeholder = "e.g., Alex Tan"
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    ZaikiFormField(
                        label = "Phone Number (Optional)",
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        placeholder = "e.g., +60123456789",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isEditing && editorSheet.existingRider != null) {
                            ZaikiSecondaryButton(
                                text = "🗑 Delete",
                                onClick = {
                                    val target = editorSheet.existingRider
                                    viewModel.dismissRiderEditorSheet()
                                    viewModel.requestDeleteRider(target)
                                },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                        ZaikiPrimaryButton(
                            text = if (isEditing) "Save Changes →" else "Add Rider →",
                            enabled = nameInput.trim().isNotEmpty(),
                            onClick = { viewModel.saveRider(nameInput, phoneInput) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }

    // 3. Delete Rider Confirmation Dialog
    val pendingDelete = uiState.riderPendingDelete
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteRiderConfirmation,
            containerColor = colors.glassFillStrong,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textBody,
            title = {
                Column {
                    Text(
                        text = "CONFIRM DELETION",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.unavailable
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Delete ${pendingDelete.name}?",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                }
            },
            text = {
                Text(
                    text = "This will permanently remove ${pendingDelete.name} and all trips logged under their profile.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textBody
                )
            },
            confirmButton = {
                ZaikiPrimaryButton(
                    text = "Delete Rider",
                    onClick = viewModel::confirmDeleteRider,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                )
            },
            dismissButton = {
                ZaikiSecondaryButton(
                    text = "Cancel",
                    onClick = viewModel::dismissDeleteRiderConfirmation,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        )
    }
}

@Composable
private fun RiderSummaryCard(
    summary: RiderDebtSummary,
    currencySymbol: String,
    onClick: () -> Unit,
    onEditRider: () -> Unit,
    onDeleteRider: () -> Unit
) {
    val colors = ZaikiThemeTokens.colors
    val hasDebt = summary.unpaidBalance > 0.0
    val initials = summary.rider.name
        .trim()
        .split(" ")
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifEmpty { "?" }

    ZaikiBentoCard(
        onClick = onClick,
        accentBorderColor = if (hasDebt) colors.unavailable.copy(alpha = 0.35f) else null
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Circular Monospace Avatar Badge
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(colors.accentWash)
                        .border(1.dp, colors.borderAccent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.highlight,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.rider.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${summary.unpaidTripCount} unpaid · ${summary.totalTripsCount} total trip(s)" +
                            if (!summary.rider.phone.isNullOrBlank()) " · ${summary.rider.phone}" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatMoney(currencySymbol, summary.unpaidBalance),
                    style = MaterialTheme.typography.titleLarge,
                    color = if (hasDebt) colors.unavailable else colors.available,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                ZaikiTagPill(
                    text = if (hasDebt) "OWES →" else "SETTLED ✓",
                    category = if (hasDebt) ZaikiTagCategory.CREATIVE else ZaikiTagCategory.PERSONAL
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        ZaikiHairlineDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // Quick Action Row on every Rider Card: View Ledger, Edit Rider, Delete Rider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tap card for ledger & WhatsApp share",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textFaint,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RiderActionChip(
                    label = "✎ Edit",
                    color = colors.highlight,
                    onClick = onEditRider
                )
                RiderActionChip(
                    label = "🗑 Delete",
                    color = colors.unavailable,
                    onClick = onDeleteRider
                )
            }
        }
    }
}

@Composable
private fun RiderActionChip(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .zaikiSquishClickable(onClick = onClick)
            .clip(ZaikiRadii.Pill)
            .background(color.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), ZaikiRadii.Pill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    val clip = ClipData.newPlainText("TripPawl Carpool Summary", text)
    clipboard?.setPrimaryClip(clip)
}

private fun copyAndShareSummary(context: Context, summaryText: String) {
    copyToClipboard(context, summaryText)
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, summaryText)
    }
    val chooser = Intent.createChooser(sendIntent, "Share Carpool Summary via")
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}
