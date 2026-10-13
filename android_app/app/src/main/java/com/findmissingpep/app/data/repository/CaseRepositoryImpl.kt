package com.findmissingpep.app.data.repository

import com.findmissingpep.app.data.api.FmpApiService
import com.findmissingpep.app.data.local.dao.CaseDao
import com.findmissingpep.app.data.model.dto.*
import com.findmissingpep.app.data.model.mapper.toDomain
import com.findmissingpep.app.data.model.mapper.toEntity
import com.findmissingpep.app.domain.model.*
import com.findmissingpep.app.domain.repository.CaseRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaseRepositoryImpl @Inject constructor(
    private val api: FmpApiService,
    private val caseDao: CaseDao,
    private val json: Json
) : CaseRepository {

    override fun observeCases(): Flow<List<MissingCase>> =
        caseDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun submitReport(
        fullName: String,
        age: Int?,
        gender: String?,
        heightCm: Int?,
        description: String?,
        lastSeenLocation: String?,
        lastSeenTime: String?,
        contactInfo: String?,
        firNumber: String?,
        firPoliceStation: String?,
        firDate: String?,
        photos: List<File>,
        firDocument: File?
    ): Result<MissingCase> = runCatching {
        // Build report_data JSON
        val reportDataJson = kotlinx.serialization.json.buildJsonObject {
            put("full_name", fullName)
            age?.let { put("age", it) }
            gender?.let { put("gender", it) }
            heightCm?.let { put("height_cm", it) }
            description?.let { put("description", it) }
            lastSeenLocation?.let { put("last_seen_location", it) }
            lastSeenTime?.let { put("last_seen_time", it) }
            contactInfo?.let { put("contact_info", it) }
            firNumber?.let { put("fir_number", it) }
            firPoliceStation?.let { put("fir_police_station", it) }
            firDate?.let { put("fir_date", it) }
        }.toString()
        val reportDataBody = reportDataJson.toRequestBody("application/json".toMediaType())

        // Build photo parts
        val photoParts = photos.mapIndexed { index, file ->
            val mimeType = when (file.extension.lowercase()) {
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "image/*"
            }
            MultipartBody.Part.createFormData(
                "photos",
                file.name,
                file.asRequestBody(mimeType.toMediaType())
            )
        }

        // Build FIR document part (optional)
        val firPart = firDocument?.let { doc ->
            val mimeType = when (doc.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "png" -> "image/png"
                "jpg", "jpeg" -> "image/jpeg"
                else -> "application/octet-stream"
            }
            MultipartBody.Part.createFormData(
                "fir_document",
                doc.name,
                doc.asRequestBody(mimeType.toMediaType())
            )
        }

        val response = api.submitReport(reportDataBody, photoParts, firPart)
        val case = response.toDomain()
        caseDao.insert(case.toEntity())
        case
    }

    override suspend fun fetchMyCases(
        status: String?,
        page: Int,
        limit: Int
    ): Result<PaginatedList<MissingCase>> = runCatching {
        val response = api.getMyReports(status, page, limit)
        val cases = response.items.map { it.toDomain() }

        // Persist to Room
        caseDao.insertAll(cases.map { it.toEntity() })

        PaginatedList(
            items = cases,
            total = response.total,
            page = response.page,
            limit = response.limit,
            hasMore = (response.page * response.limit) < response.total
        )
    }

    override suspend fun fetchAllCases(
        status: String?,
        page: Int,
        limit: Int
    ): Result<PaginatedList<MissingCase>> = runCatching {
        val response = api.getAllReports(status, page, limit)
        val cases = response.items.map { it.toDomain() }
        caseDao.insertAll(cases.map { it.toEntity() })

        PaginatedList(
            items = cases,
            total = response.total,
            page = response.page,
            limit = response.limit,
            hasMore = (response.page * response.limit) < response.total
        )
    }

    override suspend fun getCaseById(id: String): Result<MissingCase> = runCatching {
        val response = api.getReport(id)
        val case = response.toDomain()
        caseDao.insert(case.toEntity())
        case
    }

    override suspend fun updateCase(id: String, update: Map<String, String?>): Result<MissingCase> = runCatching {
        val body = MissingPersonUpdateDto(
            fullName = update["full_name"],
            description = update["description"],
            lastSeenLocation = update["last_seen_location"],
            contactInfo = update["contact_info"]
        )
        val response = api.updateReport(id, body)
        val case = response.toDomain()
        caseDao.insert(case.toEntity())
        case
    }

    override suspend fun closeCase(id: String): Result<Unit> = runCatching {
        api.deleteReport(id)
        caseDao.deleteById(id)
    }

    override suspend fun uploadPhoto(
        caseId: String,
        photo: File,
        isPrimary: Boolean
    ): Result<Photo> = runCatching {
        val mimeType = when (photo.extension.lowercase()) {
            "png" -> "image/png"
            else -> "image/jpeg"
        }
        val filePart = MultipartBody.Part.createFormData(
            "file", photo.name,
            photo.asRequestBody(mimeType.toMediaType())
        )
        val primaryBody = isPrimary.toString().toRequestBody("text/plain".toMediaType())
        val response = api.uploadPhoto(caseId, filePart, primaryBody)
        response.toDomain()
    }

    override suspend fun getCaseSightings(caseId: String): Result<List<Sighting>> = runCatching {
        api.getReportSightings(caseId).map { it.toDomain() }
    }

    override suspend fun getCaseTimeline(caseId: String): Result<PersonTimeline> = runCatching {
        api.getReportTimeline(caseId).toDomain()
    }

    override suspend fun validateFir(firNumber: String): Result<FirValidation> = runCatching {
        api.validateFir(firNumber).toDomain()
    }

    override suspend fun getPendingFirQueue(): Result<List<MissingCase>> = runCatching {
        api.getPendingFir().map { it.toDomain() }
    }

    override suspend fun verifyFir(caseId: String, notes: String): Result<MissingCase> = runCatching {
        val response = api.verifyFir(caseId, FIRVerifyRequestDto(notes))
        response.toDomain()
    }

    override suspend fun rejectFir(caseId: String, reason: String): Result<MissingCase> = runCatching {
        val response = api.rejectFir(caseId, FIRRejectRequestDto(reason))
        response.toDomain()
    }
}
