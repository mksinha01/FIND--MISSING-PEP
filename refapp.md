# FIND-MISSING-PEP — Kotlin Native Android App Reference & Build Specification (`refapp.md`)

> **Master Reference & Prompt Document**: This document is the single source of truth for planning, architecting, and implementing the native Android application in **Kotlin** for **FIND-MISSING-PEP** (AI-Based Missing Person Detection & Real-Time CCTV Monitoring System).

---

## Table of Contents

1. [Executive Summary & System Overview](#1-executive-summary--system-overview)
2. [Master AI/Developer Implementation Prompt](#2-master-aideveloper-implementation-prompt)
3. [User Perspectives & Persona Requirements](#3-user-perspectives--persona-requirements)
4. [Complete Backend API Contract & Endpoints](#4-complete-backend-api-contract--endpoints)
5. [Android Tech Stack & System Architecture](#5-android-tech-stack--system-architecture)
6. [Data Models, Enums & Kotlin DTOs](#6-data-models-enums--kotlin-dtos)
7. [Screen-by-Screen UI/UX Specification](#7-screen-by-screen-uiux-specification)
8. [Real-Time Alerting: SSE Stream & FCM Push Notifications](#8-real-time-alerting-sse-stream--fcm-push-notifications)
9. [Forensic Inspector & Interactive Breadcrumb Mapping](#9-forensic-inspector--interactive-breadcrumb-mapping)
10. [Offline-First Local Caching & Synchronization](#10-offline-first-local-caching--synchronization)
11. [Bilingual Localization (English / Hindi)](#11-bilingual-localization-english--hindi)
12. [Step-by-Step Implementation Roadmap](#12-step-by-step-implementation-roadmap)

---

## 1. Executive Summary & System Overview

### The Mission
**FIND-MISSING-PEP** bridges the gap between passive urban CCTV feeds and real-time missing-person recovery. In India alone, over 100,000 individuals go missing each year, with up to 30% remaining untraced due to delayed reporting and resource-constrained manual searches.

The system decentralizes AI inference:
- **Edge CCTV Monitoring Stations**: Ingest live RTSP video feeds, detect faces using SCRFD-10GF (down to $20 \times 20\text{ px}$), track identities across optical occlusions using ByteTrack (8-D Kalman filter), extract 512-D ArcFace metric embeddings, and perform sub-millisecond vector lookups via FAISS-CPU (`IndexFlatIP`).
- **Temporal Verification Filter**: Eliminates false positives by enforcing an $M$-of-$N$ temporal sliding window (e.g., minimum 3 positive sightings within a 5-second window at $\ge 0.60$ cosine similarity under the same track ID).
- **Central FastAPI Core**: Manages missing person cases, processes reference photos, executes FIR (First Information Report) validations, dispatches Server-Sent Events (SSE), and broadcasts Firebase Cloud Messaging (FCM) push alerts.
- **Native Kotlin Android App (`refapp`)**: Acts as the real-time mobile command center for search teams and citizen reporters in the field.

---

## 2. Master AI/Developer Implementation Prompt

Use the prompt below to generate or guide the implementation of the Kotlin application:

```markdown
# PROMPT: Architect and Build Native Android App in Kotlin for FIND-MISSING-PEP

You are a Principal Android Architect and Senior Kotlin Engineer. Your objective is to build a production-grade, offline-first native Android application using Kotlin and Jetpack Compose for "FIND-MISSING-PEP" (AI-Based Missing Person Detection & Real-Time CCTV Monitoring System).

### Requirements & Constraints:
1. Target Architecture: Clean Architecture + MVI (Model-View-Intent) with Unidirectional Data Flow (UDF).
2. UI Toolkit: 100% Jetpack Compose with Material Design 3, Edge-to-Edge layout, and dark glassmorphic styling tokens.
3. Language & Runtime: Kotlin 2.0+, Kotlin Coroutines, StateFlow, Kotlinx Serialization.
4. Dependency Injection: Dagger Hilt.
5. Networking: Retrofit 2 + OkHttp 4 with custom AuthInterceptor, Multipart form handling, and OkHttp EventSource for Server-Sent Events (SSE).
6. Local Persistence: Room Database (WAL mode enabled) + EncryptedDataStore.
7. Media & GIS: Coil 3 for authenticated image loading; Media3 / ExoPlayer for 5-second evidence clips; Google Maps Compose (or MapLibre) for chronological CCTV breadcrumb movement mapping.
8. Push Notifications: Firebase Cloud Messaging (FCM) with custom notification channels, heads-up alert styles, and deep links into the Forensic Inspector screen.
9. Bilingual Localization: Instant runtime switching between English and Hindi (हिन्दी) without app restart.

Read the API contracts, data models, and screen specifications in `refapp.md` and generate the complete implementation structure.
```

---

## 3. User Perspectives & Persona Requirements

### Persona A: Citizen Reporter & Grieving Family Member
- **Instant Reporting**: File a missing person report in under 2 minutes directly from the phone.
- **Reference Photo Ingestion**: Select 1 to 5 clear photos of the missing person with client-side EXIF rotation and JPEG compression before upload.
- **FIR Legal Integration**: Input Police Station details, FIR Number (validated in real-time against Indian police formats), and attach scanned FIR documents.
- **Real-Time Peace of Mind**: Receive high-priority push notifications the second any connected CCTV camera detects a temporal match.
- **Case Progress Tracking**: View current status (`PROCESSING`, `PENDING_FIR_REVIEW`, `ACTIVE`, `FOUND`, `CLOSED`, or `FIR_REJECTED`) and track sighting timelines.

### Persona B: Field Police Officer & Search Personnel
- **Sub-Second Field Alerts**: Receive audible chime alerts and heads-up notifications when a suspect match occurs nearby.
- **Side-by-Side Forensic Inspection**:
  - Compare reference photo directly with the 112×112 aligned CCTV crop.
  - Inspect the full 1080p surveillance scene to understand surroundings.
  - Play the 5-second circular pre/post-match MP4 video buffer to verify gait, clothing, and companions.
- **One-Tap Sighting Disposition**:
  - `CONFIRM`: Marks match verified, closes the search case across the city, and updates status to `FOUND`.
  - `REJECT`: Flags false alarm with review notes.
- **Breadcrumb Navigation**: View chronological sightings mapped on GPS coordinates with one-tap turn-by-turn navigation to the last seen CCTV camera.

### Persona C: Law Enforcement Administrator & Station In-Charge
- **Overview Dashboard**: Monitor active missing cases, live CCTV cameras, and recent sightings city-wide.
- **FIR Verification Queue**: Review and verify citizen-submitted FIR documents to activate ArcFace embeddings for active CCTV scanning.

---

## 4. Complete Backend API Contract & Endpoints

Base Backend URL: `http://<HOST>:8000` (or production HTTPS domain).

### 4.1 Authentication & User Management

| Method | Endpoint | Auth Required | Request Body / Params | Response Model | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/auth/verify` | Bearer Token | Header: `Authorization: Bearer <JWT>` | `UserResponse` | Verifies Firebase ID token; auto-provisions user on first login. |
| `GET` | `/api/auth/me` | Bearer Token | — | `UserResponse` | Fetches profile of authenticated user. |
| `POST` | `/api/users/` | Bearer Token | `UserCreate` (JSON) | `UserResponse` | Explicit profile registration/sync. |
| `GET` | `/api/users/me` | Bearer Token | — | `UserResponse` | Retrieves user profile data. |
| `PUT` | `/api/users/me` | Bearer Token | `UserUpdate` (JSON) | `UserResponse` | Updates name, phone, language, and **`fcm_token`** (mandatory for push notifications). |

### 4.2 Missing Person Reports (`/api/reports`)

| Method | Endpoint | Auth Required | Request Type | Response Model | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `POST` | `/api/reports/` | Bearer Token | `multipart/form-data` | `MissingPersonResponse` | **Atomic Submission**: `report_data` (JSON) or form fields + `photos` (1–5 files) + `fir_document` (optional PDF/Image). Extracts 512-D ArcFace vectors and verifies FIR format. |
| `GET` | `/api/reports/` | Bearer Token | Query: `status`, `page`, `limit` | `MissingPersonListResponse` | Lists reports submitted by the logged-in user. |
| `GET` | `/api/reports/all` | Bearer Token | Query: `status`, `page`, `limit` | `MissingPersonListResponse` | Lists all reports across the system (for search officers and dashboard). |
| `GET` | `/api/reports/{id}` | Bearer Token | Path: `id` (UUID) | `MissingPersonResponse` | Retrieves complete case details, photo gallery, and verification status. |
| `PUT` | `/api/reports/{id}` | Bearer Token | `MissingPersonUpdate` (JSON) | `MissingPersonResponse` | Updates report metadata or resubmits corrected FIR details. |
| `DELETE` | `/api/reports/{id}` | Bearer Token | Path: `id` (UUID) | `{status, message}` | Soft deletes report (marks `CLOSED`), deactivating embeddings to generate Edge sync tombstones. |
| `POST` | `/api/reports/{id}/photos` | Bearer Token | `multipart/form-data` | `PhotoResponse` | Uploads additional reference photo; extracts ArcFace embedding asynchronously. |
| `GET` | `/api/reports/{id}/sightings` | Bearer Token | Path: `id` (UUID) | `List<SightingResponse>` | Lists all CCTV sightings recorded for this missing person. |
| `GET` | `/api/reports/{id}/timeline` | Bearer Token | Path: `id` (UUID) | `PersonTimeline` | Chronological camera breadcrumb route with GPS coordinates and timestamps. |

### 4.3 CCTV Sighting Ingestion & Review (`/api/sightings`)

| Method | Endpoint | Auth Required | Request Type | Response Model | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/sightings/` | Bearer Token | Query: `limit`, `status_filter` | `List<SightingResponse>` | Lists recent sightings across all cameras for live monitoring. |
| `GET` | `/api/sightings/{id}` | Bearer Token | Path: `id` (UUID) | `SightingResponse` | Fetches sighting evidence URLs (`face_crop_path`, `full_frame_path`, `video_clip_path`). |
| `PUT` | `/api/sightings/{id}/confirm` | Bearer Token | `SightingReview` (JSON) | `SightingResponse` | **Confirms match**: marks sighting `CONFIRMED`, case `FOUND`, and broadcasts resolution via SSE/FCM. |
| `PUT` | `/api/sightings/{id}/reject` | Bearer Token | `SightingReview` (JSON) | `SightingResponse` | **Rejects match**: marks sighting `REJECTED` (false positive). |

### 4.4 FIR Real-Time Format Validation & Review (`/api/fir`)

| Method | Endpoint | Auth Required | Request Type | Response Model | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/fir/validate/{fir_number}` | Bearer Token | Path: `fir_number` | `FIRValidateResponse` | Real-time Indian police FIR pattern validator (e.g. `FIR/2026/1234`). |
| `GET` | `/api/fir/pending` | Bearer Token | — | `List<MissingPersonResponse>` | Operator queue of cases pending FIR document verification. |
| `POST` | `/api/fir/verify/{id}` | Bearer Token | `FIRVerifyRequest` | `MissingPersonResponse` | Approves FIR: activates 512-D embeddings for live CCTV matching (`ACTIVE`). |
| `POST` | `/api/fir/reject/{id}` | Bearer Token | `FIRRejectRequest` | `MissingPersonResponse` | Rejects FIR with reason: transitions case to `FIR_REJECTED`. |

### 4.5 Notifications & Real-Time SSE Stream

| Method | Endpoint | Auth Required | Request Type | Response Model | Description |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `GET` | `/api/notifications/` | Bearer Token | Query: `unread_only`, `limit` | `NotificationListResponse` | Retrieves user's in-app notification inbox. |
| `GET` | `/api/notifications/unread-count`| Bearer Token | — | `{"unread_count": Int}` | Unread notification count for bottom navigation badge. |
| `PUT` | `/api/notifications/{id}/read` | Bearer Token | Path: `id` (UUID) | `NotificationResponse` | Marks individual notification as read. |
| `PUT` | `/api/notifications/read-all` | Bearer Token | — | `{"status", "updated_count"}`| Marks all user notifications as read. |
| `GET` | `/api/events/stream` | Token query param or Bearer | Stream | `text/event-stream` | SSE stream yielding `event: sighting` alerts and `:ping` keep-alives every 30s. |

### 4.6 Media File Storage
All media paths returned by the API are relative URLs (e.g., `/uploads/faces/abc.jpg`). 
- Full URL format: `http://<HOST>:8000<relative_path>`.
- Media folders:
  - `/uploads/photos/`: Original high-res uploaded reference photos.
  - `/uploads/faces/`: Aligned $112 \times 112\text{ px}$ extracted face crops.
  - `/uploads/evidence/`: Full 1080p CCTV context frames, sighting face crops, and 5-second circular MP4 video buffers.

---

## 5. Android Tech Stack & System Architecture

### Technology Stack Table
| Component | Technology | Version | Purpose |
| :--- | :--- | :--- | :--- |
| **Language** | Kotlin | 2.0.20+ | Modern expressive language with strict null safety |
| **UI Toolkit** | Jetpack Compose | BOM 2024.09+ | Declarative, reactive UI with Material 3 design |
| **Architecture** | Clean Architecture (MVI) | — | Unidirectional Data Flow, separation of concerns |
| **Dependency Injection** | Dagger Hilt | 2.52+ | Compile-time dependency injection |
| **Async & Streams** | Coroutines & StateFlow | 1.9.0+ | Non-blocking reactive programming |
| **HTTP Client** | Retrofit 2 + OkHttp | 2.11.0 / 4.12.0 | Type-safe REST communication with custom interceptors |
| **Real-time SSE** | OkHttp EventSource | 4.12.0 | Robust Server-Sent Events client with auto-reconnection |
| **Push Notifications**| Firebase Cloud Messaging | 24.0.0+ | Background sighting alerts and case resolution updates |
| **Local Database** | Room Database | 2.6.1+ | Offline caching with SQLite Write-Ahead Logging (WAL) |
| **Key-Value Store** | EncryptedSharedPreferences / DataStore | 1.1.1+ | Secure token and user preferences storage |
| **Image Loading** | Coil 3 | 3.0.0-rc+ | High-performance image loading with memory and disk cache |
| **Video Player** | AndroidX Media3 (ExoPlayer) | 1.4.1+ | Smooth looping playback of 5-second evidence video clips |
| **Maps & GIS** | Google Maps Compose / MapLibre | 4.4.1+ | Interactive breadcrumb trajectory visualization |

### Clean Architecture Directory Structure

```
app/src/main/java/com/findmissingpep/app/
├── FindMissingPepApp.kt               // Application class (Hilt Android App)
│
├── di/                                // Dependency Injection Modules
│   ├── NetworkModule.kt               // OkHttpClient, Retrofit, ApiService, EventSource
│   ├── DatabaseModule.kt              // Room DB, DAOs
│   ├── RepositoryModule.kt            // Repository bindings
│   └── FirebaseModule.kt              // FirebaseAuth, FirebaseMessaging
│
├── data/
│   ├── api/
│   │   ├── FmpApiService.kt           // Retrofit REST API Interface
│   │   ├── AuthInterceptor.kt         // Automatically attaches Bearer token to requests
│   │   └── SseEventListener.kt        // OkHttp EventSource callback to StateFlow
│   ├── local/
│   │   ├── FmpDatabase.kt             // Room Database with WAL mode
│   │   ├── dao/                       // CaseDao, SightingDao, NotificationDao
│   │   └── entity/                    // CaseEntity, SightingEntity, NotificationEntity
│   ├── repository/
│   │   ├── AuthRepositoryImpl.kt
│   │   ├── CaseRepositoryImpl.kt
│   │   ├── SightingRepositoryImpl.kt
│   │   └── NotificationRepositoryImpl.kt
│   └── model/dto/                     // Kotlinx Serialization DTOs
│
├── domain/
│   ├── model/                         // Domain entities (MissingCase, Sighting, FIRDetails)
│   ├── repository/                    // Domain repository interfaces
│   └── usecase/                       // SubmitReportUseCase, ConfirmSightingUseCase, etc.
│
├── presentation/
│   ├── theme/                         // Color.kt, Type.kt, Shape.kt, Theme.kt (Dark Glassmorphic)
│   ├── navigation/                    // AppNavGraph.kt, Screen.kt, BottomNavBar.kt
│   ├── common/                        // Reusable UI components (SimilarityGauge, StatusBadge)
│   └── screens/
│       ├── auth/                      // LoginScreen, PhoneAuthScreen
│       ├── home/                      // DashboardHomeScreen (Stats, Recent Sightings)
│       ├── report_wizard/             // Step 1: Info, Step 2: FIR, Step 3: Photos
│       ├── cases/                     // MyCasesScreen, CaseDetailScreen
│       ├── sightings/                 // SightingsFeedScreen, ForensicInspectorScreen
│       ├── map/                       // BreadcrumbTimelineMapScreen
│       ├── notifications/             // NotificationInboxScreen
│       └── profile/                   // ProfileScreen (Language Toggle EN/HI)
│
└── service/
    └── FmpFirebaseMessagingService.kt // Push notification receiver & channel manager
```

---

## 6. Data Models, Enums & Kotlin DTOs

### 6.1 Core Enums
```kotlin
package com.findmissingpep.app.domain.model

enum class CaseStatus(val value: String) {
    PROCESSING("PROCESSING"),
    PENDING_FIR_REVIEW("PENDING_FIR_REVIEW"),
    ACTIVE("ACTIVE"),
    FOUND("FOUND"),
    CLOSED("CLOSED"),
    FIR_REJECTED("FIR_REJECTED"),
    REJECTED_NO_FACE("REJECTED_NO_FACE")
}

enum class SightingStatus(val value: String) {
    PENDING("PENDING"),
    CONFIRMED("CONFIRMED"),
    REJECTED("REJECTED")
}

enum class ConfidenceLevel(val value: String) {
    POSSIBLE("POSSIBLE"),
    PROBABLE("PROBABLE"),
    CONFIRMED("CONFIRMED")
}

enum class LanguageCode(val code: String) {
    ENGLISH("en"),
    HINDI("hi")
}
```

### 6.2 Data Transfer Objects (DTOs)
```kotlin
package com.findmissingpep.app.data.model.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserResponseDto(
    val id: String,
    @SerialName("firebase_uid") val firebaseUid: String,
    val name: String,
    val email: String? = null,
    val phone: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val language: String = "en",
    @SerialName("created_at") val createdAt: String
)

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
    val status: String,
    @SerialName("contact_info") val contactInfo: String? = null,
    @SerialName("fir_number") val firNumber: String? = null,
    @SerialName("fir_police_station") val firPoliceStation: String? = null,
    @SerialName("fir_date") val firDate: String? = null,
    @SerialName("fir_status") val firStatus: String? = null,
    @SerialName("fir_document_path") val firDocumentPath: String? = null,
    @SerialName("fir_rejection_reason") val firRejectionReason: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val photos: List<PhotoResponseDto> = emptyList()
)

@Serializable
data class PhotoResponseDto(
    val id: String,
    @SerialName("original_path") val originalPath: String,
    @SerialName("face_crop_path") val faceCropPath: String? = null,
    @SerialName("is_primary") val isPrimary: Boolean,
    @SerialName("processing_status") val processingStatus: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class SightingResponseDto(
    val id: String,
    @SerialName("person_id") val personId: String,
    @SerialName("person_name") val personName: String? = null,
    @SerialName("agent_id") val agentId: String,
    @SerialName("camera_id") val cameraId: String,
    @SerialName("similarity_score") val similarityScore: Double,
    @SerialName("confidence_level") val confidenceLevel: String,
    @SerialName("num_frames_matched") val numFramesMatched: Int? = null,
    @SerialName("face_crop_path") val faceCropPath: String? = null,
    @SerialName("full_frame_path") val fullFramePath: String? = null,
    @SerialName("video_clip_path") val videoClipPath: String? = null,
    @SerialName("camera_location") val cameraLocation: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    @SerialName("detected_at") val detectedAt: String,
    val status: String,
    @SerialName("created_at") val createdAt: String
)

@Serializable
data class PersonTimelineDto(
    @SerialName("person_id") val personId: String,
    @SerialName("person_name") val personName: String,
    val entries: List<TimelineEntryDto>,
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
    val status: String
)

@Serializable
data class FIRValidateResponseDto(
    @SerialName("fir_number") val firNumber: String,
    @SerialName("is_valid") val isValid: Boolean,
    val message: String,
    val confidence: Double
)
```

---

## 7. Screen-by-Screen UI/UX Specification

### Design Aesthetic: Dark Glassmorphic
- **Background**: `#0F172A` (Slate 900) to `#020617` (Slate 950).
- **Cards**: Semi-transparent `#1E293B` with border `#334155` and backdrop blur.
- **Accents**: Cyan `#06B6D4` (Detection/Vision), Emerald `#10B981` (Confirmed/Active), Rose `#F43F5E` (High Alert/Alert Chime), Amber `#F59E0B` (Pending).

### Screen 1: Auth & Login (`LoginScreen`)
- **Features**:
  - Google One-Tap Sign-In + Email/Password + Phone OTP.
  - Seamless fallback to developer/test bypass in debug mode.
  - Immediate `POST /api/auth/verify` call to exchange token and create PostgreSQL record.
  - Registers current device FCM token via `PUT /api/users/me`.

### Screen 2: Overview Dashboard (`HomeScreen`)
- **Components**:
  - Top Bar: System Status badge (`● CCTV Network Live`), Unread Notifications Bell (with badge count), Language Toggle (`EN | हि`).
  - Stat Metric Cards: Active Missing Cases, Verified Matches Today, Cameras Online.
  - Live Alert Banner: Displays latest temporal match with pulsing red ring.
  - Quick Action Buttons: `[+ Report Missing Person]`, `[View Live Sightings]`, `[Track Trajectory]`.

### Screen 3: 3-Step Missing Person Report Wizard (`ReportWizardScreen`)
- **Step 1: Individual Demographics**
  - Full Name, Age slider, Gender dropdown, Height picker, Physical Description, Last Seen Location, DateTime picker.
- **Step 2: FIR Verification & Legal Details**
  - FIR Number input with **dynamic real-time validation** calling `GET /api/fir/validate/{fir_number}`.
  - Police Station name (autocomplete known stations like Saket, Hauz Khas, etc.).
  - Document picker to attach scanned FIR copy (PDF/JPEG).
- **Step 3: Reference Photos Intake**
  - Photo grid accepting 1 to 5 images.
  - Client-side face check indicator and thumbnail preview.
  - Client-side JPEG compression (scales large photos down to $\le 1920\text{ px}$ to speed up upload).
  - Submit Button: Executes atomic multipart request with progress indicator.

### Screen 4: Case Portfolio & Detail (`CaseDetailScreen`)
- **Features**:
  - Case Status Stepper: `Submitted` → `Face Extracted` → `FIR Verified` → `Active CCTV Search` → `Found`.
  - Photo Gallery: Shows original photo side-by-side with the backend-aligned ArcFace crop (`/uploads/faces/...`).
  - Quick actions: `[Add Photos]`, `[View Sighting Timeline]`, `[Mark Case Resolved / Close]`.

### Screen 5: Live Sightings Feed (`SightingsFeedScreen`)
- **Features**:
  - Real-time animated list populated by SSE and FCM events.
  - Sighting Card: Cropped face thumbnail, Camera Location, Detection Time, and circular Similarity Gauge (e.g. `94%`).
  - Tapping card opens the Forensic Inspector Screen.

---

## 8. Real-Time Alerting: SSE Stream & FCM Push Notifications

The app implements a dual-channel real-time pipeline to guarantee zero-latency alerts whether the app is in the foreground or background.

```
                  ┌──────────────────────────────────────────┐
                  │           FastAPI Central Backend        │
                  └──────────────┬───────────────────────────┘
                                 │
                 ┌───────────────┴───────────────┐
                 │                               │
        App in Foreground                 App in Background
                 ▼                               ▼
       Server-Sent Events (SSE)          Firebase Cloud Messaging
   GET /api/events/stream?token=...             (FCM Push)
                 │                               │
                 ▼                               ▼
       SseEventListener.kt            FmpFirebaseMessagingService.kt
                 │                               │
                 ▼                               ▼
     StateFlow<SightingAlert>          Heads-Up Android Notification
     + In-App Audio Chime (chime.mp3)  + Direct Intent to Inspector Screen
```

### Kotlin SSE Listener Implementation (`SseEventListener.kt`)
```kotlin
package com.findmissingpep.app.data.api

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import okhttp3.*
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SseEventListener @Inject constructor(
    private val okHttpClient: OkHttpClient
) {
    private val _sightingAlerts = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val sightingAlerts = _sightingAlerts.asSharedFlow()

    private var eventSource: EventSource? = null

    fun startListening(baseUrl: String, token: String) {
        val request = Request.Builder()
            .url("$baseUrl/api/events/stream?token=$token")
            .header("Accept", "text/event-stream")
            .build()

        val client = okHttpClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS) // Keep connection open indefinitely
            .build()

        val factory = EventSources.createFactory(client)
        eventSource = factory.newEventSource(request, object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (type == "sighting") {
                    _sightingAlerts.tryEmit(data)
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: Response?) {
                // Exponential backoff reconnect logic
            }
        })
    }

    fun stopListening() {
        eventSource?.cancel()
        eventSource = null
    }
}
```

---

## 9. Forensic Inspector & Interactive Breadcrumb Mapping

### 9.1 Forensic Sighting Inspector (`ForensicInspectorScreen`)
To ensure field officers make confident, accountable decisions, the screen displays a complete evidentiary package:

1. **Side-by-Side Face Comparison**:
   - Left: Enrolled Reference Photo (stored at case creation).
   - Right: High-resolution $112 \times 112\text{ px}$ aligned face crop captured by the Edge CCTV camera.
2. **Similarity Confidence Bar**:
   - Visual gauge with colored thresholds:
     - $\ge 0.75$: Green (High Confidence).
     - $0.60 - 0.74$: Amber (Probable Match).
     - $< 0.60$: Red (Uncertain).
3. **1080p Full Surveillance Scene View**:
   - Interactive zoomable full-frame JPEG showing the subject's full body, clothing, and surrounding individuals.
4. **5-Second Circular Video Buffer**:
   - Seamless loop playback via AndroidX Media3 (ExoPlayer) of the pre/post match clip to inspect gait and posture.
5. **Action Controls**:
   - `[Confirm Match]`: Prompts for confirmation note, sends `PUT /api/sightings/{id}/confirm`, updates case status to `FOUND`, and closes active search across all nodes.
   - `[Reject False Alarm]`: Sends `PUT /api/sightings/{id}/reject` with optional rejection reason.

### 9.2 Interactive Breadcrumb Movement Map (`BreadcrumbTimelineMapScreen`)
Using Google Maps Compose, this screen maps the chronological trajectory of a missing individual across urban cameras:

- **Numbered Timeline Markers**: Each sighting appears as a circular numbered pin (`1`, `2`, `3`...) indicating chronological sequence.
- **Directional Polylines**: Connects cameras with gradient polylines showing direction of travel.
- **Marker Info Sheet**: Tapping any pin displays:
  - Camera Name & Area (e.g. `Saket Metro Station Gate 2`).
  - Detection Timestamp (e.g. `14:32:05 IST`).
  - Cosine Similarity Score (e.g. `91.4%`).
  - `[Navigate to Camera]` button (launches Google Maps turn-by-turn directions to camera coordinates).

---

## 10. Offline-First Local Caching & Synchronization

In many search scenarios (e.g., underground metro stations or rural outposts), mobile internet connectivity is spotty. The Android app guarantees zero-crash offline usability via Room:

- **SQLite WAL Mode**: Ensures high-concurrency non-blocking reads and writes.
- **Data Flow**:
  1. The UI observes Room DAOs via `Flow<List<CaseEntity>>`.
  2. Network fetches update Room in the background.
  3. Image and video assets are cached locally using Coil's disk cache and OkHttp cache headers.

```kotlin
@Database(
    entities = [CaseEntity::class, SightingEntity::class, NotificationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class FmpDatabase : RoomDatabase() {
    abstract fun caseDao(): CaseDao
    abstract fun sightingDao(): SightingDao
    abstract fun notificationDao(): NotificationDao
}
```

---

## 11. Bilingual Localization (English / Hindi)

The application supports real-time bilingual switching without requiring an app restart:

### Key Translation Strings Matrix (`res/values/strings.xml` & `res/values-hi/strings.xml`)

| String Key | English (`en`) | Hindi (`hi`) |
| :--- | :--- | :--- |
| `app_name` | FIND-MISSING-PEP | गुमशुदा तलाश (FIND-MISSING-PEP) |
| `dashboard_title` | Live Monitoring Hub | लाइव निगरानी केंद्र |
| `report_missing_person` | Report Missing Person | गुमशुदा व्यक्ति की रिपोर्ट करें |
| `full_name` | Full Name | पूरा नाम |
| `fir_number` | Police FIR Number | पुलिस प्राथमिकी (FIR) संख्या |
| `fir_valid` | Valid FIR Format | मान्य प्राथमिकी प्रारूप |
| `fir_invalid` | Invalid FIR Format (e.g. FIR/2026/1234) | अमान्य प्राथमिकी प्रारूप |
| `upload_photos` | Upload Clear Photos (1-5) | स्पष्ट तस्वीरें अपलोड करें (1-5) |
| `status_active` | Active CCTV Search | सक्रिय सीसीटीवी खोज जारी |
| `status_found` | Person Found | व्यक्ति मिल गया |
| `possible_match` | Possible Match Detected | संभावित मिलान पाया गया |
| `similarity_score` | Similarity Score | समानता स्कोर |
| `confirm_sighting` | Confirm Verified Match | सत्यापित मिलान की पुष्टि करें |
| `reject_sighting` | Reject False Alarm | गलत पहचान अस्वीकार करें |
| `last_seen_camera` | Last Seen At | अंतिम बार देखा गया |

---

## 12. Step-by-Step Implementation Roadmap

```
┌────────────────────────────────────────────────────────────────────────┐
│                        IMPLEMENTATION MILESTONES                       │
├────────────────────────────────────────────────────────────────────────┤
│  MILESTONE 1: Foundation, DI, Theme & Network Interceptors             │
│  ├── Configure Hilt, Kotlinx Serialization, and Retrofit 2             │
│  ├── Implement AuthInterceptor with Firebase token injection           │
│  └── Set up Material 3 dark glassmorphic design system                 │
├────────────────────────────────────────────────────────────────────────┤
│  MILESTONE 2: Authentication & Profile Sync Engine                     │
│  ├── Firebase Auth integration (Email/Google/Phone)                    │
│  ├── POST /api/auth/verify token verification & auto-provisioning      │
│  └── FCM device token registration via PUT /api/users/me               │
├────────────────────────────────────────────────────────────────────────┤
│  MILESTONE 3: Missing Person Report Wizard                             │
│  ├── Step 1 demographics form with validation                          │
│  ├── Step 2 FIR format checker with GET /api/fir/validate/{fir_number} │
│  └── Step 3 Photo intake with client-side JPEG compression & multipart │
├────────────────────────────────────────────────────────────────────────┤
│  MILESTONE 4: Real-Time Sighting Engine & Forensic Inspector           │
│  ├── OkHttp EventSource SSE connection to GET /api/events/stream       │
│  ├── In-app sighting toast and audio chime playback                   │
│  ├── Split-screen Forensic Inspector (Ref Photo vs. Crop vs. 1080p)   │
│  └── Confirm / Reject disposition actions                              │
├────────────────────────────────────────────────────────────────────────┤
│  MILESTONE 5: GIS Sighting Trajectory & Chronological Timeline         │
│  ├── Google Maps Compose integration with numbered pins                │
│  ├── Directional polyline rendering between CCTV cameras               │
│  └── Turn-by-turn navigation intent to camera coordinates              │
├────────────────────────────────────────────────────────────────────────┤
│  MILESTONE 6: Offline Room Caching & Hindi Localization                │
│  ├── Room database synchronization for cases and sightings             │
│  └── Complete strings-hi.xml localization testing                      │
└────────────────────────────────────────────────────────────────────────┘
```

---

## Verification & Architecture Checklist
- [x] All 6 backend endpoint groups mapped with HTTP methods, parameters, and bodies.
- [x] Client-side FIR validation pattern matching Indian police standards.
- [x] Dual-channel real-time notifications (SSE for foreground, FCM for background).
- [x] Multi-format forensic evidence package (112×112 crop, 1080p scene, 5s MP4 clip).
- [x] Offline SQLite WAL resilience via Android Room.
- [x] Bilingual English/Hindi accessibility.
