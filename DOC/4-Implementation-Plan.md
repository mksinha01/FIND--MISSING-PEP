# AI-Based Missing Person Detection & Real-Time CCTV Monitoring System — Full Architecture

> **Your project is an installable Edge-AI CCTV application that connects to existing CCTV infrastructure, continuously detects and recognizes faces against active missing-person reports, identifies possible sightings in real time, records where and when they occurred, and sends the evidence to the user through the central platform.**

---

## 0. Mission Statement

### Capabilities (verbs)

- **Reports** missing persons with photos via a Flutter mobile app
- **Processes** uploaded photos → face detection → alignment → 512-D ArcFace embedding
- **Syncs** active missing-person embeddings to deployed CCTV Edge Agents
- **Ingests** live RTSP/ONVIF CCTV streams at 1 FPS per camera (1–4 cameras MVP)
- **Detects** faces using SCRFD detector via ONNX Runtime (CPU-only)
- **Tracks** persons across frames using ByteTrack (assign Track IDs, avoid redundant recognition)
- **Recognizes** faces against missing-person embedding database using ArcFace + FAISS vector search
- **Verifies** matches temporally (multiple frames, aggregated similarity) before alerting
- **Captures** evidence: cropped face, full frame, optional short clip, camera ID, GPS, timestamp
- **Alerts** operators via desktop notification + SSE push to Flutter app
- **Logs** complete sighting timeline across cameras ("last seen" tracking)
- **Supports** bilingual UI: Hindi + English

### Tech Stack

| Layer | Technology | Version |
|---|---|---|
| Mobile | Flutter + Dart | 3.x / Dart 3.x |
| Backend API | Python + FastAPI | 3.11+ / 0.110+ |
| Database | PostgreSQL | 16 |
| Cache / Queue | Redis | 7 |
| Auth | Firebase Auth | latest |
| Push Notifications | Firebase Cloud Messaging (FCM) | latest |
| Real-time Events | Server-Sent Events (SSE) | — |
| Face Detection | SCRFD (ONNX) | buffalo_l |
| Face Recognition | ArcFace (ONNX) | buffalo_l |
| Face Tracking | ByteTrack | — |
| Inference Runtime | ONNX Runtime | 1.17+ |
| Vector Search | FAISS (CPU) | 1.7+ |
| Edge Agent GUI | Python + PySide6 (Qt) | 6.6+ |
| CCTV Protocol | RTSP / ONVIF | — |
| Video Decode | OpenCV | 4.9+ |
| Containerization | Docker + Docker Compose | — |
| Deployment (test) | Docker local | — |
| Deployment (prod) | Azure / AWS | — |
| Photo Storage | Local filesystem (backend server) | — |

### Build Instruction

> **Build this exactly. Do not skip anything. Do not simplify. Do not substitute libraries.** This is a research project focused on the AI pipeline — the Flutter app and backend can be functional but simple; the Edge Agent AI pipeline must be rigorous and measurable.

---

## 1. File Structure — Complete Project Tree

