package com.zaiki.trippawl.ui.navigation

import com.zaiki.trippawl.ui.components.ZaikiNavIconType
import com.zaiki.trippawl.ui.components.ZaikiNavItem

/**
 * Navigation routes for TripPawl.
 */
sealed class Screen(val route: String) {
    data object Dashboard : Screen("dashboard")
    data object RiderSummary : Screen("rider_summary")
    data object TripHistory : Screen("trip_history")
    data object Settings : Screen("settings")
    data object LogTrip : Screen("log_trip?tripId={tripId}") {
        const val BASE_ROUTE = "log_trip"
        fun createRoute(tripId: Long? = null): String {
            return if (tripId != null && tripId > 0L) {
                "log_trip?tripId=$tripId"
            } else {
                "log_trip?tripId=0"
            }
        }
    }

    companion object {
        val bottomNavItems: List<ZaikiNavItem> by lazy {
            listOf(
                ZaikiNavItem(
                    route = Dashboard.route,
                    label = "Home",
                    iconType = ZaikiNavIconType.HOME
                ),
                ZaikiNavItem(
                    route = RiderSummary.route,
                    label = "Riders",
                    iconType = ZaikiNavIconType.RIDERS
                ),
                ZaikiNavItem(
                    route = TripHistory.route,
                    label = "History",
                    iconType = ZaikiNavIconType.HISTORY
                ),
                ZaikiNavItem(
                    route = Settings.route,
                    label = "Settings",
                    iconType = ZaikiNavIconType.SETTINGS
                )
            )
        }
    }
}
