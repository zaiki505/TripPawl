package com.zaiki.trippawl.data.repository

import com.zaiki.trippawl.data.local.dao.TripDao
import com.zaiki.trippawl.data.local.entity.TripEntity
import com.zaiki.trippawl.domain.model.DashboardMetrics
import com.zaiki.trippawl.domain.model.RecurrenceUnit
import com.zaiki.trippawl.domain.model.Trip
import com.zaiki.trippawl.domain.model.computeNextOccurrenceMillis
import com.zaiki.trippawl.domain.repository.TripRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [TripRepository] backed by Room's [TripDao].
 */
@Singleton
class TripRepositoryImpl @Inject constructor(
    private val tripDao: TripDao
) : TripRepository {

    override fun observeRecentTrips(limit: Int): Flow<List<Trip>> =
        tripDao.observeRecentTripsWithRider(limit).map { list -> list.map { it.toDomainModel() } }

    override fun observeAllTrips(): Flow<List<Trip>> =
        tripDao.observeAllTripsWithRider().map { list -> list.map { it.toDomainModel() } }

    override fun observeTripsForRider(riderId: Long): Flow<List<Trip>> =
        tripDao.observeTripsForRider(riderId).map { list -> list.map { it.toDomainModel() } }

    override fun observeDashboardMetrics(
        monthStartMillis: Long,
        monthEndMillis: Long
    ): Flow<DashboardMetrics> = tripDao.observeDashboardMetrics(monthStartMillis, monthEndMillis)

    override suspend fun getTripById(tripId: Long): Trip? =
        tripDao.getTripWithRiderById(tripId)?.toDomainModel()

    override suspend fun getTripsByGroupId(tripGroupId: String): List<Trip> =
        if (tripGroupId.isBlank()) {
            emptyList()
        } else {
            tripDao.getTripsWithRiderByGroupId(tripGroupId).map { it.toDomainModel() }
        }

    override suspend fun insertTrip(trip: Trip): Long =
        tripDao.insertTrip(TripEntity.fromDomainModel(trip))

    override suspend fun updateTrip(trip: Trip) =
        tripDao.updateTrip(TripEntity.fromDomainModel(trip))

    override suspend fun deleteTrip(tripId: Long) =
        tripDao.deleteTripById(tripId)

    override suspend fun deleteTripsByGroupId(tripGroupId: String) {
        if (tripGroupId.isNotBlank()) {
            tripDao.deleteTripsByGroupId(tripGroupId)
        }
    }

    override suspend fun toggleTripPaymentStatus(tripId: Long, isPaid: Boolean) {
        val settledDate = if (isPaid) System.currentTimeMillis() else null
        tripDao.updateTripPaymentStatus(tripId = tripId, isPaid = isPaid, settledDate = settledDate)
    }

    override suspend fun toggleGroupPaymentStatus(tripGroupId: String, isPaid: Boolean) {
        if (tripGroupId.isBlank()) return
        val settledDate = if (isPaid) System.currentTimeMillis() else null
        tripDao.updateGroupPaymentStatus(
            tripGroupId = tripGroupId,
            isPaid = isPaid,
            settledDate = settledDate
        )
    }

    override suspend fun setTripWaived(tripId: Long, isWaived: Boolean) {
        val settledDate = if (isWaived) System.currentTimeMillis() else null
        tripDao.updateTripWaivedStatus(tripId = tripId, isWaived = isWaived, settledDate = settledDate)
    }

    override suspend fun setGroupWaived(tripGroupId: String, isWaived: Boolean): Int {
        if (tripGroupId.isBlank()) return 0
        return if (isWaived) {
            tripDao.waiveUnpaidSharesInGroup(tripGroupId, System.currentTimeMillis())
        } else {
            tripDao.unwaiveSharesInGroup(tripGroupId)
        }
    }

    override suspend fun settleAllUnpaidTripsForRider(riderId: Long): Int {
        val now = System.currentTimeMillis()
        return tripDao.settleAllUnpaidTripsForRider(riderId = riderId, settledDate = now)
    }

    override suspend fun processDueRecurringTrips(nowMillis: Long): Int {
        val dueRows = tripDao.getDueRecurringTripsWithRider(nowMillis).map { it.toDomainModel() }
        if (dueRows.isEmpty()) return 0

        val dueGroups = dueRows.groupBy { trip ->
            trip.tripGroupId.takeIf { it.isNotBlank() } ?: "single_${trip.id}"
        }
        var spawnedGroupsCount = 0

        for ((_, shares) in dueGroups) {
            val first = shares.firstOrNull() ?: continue
            val unit = first.recurrenceUnit
            val interval = first.recurrenceInterval.coerceAtLeast(1)
            if (!first.isRecurring || unit == RecurrenceUnit.NONE) {
                shares.forEach { share ->
                    tripDao.updateTripRecurrenceNextDate(share.id, isRecurring = false, nextOccurrenceDate = null)
                }
                continue
            }

            // Clear the old template's pending nextOccurrenceDate so only the newest spawned occurrence schedules the next one
            shares.forEach { share ->
                tripDao.updateTripRecurrenceNextDate(share.id, isRecurring = true, nextOccurrenceDate = null)
            }

            var currentOccurrenceMillis = first.nextOccurrenceDate ?: continue
            var safetyCounter = 0

            while (currentOccurrenceMillis <= nowMillis && safetyCounter < 12) {
                safetyCounter++
                val nextFollowingMillis = computeNextOccurrenceMillis(
                    fromMillis = currentOccurrenceMillis,
                    interval = interval,
                    unit = unit
                )
                val isLastCatchup = nextFollowingMillis == null || nextFollowingMillis > nowMillis
                val newGroupId = "grp_rec_${currentOccurrenceMillis}_${UUID.randomUUID().toString().take(8)}"
                val durationDelta = if (first.arrivalDate != null && first.arrivalDate > first.tripDate) {
                    first.arrivalDate - first.tripDate
                } else {
                    null
                }

                shares.forEach { templateShare ->
                    val spawned = templateShare.copy(
                        id = 0L,
                        tripGroupId = newGroupId,
                        tripDate = currentOccurrenceMillis,
                        arrivalDate = durationDelta?.let { currentOccurrenceMillis + it },
                        isPaid = false,
                        settledDate = null,
                        isRecurring = true,
                        recurrenceInterval = interval,
                        recurrenceUnit = unit,
                        nextOccurrenceDate = if (isLastCatchup) nextFollowingMillis else null
                    )
                    tripDao.insertTrip(TripEntity.fromDomainModel(spawned))
                }
                spawnedGroupsCount++

                if (nextFollowingMillis == null) break
                currentOccurrenceMillis = nextFollowingMillis
            }
        }

        return spawnedGroupsCount
    }
}
