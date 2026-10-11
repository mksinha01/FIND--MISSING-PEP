package com.findmissingpep.app.data.repository

import com.findmissingpep.app.data.api.FmpApiService
import com.findmissingpep.app.data.local.dao.SightingDao
import com.findmissingpep.app.data.model.dto.SightingReviewDto
import com.findmissingpep.app.data.model.mapper.toDomain
import com.findmissingpep.app.data.model.mapper.toEntity
import com.findmissingpep.app.domain.model.Sighting
import com.findmissingpep.app.domain.repository.SightingRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SightingRepositoryImpl @Inject constructor(
    private val api: FmpApiService,
    private val sightingDao: SightingDao
) : SightingRepository {

    override fun observeSightings(): Flow<List<Sighting>> =
        sightingDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun fetchSightings(
        limit: Int,
        statusFilter: String?
    ): Result<List<Sighting>> = runCatching {
        val response = api.getSightings(limit, statusFilter)
        val sightings = response.map { it.toDomain() }
        sightingDao.insertAll(sightings.map { it.toEntity() })
        sightings
    }

    override suspend fun getSightingById(id: String): Result<Sighting> = runCatching {
        val response = api.getSighting(id)
        val sighting = response.toDomain()
        sightingDao.insert(sighting.toEntity())
        sighting
    }

    override suspend fun confirmSighting(id: String, reviewNotes: String): Result<Sighting> = runCatching {
        val response = api.confirmSighting(id, SightingReviewDto(reviewNotes))
        val sighting = response.toDomain()
        sightingDao.insert(sighting.toEntity())
        sighting
    }

    override suspend fun rejectSighting(id: String, reviewNotes: String): Result<Sighting> = runCatching {
        val response = api.rejectSighting(id, SightingReviewDto(reviewNotes))
        val sighting = response.toDomain()
        sightingDao.insert(sighting.toEntity())
        sighting
    }
}
