package com.zaiki.trippawl.data.repository

import com.zaiki.trippawl.data.local.dao.RiderDao
import com.zaiki.trippawl.data.local.entity.RiderEntity
import com.zaiki.trippawl.domain.model.Rider
import com.zaiki.trippawl.domain.model.RiderDebtSummary
import com.zaiki.trippawl.domain.repository.RiderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [RiderRepository] backed by Room's [RiderDao].
 */
@Singleton
class RiderRepositoryImpl @Inject constructor(
    private val riderDao: RiderDao
) : RiderRepository {

    override fun observeAllRiders(): Flow<List<Rider>> =
        riderDao.observeAllRiders().map { list -> list.map { it.toDomainModel() } }

    override fun observeRiderDebtSummaries(): Flow<List<RiderDebtSummary>> =
        riderDao.observeRiderDebtSummaries().map { list -> list.map { it.toDomainModel() } }

    override suspend fun getRiderById(id: Long): Rider? =
        riderDao.getRiderById(id)?.toDomainModel()

    override suspend fun getOrCreateRiderByName(name: String, phone: String?): Rider {
        val trimmedName = name.trim()
        val existing = riderDao.getRiderByNameIgnoreCase(trimmedName)
        if (existing != null) {
            // Update phone if newly provided and previously blank
            if (!phone.isNullOrBlank() && existing.phone.isNullOrBlank()) {
                val updated = existing.copy(phone = phone.trim())
                riderDao.updateRider(updated)
                return updated.toDomainModel()
            }
            return existing.toDomainModel()
        }
        val newEntity = RiderEntity(
            name = trimmedName,
            phone = phone?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = System.currentTimeMillis()
        )
        val generatedId = riderDao.insertRider(newEntity)
        return newEntity.copy(id = generatedId).toDomainModel()
    }

    override suspend fun insertRider(rider: Rider): Long =
        riderDao.insertRider(RiderEntity.fromDomainModel(rider))

    override suspend fun updateRider(rider: Rider) =
        riderDao.updateRider(RiderEntity.fromDomainModel(rider))

    override suspend fun deleteRider(riderId: Long) =
        riderDao.deleteRiderById(riderId)
}