```
FIND-MISSING-PEP/
│
├── DOC/                                    ← Project documentation (existing)
│   ├── 1-BRAINSTROM.MD
│   ├── 2-DEMO-PROMT-FOR-BUID.MD
│   ├── 3-One-Shot-Build-Spec.md
│   └── dump1-BRAINSTROM.MD
│
├── backend/                                ← FastAPI Backend (Component 2)
│   ├── Dockerfile                          ← Backend container definition
│   ├── docker-compose.yml                  ← Orchestrates backend + postgres + redis
│   ├── requirements.txt                    ← Pinned Python dependencies
│   ├── .env.example                        ← Environment variable template
│   ├── .gitignore                          ← Excludes .env, uploads/, __pycache__
│   ├── start.sh                            ← Entrypoint: echoes config, runs uvicorn
│   ├── alembic.ini                         ← DB migration config
│   ├── alembic/                            ← Migration scripts
│   │   └── versions/                       ← Individual migration files
│   │
│   ├── app/
│   │   ├── __init__.py
│   │   ├── main.py                         ← FastAPI app factory, CORS, lifespan
│   │   ├── config.py                       ← Settings from env vars (pydantic-settings)
│   │   ├── database.py                     ← Async SQLAlchemy engine + session factory
│   │   │
│   │   ├── models/                         ← SQLAlchemy ORM models
│   │   │   ├── __init__.py
│   │   │   ├── user.py                     ← User model (firebase_uid, name, phone)
│   │   │   ├── missing_person.py           ← MissingPerson report model
│   │   │   ├── photo.py                    ← Photo model (file_path, face_embedding ref)
│   │   │   ├── face_embedding.py           ← FaceEmbedding model (512-D vector, person ref)
│   │   │   ├── edge_agent.py               ← Registered Edge Agent devices
│   │   │   ├── camera.py                   ← Camera model (rtsp_url, location, agent ref)
│   │   │   ├── sighting.py                 ← Sighting event model (match data + evidence)
│   │   │   └── notification.py             ← Notification log model
│   │   │
│   │   ├── schemas/                        ← Pydantic request/response schemas
│   │   │   ├── __init__.py
│   │   │   ├── user.py
│   │   │   ├── missing_person.py
│   │   │   ├── photo.py
│   │   │   ├── edge_agent.py
│   │   │   ├── camera.py
│   │   │   ├── sighting.py
│   │   │   └── notification.py
│   │   │
│   │   ├── crud/                           ← Data access layer (one function = one query)
│   │   │   ├── __init__.py
│   │   │   ├── user.py                     ← create_user, get_user, update_user
│   │   │   ├── missing_person.py           ← create_report, list_reports, update_status
│   │   │   ├── photo.py                    ← save_photo_record, get_photos_for_person
│   │   │   ├── face_embedding.py           ← store_embedding, get_active_embeddings
│   │   │   ├── edge_agent.py               ← register_agent, heartbeat, list_agents
│   │   │   ├── camera.py                   ← register_camera, list_cameras
│   │   │   ├── sighting.py                 ← create_sighting, list_sightings, confirm/reject
│   │   │   └── notification.py             ← create_notification, mark_read
│   │   │
│   │   ├── services/                       ← Business logic / action layer
│   │   │   ├── __init__.py
│   │   │   ├── face_processing.py          ← Run SCRFD detect + ArcFace embed on uploaded photo
│   │   │   ├── embedding_sync.py           ← Package active embeddings for Edge Agent sync
│   │   │   ├── notification_service.py     ← Send FCM push + store in DB
│   │   │   └── sse_manager.py              ← SSE connection manager for real-time events
│   │   │
│   │   ├── api/                            ← REST endpoints grouped by resource
│   │   │   ├── __init__.py
│   │   │   ├── deps.py                     ← Common dependencies (get_db, get_current_user)
│   │   │   ├── auth.py                     ← Firebase token verification middleware
│   │   │   ├── users.py                    ← /api/users/*
│   │   │   ├── reports.py                  ← /api/reports/* (missing persons)
│   │   │   ├── agents.py                   ← /api/agents/* (edge agent registration)
│   │   │   ├── cameras.py                  ← /api/cameras/*
│   │   │   ├── sightings.py               ← /api/sightings/*
│   │   │   ├── embeddings.py              ← /api/embeddings/* (sync endpoint for Edge)
│   │   │   ├── notifications.py           ← /api/notifications/*
│   │   │   └── sse.py                      ← /api/events/stream (SSE endpoint)
│   │   │
│   │   └── utils/
│   │       ├── __init__.py
│   │       └── file_storage.py             ← Save/serve files from local filesystem
│   │
│   ├── ai_models/                          ← ONNX model files (downloaded at build time)
│   │   ├── det_10g.onnx                    ← SCRFD face detector
│   │   └── w600k_r50.onnx                  ← ArcFace recognition model
│   │
│   └── uploads/                            ← Uploaded photos + evidence (gitignored)
│       ├── photos/                         ← Original uploaded photos
│       ├── faces/                          ← Cropped + aligned face images
│       └── evidence/                       ← Sighting evidence (crops, clips)
│
├── edge_agent/                             ← AI CCTV Edge Agent (Component 3)
│   ├── main.py                             ← Application entrypoint
│   ├── requirements.txt                    ← Pinned dependencies
│   ├── config.py                           ← Agent configuration (backend URL, credentials)
│   ├── setup.py                            ← PyInstaller / cx_Freeze build config
│   │
│   ├── ui/                                 ← PySide6 GUI
│   │   ├── __init__.py
│   │   ├── main_window.py                  ← Main window: camera grid + status + alerts
│   │   ├── login_dialog.py                 ← Login / device registration screen
│   │   ├── camera_config_dialog.py         ← Camera discovery + selection screen
│   │   ├── alert_widget.py                 ← Possible match alert popup (confirm/reject)
│   │   ├── camera_feed_widget.py           ← Single camera preview with overlay
│   │   ├── status_bar_widget.py            ← AI status: FPS, faces detected, CPU usage
│   │   ├── timeline_widget.py              ← Sighting timeline view
│   │   ├── settings_dialog.py              ← Settings: language, thresholds, backend URL
│   │   ├── resources/                      ← Icons, QSS stylesheets, translations
│   │   │   ├── style.qss                   ← Dark theme stylesheet
│   │   │   ├── icons/                      ← App icons (tray, alert, status)
│   │   │   ├── i18n/
│   │   │   │   ├── en.json                 ← English strings
│   │   │   │   └── hi.json                 ← Hindi strings
│   │   │   └── logo.png
│   │   └── workers/                        ← QThread workers for background tasks
│   │       ├── __init__.py
│   │       ├── stream_worker.py            ← Camera stream reader (runs in QThread)
│   │       └── sync_worker.py              ← Background embedding sync with backend
│   │
│   ├── ai/                                 ← AI Pipeline (CORE — most critical code)
│   │   ├── __init__.py
│   │   ├── pipeline.py                     ← Main AI pipeline orchestrator per camera
│   │   ├── face_detector.py                ← SCRFD face detection via ONNX Runtime
│   │   ├── face_aligner.py                 ← Affine alignment using 5 landmarks
│   │   ├── face_recognizer.py              ← ArcFace embedding extraction via ONNX
│   │   ├── face_quality.py                 ← Quality gate: blur, size, angle thresholds
│   │   ├── tracker.py                      ← ByteTrack multi-object tracker integration
│   │   ├── track_state.py                  ← Per-track state: embedding history, scores
│   │   ├── temporal_verifier.py            ← Aggregates multiple scores → match decision
│   │   ├── vector_search.py                ← FAISS index: add/remove/search embeddings
│   │   └── evidence_collector.py           ← Capture crops, frames, metadata for sighting
│   │
│   ├── network/                            ← Backend communication
│   │   ├── __init__.py
│   │   ├── api_client.py                   ← HTTP client to backend API
│   │   ├── embedding_syncer.py             ← Periodic sync of missing-person embeddings
│   │   ├── sighting_uploader.py            ← Upload sighting events + evidence
│   │   └── sse_listener.py                 ← Listen for real-time events from backend
│   │
│   ├── camera/                             ← CCTV integration
│   │   ├── __init__.py
│   │   ├── rtsp_reader.py                  ← OpenCV RTSP stream reader with reconnect
│   │   ├── onvif_discovery.py              ← ONVIF camera auto-discovery
│   │   ├── stream_manager.py               ← Manages multiple camera streams
│   │   └── frame_sampler.py                ← 1 FPS sampling from decoded stream
│   │
│   ├── storage/                            ← Local storage
│   │   ├── __init__.py
│   │   ├── local_db.py                     ← SQLite for local state, offline queue
│   │   ├── faiss_store.py                  ← FAISS index persistence (save/load)
│   │   └── evidence_store.py              ← Local evidence file management
│   │
│   ├── models/                             ← ONNX model files
│   │   ├── det_10g.onnx                    ← SCRFD face detector
│   │   └── w600k_r50.onnx                  ← ArcFace recognition model
│   │
│   └── logs/                               ← Runtime logs (gitignored)
│
├── flutter_app/                            ← Flutter Mobile App (Component 1)
│   ├── pubspec.yaml                        ← Flutter dependencies
│   ├── .env                                ← Backend URL, Firebase config
│   │
│   ├── lib/
│   │   ├── main.dart                       ← App entrypoint, Firebase init, routing
│   │   ├── config/
│   │   │   ├── theme.dart                  ← App theme (dark/light, colors, typography)
│   │   │   ├── routes.dart                 ← Named route definitions
│   │   │   └── constants.dart              ← API base URL, timeouts
│   │   │
│   │   ├── models/                         ← Data models (Dart classes)
│   │   │   ├── user_model.dart
│   │   │   ├── missing_person_model.dart
│   │   │   ├── sighting_model.dart
│   │   │   └── notification_model.dart
│   │   │
│   │   ├── services/                       ← API + Firebase services
│   │   │   ├── auth_service.dart           ← Firebase Auth (Google + Email login)
│   │   │   ├── api_service.dart            ← HTTP client to FastAPI backend
│   │   │   ├── notification_service.dart   ← FCM push notification handler
│   │   │   ├── sse_service.dart            ← SSE listener for real-time updates
│   │   │   └── image_service.dart          ← Photo picker + compression
│   │   │
│   │   ├── providers/                      ← State management (Riverpod or Provider)
│   │   │   ├── auth_provider.dart
│   │   │   ├── reports_provider.dart
│   │   │   ├── sightings_provider.dart
│   │   │   └── notifications_provider.dart
│   │   │
│   │   ├── screens/                        ← UI screens
│   │   │   ├── splash_screen.dart          ← App loading + auth check
│   │   │   ├── login_screen.dart           ← Login (Google + Email)
│   │   │   ├── home_screen.dart            ← Dashboard: stats + recent activity
│   │   │   ├── report_form_screen.dart     ← Submit missing person report
│   │   │   ├── my_reports_screen.dart      ← List of user's reports
│   │   │   ├── report_detail_screen.dart   ← Single report + sightings
│   │   │   ├── sighting_detail_screen.dart ← Sighting evidence + map + timeline
│   │   │   ├── notifications_screen.dart   ← Notification inbox
│   │   │   └── profile_screen.dart         ← User profile + settings + language
│   │   │
│   │   ├── widgets/                        ← Reusable UI components
│   │   │   ├── report_card.dart            ← Missing person card
│   │   │   ├── sighting_card.dart          ← Sighting event card
│   │   │   ├── timeline_widget.dart        ← Visual timeline of sightings
│   │   │   ├── photo_upload_widget.dart    ← Multi-photo upload with preview
│   │   │   ├── status_badge.dart           ← ACTIVE / CLOSED / FOUND badge
│   │   │   └── similarity_gauge.dart       ← Visual similarity percentage gauge
│   │   │
│   │   └── l10n/                           ← Localization
│   │       ├── app_en.arb                  ← English strings
│   │       └── app_hi.arb                  ← Hindi strings
│   │
│   ├── android/                            ← Android-specific config
│   │   └── app/
│   │       └── google-services.json        ← Firebase config (gitignored)
│   │
│   └── ios/                                ← iOS-specific config
│       └── Runner/
│           └── GoogleService-Info.plist    ← Firebase config (gitignored)
│
├── research/                               ← Research & experiments (for paper)
│   ├── notebooks/
│   │   ├── 01_face_recognition_baseline.ipynb   ← Test ArcFace on known datasets
│   │   ├── 02_single_vs_temporal_matching.ipynb ← Compare single-frame vs temporal
│   │   ├── 03_model_comparison.ipynb            ← Compare SCRFD vs RetinaFace etc.
│   │   └── 04_cctv_conditions_eval.ipynb        ← Test under blur, lighting, angle
│   │
│   ├── datasets/                                ← Test datasets (gitignored)
│   │   ├── lfw/                                 ← Labeled Faces in the Wild
│   │   └── custom_cctv/                         ← Recorded CCTV test footage
│   │
│   ├── results/                                 ← Experiment results, charts, tables
│   │   ├── metrics.csv
│   │   └── figures/
│   │
│   └── paper/                                   ← Research paper draft
│       ├── paper.tex                            ← LaTeX source
│       └── references.bib                       ← Bibliography
│
└── scripts/                                ← Utility scripts
    ├── download_models.py                  ← Downloads ONNX models from InsightFace
    ├── seed_test_data.py                   ← Seeds DB with test users + reports
    └── generate_embeddings.py              ← Bulk-process photos into embeddings
```

---

## 2. Environment / Secrets

### Settings Precedence

> **Runtime env vars > `.env` file > code defaults**

### Backend `.env.example`

```env
# ── App ──
APP_NAME=FindMissingPerson
APP_ENV=development                        # development | staging | production
DEBUG=true
SECRET_KEY=change-me-to-random-64-chars    # REQUIRED — used for signing internal tokens

# ── Database ──
DATABASE_URL=postgresql+asyncpg://fmp_user:fmp_pass@localhost:5432/fmp_db  # REQUIRED
# https://www.postgresql.org/docs/current/libpq-connect.html

# ── Redis ──
REDIS_URL=redis://localhost:6379/0         # REQUIRED for background tasks + SSE

# ── Firebase ──
FIREBASE_PROJECT_ID=your-firebase-project  # REQUIRED — https://console.firebase.google.com
FIREBASE_CREDENTIALS_PATH=./firebase-sa.json  # REQUIRED — service account JSON
# https://firebase.google.com/docs/admin/setup#initialize_the_sdk_in_non-google_environments

# ── File Storage ──
UPLOAD_DIR=./uploads                       # Local filesystem path for photos + evidence
MAX_UPLOAD_SIZE_MB=10                      # Max photo size in MB

# ── AI Models ──
ONNX_MODEL_DIR=./ai_models                # Path to ONNX model files
FACE_DETECT_THRESHOLD=0.5                 # SCRFD detection confidence threshold
FACE_SIMILARITY_THRESHOLD=0.4             # ArcFace cosine similarity threshold (0.0–1.0)

# ── Server ──
HOST=0.0.0.0
PORT=8000
WORKERS=1                                  # Uvicorn workers (1 for development)

# ── CORS ──
ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080  # Comma-separated
```

