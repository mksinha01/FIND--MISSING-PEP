package com.findmissingpep.app.domain.model

/** Authenticated user profile */
data class User(
    val id: String,
    val firebaseUid: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val avatarUrl: String? = null,
    val language: String = "en",
    val role: UserRole = UserRole.CITIZEN,
    val createdAt: String = ""
)

/** Missing person case */
data class MissingCase(
    val id: String,
    val userId: String,
    val fullName: String,
    val age: Int? = null,
    val gender: String? = null,
    val heightCm: Int? = null,
    val description: String? = null,
    val lastSeenLocation: String? = null,
    val lastSeenTime: String? = null,
    val status: CaseStatus = CaseStatus.PROCESSING,
    val contactInfo: String? = null,
    val firNumber: String? = null,
    val firPoliceStation: String? = null,
    val firDate: String? = null,
    val firStatus: String? = null,
    val firDocumentPath: String? = null,
    val firRejectionReason: String? = null,
    val createdAt: String = "",
    val updatedAt: String = "",
    val photos: List<Photo> = emptyList(),
    val sightingsCount: Int = 0
)

/** Reference photo for a case */
data class Photo(
    val id: String,
    val originalPath: String,
    val faceCropPath: String? = null,
    val isPrimary: Boolean = false,
    val processingStatus: String = "",
    val createdAt: String = ""
)

/** CCTV sighting event */
data class Sighting(
    val id: String,
    val personId: String,
    val personName: String? = null,
    val agentId: String,
    val cameraId: String,
    val similarityScore: Double,
    val confidenceLevel: ConfidenceLevel = ConfidenceLevel.POSSIBLE,
    val numFramesMatched: Int? = null,
    val faceCropPath: String? = null,
    val fullFramePath: String? = null,
    val videoClipPath: String? = null,
    val cameraLocation: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val detectedAt: String = "",
    val status: SightingStatus = SightingStatus.PENDING,
    val createdAt: String = ""
)

/** Chronological movement timeline */
data class PersonTimeline(
    val personId: String,
    val personName: String,
    val entries: List<TimelineEntry>,
    val lastSeenCamera: String? = null,
    val lastSeenTime: String? = null
)

/** Single timeline point */
data class TimelineEntry(
    val sightingId: String,
    val cameraName: String,
    val cameraLocation: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val detectedAt: String,
    val similarityScore: Double,
    val status: String
)

/** FIR validation result */
data class FirValidation(
    val firNumber: String,
    val isValid: Boolean,
    val message: String,
    val confidence: Double
)

/** In-app notification */
data class AppNotification(
    val id: String,
    val title: String,
    val message: String,
    val type: String = "",
    val referenceId: String? = null,
    val isRead: Boolean = false,
    val createdAt: String = ""
)

/** Paginated list wrapper */
data class PaginatedList<T>(
    val items: List<T>,
    val total: Int,
    val page: Int,
    val limit: Int,
    val hasMore: Boolean
)
