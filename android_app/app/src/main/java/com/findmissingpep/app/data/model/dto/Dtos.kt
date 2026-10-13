package com.findmissingpep.app.data.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── User DTOs ──────────────────────────────────────────────────────────────

@Serializable
data class UserResponseDto(
    val id: String,
    @SerialName("firebase_uid") val firebaseUid: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val language: String = "en",
    val role: String = "citizen",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class UserCreateDto(
    @SerialName("firebase_uid") val firebaseUid: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    val language: String = "en"
)

@Serializable
data class UserUpdateDto(
    val name: String? = null,
    val phone: String? = null,
    val language: String? = null,
    @SerialName("fcm_token") val fcmToken: String? = null
)

// ─── Missing Person DTOs ────────────────────────────────────────────────────

@Serializable
data class MissingPersonResponseDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("full_name") val fullName: String,
    val age: Int? = null,
    val gender: String? = null,
    @SerialName("height_cm") val heightCm: Int? = null,
    val description: String? = null,
    @SerialName("last_seen_location") val lastSeenLocation: String? = null,
    @SerialName("last_seen_time") val lastSeenTime: String? = null,
    val status: String = "PROCESSING",
    @SerialName("contact_info") val contactInfo: String? = null,
    @SerialName("fir_number") val firNumber: String? = null,
    @SerialName("fir_police_station") val firPoliceStation: String? = null,
    @SerialName("fir_date") val firDate: String? = null,
    @SerialName("fir_status") val firStatus: String? = null,
    @SerialName("fir_document_path") val firDocumentPath: String? = null,
    @SerialName("fir_rejection_reason") val firRejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("updated_at") val updatedAt: String = "",
    val photos: List<PhotoResponseDto> = emptyList()
)

@Serializable
data class MissingPersonListResponseDto(
    val items: List<MissingPersonResponseDto> = emptyList(),
    val total: Int = 0,
    val page: Int = 1,
    val limit: Int = 20
)

@Serializable
data class MissingPersonUpdateDto(
    @SerialName("full_name") val fullName: String? = null,
    val age: Int? = null,
    val gender: String? = null,
    @SerialName("height_cm") val heightCm: Int? = null,
    val description: String? = null,
    @SerialName("last_seen_location") val lastSeenLocation: String? = null,
    @SerialName("last_seen_time") val lastSeenTime: String? = null,
    @SerialName("contact_info") val contactInfo: String? = null,
    @SerialName("fir_number") val firNumber: String? = null,
    @SerialName("fir_police_station") val firPoliceStation: String? = null,
    @SerialName("fir_date") val firDate: String? = null
)

@Serializable
data class PhotoResponseDto(
    val id: String,
    @SerialName("original_path") val originalPath: String,
    @SerialName("face_crop_path") val faceCropPath: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean = false,
    @SerialName("processing_status") val processingStatus: String = "",
    @SerialName("created_at") val createdAt: String = ""
)

// ─── Sighting DTOs ──────────────────────────────────────────────────────────

@Serializable
data class SightingResponseDto(
    val id: String,
    @SerialName("person_id") val personId: String,
    @SerialName("person_name") val personName: String? = null,
    @SerialName("agent_id") val agentId: String,
    @SerialName("camera_id") val cameraId: String,
    @SerialName("similarity_score") val similarityScore: Double,
    @SerialName("confidence_level") val confidenceLevel: String = "POSSIBLE",
    @SerialName("num_frames_matched") val numFramesMatched: Int? = null,
    @SerialName("face_crop_path") val faceCropPath: String? = null,
    @SerialName("full_frame_path") val fullFramePath: String? = null,
    @SerialName("video_clip_path") val videoClipPath: String? = null,
    @SerialName("camera_location") val cameraLocation: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("detected_at") val detectedAt: String = "",
    val status: String = "PENDING",
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class SightingReviewDto(
    @SerialName("review_notes") val reviewNotes: String
)

// ─── Timeline DTOs ──────────────────────────────────────────────────────────

@Serializable
data class PersonTimelineDto(
    @SerialName("person_id") val personId: String,
    @SerialName("person_name") val personName: String,
    val entries: List<TimelineEntryDto> = emptyList(),
    @SerialName("last_seen_camera") val lastSeenCamera: String? = null,
    @SerialName("last_seen_time") val lastSeenTime: String? = null
)

@Serializable
data class TimelineEntryDto(
    @SerialName("sighting_id") val sightingId: String,
    @SerialName("camera_name") val cameraName: String,
    @SerialName("camera_location") val cameraLocation: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("detected_at") val detectedAt: String,
    @SerialName("similarity_score") val similarityScore: Double,
    val status: String = ""
)

// ─── FIR DTOs ───────────────────────────────────────────────────────────────

@Serializable
data class FIRValidateResponseDto(
    @SerialName("fir_number") val firNumber: String,
    @SerialName("is_valid") val isValid: Boolean,
    val message: String,
    val confidence: Double = 0.0
)

@Serializable
data class FIRVerifyRequestDto(
    val notes: String
)

@Serializable
data class FIRRejectRequestDto(
    val reason: String
)

// ─── Notification DTOs ──────────────────────────────────────────────────────

@Serializable
data class NotificationResponseDto(
    val id: String,
    val title: String,
    val message: String,
    val type: String = "",
    @SerialName("reference_id") val referenceId: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class NotificationListResponseDto(
    val items: List<NotificationResponseDto> = emptyList(),
    val total: Int = 0
)

@Serializable
data class UnreadCountDto(
    @SerialName("unread_count") val unreadCount: Int
)

@Serializable
data class MarkAllReadResponseDto(
    val status: String = "",
    @SerialName("updated_count") val updatedCount: Int = 0
)

@Serializable
data class DeleteResponseDto(
    val status: String = "",
    val message: String = ""
)

// ─── SSE Event Data ─────────────────────────────────────────────────────────

@Serializable
data class SseAlertData(
    @SerialName("sighting_id") val sightingId: String = "",
    @SerialName("notification_id") val notificationId: String? = null,
    @SerialName("person_id") val personId: String = "",
    @SerialName("person_name") val personName: String? = null,
    val title: String? = null,
    val body: String? = null,
    @SerialName("similarity_score") val similarityScore: Double = 0.0,
    val similarity: Double? = null,
    @SerialName("confidence_level") val confidenceLevel: String? = null,
    @SerialName("camera_id") val cameraId: String? = null,
    @SerialName("camera_location") val cameraLocation: String? = null,
    @SerialName("detected_at") val detectedAt: String = "",
    @SerialName("face_crop_path") val faceCropPath: String? = null,
    @SerialName("full_frame_path") val fullFramePath: String? = null,
    @SerialName("video_clip_path") val videoClipPath: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("num_frames_matched") val numFramesMatched: Int? = null
)