### Edge Agent `config.ini`

```ini
[backend]
url = http://localhost:8000
api_key = agent-api-key-here               ; REQUIRED — issued during device registration

[agent]
device_id =                                ; Auto-generated on first run
location_name = Main Building              ; Human-readable location
sync_interval_seconds = 60                 ; How often to sync embeddings from backend

[ai]
onnx_model_dir = ./models
face_detect_threshold = 0.5
face_similarity_threshold = 0.4
frame_sample_fps = 1                       ; Frames per second to process per camera
quality_min_face_size = 40                 ; Minimum face size in pixels
quality_max_blur = 100                     ; Laplacian variance blur threshold

[tracking]
max_track_age = 30                         ; Frames before a lost track is deleted
min_recognition_interval = 5              ; Seconds between recognition runs per track
temporal_window_size = 5                   ; Number of scores for temporal verification
temporal_threshold = 0.42                  ; Mean similarity to trigger possible match

[ui]
language = en                              ; en | hi
theme = dark                               ; dark | light
```

### `.gitignore` (root)

```gitignore
# Environment
.env
*.env
firebase-sa.json
google-services.json
GoogleService-Info.plist

# Uploads & evidence
uploads/
evidence/
logs/

# AI Models (large files)
*.onnx
models/

# Python
__pycache__/
*.pyc
.venv/
venv/

# Datasets
datasets/

# IDE
.vscode/
.idea/

# OS
.DS_Store
Thumbs.db
```

---

## 3. Data Schema

### PostgreSQL (Backend)

```sql
-- ══════════════════════════════════════════════════════
-- USERS
-- RLS: OFF (application-level auth via Firebase token)
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid    VARCHAR(128) UNIQUE NOT NULL,      -- Firebase Auth UID
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) UNIQUE,
    phone           VARCHAR(20),
    avatar_url      VARCHAR(512),
    language        VARCHAR(5) DEFAULT 'en',           -- en | hi
    fcm_token       TEXT,                              -- Firebase Cloud Messaging token
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_firebase_uid ON users(firebase_uid);


-- ══════════════════════════════════════════════════════
-- MISSING PERSON REPORTS
-- RLS: OFF
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS missing_persons (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    
    -- Person details
    full_name       VARCHAR(255) NOT NULL,
    age             INTEGER,
    gender          VARCHAR(20),                       -- male | female | other
    height_cm       INTEGER,
    description     TEXT,                              -- Physical description, clothing
    last_seen_location TEXT,
    last_seen_time  TIMESTAMPTZ,
    
    -- Report status
    status          VARCHAR(20) DEFAULT 'PROCESSING',  -- PROCESSING | ACTIVE | FOUND | CLOSED
    -- PROCESSING: photos being processed for embeddings
    -- ACTIVE: embeddings ready, being monitored on CCTV
    -- FOUND: confirmed sighting, case resolved
    -- CLOSED: user closed the report
    
    contact_info    TEXT,                              -- Additional contact details
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mp_user ON missing_persons(user_id);
CREATE INDEX IF NOT EXISTS idx_mp_status ON missing_persons(status);


-- ══════════════════════════════════════════════════════
-- PHOTOS
-- RLS: OFF
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS photos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id       UUID NOT NULL REFERENCES missing_persons(id) ON DELETE CASCADE,
    original_path   VARCHAR(512) NOT NULL,             -- Path to original uploaded photo
    face_crop_path  VARCHAR(512),                      -- Path to aligned face crop
    is_primary      BOOLEAN DEFAULT FALSE,             -- Primary photo for display
    processing_status VARCHAR(20) DEFAULT 'PENDING',   -- PENDING | SUCCESS | FAILED | NO_FACE
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_photos_person ON photos(person_id);


-- ══════════════════════════════════════════════════════
-- FACE EMBEDDINGS
-- RLS: OFF
-- Each photo can produce one embedding (the best-quality face)
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS face_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id       UUID NOT NULL REFERENCES missing_persons(id) ON DELETE CASCADE,
    photo_id        UUID REFERENCES photos(id) ON DELETE SET NULL,
    embedding       BYTEA NOT NULL,                    -- 512-D float32 vector (2048 bytes)
    model_version   VARCHAR(50) DEFAULT 'arcface_r50', -- Model that generated this
    quality_score   FLOAT,                             -- Face quality score (0.0–1.0)
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_embeddings_person ON face_embeddings(person_id);


-- ══════════════════════════════════════════════════════
-- EDGE AGENTS
-- RLS: OFF
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS edge_agents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       VARCHAR(128) UNIQUE NOT NULL,      -- Machine-generated device fingerprint
    name            VARCHAR(255),                      -- Human-readable name
    location        VARCHAR(255),                      -- Physical location description
    api_key_hash    VARCHAR(256) NOT NULL,              -- Hashed API key for auth
    status          VARCHAR(20) DEFAULT 'OFFLINE',     -- ONLINE | OFFLINE | ERROR
    last_heartbeat  TIMESTAMPTZ,
    last_sync_at    TIMESTAMPTZ,                       -- Last embedding sync timestamp
    os_info         VARCHAR(255),                      -- Windows 10/11, etc.
    version         VARCHAR(50),                       -- Agent software version
    camera_count    INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);


-- ══════════════════════════════════════════════════════
-- CAMERAS
-- RLS: OFF
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS cameras (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id        UUID NOT NULL REFERENCES edge_agents(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,             -- e.g. "Main Gate Camera 1"
    rtsp_url        VARCHAR(512) NOT NULL,
    location        VARCHAR(255),                      -- Specific camera location
    latitude        FLOAT,
    longitude       FLOAT,
    status          VARCHAR(20) DEFAULT 'INACTIVE',    -- ACTIVE | INACTIVE | ERROR
    resolution      VARCHAR(20),                       -- e.g. "1920x1080"
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cameras_agent ON cameras(agent_id);


-- ══════════════════════════════════════════════════════
-- SIGHTINGS
-- RLS: OFF
-- A sighting = the Edge Agent detected a possible match
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS sightings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id       UUID NOT NULL REFERENCES missing_persons(id),
    agent_id        UUID NOT NULL REFERENCES edge_agents(id),
    camera_id       UUID NOT NULL REFERENCES cameras(id),
    
    -- Match data
    similarity_score FLOAT NOT NULL,                   -- Aggregated temporal similarity (0.0–1.0)
    confidence_level VARCHAR(20) DEFAULT 'POSSIBLE',   -- POSSIBLE | PROBABLE | CONFIRMED | REJECTED
    num_frames_matched INTEGER,                        -- How many frames contributed to this score
    
    -- Evidence
    face_crop_path  VARCHAR(512),                      -- Cropped face from CCTV
    full_frame_path VARCHAR(512),                      -- Full frame capture
    video_clip_path VARCHAR(512),                      -- Optional short video clip
    
    -- Location & time
    camera_location VARCHAR(255),
    latitude        FLOAT,
    longitude       FLOAT,
    detected_at     TIMESTAMPTZ NOT NULL,              -- When the detection happened
    
    -- Operator review
    reviewed_by     UUID REFERENCES users(id),
    reviewed_at     TIMESTAMPTZ,
    review_notes    TEXT,
    
    status          VARCHAR(20) DEFAULT 'PENDING',     -- PENDING | CONFIRMED | REJECTED
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_sightings_person ON sightings(person_id);
CREATE INDEX IF NOT EXISTS idx_sightings_status ON sightings(status);
CREATE INDEX IF NOT EXISTS idx_sightings_detected ON sightings(detected_at DESC);


-- ══════════════════════════════════════════════════════
-- NOTIFICATIONS
-- RLS: OFF
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type            VARCHAR(50) NOT NULL,              -- SIGHTING | CONFIRMATION | STATUS_CHANGE
    title           VARCHAR(255) NOT NULL,
    body            TEXT,
    data            JSONB,                             -- Additional structured data
    sighting_id     UUID REFERENCES sightings(id),
    is_read         BOOLEAN DEFAULT FALSE,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_read ON notifications(user_id, is_read);


-- ══════════════════════════════════════════════════════
-- AUDIT LOG
-- RLS: OFF
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS audit_log (
    id              BIGSERIAL PRIMARY KEY,
    actor_type      VARCHAR(20) NOT NULL,              -- USER | AGENT | SYSTEM
    actor_id        VARCHAR(128),
    action          VARCHAR(100) NOT NULL,              -- e.g. REPORT_CREATED, SIGHTING_CONFIRMED
    resource_type   VARCHAR(50),                        -- e.g. missing_person, sighting
    resource_id     UUID,
    details         JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC);
```

### SQLite (Edge Agent — Local State)

