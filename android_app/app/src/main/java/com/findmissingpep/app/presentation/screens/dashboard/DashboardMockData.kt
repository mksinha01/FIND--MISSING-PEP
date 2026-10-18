package com.findmissingpep.app.presentation.screens.dashboard

import com.findmissingpep.app.BuildConfig

/**
 * Data models and real production fallbacks for the FIND-MISSING-PEP Mission Control Dashboard.
 */

data class OperationsMetrics(
    val activeSearches: Int = 2,
    val verifiedToday: Int = 36,
    val edgeCamsOnline: String = "2/2 Online"
)

data class GoldenHourHeroMatch(
    val personId: String = "848355a4-56cc-4942-82bd-c0bb24abfc74",
    val personName: String = "asish",
    val age: Int = 20,
    val urgentTag: String = "LIVE CCTV SIGHTING • Verified Match",
    val similarityScore: Double = 0.785,
    val matchAlgorithm: String = "ArcFace Cosine Match (512-D)",
    val cameraNode: String = "CAM-01 • Main Entrance Gate",
    val detectionProtocol: String = "Temporal M-of-N Filter Passed",
    val lastSeenTimestamp: String = "23:30 IST",
    val firReferencePhoto: String = "${BuildConfig.API_BASE_URL}/uploads/faces/8474460fa67a45cab82111078705e902.jpg",
    val cctvFaceCrop: String = "${BuildConfig.API_BASE_URL}/uploads/evidence/83054da98deb41f894beb6666f8a03ca.jpg"
)

data class RecentSightingItem(
    val id: String,
    val cameraName: String,
    val status: String,
    val confidence: Double,
    val timestamp: String,
    val isVerified: Boolean,
    val imageUrl: String
)

data class WaypointItem(
    val id: Int,
    val name: String,
    val time: String,
    val subtitle: String,
    val isLatest: Boolean,
    val latitude: Double,
    val longitude: Double,
    val heading: String = "Tracked CCTV Vector",
    val velocity: String = "Pedestrian Velocity",
    val snapshotUrl: String? = null
)

data class ForensicDossierData(
    val firNumber: String = "FIR #1234/2026 • PS Durg",
    val personName: String = "asish",
    val age: Int = 20,
    val medicalAlert: String = "Active Investigation • Last seen in red shirt",
    val reportedMissingDate: String = "11-Sep-2026",
    val validationWindowText: String = "PASSED: Temporal ArcFace Validation (79%)",
    val byteTrackId: String = "#CAM-01",
    val cryptographicTimestamp: String = "2026-09-11 23:30:28 UTC",
    val sha256Hash: String = "SHA-256 Verified • Tamper Proof",
    val personPhotoUrl: String? = "${BuildConfig.API_BASE_URL}/uploads/faces/8474460fa67a45cab82111078705e902.jpg",
    val frameMatches: List<FrameMatch> = listOf(
        FrameMatch(1, 94, true),
        FrameMatch(2, 96, true),
        FrameMatch(3, 95, true),
        FrameMatch(4, 94, true)
    )
)

data class FrameMatch(
    val frameIndex: Int,
    val confidence: Int,
    val passed: Boolean
)

object DashboardMockData {
    val metrics = OperationsMetrics()
    val heroMatch = GoldenHourHeroMatch()

    val recentSightings = listOf(
        RecentSightingItem(
            id = "sight-01",
            cameraName = "CAM-01 • Main Entrance Gate",
            status = "Verified",
            confidence = 0.785,
            timestamp = "23:30 IST",
            isVerified = true,
            imageUrl = "${BuildConfig.API_BASE_URL}/uploads/evidence/83054da98deb41f894beb6666f8a03ca.jpg"
        ),
        RecentSightingItem(
            id = "sight-02",
            cameraName = "CAM-02 • Sector 6 Concourse",
            status = "Verified",
            confidence = 0.719,
            timestamp = "15:34 IST",
            isVerified = true,
            imageUrl = "${BuildConfig.API_BASE_URL}/uploads/evidence/33438a56b9644a62a12a6d4ff340e6fa.jpg"
        ),
        RecentSightingItem(
            id = "sight-03",
            cameraName = "CAM-01 • North Arcade",
            status = "Under Review",
            confidence = 0.680,
            timestamp = "14:10 IST",
            isVerified = false,
            imageUrl = "${BuildConfig.API_BASE_URL}/uploads/evidence/83054da98deb41f894beb6666f8a03ca.jpg"
        )
    )

    val waypoints = listOf(
        WaypointItem(
            id = 1,
            name = "Node #1 — Sector 6 Market Gateway",
            time = "14:10 IST",
            subtitle = "Camera Trigger • Initial match",
            isLatest = false,
            latitude = 21.1938,
            longitude = 81.3509,
            heading = "North toward Main Gate",
            velocity = "Walking",
            snapshotUrl = "${BuildConfig.API_BASE_URL}/uploads/evidence/3530494478f444d79a179704e2b914c1.jpg"
        ),
        WaypointItem(
            id = 2,
            name = "Node #2 — Main Entrance Gate",
            time = "23:30 IST",
            subtitle = "Active Verified Vector • Pulsing Cyan Ring",
            isLatest = true,
            latitude = 21.1950,
            longitude = 81.3520,
            heading = "Eastbound toward Plaza",
            velocity = "Pedestrian",
            snapshotUrl = "${BuildConfig.API_BASE_URL}/uploads/evidence/3530494478f444d79a179704e2b914c1.jpg"
        )
    )

    val forensicDossier = ForensicDossierData()
}
