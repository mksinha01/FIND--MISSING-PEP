package com.findmissingpep.app.domain.repository

import com.findmissingpep.app.domain.model.Sighting
import kotlinx.coroutines.flow.Flow

interface SightingRepository {
    /** Observe cached sightings from Room (real-time via SSE and FCM) */
    fun observeSightings(): Flow<List<Sighting>>

    /** Fetch sightings from network */
    suspend fun fetchSightings(limit: Int = 50, statusFilter: String? = null): Result<List<Sighting>>

    /** Get single sighting by ID */
    suspend fun getSightingById(id: String): Result<Sighting>

    /** Confirm a sighting (closes the case) */
    suspend fun confirmSighting(id: String, reviewNotes: String): Result<Sighting>

    /** Reject a sighting (false alarm) */
    suspend fun rejectSighting(id: String, reviewNotes: String): Result<Sighting>
}