```sql
-- Local state for offline operation + queue

CREATE TABLE IF NOT EXISTS sync_state (
    key             TEXT PRIMARY KEY,
    value           TEXT,
    updated_at      TEXT DEFAULT (datetime('now'))
);
-- known keys: last_sync_timestamp, device_id, api_key

CREATE TABLE IF NOT EXISTS cached_embeddings (
    person_id       TEXT PRIMARY KEY,
    person_name     TEXT,
    embedding_data  BLOB NOT NULL,                     -- Serialized numpy array
    photo_url       TEXT,                              -- For display in alerts
    synced_at       TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS pending_sightings (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    person_id       TEXT NOT NULL,
    camera_id       TEXT NOT NULL,
    similarity_score REAL NOT NULL,
    num_frames      INTEGER,
    face_crop_path  TEXT,
    full_frame_path TEXT,
    detected_at     TEXT NOT NULL,
    uploaded        INTEGER DEFAULT 0,                 -- 0 = pending, 1 = uploaded
    created_at      TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS track_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    track_id        INTEGER NOT NULL,
    camera_id       TEXT NOT NULL,
    person_id       TEXT,                              -- NULL if no match
    similarity_score REAL,
    frame_number    INTEGER,
    timestamp       TEXT DEFAULT (datetime('now'))
);
```

---

## 4. Dependencies

### Backend (`requirements.txt`)

```
# ── Web Framework ──
fastapi>=0.110.0
uvicorn[standard]>=0.27.0
python-multipart>=0.0.6
sse-starlette>=1.6.0                       # SSE support for FastAPI

# ── Database ──
sqlalchemy[asyncio]>=2.0.25
asyncpg>=0.29.0                            # Async PostgreSQL driver
alembic>=1.13.0                            # DB migrations
redis>=5.0.0
aioredis>=2.0.0

# ── Auth ──
firebase-admin>=6.3.0                      # Firebase Admin SDK
python-jose>=3.3.0                         # JWT handling

# ── Validation ──
pydantic>=2.5.0
pydantic-settings>=2.1.0

# ── AI / Face Processing ──
onnxruntime>=1.17.0                        # CPU inference
numpy>=1.26.0
opencv-python-headless>=4.9.0              # Image processing (no GUI)
insightface>=0.7.3                         # Face analysis (SCRFD + ArcFace)
scikit-learn>=1.4.0                        # Cosine similarity utilities

# ── File handling ──
pillow>=10.2.0                             # Image manipulation
aiofiles>=23.2.0                           # Async file I/O

# ── Utilities ──
python-dotenv>=1.0.0
httpx>=0.26.0                              # Async HTTP client
```

### Edge Agent (`requirements.txt`)

```
# ── GUI ──
PySide6>=6.6.0                             # Qt6 GUI framework

# ── AI Pipeline ──
onnxruntime>=1.17.0                        # CPU inference
numpy>=1.26.0
opencv-python>=4.9.0                       # Video capture + image processing
insightface>=0.7.3                         # SCRFD + ArcFace models
faiss-cpu>=1.7.4                           # Vector similarity search
scipy>=1.12.0                              # Distance calculations

# ── Tracking ──
lap>=0.4.0                                 # Linear assignment for ByteTrack
filterpy>=1.4.5                            # Kalman filter for tracking

# ── Network ──
httpx>=0.26.0                              # HTTP client for backend API
sseclient-py>=1.8.0                        # SSE client

# ── CCTV ──
onvif-zeep>=0.2.12                         # ONVIF camera discovery
wsdiscovery>=2.0.0                         # WS-Discovery for network cameras

# ── Storage ──
# sqlite3 is built into Python stdlib

# ── Utilities ──
pillow>=10.2.0
apscheduler>=3.10.4                        # Scheduled tasks (sync, heartbeat)

# ── Packaging ──
pyinstaller>=6.3.0                         # Build Windows executable
```

### Flutter (`pubspec.yaml` — key dependencies)

```yaml
dependencies:
  flutter:
    sdk: flutter
  
  # ── Auth ──
  firebase_core: ^2.27.0
  firebase_auth: ^4.17.0
  google_sign_in: ^6.2.0
  firebase_messaging: ^14.7.0              # Push notifications
  
  # ── Networking ──
  dio: ^5.4.0                              # HTTP client
  flutter_sse: ^0.3.0                      # SSE client
  
  # ── State Management ──
  flutter_riverpod: ^2.5.0
  
  # ── UI ──
  cached_network_image: ^3.3.0
  image_picker: ^1.0.7
  photo_view: ^0.14.0                      # Zoom on evidence photos
  shimmer: ^3.0.0                          # Loading skeletons
  flutter_local_notifications: ^17.0.0
  
  # ── Localization ──
  flutter_localizations:
    sdk: flutter
  intl: ^0.19.0
  
  # ── Navigation ──
  go_router: ^13.2.0
  
  # ── Storage ──
  shared_preferences: ^2.2.2
  
  # ── Utilities ──
  uuid: ^4.3.0
  timeago: ^3.6.0
  url_launcher: ^6.2.0
```

---

## 5. Containerization / Runtime

### Backend `Dockerfile`

```dockerfile
FROM python:3.11-slim

# System dependencies for OpenCV + PostgreSQL
RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1-mesa-glx \
    libglib2.0-0 \
    libpq-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Download AI models if not present
RUN python -c "from insightface.app import FaceAnalysis; FaceAnalysis(name='buffalo_l', root='./ai_models')"

EXPOSE 8000

COPY start.sh .
RUN chmod +x start.sh

CMD ["./start.sh"]
```

### `start.sh`

```bash
#!/bin/bash
set -e

echo "╔══════════════════════════════════════════╗"
echo "║  FindMissingPerson Backend Starting...   ║"
echo "╠══════════════════════════════════════════╣"
echo "║  APP_ENV:    ${APP_ENV:-development}     ║"
echo "║  PORT:       ${PORT:-8000}               ║"
echo "║  WORKERS:    ${WORKERS:-1}               ║"
echo "║  DB:         ${DATABASE_URL:0:30}...     ║"
echo "║  REDIS:      ${REDIS_URL:-not set}       ║"
echo "║  UPLOAD_DIR: ${UPLOAD_DIR:-./uploads}    ║"
echo "╚══════════════════════════════════════════╝"

# Run migrations
echo "Running database migrations..."
alembic upgrade head

# Create upload directories
mkdir -p ${UPLOAD_DIR:-./uploads}/{photos,faces,evidence}

echo "Starting uvicorn..."
exec uvicorn app.main:app \
    --host ${HOST:-0.0.0.0} \
    --port ${PORT:-8000} \
    --workers ${WORKERS:-1} \
    --log-level info
```

### `docker-compose.yml`

```yaml
version: '3.8'

services:
  backend:
    build: ./backend
    ports:
      - "8000:8000"
    env_file:
      - ./backend/.env
    volumes:
      - uploads_data:/app/uploads
      - ./backend/ai_models:/app/ai_models
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_started
    restart: unless-stopped

  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: fmp_db
      POSTGRES_USER: fmp_user
      POSTGRES_PASSWORD: fmp_pass
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U fmp_user -d fmp_db"]
      interval: 5s
      timeout: 3s
      retries: 5

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data

volumes:
  postgres_data:
  redis_data:
  uploads_data:
```

### Port Ownership

| Service | Port | Protocol |
|---|---|---|
| Backend API | 8000 | HTTP |
| PostgreSQL | 5432 | TCP |
| Redis | 6379 | TCP |
| Edge Agent (no server port) | — | Outbound HTTP/SSE only |

---

## 6. AI Pipeline Architecture — Detailed Breakdown

> [!IMPORTANT]
> This is the **most critical section** for the research project. The AI pipeline inside the Edge Agent is the core novelty.

### 6.1 Pipeline Flow (Per Camera)

```
┌─────────────────────────────────────────────────────────────────────┐
│                    AI PIPELINE (per camera stream)                   │
│                                                                     │
│  RTSP Stream                                                        │
│      │                                                              │
│      ▼                                                              │
│  ┌──────────────┐    Drop frames to achieve                        │
│  │ Frame Sampler │───  1 FPS target rate                            │
│  └──────┬───────┘                                                   │
│         │ 1 frame/sec                                               │
│         ▼                                                           │
│  ┌──────────────┐    SCRFD model (ONNX Runtime)                    │
│  │ Face Detector │──  Returns: bounding boxes +                     │
│  │  (SCRFD)      │   5-point landmarks + confidence                 │
│  └──────┬───────┘                                                   │
│         │ 0..N faces per frame                                      │
│         ▼                                                           │
│  ┌──────────────┐    Assigns persistent IDs to faces               │
│  │   ByteTrack   │──  across frames using IoU +                     │
│  │   Tracker     │   Kalman prediction                              │
│  └──────┬───────┘                                                   │
│         │ tracked faces with IDs                                    │
│         ▼                                                           │
│  ┌──────────────┐    Reject: blurry, too small,                    │
│  │ Quality Gate  │──  extreme angle, occluded                       │
│  └──────┬───────┘                                                   │
│         │ quality-passed faces only                                  │
│         ▼                                                           │
│  ┌──────────────┐    Affine transform using                        │
│  │ Face Aligner  │──  5 landmarks → 112×112 crop                    │
│  └──────┬───────┘                                                   │
│         │ aligned 112×112 face                                      │
│         ▼                                                           │
│  ┌──────────────┐    ArcFace model (ONNX Runtime)                  │
│  │ Face Encoder  │──  Returns: 512-D L2-normalized                  │
│  │  (ArcFace)    │   embedding vector                               │
│  └──────┬───────┘                                                   │
│         │ 512-D embedding                                           │
│         ▼                                                           │
│  ┌──────────────┐    Search FAISS index of active                  │
│  │ Vector Search │──  missing-person embeddings                     │
│  │   (FAISS)     │   Returns: top-K candidates + distances          │
│  └──────┬───────┘                                                   │
│         │ candidate matches                                         │
│         ▼                                                           │
│  ┌──────────────┐    Aggregate scores across                       │
│  │  Temporal     │──  temporal_window_size frames                   │
│  │  Verifier     │   for same track ID                              │
│  └──────┬───────┘                                                   │
│         │ POSSIBLE MATCH (if mean > temporal_threshold)             │
│         ▼                                                           │
│  ┌──────────────┐    Crop face, save full frame,                   │
│  │  Evidence     │──  record metadata                               │
│  │  Collector    │                                                   │
│  └──────┬───────┘                                                   │
│         │ sighting event + evidence files                            │
│         ▼                                                           │
│     ALERT (Operator Desktop + Upload to Backend)                    │
└─────────────────────────────────────────────────────────────────────┘
```

