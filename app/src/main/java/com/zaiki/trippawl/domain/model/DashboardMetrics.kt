package com.zaiki.trippawl.domain.model

/**
 * Aggregated metrics displayed on the Dashboard (Home Screen).
 */
data class DashboardMetrics(
    val totalUncollectedDebt: Double = 0.0,
    val totalFuelSpentCurrentMonth: Double = 0.0,
    val totalSettledPayments: Double = 0.0,
    val unpaidTripCount: Int = 0,
    val currentMonthTripCount: Int = 0,
    val settledTripCount: Int = 0,
    val totalWaivedAmount: Double = 0.0,
    val waivedTripCount: Int = 0
)
