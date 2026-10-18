package com.findmissingpep.app.data.model.mapper

import com.findmissingpep.app.data.local.entity.CaseEntity
import com.findmissingpep.app.data.local.entity.NotificationEntity
import com.findmissingpep.app.data.local.entity.SightingEntity
import com.findmissingpep.app.data.model.dto.*
import com.findmissingpep.app.domain.model.*

// ─── DTO → Domain ───────────────────────────────────────────────────────────

fun UserResponseDto.toDomain() = User(
    id = id,
    firebaseUid = firebaseUid,
    name = name,
    email = email,
    phone = phone,
    avatarUrl = avatarUrl,
    language = language,
    role = UserRole.fromValue(role),
    createdAt = createdAt
)

fun MissingPersonResponseDto.toDomain() = MissingCase(
    id = id,
    userId = userId,
    fullName = fullName,
    age = age,
    gender = gender,
    heightCm = heightCm,
    description = description,
    lastSeenLocation = lastSeenLocation,
    lastSeenTime = lastSeenTime,
    status = CaseStatus.fromValue(status),
    contactInfo = contactInfo,
    firNumber = firNumber,
    firPoliceStation = firPoliceStation,
    firDate = firDate,
    firStatus = firStatus,
    firDocumentPath = firDocumentPath,
    firRejectionReason = firRejectionReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    photos = photos.map { it.toDomain() }
)

fun PhotoResponseDto.toDomain() = Photo(
    id = id,
    originalPath = originalPath,
    faceCropPath = faceCropPath,
    isPrimary = isPrimary,
    processingStatus = processingStatus,
    createdAt = createdAt
)

fun SightingResponseDto.toDomain() = Sighting(
    id = id,
    personId = personId,
    personName = personName,
    agentId = agentId,
    cameraId = cameraId,
    similarityScore = similarityScore,
    confidenceLevel = ConfidenceLevel.fromValue(confidenceLevel),
    numFramesMatched = numFramesMatched,
    faceCropPath = faceCropPath,
    fullFramePath = fullFramePath,
    videoClipPath = videoClipPath,
    cameraLocation = cameraLocation,
    latitude = latitude,
    longitude = longitude,
    detectedAt = detectedAt,
    status = SightingStatus.fromValue(status),
    createdAt = createdAt
)

fun PersonTimelineDto.toDomain() = PersonTimeline(
    personId = personId,
    personName = personName,
    entries = entries.map { it.toDomain() },
    lastSeenCamera = lastSeenCamera,
    lastSeenTime = lastSeenTime
)

fun TimelineEntryDto.toDomain() = TimelineEntry(
    sightingId = sightingId,
    cameraName = cameraName,
    cameraLocation = cameraLocation,
    latitude = latitude,
    longitude = longitude,
    detectedAt = detectedAt,
    similarityScore = similarityScore,
    status = status
)

fun FIRValidateResponseDto.toDomain() = FirValidation(
    firNumber = firNumber,
    isValid = isValid,
    message = message,
    confidence = confidence
)

fun NotificationResponseDto.toDomain() = AppNotification(
    id = id,
    title = title,
    message = message,
    type = type,
    referenceId = referenceId,
    isRead = isRead,
    createdAt = createdAt
)

// ─── Domain → Entity (for Room caching) ─────────────────────────────────────

fun MissingCase.toEntity() = CaseEntity(
    id = id,
    userId = userId,
    fullName = fullName,
    age = age,
    gender = gender,
    heightCm = heightCm,
    description = description,
    lastSeenLocation = lastSeenLocation,
    lastSeenTime = lastSeenTime,
    status = status.value,
    contactInfo = contactInfo,
    firNumber = firNumber,
    firPoliceStation = firPoliceStation,
    firDate = firDate,
    firStatus = firStatus,
    firDocumentPath = firDocumentPath,
    firRejectionReason = firRejectionReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    primaryPhotoPath = photos.firstOrNull { it.isPrimary }?.faceCropPath
        ?: photos.firstOrNull { it.isPrimary }?.originalPath
        ?: photos.firstOrNull()?.faceCropPath
        ?: photos.firstOrNull()?.originalPath
)