### 6.2 Face Detector: SCRFD

```python
# Pseudocode for face_detector.py

class FaceDetector:
    """
    SCRFD face detector via ONNX Runtime.
    Input:  BGR frame (any size)
    Output: List[Detection] where Detection = {
        bbox: [x1, y1, x2, y2],        # pixel coordinates
        score: float,                    # confidence 0.0–1.0
        landmarks: [[x,y] × 5]          # left_eye, right_eye, nose, left_mouth, right_mouth
    }
    
    Model: det_10g.onnx from InsightFace buffalo_l pack
    Input size: 640×640 (auto-resized with letterboxing)
    """
    
    def __init__(self, model_path: str, threshold: float = 0.5):
        self.session = ort.InferenceSession(model_path, providers=['CPUExecutionProvider'])
        self.threshold = threshold
    
    def detect(self, frame: np.ndarray) -> List[Detection]:
        # 1. Resize + letterbox to 640×640
        # 2. Normalize to [0, 1]
        # 3. Run ONNX inference
        # 4. Post-process: NMS, threshold filtering
        # 5. Return detections
        ...
```

### 6.3 Face Recognizer: ArcFace

```python
# Pseudocode for face_recognizer.py

class FaceRecognizer:
    """
    ArcFace face recognition via ONNX Runtime.
    Input:  Aligned face image (112×112×3 RGB)
    Output: 512-D L2-normalized embedding (np.ndarray, float32)
    
    Model: w600k_r50.onnx from InsightFace buffalo_l pack
    Trained on: WebFace600K dataset
    Embedding: 512 dimensions, L2-normalized
    
    Similarity metric: Cosine similarity (dot product of L2-normed vectors)
        score = np.dot(embedding_a, embedding_b)
        range: [-1.0, 1.0], higher = more similar
        typical threshold: 0.3–0.5
    """
    
    def __init__(self, model_path: str):
        self.session = ort.InferenceSession(model_path, providers=['CPUExecutionProvider'])
    
    def get_embedding(self, aligned_face: np.ndarray) -> np.ndarray:
        # 1. Ensure 112×112×3 RGB
        # 2. Normalize pixel values
        # 3. Run ONNX inference
        # 4. L2-normalize output
        # 5. Return 512-D vector
        ...
```

### 6.4 ByteTrack Tracker

```python
# Pseudocode for tracker.py

class ByteTracker:
    """
    Multi-object tracker based on ByteTrack algorithm.
    
    Purpose: Assign persistent IDs to faces across frames so we
    don't run recognition on every detection every frame.
    
    Algorithm:
    1. High-confidence detections matched to existing tracks via IoU
    2. Low-confidence detections matched to remaining tracks (second association)
    3. Unmatched detections → new tracks
    4. Unmatched tracks → increment age, remove if too old
    
    Each track maintains:
    - track_id: int (persistent across frames)
    - bbox: current bounding box (Kalman-predicted if no detection)
    - state: TRACKED | LOST
    - recognition_history: List[{person_id, similarity}]
    - last_recognition_time: timestamp
    """
    
    def update(self, detections: List[Detection]) -> List[Track]:
        # 1. Split detections by confidence (high > 0.5, low 0.1–0.5)
        # 2. Match high-confidence detections to active tracks (IoU)
        # 3. Match low-confidence detections to remaining tracks
        # 4. Create new tracks for unmatched detections
        # 5. Update Kalman filter for all tracks
        # 6. Remove old lost tracks
        # 7. Return active tracks
        ...
```

### 6.5 Temporal Verification

```python
# Pseudocode for temporal_verifier.py

class TemporalVerifier:
    """
    Aggregates recognition scores across multiple frames for the same
    tracked person before declaring a 'Possible Match'.
    
    This is the KEY RESEARCH CONTRIBUTION:
        Single-frame matching → unreliable (one bad angle = false positive)
        Temporal matching → collect N scores, compute mean → much more reliable
    
    Config:
        window_size: 5      # Need at least 5 recognition results
        threshold: 0.42     # Mean similarity must exceed this
    
    Process for each track:
        Frame 1: track #42 → similarity to MP-102 = 0.45
        Frame 2: track #42 → similarity to MP-102 = 0.41
        Frame 3: track #42 → similarity to MP-102 = 0.48
        Frame 4: track #42 → similarity to MP-102 = 0.43
        Frame 5: track #42 → similarity to MP-102 = 0.46
        
        Mean = 0.446 > threshold(0.42) → POSSIBLE MATCH ✓
        
    Anti-spam: Once a match is triggered for a (track_id, person_id) pair,
    suppress further alerts for that pair for 5 minutes.
    """
    
    def check_match(self, track_id: int, person_id: str, 
                    similarity: float) -> Optional[MatchEvent]:
        # 1. Append score to track's history for this person_id
        # 2. If len(history) >= window_size
        # 3.   Compute mean of last window_size scores
        # 4.   If mean >= threshold AND not recently alerted
        # 5.     Return MatchEvent
        # 6.   Else return None
        ...
```

### 6.6 Vector Search (FAISS)

```python
# Pseudocode for vector_search.py

class VectorSearchEngine:
    """
    FAISS-based vector similarity search for face embeddings.
    
    Index type: IndexFlatIP (inner product = cosine similarity for L2-normed vectors)
    Why not IVF/HNSW: For MVP with < 1000 missing persons, brute-force
    flat search is fast enough and gives exact results. Switch to IVF 
    for > 10K persons.
    
    Operations:
        - rebuild_index(embeddings): Replace entire index with fresh data from backend
        - search(query_embedding, top_k=5): Return top-K nearest missing persons
        - add_person(person_id, embedding): Add a single person
        - remove_person(person_id): Remove a person (when case closed)
    """
    
    def __init__(self, dimension: int = 512):
        self.index = faiss.IndexFlatIP(dimension)
        self.person_ids: List[str] = []    # Maps index position → person_id
    
    def search(self, query: np.ndarray, top_k: int = 5) -> List[Tuple[str, float]]:
        # Returns: [(person_id, similarity_score), ...]
        ...
```

### 6.7 Face Quality Gate

```python
# Pseudocode for face_quality.py

class FaceQualityChecker:
    """
    Filters out low-quality face detections before running recognition.
    This saves CPU and prevents false positives from bad inputs.
    
    Quality checks (in order, each can reject):
    
    1. MINIMUM SIZE:  face_width >= 40px AND face_height >= 40px
       Why: ArcFace needs 112×112 input; upscaling a 20px face = garbage embedding
    
    2. BLUR CHECK:    Laplacian variance >= 100
       Why: Motion blur destroys facial features
       Method: cv2.Laplacian(gray_face, cv2.CV_64F).var()
    
    3. ASPECT RATIO:  0.6 <= (width/height) <= 1.2
       Why: Extreme aspect ratios indicate occluded or side-angle faces
    
    4. LANDMARK CHECK: All 5 landmarks must be inside the bounding box
       Why: If landmarks are outside bbox, alignment will produce garbage
    """
    
    def is_quality_sufficient(self, face_crop: np.ndarray, 
                               bbox: list, landmarks: list) -> Tuple[bool, str]:
        # Returns (passed: bool, reason: str if rejected)
        ...
```

---

## 7. Data Access Layer — Backend CRUD

