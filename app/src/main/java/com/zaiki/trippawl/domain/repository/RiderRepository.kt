package com.zaiki.trippawl.domain.repository

import com.zaiki.trippawl.domain.model.Rider
import com.zaiki.trippawl.domain.model.RiderDebtSummary
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for managing carpool riders and their aggregated balances.
 */
interface RiderRepository {
    fun observeAllRiders(): Flow<List<Rider>>
    fun observeRiderDebtSummaries(): Flow<List<RiderDebtSummary>>
    suspend fun getRiderById(id: Long): Rider?
    suspend fun getOrCreateRiderByName(name: String, phone: String? = null): Rider
    suspend fun insertRider(rider: Rider): Long
    suspend fun updateRider(rider: Rider)
    suspend fun deleteRider(riderId: Long)
}
