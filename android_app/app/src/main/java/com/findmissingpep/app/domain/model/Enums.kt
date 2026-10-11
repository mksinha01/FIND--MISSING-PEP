package com.findmissingpep.app.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Case lifecycle status */
@Serializable
enum class CaseStatus(val value: String) {
    @SerialName("PROCESSING") PROCESSING("PROCESSING"),
    @SerialName("PENDING_FIR_REVIEW") PENDING_FIR_REVIEW("PENDING_FIR_REVIEW"),
    @SerialName("ACTIVE") ACTIVE("ACTIVE"),
    @SerialName("FOUND") FOUND("FOUND"),
    @SerialName("CLOSED") CLOSED("CLOSED"),
    @SerialName("FIR_REJECTED") FIR_REJECTED("FIR_REJECTED"),
    @SerialName("REJECTED_NO_FACE") REJECTED_NO_FACE("REJECTED_NO_FACE");

    companion object {
        fun fromValue(value: String): CaseStatus =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: PROCESSING
    }
}

/** Sighting verification status */
@Serializable
enum class SightingStatus(val value: String) {
    @SerialName("PENDING") PENDING("PENDING"),
    @SerialName("CONFIRMED") CONFIRMED("CONFIRMED"),
    @SerialName("REJECTED") REJECTED("REJECTED");

    companion object {
        fun fromValue(value: String): SightingStatus =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: PENDING
    }
}

/** Match confidence tier */
@Serializable
enum class ConfidenceLevel(val value: String) {
    @SerialName("POSSIBLE") POSSIBLE("POSSIBLE"),
    @SerialName("PROBABLE") PROBABLE("PROBABLE"),
    @SerialName("CONFIRMED") CONFIRMED("CONFIRMED");

    companion object {
        fun fromValue(value: String): ConfidenceLevel =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: POSSIBLE
    }
}

/** App language */
enum class LanguageCode(val code: String) {
    ENGLISH("en"),
    HINDI("hi")
}

/** User roles */
enum class UserRole(val value: String) {
    CITIZEN("citizen"),
    OFFICER("officer"),
    ADMIN("admin");

    companion object {
        fun fromValue(value: String): UserRole =
            entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: CITIZEN
    }
}