```python
# ── crud/face_embedding.py ──

async def store_embedding(db: AsyncSession, person_id: UUID, photo_id: UUID,
                          embedding: np.ndarray, quality_score: float) -> FaceEmbedding:
    """Store a 512-D face embedding. Converts numpy to bytes via .tobytes()"""

async def get_active_embeddings(db: AsyncSession) -> List[dict]:
    """
    Returns all embeddings for ACTIVE missing persons.
    Used by Edge Agents during sync.
    Returns: [{person_id, person_name, embedding_bytes, photo_url}, ...]
    """

async def get_embeddings_since(db: AsyncSession, since: datetime) -> List[dict]:
    """
    Returns embeddings created/updated since a timestamp.
    Used for incremental sync by Edge Agents.
    """

# ── crud/sighting.py ──

async def create_sighting(db: AsyncSession, sighting: SightingCreate) -> Sighting:
    """Create a new sighting event from Edge Agent detection."""

async def list_sightings_for_person(db: AsyncSession, person_id: UUID) -> List[Sighting]:
    """All sightings for a missing person, ordered by detected_at DESC."""

async def confirm_sighting(db: AsyncSession, sighting_id: UUID, 
                            reviewer_id: UUID, notes: str) -> Sighting:
    """Operator confirms a sighting. Triggers notification to reporter."""

async def reject_sighting(db: AsyncSession, sighting_id: UUID,
                           reviewer_id: UUID, notes: str) -> Sighting:
    """Operator rejects a false positive."""

async def get_person_timeline(db: AsyncSession, person_id: UUID) -> List[dict]:
    """
    Returns chronological list of sightings across cameras.
    The "last seen" feature.
    Returns: [{camera_name, location, detected_at, similarity}, ...]
    """
```

---

## 8. Action / Tool Layer — Backend Services

```python
# ── services/face_processing.py ──

async def process_uploaded_photo(photo_path: str, person_id: UUID, 
                                  photo_id: UUID, db: AsyncSession) -> dict:
    """
    Called when: User uploads a photo of a missing person.
    
    Steps:
    1. Load image from photo_path
    2. Run SCRFD face detection
    3. If no face found → mark photo as NO_FACE, return error
    4. If multiple faces → use the largest face
    5. Align face using 5 landmarks → 112×112 crop
    6. Save aligned face crop to uploads/faces/
    7. Run ArcFace → 512-D embedding
    8. Store embedding in DB via crud.store_embedding()
    9. Mark photo as SUCCESS
    10. If all photos for this person are processed → mark report ACTIVE
    
    Returns: {status, face_crop_path, quality_score}
    """

# ── services/embedding_sync.py ──

async def get_sync_package(since: Optional[datetime] = None) -> dict:
    """
    Called when: Edge Agent requests missing-person embeddings.
    
    Returns: {
        full_sync: bool,
        persons: [{
            person_id, name, embeddings: [bytes], photo_url
        }],
        removed_ids: [person_ids no longer active],
        sync_timestamp: datetime
    }
    """

# ── services/notification_service.py ──

async def send_sighting_notification(user_id: UUID, sighting: Sighting) -> None:
    """
    Called when: A new sighting is created by an Edge Agent.
    
    Steps:
    1. Get user's FCM token from DB
    2. Build notification payload (bilingual title/body)
    3. Send via Firebase Cloud Messaging
    4. Store notification record in DB
    5. Publish SSE event for real-time Flutter update
    """
```

---

## 9. Orchestration — Edge Agent Main Loop

```python
# ── edge_agent/ai/pipeline.py ──

class AIPipeline:
    """
    Main AI pipeline orchestrator. Runs one instance per camera.
    Executed in a dedicated thread (QThread) to avoid blocking GUI.
    
    Lifecycle:
    1. INIT: Load models, connect to camera stream
    2. RUN: Process frames continuously at 1 FPS
    3. PAUSE: Stop processing but keep stream alive
    4. STOP: Release all resources, close stream
    
    On temporary backend unavailability:
        - Continue processing locally
        - Queue sighting events in SQLite (pending_sightings)
        - Upload queued events when connection restores
    """
    
    def __init__(self, camera_config, models, faiss_index, backend_client):
        self.detector = FaceDetector(models.det_path)
        self.recognizer = FaceRecognizer(models.rec_path)
        self.tracker = ByteTracker()
        self.quality_checker = FaceQualityChecker()
        self.verifier = TemporalVerifier(window_size=5, threshold=0.42)
        self.vector_search = faiss_index
        self.evidence = EvidenceCollector()
        self.state = PipelineState.INIT
    
    def process_frame(self, frame: np.ndarray, timestamp: datetime):
        """
        Process one frame. Called at 1 FPS per camera.
        
        SEQUENCE IS CRITICAL — do not reorder:
        1. Detect faces (SCRFD)
        2. Update tracker (ByteTrack) — must happen before quality check
           so track IDs are assigned even to low-quality detections
        3. Quality filter — only pass good faces to recognition
        4. For each quality-passed face:
           a. Align face
           b. Extract embedding (ArcFace)
           c. Search FAISS for candidates
           d. For each candidate above loose threshold (0.3):
              - Feed to temporal verifier
              - If temporal verifier triggers → create sighting event
        """
        
        # 1. Detect
        detections = self.detector.detect(frame)
        
        # 2. Track
        tracks = self.tracker.update(detections)
        
        # 3–4. For each tracked face
        for track in tracks:
            if not track.needs_recognition():  # throttle: once per 5 seconds
                continue
            
            if not self.quality_checker.is_quality_sufficient(track.face_crop, ...):
                continue
            
            # Align
            aligned = self.aligner.align(frame, track.landmarks)
            
            # Embed
            embedding = self.recognizer.get_embedding(aligned)
            
            # Search
            candidates = self.vector_search.search(embedding, top_k=5)
            
            # Temporal verify
            for person_id, similarity in candidates:
                if similarity < 0.3:
                    continue
                    
                match = self.verifier.check_match(
                    track.track_id, person_id, similarity
                )
                
                if match:
                    evidence = self.evidence.capture(
                        frame, track, person_id, similarity, timestamp
                    )
                    self.on_possible_match(match, evidence)  # Emit signal to GUI
```

---

## 10. API Layer — REST Endpoints

```python
# ── api/reports.py ── (mirrors crud/missing_person.py)

# POST   /api/reports/                      → Create missing person report
# GET    /api/reports/                      → List user's reports
# GET    /api/reports/{id}                  → Get report detail
# PUT    /api/reports/{id}                  → Update report (status, info)
# DELETE /api/reports/{id}                  → Close/delete report
# POST   /api/reports/{id}/photos           → Upload photos for report
# GET    /api/reports/{id}/sightings        → Get sightings for this person
# GET    /api/reports/{id}/timeline         → Get sighting timeline (last-seen)

# ── api/agents.py ──

# POST   /api/agents/register              → Register new Edge Agent device
# POST   /api/agents/{id}/heartbeat        → Agent heartbeat (status, stats)
# GET    /api/agents/                       → List registered agents (admin)

# ── api/embeddings.py ──

# GET    /api/embeddings/sync              → Edge Agent fetches active embeddings
#        Query params: ?since=<ISO timestamp> for incremental sync

# ── api/sightings.py ──

# POST   /api/sightings/                   → Edge Agent reports a sighting
# GET    /api/sightings/{id}               → Get sighting detail with evidence
# PUT    /api/sightings/{id}/confirm       → Operator confirms sighting
# PUT    /api/sightings/{id}/reject        → Operator rejects sighting

# ── api/sse.py ──

# GET    /api/events/stream                → SSE stream for real-time updates
#        Auth: Firebase token in query param
#        Events: new_sighting, sighting_confirmed, report_status_change

# ── api/auth.py ──

# Middleware: Verifies Firebase ID token from Authorization header
# Sets request.state.user with firebase_uid + user data
```

---

## 11. Frontend / UI Spec

### Flutter App — Screen Structure

```
Splash Screen
    │
    ├── Login Screen (Firebase Auth: Google + Email/Password)
    │
    └── Main Shell (Bottom Navigation)
        │
        ├── 🏠 Home Tab
        │   └── Dashboard: active reports count, recent sightings,
        │       system status, quick-action buttons
        │
        ├── 📋 Reports Tab
        │   ├── My Reports List (cards with status badges)
        │   │   └── Report Detail
        │   │       ├── Person info + photos
        │   │       ├── Sightings list
        │   │       └── Timeline view (last-seen map)
        │   │
        │   └── FAB: + New Report
        │       └── Report Form
        │           ├── Person details (name, age, description)
        │           ├── Photo upload (1-4 photos)
        │           └── Last seen info
        │
        ├── 🔔 Notifications Tab
        │   └── Notification list (new sightings, confirmations)
        │       └── Tap → Sighting Detail
        │           ├── Evidence image (zoomable)
        │           ├── Camera + location info
        │           ├── Timestamp
        │           └── Similarity gauge
        │
        └── 👤 Profile Tab
            ├── User info
            ├── Language toggle (EN / HI)
            └── Logout
```

### Edge Agent — PySide6 Window Layout

