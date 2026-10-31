package com.zaiki.trippawl.domain.model

/**
 * Domain model representing a carpool rider.
 */
data class Rider(
    val id: Long = 0L,
    val name: String,
    val phone: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
