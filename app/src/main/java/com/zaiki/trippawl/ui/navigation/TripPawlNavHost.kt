package com.zaiki.trippawl.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zaiki.trippawl.ui.components.ZaikiAuroraBackground
import com.zaiki.trippawl.ui.components.ZaikiNavPill
import com.zaiki.trippawl.ui.dashboard.DashboardScreen
import com.zaiki.trippawl.ui.dashboard.DashboardViewModel
import com.zaiki.trippawl.ui.history.TripHistoryScreen
import com.zaiki.trippawl.ui.history.TripHistoryViewModel
import com.zaiki.trippawl.ui.logtrip.LogTripBottomSheet
import com.zaiki.trippawl.ui.logtrip.LogTripScreen
import com.zaiki.trippawl.ui.logtrip.LogTripViewModel
import com.zaiki.trippawl.ui.riders.RiderSummaryScreen
import com.zaiki.trippawl.ui.riders.RiderSummaryViewModel
import com.zaiki.trippawl.ui.settings.SettingsScreen
import com.zaiki.trippawl.ui.settings.SettingsViewModel

private data class ActiveTripSheetRequest(
    val tripId: Long? = null,
    val requestKey: Long = System.currentTimeMillis()
)

/**
 * Root Jetpack Compose Navigation host wrapped in the Zaiki Aurora background
 * with the floating frosted-glass [ZaikiNavPill] and slide-up [LogTripBottomSheet].
 */
@Composable
fun TripPawlNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val showBottomPill = currentRoute != null && !currentRoute.startsWith(Screen.LogTrip.BASE_ROUTE)

    // State controlling the slide-up Log / Edit Trip Bottom Sheet
    var activeTripSheet by remember { mutableStateOf<ActiveTripSheetRequest?>(null) }

    val openLogNewTripSheet: () -> Unit = {
        activeTripSheet = ActiveTripSheetRequest(tripId = null)
    }
    val openEditTripSheet: (Long) -> Unit = { tripId ->
        activeTripSheet = ActiveTripSheetRequest(tripId = tripId)
    }

    val navigateToTab: (String) -> Unit = { targetRoute ->
        if (currentRoute != targetRoute) {
            navController.navigate(targetRoute) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val backdropGraphicsLayer = rememberGraphicsLayer()
    var backdropOriginInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                backdropOriginInRoot = coords.positionInRoot()
            }
    ) {
        // 1. Record Aurora Background + Screen Content into backdropGraphicsLayer for real-time nav blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    backdropGraphicsLayer.record {
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(backdropGraphicsLayer)
                }
        ) {
            ZaikiAuroraBackground(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Dashboard.route,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable(route = Screen.Dashboard.route) {
                            val viewModel: DashboardViewModel = hiltViewModel()
                            DashboardScreen(
                                viewModel = viewModel,
                                onLogNewTripClick = openLogNewTripSheet,
                                onEditTripClick = openEditTripSheet,
                                onViewAllTripsClick = {
                                    navigateToTab(Screen.TripHistory.route)
                                },
                                onViewRidersClick = {
                                    navigateToTab(Screen.RiderSummary.route)
                                }
                            )
                        }

                        composable(
                            route = Screen.LogTrip.route,
                            arguments = listOf(
                                navArgument("tripId") {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                }
                            )
                        ) {
                            val viewModel: LogTripViewModel = hiltViewModel()
                            LogTripScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }

                        composable(route = Screen.TripHistory.route) {
                            val viewModel: TripHistoryViewModel = hiltViewModel()
                            TripHistoryScreen(
                                viewModel = viewModel,
                                onEditTrip = openEditTripSheet,
                                onLogNewTripClick = openLogNewTripSheet
                            )
                        }

                        composable(route = Screen.RiderSummary.route) {
                            val viewModel: RiderSummaryViewModel = hiltViewModel()
                            RiderSummaryScreen(
                                viewModel = viewModel,
                                onLogNewTripClick = openLogNewTripSheet,
                                onEditTripClick = openEditTripSheet
                            )
                        }

                        composable(route = Screen.Settings.route) {
                            val viewModel: SettingsViewModel = hiltViewModel()
                            SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }

        // 2. Floating Frosted Glass Navigation Bar with live backdrop blur
        if (showBottomPill) {
            ZaikiNavPill(
                items = Screen.bottomNavItems,
                currentRoute = currentRoute,
                onNavigate = navigateToTab,
                backdropGraphicsLayer = backdropGraphicsLayer,
                backdropOriginInRoot = backdropOriginInRoot,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
            )
        }

        // 3. Slide-Up Log / Edit Trip Bottom Sheet (similar to Rider Detail / Edit Rider sheet)
        val sheetRequest = activeTripSheet
        if (sheetRequest != null) {
            val logTripViewModel: LogTripViewModel = hiltViewModel()
            LogTripBottomSheet(
                tripId = sheetRequest.tripId,
                requestKey = sheetRequest.requestKey,
                viewModel = logTripViewModel,
                onDismiss = { activeTripSheet = null }
            )
        }
    }
}