fun Sighting.toEntity() = SightingEntity(
    id = id,
    personId = personId,
    personName = personName,
    agentId = agentId,
    cameraId = cameraId,
    similarityScore = similarityScore,
    confidenceLevel = confidenceLevel.value,
    faceCropPath = faceCropPath,
    fullFramePath = fullFramePath,
    videoClipPath = videoClipPath,
    cameraLocation = cameraLocation,
    latitude = latitude,
    longitude = longitude,
    detectedAt = detectedAt,
    status = status.value,
    createdAt = createdAt
)

fun AppNotification.toEntity() = NotificationEntity(
    id = id,
    title = title,
    message = message,
    type = type,
    referenceId = referenceId,
    isRead = isRead,
    createdAt = createdAt
)

// ─── Entity → Domain ────────────────────────────────────────────────────────

fun CaseEntity.toDomain() = MissingCase(
    id = id,
    userId = userId,
    fullName = fullName,
    age = age,
    gender = gender,
    heightCm = heightCm,
    description = description,
    lastSeenLocation = lastSeenLocation,
    lastSeenTime = lastSeenTime,
    status = CaseStatus.fromValue(status),
    contactInfo = contactInfo,
    firNumber = firNumber,
    firPoliceStation = firPoliceStation,
    firDate = firDate,
    firStatus = firStatus,
    firDocumentPath = firDocumentPath,
    firRejectionReason = firRejectionReason,
    createdAt = createdAt,
    updatedAt = updatedAt,
    photos = if (!primaryPhotoPath.isNullOrBlank()) {
        listOf(
            Photo(
                id = id,
                originalPath = primaryPhotoPath,
                faceCropPath = primaryPhotoPath,
                isPrimary = true
            )
        )
    } else emptyList()
)

fun SightingEntity.toDomain() = Sighting(
    id = id,
    personId = personId,
    personName = personName,
    agentId = agentId,
    cameraId = cameraId,
    similarityScore = similarityScore,
    confidenceLevel = ConfidenceLevel.fromValue(confidenceLevel),
    faceCropPath = faceCropPath,
    fullFramePath = fullFramePath,
    videoClipPath = videoClipPath,
    cameraLocation = cameraLocation,
    latitude = latitude,
    longitude = longitude,
    detectedAt = detectedAt,
    status = SightingStatus.fromValue(status),
    createdAt = createdAt
)

fun NotificationEntity.toDomain() = AppNotification(
    id = id,
    title = title,
    message = message,
    type = type,
    referenceId = referenceId,
    isRead = isRead,
    createdAt = createdAt
)

fun SseAlertData.toDomain(): Sighting {
    val score = if (similarityScore > 0.0) similarityScore else (similarity ?: 0.0)
    val conf = confidenceLevel?.let { ConfidenceLevel.fromValue(it) } ?: when {
        score >= 0.85 -> ConfidenceLevel.CONFIRMED
        score >= 0.70 -> ConfidenceLevel.PROBABLE
        else -> ConfidenceLevel.POSSIBLE
    }
    return Sighting(
        id = sightingId,
        personId = personId,
        personName = personName ?: title,
        agentId = "",
        cameraId = cameraId ?: "CAM-ALERT",
        similarityScore = score,
        confidenceLevel = conf,
        numFramesMatched = numFramesMatched,
        faceCropPath = faceCropPath,
        fullFramePath = fullFramePath,
        videoClipPath = videoClipPath,
        cameraLocation = cameraLocation,
        latitude = latitude,
        longitude = longitude,
        detectedAt = detectedAt,
        status = if (score >= 0.85) SightingStatus.CONFIRMED else SightingStatus.PENDING,
        createdAt = detectedAt
    )
}