```
╔══════════════════════════════════════════════════════════════════╗
║  FindMissingPerson — AI CCTV Agent        [_] [□] [×]          ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║  ┌─────────────────────┐  ┌─────────────────────┐              ║
║  │  CAM-01: Main Gate  │  │  CAM-02: Lobby      │              ║
║  │  ┌───────────────┐  │  │  ┌───────────────┐  │              ║
║  │  │  Live Preview  │  │  │  │  Live Preview  │  │              ║
║  │  │  + face boxes  │  │  │  │  + face boxes  │  │              ║
║  │  │  + track IDs   │  │  │  │  + track IDs   │  │              ║
║  │  └───────────────┘  │  │  └───────────────┘  │              ║
║  │  Faces: 3  FPS: 1.0 │  │  Faces: 1  FPS: 1.0 │              ║
║  └─────────────────────┘  └─────────────────────┘              ║
║                                                                  ║
║  ┌─────────────────────┐  ┌─────────────────────┐              ║
║  │  CAM-03: Parking    │  │  CAM-04: Exit       │              ║
║  │  ┌───────────────┐  │  │  ┌───────────────┐  │              ║
║  │  │  Live Preview  │  │  │  │  Live Preview  │  │              ║
║  │  └───────────────┘  │  │  └───────────────┘  │              ║
║  └─────────────────────┘  └─────────────────────┘              ║
║                                                                  ║
╠══════════════════════════════════════════════════════════════════╣
║  Status: ● AI Running  │  Active Cases: 12  │  CPU: 45%        ║
║  Uptime: 3h 42m       │  Faces Today: 1,247 │  Last Sync: 2m  ║
╠══════════════════════════════════════════════════════════════════╣
║  📋 Recent Alerts                                    [Settings] ║
║  ├── 🚨 14:32 — Possible Match: MP-102 (92%) — CAM-07  [VIEW] ║
║  ├── 🚨 13:15 — Possible Match: MP-089 (88%) — CAM-01  [VIEW] ║
║  └── ✅ 12:01 — Confirmed: MP-076 — CAM-03                     ║
╚══════════════════════════════════════════════════════════════════╝
```

### Alert Popup (modal when match detected)

```
╔══════════════════════════════════════╗
║ 🚨 POSSIBLE MATCH                    ║
╠══════════════════════════════════════╣
║                                      ║
║  [Reference Photo]  [CCTV Capture]   ║
║                                      ║
║  Person: Rahul Kumar (MP-102)        ║
║  Camera: CAM-07 — Main Gate          ║
║  Time: 14:32:17                      ║
║  Similarity: 92.3%                   ║
║  Frames Matched: 5                   ║
║                                      ║
║  ┌─────────┐    ┌─────────┐         ║
║  │ CONFIRM  │    │ REJECT  │         ║
║  └─────────┘    └─────────┘         ║
╚══════════════════════════════════════╝
```

### Enums / Reference Data (UI Dropdowns)

```
Report Status:    PROCESSING | ACTIVE | FOUND | CLOSED
Gender:           male | female | other
Sighting Status:  PENDING | CONFIRMED | REJECTED
Confidence:       POSSIBLE | PROBABLE | CONFIRMED | REJECTED
Agent Status:     ONLINE | OFFLINE | ERROR
Camera Status:    ACTIVE | INACTIVE | ERROR
Notification Type: SIGHTING | CONFIRMATION | STATUS_CHANGE
Language:         en | hi
```

---

## 12. Critical Architecture Rules — DO NOT DEVIATE

### Rule 1: Never alert from a single frame

```python
# ❌ WRONG — single frame alert
if similarity > 0.4:
    send_alert(person_id)  # Will produce MANY false positives

# ✅ CORRECT — temporal verification over N frames
scores = track.get_recent_scores(person_id, window=5)
if len(scores) >= 5 and np.mean(scores) > 0.42:
    send_alert(person_id)  # Much more reliable
```

### Rule 2: Track first, then recognize — never the reverse

```python
# ❌ WRONG — recognize every detection every frame
for detection in detections:
    embedding = recognizer.get_embedding(detection.face)  # CPU-killer
    results = search(embedding)  # 25 FPS × N faces × N missing persons

# ✅ CORRECT — track assigns IDs, recognize only once per 5 seconds per track
tracks = tracker.update(detections)
for track in tracks:
    if track.time_since_last_recognition() < 5.0:  # Throttle
        continue
    embedding = recognizer.get_embedding(track.best_face)
    results = search(embedding)
```

### Rule 3: ONNX Runtime only — no PyTorch at inference time in Edge Agent

```python
# ❌ WRONG — PyTorch in production Edge Agent
import torch
model = torch.load("arcface.pth")
with torch.no_grad():
    embedding = model(face_tensor)  # Slow, huge memory, needs full PyTorch

# ✅ CORRECT — ONNX Runtime for inference
import onnxruntime as ort
session = ort.InferenceSession("w600k_r50.onnx", providers=['CPUExecutionProvider'])
embedding = session.run(None, {"input": face_array})[0]  # Fast, small footprint
```

### Rule 4: Edge Agent must work offline — queue sightings locally

```python
# ❌ WRONG — crash or skip sighting when backend is down
try:
    response = api_client.post("/sightings/", data=sighting)
except ConnectionError:
    pass  # Sighting LOST forever

# ✅ CORRECT — queue locally, sync when online
try:
    response = api_client.post("/sightings/", data=sighting)
except ConnectionError:
    local_db.save_pending_sighting(sighting)  # SQLite queue
    
# Background sync worker periodically retries pending sightings
```

### Rule 5: Face embeddings are stored as bytes, compared as float32 vectors

```python
# ❌ WRONG — store embedding as JSON array of floats
embedding_json = json.dumps(embedding.tolist())  # Huge, slow to parse

# ✅ CORRECT — store as binary, reconstruct as numpy
embedding_bytes = embedding.astype(np.float32).tobytes()  # 2048 bytes for 512-D
# To reconstruct:
embedding = np.frombuffer(embedding_bytes, dtype=np.float32)  # Zero-copy
```

### Rule 6: RTSP stream reader must auto-reconnect

```python
# ❌ WRONG — crash on stream disconnect
cap = cv2.VideoCapture(rtsp_url)
while True:
    ret, frame = cap.read()
    if not ret:
        break  # Camera disconnected → entire pipeline stops

# ✅ CORRECT — reconnect with exponential backoff
class RTSPReader:
    def read_frame(self):
        ret, frame = self.cap.read()
        if not ret:
            self.reconnect(max_retries=5, backoff_seconds=[1, 2, 5, 10, 30])
            # Log warning, continue with next frame
            return None
        return frame
```

---

## 13. Deployment

### Step 1: Backend (Docker — for testing)

```bash
# 1. Clone repository
git clone <repo-url> && cd FIND-MISSING-PEP

# 2. Copy environment file
cp backend/.env.example backend/.env
# Edit backend/.env with your Firebase credentials + secrets

# 3. Place Firebase service account JSON
cp /path/to/firebase-sa.json backend/firebase-sa.json

# 4. Start all services
docker compose up -d --build

# 5. Verify — successful startup log should show:
# ╔══════════════════════════════════════════╗
# ║  FindMissingPerson Backend Starting...   ║
# ║  APP_ENV:    development                 ║
# ║  PORT:       8000                        ║
# ╚══════════════════════════════════════════╝
# Running database migrations...
# Starting uvicorn...
# INFO: Uvicorn running on http://0.0.0.0:8000

# 6. Test API
curl http://localhost:8000/docs  # Should show Swagger UI
```

### Step 2: Edge Agent (Windows Development)

```powershell
# 1. Create virtual environment
cd edge_agent
python -m venv .venv
.venv\Scripts\Activate.ps1

# 2. Install dependencies
pip install -r requirements.txt

# 3. Download AI models
python ../scripts/download_models.py --output ./models

# 4. Configure
# Edit config.ini with backend URL

# 5. Run
python main.py

# 6. Verify — GUI should open with:
# - Login dialog
# - After login: camera discovery screen
# - After config: main monitoring window with status bar showing "AI Ready"
```

### Step 3: Flutter App

```bash
# 1. Setup
cd flutter_app
flutter pub get

# 2. Configure Firebase
# Place google-services.json in android/app/
# Place GoogleService-Info.plist in ios/Runner/

# 3. Run
flutter run

# 4. Verify
# - Splash screen → Login screen
# - Google Sign-In works
# - Home dashboard loads with 0 reports
```

### Step 4: Build Edge Agent Executable (for distribution)

```powershell
cd edge_agent
pyinstaller --onedir --windowed --name "FindMissingPerson-Agent" `
    --add-data "models;models" `
    --add-data "ui/resources;ui/resources" `
    --icon "ui/resources/icons/app.ico" `
    main.py
```

---

## 14. Known Gotchas Table

