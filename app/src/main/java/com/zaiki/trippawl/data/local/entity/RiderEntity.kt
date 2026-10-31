package com.zaiki.trippawl.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.zaiki.trippawl.domain.model.Rider

/**
 * Room Entity representing a saved carpool rider.
 */
@Entity(
    tableName = "riders",
    indices = [Index(value = ["name"])]
)
data class RiderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomainModel(): Rider = Rider(
        id = id,
        name = name,
        phone = phone,
        createdAt = createdAt
    )

    companion object {
        fun fromDomainModel(rider: Rider): RiderEntity = RiderEntity(
            id = rider.id,
            name = rider.name.trim(),
            phone = rider.phone?.trim()?.takeIf { it.isNotEmpty() },
            createdAt = rider.createdAt
        )
    }
}
