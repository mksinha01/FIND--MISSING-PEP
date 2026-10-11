package com.findmissingpep.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cases")
data class CaseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "user_id") val userId: String,
    @ColumnInfo(name = "full_name") val fullName: String,
    val age: Int? = null,
    val gender: String? = null,
    @ColumnInfo(name = "height_cm") val heightCm: Int? = null,
    val description: String? = null,
    @ColumnInfo(name = "last_seen_location") val lastSeenLocation: String? = null,
    @ColumnInfo(name = "last_seen_time") val lastSeenTime: String? = null,
    val status: String = "PROCESSING",
    @ColumnInfo(name = "contact_info") val contactInfo: String? = null,
    @ColumnInfo(name = "fir_number") val firNumber: String? = null,
    @ColumnInfo(name = "fir_police_station") val firPoliceStation: String? = null,
    @ColumnInfo(name = "fir_date") val firDate: String? = null,
    @ColumnInfo(name = "fir_status") val firStatus: String? = null,
    @ColumnInfo(name = "fir_document_path") val firDocumentPath: String? = null,
    @ColumnInfo(name = "fir_rejection_reason") val firRejectionReason: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: String = "",
    @ColumnInfo(name = "updated_at") val updatedAt: String = "",
    @ColumnInfo(name = "primary_photo_path") val primaryPhotoPath: String? = null
)

@Entity(tableName = "sightings")
data class SightingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "person_id") val personId: String,
    @ColumnInfo(name = "person_name") val personName: String? = null,
    @ColumnInfo(name = "agent_id") val agentId: String,
    @ColumnInfo(name = "camera_id") val cameraId: String,
    @ColumnInfo(name = "similarity_score") val similarityScore: Double,
    @ColumnInfo(name = "confidence_level") val confidenceLevel: String = "POSSIBLE",
    @ColumnInfo(name = "face_crop_path") val faceCropPath: String? = null,
    @ColumnInfo(name = "full_frame_path") val fullFramePath: String? = null,
    @ColumnInfo(name = "video_clip_path") val videoClipPath: String? = null,
    @ColumnInfo(name = "camera_location") val cameraLocation: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @ColumnInfo(name = "detected_at") val detectedAt: String = "",
    val status: String = "PENDING",
    @ColumnInfo(name = "created_at") val createdAt: String = ""
)

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val message: String,
    val type: String = "",
    @ColumnInfo(name = "reference_id") val referenceId: String? = null,
    @ColumnInfo(name = "is_read") val isRead: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: String = ""
)
