package com.findmissingpep.app.domain.repository

import com.findmissingpep.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.io.File

interface CaseRepository {
    /** Observe cached cases from Room */
    fun observeCases(): Flow<List<MissingCase>>

    /** Submit a new missing person report (multipart) */
    suspend fun submitReport(
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
    ): Result<MissingCase>

    /** Fetch reports for current user (network → Room) */
    suspend fun fetchMyCases(status: String? = null, page: Int = 1, limit: Int = 20): Result<PaginatedList<MissingCase>>

    /** Fetch ALL reports (officer/admin view) */
    suspend fun fetchAllCases(status: String? = null, page: Int = 1, limit: Int = 20): Result<PaginatedList<MissingCase>>

    /** Get single case by ID */
    suspend fun getCaseById(id: String): Result<MissingCase>

    /** Update case metadata */
    suspend fun updateCase(id: String, update: Map<String, String?>): Result<MissingCase>

    /** Soft-delete / close case */
    suspend fun closeCase(id: String): Result<Unit>

    /** Upload additional photo to a case */
    suspend fun uploadPhoto(caseId: String, photo: File, isPrimary: Boolean = false): Result<Photo>

    /** Get sightings for a case */
    suspend fun getCaseSightings(caseId: String): Result<List<Sighting>>

    /** Get movement timeline for a case */
    suspend fun getCaseTimeline(caseId: String): Result<PersonTimeline>

    /** Validate FIR number format */
    suspend fun validateFir(firNumber: String): Result<FirValidation>

    /** Get pending FIR verification queue */
    suspend fun getPendingFirQueue(): Result<List<MissingCase>>

    /** Verify FIR (approve) */
    suspend fun verifyFir(caseId: String, notes: String): Result<MissingCase>

    /** Reject FIR */
    suspend fun rejectFir(caseId: String, reason: String): Result<MissingCase>
}