| # | Symptom | Root Cause | Fix |
|---|---|---|---|
| 1 | ONNX Runtime crash on startup | Wrong model version or missing DLLs | Use `onnxruntime==1.17.x`, ensure Visual C++ Redistributable installed |
| 2 | RTSP stream shows green/corrupted frames | Network congestion / TCP packet loss | Use `cv2.CAP_PROP_BUFFERSIZE=1` + TCP transport: `rtsp_url + "?rtsp_transport=tcp"` |
| 3 | FAISS crashes with `Segmentation fault` | Embedding dimension mismatch (512 vs 256) | Verify all embeddings are exactly 512-D float32. Check model output shape. |
| 4 | Very high false positive rate | Single-frame matching, no temporal verification | Implement temporal verification (Rule 1). Tune threshold. |
| 5 | Edge Agent CPU at 100% | Processing too many FPS or no recognition throttle | Set `frame_sample_fps=1`. Throttle recognition to once per 5s per track. |
| 6 | Firebase Auth token expired (401) | Flutter not refreshing token | Use `user.getIdToken(true)` to force refresh before API calls |
| 7 | ONVIF discovery finds no cameras | Camera on different subnet / ONVIF disabled | Manual RTSP URL entry as fallback. Check camera firmware settings. |
| 8 | `insightface` downloads models to wrong path | Default download dir is `~/.insightface` | Set `root=` parameter in `FaceAnalysis(root='./ai_models')` |
| 9 | PySide6 crashes on exit | QThread still running when window closes | Signal threads to stop, `thread.wait()` in `closeEvent()` |
| 10 | SSE connection drops silently | Proxy/load balancer timeout | Implement heartbeat ping every 30s + auto-reconnect in client |

---

## 15. Reference Data

### Face Detection Thresholds

```
SCRFD Confidence:     0.5 (default), lower = more detections but more noise
ArcFace Similarity:   Range [-1.0, 1.0] (cosine similarity)
                      0.2–0.3 = different person (usually)
                      0.3–0.4 = uncertain
                      0.4–0.6 = same person (likely)
                      0.6+    = same person (high confidence)
```

### Model Files (InsightFace buffalo_l)

```
det_10g.onnx         — SCRFD face detector    (~16 MB)
w600k_r50.onnx       — ArcFace recognizer     (~166 MB)
```

### ByteTrack Parameters

```
track_high_thresh:    0.5     — High confidence detection threshold
track_low_thresh:     0.1     — Low confidence (for second association)
new_track_thresh:     0.6     — Minimum confidence to create new track
match_thresh:         0.8     — IoU threshold for matching
track_buffer:         30      — Frames to keep lost tracks alive
```

---

## 16. Cost / Ops Reference

| Resource | Estimate |
|---|---|
| Backend VPS (2 vCPU, 4GB RAM) | ~$20/month (DigitalOcean) |
| PostgreSQL (managed, smallest) | ~$15/month or Docker on same VPS |
| Firebase Auth | Free tier (50K MAU) |
| Firebase Cloud Messaging | Free (unlimited) |
| ONNX model download | One-time, ~180 MB total |
| Edge Agent hardware | Any Windows PC with 4+ GB RAM, 4+ cores, no GPU needed |
| Per-sighting storage | ~50 KB (face crop + metadata) |
| Edge Agent CPU usage (4 cameras, 1 FPS) | ~30–50% of a modern 4-core CPU |

---

## 17. External API Integration Reference

### Firebase Admin — Verify Token

```python
from firebase_admin import auth, credentials, initialize_app

cred = credentials.Certificate("firebase-sa.json")
initialize_app(cred)

# Verify ID token from Flutter
decoded = auth.verify_id_token(id_token)
uid = decoded['uid']
```

### Firebase Cloud Messaging — Send Push

```python
from firebase_admin import messaging

message = messaging.Message(
    notification=messaging.Notification(
        title="🚨 Possible Sighting",
        body="A possible match for Rahul Kumar was detected at Main Gate",
    ),
    data={"sighting_id": str(sighting_id), "type": "SIGHTING"},
    token=user_fcm_token,
)
response = messaging.send(message)
```

### InsightFace — Model Loading

```python
from insightface.app import FaceAnalysis

app = FaceAnalysis(name='buffalo_l', root='./ai_models',
                   providers=['CPUExecutionProvider'])
app.prepare(ctx_id=-1, det_size=(640, 640))  # ctx_id=-1 = CPU

# Detect + embed in one call
faces = app.get(frame)
for face in faces:
    bbox = face.bbox        # [x1, y1, x2, y2]
    landmarks = face.kps    # 5×2 array
    embedding = face.embedding  # 512-D vector
    det_score = face.det_score  # confidence
```

### ONVIF Camera Discovery

```python
from wsdiscovery import WSDiscovery

wsd = WSDiscovery()
wsd.start()
services = wsd.searchServices()
for service in services:
    xaddrs = service.getXAddrs()
    # Extract RTSP URL from ONVIF device
    # Typically: rtsp://<ip>:<port>/stream1
wsd.stop()
```

---

## 18. One-Time Setup Sequence (Post-Deploy)

```
1. Start Docker services (backend + postgres + redis)
2. Verify backend is running: curl http://localhost:8000/docs
3. Create Firebase project at https://console.firebase.google.com
4. Enable Authentication → Email/Password + Google Sign-In
5. Download Firebase service account JSON → place as backend/firebase-sa.json
6. Download google-services.json → place in flutter_app/android/app/
7. Download AI models: python scripts/download_models.py
8. Run Flutter app → Register first user
9. Create a test missing-person report with 2-3 photos
10. Verify photos are processed: report status changes from PROCESSING → ACTIVE
11. Start Edge Agent → Login → Discover cameras (or enter RTSP URL manually)
12. Start monitoring → Verify faces are detected (check status bar)
13. Walk past a camera → Verify face detection works
14. Test with a photo of the reported person → Verify possible match alert fires
15. Confirm/reject the sighting from Edge Agent UI
16. Verify Flutter app receives notification
```

---

## Development Phase Order

> [!IMPORTANT]
> Build in this exact sequence. Each phase depends only on completed phases.

```mermaid
graph TD
    P1["Phase 1: Face Recognition Core<br/>Prove ArcFace works on test images"]
    P2["Phase 2: Real-Time CCTV Pipeline<br/>Detection + Tracking + Recognition on video"]
    P3["Phase 3: Edge Agent Desktop App<br/>PySide6 GUI + camera integration"]
    P4["Phase 4: Backend API<br/>FastAPI + PostgreSQL + embedding storage"]
    P5["Phase 5: Flutter Mobile App<br/>Report submission + notifications"]
    P6["Phase 6: End-to-End Integration<br/>Connect all 3 components"]
    P7["Phase 7: Research Experiments<br/>Single-frame vs Temporal comparison"]
    
    P1 --> P2
    P2 --> P3
    P3 --> P4
    P4 --> P5
    P5 --> P6
    P6 --> P7
```

| Phase | Deliverable | Estimated Duration |
|---|---|---|
| 1 — Face Recognition | Notebook proving ArcFace works on LFW dataset | 1 week |
| 2 — CCTV Pipeline | Script processing recorded CCTV video → face detection + tracking + recognition | 2 weeks |
| 3 — Edge Agent | Working PySide6 desktop app with camera feed + AI pipeline | 2–3 weeks |
| 4 — Backend | FastAPI running in Docker with all endpoints + DB schema | 2 weeks |
| 5 — Flutter App | Working app with login, report submission, notifications | 2 weeks |
| 6 — Integration | All 3 components talking to each other end-to-end | 1–2 weeks |
| 7 — Research | Experiments, metrics, paper draft | 2–3 weeks |
| **Total** | | **~12–15 weeks** |

---

## Research Paper Direction

### Title
**"Temporal Face Matching for Real-Time Missing-Person Detection in CCTV Networks"**

### Key Experiments to Run

| # | Experiment | Metric |
|---|---|---|
| 1 | Single-frame vs temporal matching | Precision, Recall, F1, FAR, FRR |
| 2 | Temporal window size (3 vs 5 vs 10 frames) | F1, latency to alert |
| 3 | Similarity threshold tuning (0.3 to 0.6) | ROC curve, EER |
| 4 | SCRFD vs RetinaFace (detector comparison) | Detection mAP, FPS, CPU usage |
| 5 | Difficult conditions: low-res, blur, angles | Recognition accuracy per condition |
| 6 | Number of concurrent cameras vs CPU usage | Scalability curve |
| 7 | Offline resilience: event queue reliability | Zero-loss under 5min disconnection |

---

## User Review Required

> [!IMPORTANT]
> **Research Focus Confirmation**: The architecture prioritizes a rigorous, measurable AI pipeline (temporal face matching) as the core research contribution, while keeping Flutter and Backend functional but simpler. Is this the right balance?

> [!IMPORTANT]
> **CPU-Only Performance**: At 1 FPS × 4 cameras on CPU-only hardware (4-core i5), expect ~40–50% CPU usage with ONNX Runtime. On weaker machines this may be tight. Should we design an adaptive mode that drops to 0.5 FPS if CPU > 80%?

> [!IMPORTANT]
> **Firebase Dependency**: Firebase Auth + FCM provides quick authentication and push notifications, but ties you to Google's ecosystem. For a university research project this is fine, but for production you may want to replace with self-hosted auth later. Acceptable for now?

## Open Questions

> [!WARNING]
> **Paper Submission Target**: Which conference or journal are you targeting? This affects the depth of experiments needed (e.g., a top-tier venue needs more baselines and ablation studies).

> [!WARNING]
> **Test Dataset**: Will you use public datasets (LFW, IJB-C, ChokePoint) or record your own CCTV footage? Recording custom footage gives more realistic results for the paper but requires ethical clearance.
