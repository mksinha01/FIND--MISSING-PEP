# FIND-MISSING-PEP — Complete Architecture Document

> **One-Shot Build Spec**: This document is the single source of truth for building the AI-Based Missing Person Detection & Real-Time CCTV Monitoring System. It follows the [One-Shot-Build-Spec template](file:///c:/SSD%20WINDOW/code/FIND%20-MISSING%20PEP/DOC/3-One-Shot-Build-Spec.md) exactly — config → schema → logic → API → UI → rules → ops. **Build this exactly. Do not skip anything. Do not simplify. Do not substitute libraries.**

---

## Table of Contents

| § | Section | Purpose |
|---|---------|---------|
| 0 | [Mission Statement](#0-mission-statement) | What the system does, tech stack, build instruction |
| 1 | [File Structure](#1-file-structure--complete-project-tree) | Every file named, one-line responsibility each |
| 2 | [Environment / Secrets](#2-environment--secrets) | Full `.env` templates, precedence chain, `.gitignore` |
| 3 | [Data Schema](#3-data-schema) | All DDL (PostgreSQL + SQLite), idempotent, access control stated |
| 4 | [Dependencies](#4-dependencies) | Pinned/minimum-version lists for all three components |
| 5 | [Containerization / Runtime](#5-containerization--runtime) | Dockerfile, start.sh, docker-compose, port ownership |
| 6 | [Domain Config / AI Pipeline](#6-domain-config--ai-pipeline-architecture) | Full AI pipeline breakdown — the core research novelty |
| 7 | [Data Access Layer](#7-data-access-layer--backend-crud) | One function = one query, grouped by table |
| 8 | [Action / Tool Layer](#8-action--tool-layer--backend-services) | Business logic, external calls, graceful fallbacks |
| 9 | [Orchestration / Entrypoint](#9-orchestration--edge-agent-main-loop) | Edge Agent pipeline + Backend app factory |
| 10 | [API Layer](#10-api-layer--rest-endpoints) | REST endpoints mirroring data layer |
| 11 | [Frontend / UI Spec](#11-frontend--ui-spec) | Flutter screens, Edge Agent GUI, enums |
| 12 | [Critical Architecture Rules](#12-critical-architecture-rules--do-not-deviate) | Wrong-vs-correct from real bugs, not hypotheticals |
| 13 | [Deployment](#13-deployment) | Numbered steps from empty machine to running system |
| 14 | [Known Gotchas Table](#14-known-gotchas-table) | Symptom → Root Cause → Fix |
| 15 | [Reference Data](#15-reference-data) | Thresholds, model files, ByteTrack params, status enums |
| 16 | [Cost / Ops Reference](#16-cost--ops-reference) | Per-unit costs, worked examples |
| 17 | [External API Integration Reference](#17-external-api-integration-reference) | Copy-paste ready code snippets per service |
| 18 | [One-Time Setup Sequence](#18-one-time-setup-sequence-post-deploy) | Post-deploy checklist to make the product usable |
| A | [Development Phase Order](#appendix-a-development-phase-order) | Build sequence, timeline, dependencies |
| B | [Research Paper Direction](#appendix-b-research-paper-direction) | Title, experiments, metrics |

---

## 0. Mission Statement

### What the System Does

An installable Edge-AI CCTV application that connects to existing CCTV infrastructure, continuously detects and recognizes faces against active missing-person reports, identifies possible sightings in real time, records where and when they occurred, and sends the evidence to the user through the central platform.

### Capabilities (verbs)

| # | Verb | Description |
|---|------|-------------|
| 1 | **Reports** | Missing persons with photos via a Flutter mobile app |
| 2 | **Processes** | Uploaded photos → face detection → alignment → 512-D ArcFace embedding |
| 3 | **Syncs** | Active missing-person embeddings to deployed CCTV Edge Agents |
| 4 | **Ingests** | Live RTSP/ONVIF CCTV streams at 10–15 FPS per camera (1–4 cameras MVP) |
| 5 | **Detects** | Faces using SCRFD detector via ONNX Runtime (CPU-only, lightweight models) |
| 6 | **Tracks** | Faces across frames at 10–15 FPS using ByteTrack (assigns persistent Track IDs + facial landmarks) |
| 7 | **Recognizes** | Faces against missing-person embedding database using ArcFace (sampled at 1.0s intervals per tracked face) + FAISS vector search |
| 8 | **Verifies** | Matches temporally (3–5 consecutive scores, mean similarity ≥ 0.60) before alerting |
| 9 | **Captures** | Evidence: cropped face (112×112), full frame, 15 FPS pre/post match clip (from circular buffer), camera UUID, GPS, timestamp |
| 10 | **Alerts** | Operators via desktop notification + FCM push (primary mobile background delivery) and SSE (in-app foreground) |
| 11 | **Logs** | Complete sighting timeline across cameras ("last seen" tracking) |
| 12 | **Supports** | Bilingual UI: Hindi + English |

### System Component Map

```
                    ┌─────────────────────────────────────────────────────────┐
                    │                    SYSTEM OVERVIEW                       │
                    │                                                         │
  ┌──────────┐     │  ┌──────────────────────────────────────────────────┐   │
  │          │     │  │              BACKEND (FastAPI)                    │   │
  │  Flutter │◄────┼──┤  REST API │ Postgres │ Redis │ Firebase          │   │
  │  Mobile  │ FCM │  │  Photo Processing │ Embedding Storage            │   │
  │   App    │+SSE │  │  Notification Service │ SSE Manager              │   │
  │          │────►┼──┤  Static File Mount (/uploads)                     │   │
  └──────────┘     │  └───────────────────────┬──────────────────────────┘   │
                    │                          │ Embedding Sync (HTTP)        │
                    │                          ▼                              │
                    │  ┌──────────────────────────────────────────────────┐   │
                    │  │            EDGE AGENT (PySide6 Desktop)          │   │
                    │  │                                                  │   │
                    │  │  ┌────────┐  ┌─────────┐  ┌──────────────────┐  │   │
                    │  │  │  CCTV  │─►│   AI    │─►│  Sighting Upload │  │   │
                    │  │  │ Reader │  │Pipeline │  │  + Local Queue   │  │   │
                    │  │  └────────┘  └─────────┘  └──────────────────┘  │   │
                    │  │                                                  │   │
                    │  │  SCRFD (15 FPS) → ByteTrack → Quality Gate       │   │
                    │  │  → ArcFace (1s throttle) → FAISS (atomic swap)   │   │
                    │  │  → Deduplication → Temporal Verification (≥0.60) │   │
                    │  │  → Circular Buffer Clip → Alert                  │   │
                    │  └──────────────────────────────────────────────────┘   │
                    └─────────────────────────────────────────────────────────┘
```

### Tech Stack

| Layer | Technology | Version | Role |
|---|---|---|---|
| Mobile | Flutter + Dart | 3.x / Dart 3.x | Report submission, notifications, sighting review (`flutter_map`, `fetch_client`) |
| Backend API | Python + FastAPI | 3.11+ / 0.110+ | REST API, photo processing, data hub, static file serving |
| Database | PostgreSQL | 16 | Persistent storage for all entities |
| Cache / Queue | Redis | 7 | Background tasks, SSE pub/sub (via `redis.asyncio`) |
| Auth | Firebase Auth | latest | User authentication (Google + Email) |
| Push Notifications | Firebase Cloud Messaging (FCM) | latest | Primary real-time push delivery to mobile app (background & closed) |
| Real-time Events | Server-Sent Events (SSE) | — | Live updates to Flutter (foreground) + Edge Agent |
| Face Detection | SCRFD (ONNX) | buffalo_l (`det_10g`, `det_2.5g`, or `det_0.5g`) | Multi-face detection with 5 landmarks |
| Face Recognition | ArcFace (ONNX) | buffalo_l (`w600k_r50`) | 512-D face embedding extraction |
| Face Tracking | ByteTrack + `lapx` | — | Multi-object tracking across frames at 10–15 FPS |
| Inference Runtime | ONNX Runtime | 1.17+ | CPU-only model inference |
| Vector Search | FAISS (CPU) | 1.7+ | Fast embedding similarity search (thread-safe atomic swap) |
| Edge Agent GUI | Python + PySide6 (Qt) | 6.6+ | Desktop monitoring application |
| CCTV Protocol | RTSP / ONVIF | — | Camera stream ingestion (hardware decode via DXVA2/D3D11) |
| Video Decode | OpenCV | 4.9+ | Frame capture and image processing (`cv2.CAP_FFMPEG`) |
| Containerization | Docker + Docker Compose | — | Backend deployment (Debian Bookworm compatible) |
| Photo Storage | Local filesystem | — | Uploaded photos, face crops, evidence (mounted at `/uploads`) |

### Build Instruction

> **Build this exactly. Do not skip anything. Do not simplify. Do not substitute libraries.** This is a research project focused on the AI pipeline — the Flutter app and backend can be functional but simple; the Edge Agent AI pipeline must be rigorous and measurable.

---

## 1. File Structure — Complete Project Tree

```
FIND-MISSING-PEP/
│
├── DOC/                                    ← Project documentation (existing)
│   ├── 1-BRAINSTROM.MD                     ← Initial brainstorming notes
│   ├── 2-DEMO-PROMT-FOR-BUID.MD           ← Demo prompt reference
│   ├── 3-One-Shot-Build-Spec.md            ← Build spec template methodology
│   ├── 4-Implementation-Plan.md            ← Detailed implementation plan
│   ├── 5-Architecture.md                   ← THIS DOCUMENT — full architecture
│   └── dump1-BRAINSTROM.MD                 ← Additional brainstorm dump
│
├── backend/                                ← FastAPI Backend (Component 2)
│   ├── Dockerfile                          ← Backend container definition
│   ├── docker-compose.yml                  ← Orchestrates backend + postgres + redis
│   ├── requirements.txt                    ← Pinned Python dependencies
│   ├── .env.example                        ← Environment variable template
│   ├── .gitignore                          ← Excludes .env, uploads/, __pycache__
│   ├── start.sh                            ← Entrypoint: echoes config, runs uvicorn
│   ├── alembic.ini                         ← DB migration config (points to app.database)
│   ├── alembic/                            ← Migration framework
│   │   ├── env.py                          ← Migration environment (uses async engine)
│   │   ├── script.py.mako                  ← Migration script template
│   │   └── versions/                       ← Individual migration files (auto-generated)
│   │       └── 001_initial_schema.py       ← First migration: all tables from §3
│   │
│   ├── app/
│   │   ├── __init__.py                     ← Package marker (empty)
│   │   ├── main.py                         ← FastAPI app factory, CORS, lifespan events
│   │   ├── config.py                       ← Settings from env vars (pydantic-settings)
│   │   ├── database.py                     ← Async SQLAlchemy engine + session factory
│   │   │
│   │   ├── models/                         ← SQLAlchemy ORM models (one class per table)
│   │   │   ├── __init__.py                 ← Imports all models for Alembic auto-detection
│   │   │   ├── base.py                     ← DeclarativeBase with common columns (id, timestamps)
│   │   │   ├── user.py                     ← User model (firebase_uid, name, phone, fcm_token)
│   │   │   ├── missing_person.py           ← MissingPerson report model (status lifecycle)
│   │   │   ├── photo.py                    ← Photo model (file_path, processing_status)
│   │   │   ├── face_embedding.py           ← FaceEmbedding model (512-D BYTEA, quality_score)
│   │   │   ├── edge_agent.py               ← Registered Edge Agent devices (api_key_hash)
│   │   │   ├── camera.py                   ← Camera model (rtsp_url, lat/lng, agent FK)
│   │   │   ├── sighting.py                 ← Sighting event model (evidence paths, scores)
│   │   │   ├── notification.py             ← Notification log model (type, is_read)
│   │   │   └── audit_log.py                ← Audit trail (actor, action, resource)
│   │   │
│   │   ├── schemas/                        ← Pydantic request/response schemas
│   │   │   ├── __init__.py                 ← Package marker
│   │   │   ├── user.py                     ← UserCreate, UserResponse, UserUpdate
│   │   │   ├── missing_person.py           ← ReportCreate, ReportResponse, ReportUpdate
│   │   │   ├── photo.py                    ← PhotoResponse, PhotoProcessingStatus
│   │   │   ├── face_embedding.py           ← EmbeddingSyncResponse, EmbeddingPackage
│   │   │   ├── edge_agent.py               ← AgentRegister, AgentHeartbeat, AgentResponse
│   │   │   ├── camera.py                   ← CameraCreate, CameraResponse
│   │   │   ├── sighting.py                 ← SightingCreate, SightingResponse, SightingReview
│   │   │   └── notification.py             ← NotificationResponse, NotificationUpdate
│   │   │
│   │   ├── crud/                           ← Data access layer (one function = one query)
│   │   │   ├── __init__.py                 ← Package marker
│   │   │   ├── user.py                     ← create_user, get_user_by_firebase_uid, update_user
│   │   │   ├── missing_person.py           ← create_report, list_reports, update_status
│   │   │   ├── photo.py                    ← save_photo_record, get_photos_for_person
│   │   │   ├── face_embedding.py           ← store_embedding, get_active_embeddings, get_since
│   │   │   ├── edge_agent.py               ← register_agent, heartbeat, list_agents
│   │   │   ├── camera.py                   ← register_camera, list_cameras_for_agent
│   │   │   ├── sighting.py                 ← create_sighting, list_sightings, confirm, reject
│   │   │   └── notification.py             ← create_notification, list_for_user, mark_read
│   │   │
│   │   ├── services/                       ← Business logic / action layer
│   │   │   ├── __init__.py                 ← Package marker
│   │   │   ├── face_processing.py          ← SCRFD detect + ArcFace embed on uploaded photo
│   │   │   ├── embedding_sync.py           ← Package active embeddings for Edge Agent sync
│   │   │   ├── notification_service.py     ← Send FCM push + store in DB + publish SSE
│   │   │   └── sse_manager.py              ← SSE connection manager (per-user channels)
│   │   │
│   │   ├── api/                            ← REST endpoints grouped by resource
│   │   │   ├── __init__.py                 ← Package marker
│   │   │   ├── deps.py                     ← Common dependencies (get_db, get_current_user)
│   │   │   ├── auth.py                     ← Firebase token verification middleware
│   │   │   ├── users.py                    ← /api/users/* endpoints
│   │   │   ├── reports.py                  ← /api/reports/* (missing persons CRUD + photo upload)
│   │   │   ├── agents.py                   ← /api/agents/* (edge agent registration + heartbeat)
│   │   │   ├── cameras.py                  ← /api/cameras/* (camera CRUD per agent)
│   │   │   ├── sightings.py                ← /api/sightings/* (create, confirm, reject)
│   │   │   ├── embeddings.py               ← /api/embeddings/* (sync endpoint for Edge)
│   │   │   ├── notifications.py            ← /api/notifications/* (list, mark read)
│   │   │   └── sse.py                      ← /api/events/stream (SSE endpoint)
│   │   │
│   │   └── utils/
│   │       ├── __init__.py                 ← Package marker
│   │       └── file_storage.py             ← Save/serve files from local filesystem
│   │
│   ├── ai_models/                          ← ONNX model files (downloaded at build time)
│   │   ├── det_10g.onnx                    ← SCRFD face detector (~16 MB)
│   │   └── w600k_r50.onnx                  ← ArcFace recognition model (~166 MB)
│   │
│   └── uploads/                            ← Uploaded photos + evidence (gitignored)
│       ├── photos/                         ← Original uploaded photos
│       ├── faces/                          ← Cropped + aligned face images (112×112)
│       └── evidence/                       ← Sighting evidence (crops, full frames, clips)
│
├── edge_agent/                             ← AI CCTV Edge Agent (Component 3)
│   ├── main.py                             ← Application entrypoint (QApplication + main window)
│   ├── requirements.txt                    ← Pinned dependencies
│   ├── config.py                           ← Agent configuration from config.ini (ConfigParser)
│   ├── config.ini                          ← Default config file (copied on first run)
│   ├── setup.py                            ← PyInstaller / cx_Freeze build config
│   │
│   ├── ui/                                 ← PySide6 GUI
│   │   ├── __init__.py                     ← Package marker
│   │   ├── main_window.py                  ← Main window: camera grid + status bar + alert list
│   │   ├── login_dialog.py                 ← Login / device registration dialog
│   │   ├── camera_config_dialog.py         ← Camera discovery (ONVIF) + manual RTSP entry
│   │   ├── alert_widget.py                 ← Possible match alert popup (confirm/reject buttons)
│   │   ├── camera_feed_widget.py           ← Single camera preview with face bbox overlay
│   │   ├── status_bar_widget.py            ← AI status: FPS, faces detected, CPU%, uptime
│   │   ├── timeline_widget.py              ← Sighting timeline view (chronological list)
│   │   ├── settings_dialog.py              ← Settings: language, thresholds, backend URL
│   │   ├── resources/                      ← Static resources
│   │   │   ├── style.qss                   ← Dark theme QSS stylesheet
│   │   │   ├── icons/                      ← App icons (tray, alert, status indicators)
│   │   │   │   ├── app.ico                 ← Windows app icon
│   │   │   │   ├── tray.png                ← System tray icon
│   │   │   │   ├── alert.png               ← Alert notification icon
│   │   │   │   └── status_*.png            ← Status indicators (online, offline, error)
│   │   │   ├── i18n/                       ← Internationalization strings
│   │   │   │   ├── en.json                 ← English strings (default)
│   │   │   │   └── hi.json                 ← Hindi strings
│   │   │   └── logo.png                    ← App logo for splash/login
│   │   └── workers/                        ← QThread workers for background tasks
│   │       ├── __init__.py                 ← Package marker
│   │       ├── stream_worker.py            ← Camera stream reader (runs in QThread)
│   │       └── sync_worker.py              ← Background embedding sync with backend
│   │
│   ├── ai/                                 ← AI Pipeline (CORE — most critical code)
│   │   ├── __init__.py                     ← Package marker
│   │   ├── pipeline.py                     ← Main AI pipeline orchestrator per camera
│   │   ├── face_detector.py                ← SCRFD face detection via ONNX Runtime
│   │   ├── face_aligner.py                 ← Affine alignment using 5 landmarks → 112×112
│   │   ├── face_recognizer.py              ← ArcFace embedding extraction via ONNX
│   │   ├── face_quality.py                 ← Quality gate: blur, size, angle, landmark checks
│   │   ├── tracker.py                      ← ByteTrack multi-object tracker integration
│   │   ├── track_state.py                  ← Per-track state: embedding history, scores, timing
│   │   ├── temporal_verifier.py            ← Aggregates N scores → match/no-match decision
│   │   ├── vector_search.py                ← FAISS index: add/remove/search embeddings
│   │   └── evidence_collector.py           ← Capture crops, full frames, metadata for sighting
│   │
│   ├── network/                            ← Backend communication
│   │   ├── __init__.py                     ← Package marker
│   │   ├── api_client.py                   ← HTTP client to backend API (httpx-based)
│   │   ├── embedding_syncer.py             ← Periodic sync of missing-person embeddings
│   │   ├── sighting_uploader.py            ← Upload sighting events + evidence files
│   │   └── sse_listener.py                 ← Listen for real-time events from backend SSE
│   │
│   ├── camera/                             ← CCTV integration
│   │   ├── __init__.py                     ← Package marker
│   │   ├── rtsp_reader.py                  ← OpenCV RTSP stream reader with auto-reconnect
│   │   ├── onvif_discovery.py              ← ONVIF camera auto-discovery on local network
│   │   ├── stream_manager.py               ← Manages multiple camera streams lifecycle
│   │   ├── circular_buffer.py              ← 15 FPS circular frame buffer (maxlen=150) for clips
│   │   └── frame_sampler.py                ← Regulates 10–15 FPS tracking stream
│   │
│   ├── storage/                            ← Local storage
│   │   ├── __init__.py                     ← Package marker
│   │   ├── local_db.py                     ← SQLite for local state + offline sighting queue
│   │   ├── faiss_store.py                  ← FAISS index persistence (save/load to disk)
│   │   └── evidence_store.py              ← Local evidence file management + cleanup
│   │
│   ├── utils/                              ← Edge Agent utilities
│   │   ├── __init__.py                     ← Package marker
│   │   └── path_resolver.py                ← Path resolver for PyInstaller (sys._MEIPASS) and dev
│   │
│   ├── models/                             ← ONNX model files (same models as backend)
│   │   ├── det_10g.onnx                    ← SCRFD face detector (~16 MB, or det_2.5g / det_0.5g)
│   │   └── w600k_r50.onnx                  ← ArcFace recognition model (~166 MB)
│   │
│   └── logs/                               ← Runtime logs (gitignored, rotated daily)
│
├── flutter_app/                            ← Flutter Mobile App (Component 1)
│   ├── pubspec.yaml                        ← Flutter dependencies (see §4)
│   ├── .env                                ← Backend URL, Firebase config (gitignored)
│   │
│   ├── lib/
│   │   ├── main.dart                       ← App entrypoint: Firebase init, Riverpod, routing
│   │   ├── config/
│   │   │   ├── theme.dart                  ← App theme (dark/light, color palette, typography)
│   │   │   ├── routes.dart                 ← GoRouter route definitions
│   │   │   └── constants.dart              ← API base URL, timeouts, retry config
│   │   │
│   │   ├── models/                         ← Data models (Dart classes, JSON serializable)
│   │   │   ├── user_model.dart             ← User (id, name, email, language, avatar)
│   │   │   ├── missing_person_model.dart   ← MissingPerson (status, description, photos)
│   │   │   ├── sighting_model.dart         ← Sighting (similarity, evidence paths, camera)
│   │   │   └── notification_model.dart     ← Notification (type, title, body, is_read)
│   │   │
│   │   ├── services/                       ← API + Firebase services
│   │   │   ├── auth_service.dart           ← Firebase Auth (Google + Email login)
│   │   │   ├── api_service.dart            ← Dio HTTP client to FastAPI backend
│   │   │   ├── notification_service.dart   ← FCM push notification handler + local notifs
│   │   │   ├── sse_service.dart            ← SSE listener for real-time sighting updates
│   │   │   └── image_service.dart          ← Photo picker + compression + upload
│   │   │
│   │   ├── providers/                      ← State management (Riverpod)
│   │   │   ├── auth_provider.dart          ← Auth state (user, token, login/logout)
│   │   │   ├── reports_provider.dart       ← Reports list + CRUD operations
│   │   │   ├── sightings_provider.dart     ← Sightings for a given person
│   │   │   └── notifications_provider.dart ← Notification inbox + unread count
│   │   │
│   │   ├── screens/                        ← UI screens (one file per screen)
│   │   │   ├── splash_screen.dart          ← App loading + auth state check → route
│   │   │   ├── login_screen.dart           ← Login (Google Sign-In + Email/Password)
│   │   │   ├── home_screen.dart            ← Dashboard: stats, recent sightings, quick actions
│   │   │   ├── report_form_screen.dart     ← Submit missing person report (form + photos)
│   │   │   ├── my_reports_screen.dart      ← List of user's reports (filterable by status)
│   │   │   ├── report_detail_screen.dart   ← Single report: person info + photos + sightings
│   │   │   ├── sighting_detail_screen.dart ← Evidence viewer: zoomable photo, map, similarity
│   │   │   ├── notifications_screen.dart   ← Notification inbox (tap → sighting detail)
│   │   │   └── profile_screen.dart         ← User profile, language toggle, logout
│   │   │
│   │   ├── widgets/                        ← Reusable UI components
│   │   │   ├── report_card.dart            ← Missing person card (photo, name, status badge)
│   │   │   ├── sighting_card.dart          ← Sighting event card (evidence thumb, similarity)
│   │   │   ├── timeline_widget.dart        ← Visual timeline of sightings on a map
│   │   │   ├── photo_upload_widget.dart    ← Multi-photo upload with preview + remove
│   │   │   ├── status_badge.dart           ← Colored badge: ACTIVE / CLOSED / FOUND / PROCESSING
│   │   │   └── similarity_gauge.dart       ← Circular gauge showing similarity percentage
│   │   │
│   │   └── l10n/                           ← Localization (Flutter intl)
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
│   │   ├── 01_face_recognition_baseline.ipynb   ← Test ArcFace on LFW dataset
│   │   ├── 02_single_vs_temporal_matching.ipynb ← Compare single-frame vs temporal (KEY)
│   │   ├── 03_model_comparison.ipynb            ← Compare SCRFD vs RetinaFace
│   │   └── 04_cctv_conditions_eval.ipynb        ← Test under blur, lighting, angle
│   │
│   ├── datasets/                                ← Test datasets (gitignored, large)
│   │   ├── lfw/                                 ← Labeled Faces in the Wild
│   │   └── custom_cctv/                         ← Recorded CCTV test footage
│   │
│   ├── results/                                 ← Experiment results, charts, tables
│   │   ├── metrics.csv                          ← All experiment metrics in flat CSV
│   │   └── figures/                             ← Generated charts for paper
│   │
│   └── paper/                                   ← Research paper draft
│       ├── paper.tex                            ← LaTeX source
│       └── references.bib                       ← Bibliography
│
├── scripts/                                ← Utility scripts
│   ├── download_models.py                  ← Downloads ONNX models from InsightFace hub
│   ├── seed_test_data.py                   ← Seeds DB with test users + reports
│   └── generate_embeddings.py              ← Bulk-process photos into embeddings
│
├── .gitignore                              ← Root gitignore (see §2)
└── README.md                               ← Project overview + quick start
```

**File count**: ~90 source files across 3 components + documentation + research.

> **Rule of thumb applied**: Every file has a one-line responsibility. If a file description needed two sentences, it was split.

---

## 2. Environment / Secrets

### Settings Precedence

> **Runtime env vars > `.env` file > code defaults**
>
> This is stated once here and enforced everywhere. If a config change isn't taking effect, check this chain in order.

### Backend `.env.example`

```env
# ═══════════════════════════════════════════════════════════════
# FIND-MISSING-PEP — Backend Configuration
# Copy to .env and fill in REQUIRED values
# Precedence: Runtime env vars > .env file > code defaults
# ═══════════════════════════════════════════════════════════════

# ── App ──
APP_NAME=FindMissingPerson
APP_ENV=development                        # development | staging | production
DEBUG=true
SECRET_KEY=change-me-to-random-64-chars    # REQUIRED — used for signing internal tokens
                                           # Generate: python -c "import secrets; print(secrets.token_hex(32))"
ENROLLMENT_KEY=change-me-to-enrollment-key # REQUIRED — secret admin token required for POST /api/agents/register
RTSP_ENCRYPTION_KEY=change-me-to-32-byte-hex # REQUIRED — AES-GCM 256-bit key for encrypting camera credentials

# ── Database ──
DATABASE_URL=postgresql+asyncpg://fmp_user:fmp_pass@localhost:5432/fmp_db  # REQUIRED
# Docs: https://www.postgresql.org/docs/current/libpq-connect.html
# Format: postgresql+asyncpg://<user>:<pass>@<host>:<port>/<db>

# ── Redis ──
REDIS_URL=redis://localhost:6379/0         # REQUIRED for SSE pub/sub + background task queue
# Native async client via redis.asyncio (redis-py 5.x)
# Docs: https://redis.io/docs/connect/clients/python/

# ── Firebase ──
FIREBASE_PROJECT_ID=your-firebase-project  # REQUIRED — https://console.firebase.google.com
FIREBASE_CREDENTIALS_PATH=./firebase-sa.json  # REQUIRED — service account JSON
# Docs: https://firebase.google.com/docs/admin/setup#initialize_the_sdk_in_non-google_environments
# Get: Firebase Console → Project Settings → Service Accounts → Generate New Private Key

# ── File Storage ──
UPLOAD_DIR=./uploads                       # Local filesystem path for photos + evidence (mounted at /uploads)
MAX_UPLOAD_SIZE_MB=10                      # Max photo size in MB

# ── AI Models ──
ONNX_MODEL_DIR=./ai_models                # Path to ONNX model files (det_10g.onnx, w600k_r50.onnx)
FACE_DETECT_THRESHOLD=0.5                 # SCRFD detection confidence threshold (0.0–1.0)
FACE_SIMILARITY_THRESHOLD=0.50            # ArcFace cosine similarity candidate cutoff (0.0–1.0)

# ── Server ──
HOST=0.0.0.0                              # Bind address
PORT=8000                                  # HTTP port
WORKERS=1                                  # Uvicorn workers (1 for dev, 2–4 for prod)

# ── CORS ──
ALLOWED_ORIGINS=http://localhost:3000,http://localhost:8080  # Comma-separated allowed origins
```

### Edge Agent `config.ini`

```ini
; ═══════════════════════════════════════════════════════════════
; FIND-MISSING-PEP — Edge Agent Configuration
; Located at: edge_agent/config.ini
; Precedence: config.ini values > code defaults
; ═══════════════════════════════════════════════════════════════

[backend]
url = http://localhost:8000               ; REQUIRED — Backend API URL
enrollment_key = change-me-to-enrollment-key ; REQUIRED — Enrollment key for device registration
api_key = agent-api-key-here              ; Populated automatically during device registration

[agent]
device_id =                               ; Auto-generated UUID on first run, stored in SQLite
location_name = Main Building             ; Human-readable location (displayed in backend)
sync_interval_seconds = 60                ; How often to sync embeddings from backend (seconds)
heartbeat_interval_seconds = 30           ; How often to send heartbeat to backend

[ai]
onnx_model_dir = ./models                ; Directory containing det_10g.onnx (or det_2.5g) + w600k_r50.onnx
face_detect_threshold = 0.5              ; SCRFD confidence cutoff
face_similarity_threshold = 0.50          ; ArcFace cosine similarity cutoff for FAISS candidates
tracking_fps = 15                         ; Tracking & detection frame rate per camera (decoupled from ArcFace)
quality_min_face_size = 40               ; Minimum face width/height in pixels
quality_max_blur = 100                   ; Laplacian variance threshold
quality_min_aspect = 0.6                 ; Minimum width/height ratio
quality_max_aspect = 1.2                 ; Maximum width/height ratio

[tracking]
max_track_age = 30                       ; Frames before a lost track is deleted
min_recognition_interval = 1.0           ; Seconds between ArcFace runs per track ID (1.0s sampling)
temporal_window_size = 3                 ; Number of temporal scores needed for verification (3 consecutive)
temporal_threshold = 0.60                ; Mean similarity threshold to trigger alert (standard FAR 10^-4)
match_cooldown_seconds = 300             ; Suppress repeat alerts for same (track, person) pair

[ui]
language = en                            ; en | hi
theme = dark                             ; dark | light
show_face_boxes = true                   ; Draw bounding boxes on camera preview
show_track_ids = true                    ; Show track ID labels on camera preview
```

### Flutter `.env`

```env
# ── Flutter App Configuration ──
API_BASE_URL=http://localhost:8000/api    # REQUIRED — Backend API base URL
SSE_URL=http://localhost:8000/api/events/stream  # SSE endpoint
REQUEST_TIMEOUT_MS=30000                  # HTTP request timeout in milliseconds
```

### `.gitignore` (Root)

```gitignore
# ═══════════════════════════════════════════════════════
# FIND-MISSING-PEP — Root .gitignore
# ═══════════════════════════════════════════════════════

# Environment & Secrets
.env
*.env
!.env.example
firebase-sa.json
google-services.json
GoogleService-Info.plist

# Uploads & Evidence (runtime data)
uploads/
evidence/
logs/

# AI Models (large binary files — download via script)
*.onnx
ai_models/
models/

# Python
__pycache__/
*.pyc
*.pyo
.venv/
venv/
*.egg-info/
dist/
build/

# Research Datasets (too large for git)
datasets/
*.h5
*.pkl

# Flutter
flutter_app/.dart_tool/
flutter_app/.packages
flutter_app/build/

# IDE
.vscode/
.idea/
*.swp
*.swo

# OS
.DS_Store
Thumbs.db
desktop.ini

# Docker volumes (if local)
postgres_data/
redis_data/
```

---

## 3. Data Schema

### PostgreSQL (Backend) — All DDL Idempotent

> **Access Control**: RLS is OFF for all tables. Authentication is handled at the application layer via Firebase token verification middleware. Authorization is enforced in API endpoints (users can only see their own reports; agents can only submit sightings).

```sql
-- ═══════════════════════════════════════════════════════════════
-- FIND-MISSING-PEP — PostgreSQL Schema
-- All statements are idempotent (CREATE IF NOT EXISTS)
-- Access control: RLS OFF — app-level auth via Firebase tokens
-- Run order: top to bottom (respects FK dependencies)
-- ═══════════════════════════════════════════════════════════════


-- ══════════════════════════════════════════════════════
-- USERS
-- RLS: OFF (application-level auth via Firebase token)
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    firebase_uid    VARCHAR(128) UNIQUE NOT NULL,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) UNIQUE,
    phone           VARCHAR(20),
    avatar_url      VARCHAR(512),
    language        VARCHAR(5) DEFAULT 'en',           -- en | hi
    fcm_token       TEXT,                              -- Firebase Cloud Messaging device token
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_users_firebase_uid ON users(firebase_uid);


-- ══════════════════════════════════════════════════════
-- MISSING PERSON REPORTS
-- RLS: OFF
-- Status lifecycle: PROCESSING → ACTIVE → FOUND | CLOSED
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS missing_persons (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    full_name       VARCHAR(255) NOT NULL,
    age             INTEGER,
    gender          VARCHAR(20),                       -- male | female | other
    height_cm       INTEGER,
    description     TEXT,
    last_seen_location TEXT,
    last_seen_time  TIMESTAMPTZ,
    status          VARCHAR(20) DEFAULT 'PROCESSING',  -- PROCESSING | ACTIVE | FOUND | CLOSED
    contact_info    TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_mp_user ON missing_persons(user_id);
CREATE INDEX IF NOT EXISTS idx_mp_status ON missing_persons(status);


-- ══════════════════════════════════════════════════════
-- PHOTOS
-- RLS: OFF
-- Processing lifecycle: PENDING → SUCCESS | FAILED | NO_FACE
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS photos (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id       UUID NOT NULL REFERENCES missing_persons(id) ON DELETE CASCADE,
    original_path   VARCHAR(512) NOT NULL,
    face_crop_path  VARCHAR(512),
    is_primary      BOOLEAN DEFAULT FALSE,
    processing_status VARCHAR(20) DEFAULT 'PENDING',   -- PENDING | SUCCESS | FAILED | NO_FACE
    error_message   TEXT,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_photos_person ON photos(person_id);


-- ══════════════════════════════════════════════════════
-- FACE EMBEDDINGS
-- RLS: OFF
-- Embedding stored as BYTEA: 512 × float32 = 2048 bytes
-- Tracks active state & updated_at for reliable incremental sync
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS face_embeddings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id       UUID NOT NULL REFERENCES missing_persons(id) ON DELETE CASCADE,
    photo_id        UUID REFERENCES photos(id) ON DELETE SET NULL,
    embedding       BYTEA NOT NULL,                    -- 512-D float32 vector (2048 bytes)
    model_version   VARCHAR(50) DEFAULT 'arcface_r50',
    quality_score   FLOAT,
    is_active       BOOLEAN DEFAULT TRUE,              -- Set FALSE when person is FOUND/CLOSED
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_embeddings_person ON face_embeddings(person_id);
CREATE INDEX IF NOT EXISTS idx_embeddings_sync ON face_embeddings(updated_at, is_active);


-- ══════════════════════════════════════════════════════
-- EDGE AGENTS
-- RLS: OFF
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS edge_agents (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id       VARCHAR(128) UNIQUE NOT NULL,
    name            VARCHAR(255),
    location        VARCHAR(255),
    api_key_hash    VARCHAR(256) NOT NULL,
    status          VARCHAR(20) DEFAULT 'OFFLINE',     -- ONLINE | OFFLINE | ERROR
    last_heartbeat  TIMESTAMPTZ,
    last_sync_at    TIMESTAMPTZ,
    os_info         VARCHAR(255),
    version         VARCHAR(50),
    camera_count    INTEGER DEFAULT 0,
    created_at      TIMESTAMPTZ DEFAULT NOW(),
    updated_at      TIMESTAMPTZ DEFAULT NOW()
);


-- ══════════════════════════════════════════════════════
-- CAMERAS
-- RLS: OFF
-- RTSP URLs stored encrypted at rest via AES-GCM
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS cameras (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    agent_id        UUID NOT NULL REFERENCES edge_agents(id) ON DELETE CASCADE,
    name            VARCHAR(255) NOT NULL,
    rtsp_url        VARCHAR(512) NOT NULL,             -- Encrypted ciphertext (AES-GCM)
    location        VARCHAR(255),
    latitude        FLOAT,
    longitude       FLOAT,
    status          VARCHAR(20) DEFAULT 'INACTIVE',    -- ACTIVE | INACTIVE | ERROR
    resolution      VARCHAR(20),
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_cameras_agent ON cameras(agent_id);


-- ══════════════════════════════════════════════════════
-- SIGHTINGS
-- RLS: OFF
-- Status lifecycle: PENDING → CONFIRMED | REJECTED
-- Cascades on report/camera deletion to prevent FK violation
-- ══════════════════════════════════════════════════════
CREATE TABLE IF NOT EXISTS sightings (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id       UUID NOT NULL REFERENCES missing_persons(id) ON DELETE CASCADE,
    agent_id        UUID NOT NULL REFERENCES edge_agents(id) ON DELETE CASCADE,
    camera_id       UUID NOT NULL REFERENCES cameras(id) ON DELETE CASCADE,
    similarity_score FLOAT NOT NULL,
    confidence_level VARCHAR(20) DEFAULT 'POSSIBLE',   -- POSSIBLE | PROBABLE | CONFIRMED | REJECTED
    num_frames_matched INTEGER,
    face_crop_path  VARCHAR(512),
    full_frame_path VARCHAR(512),
    video_clip_path VARCHAR(512),
    camera_location VARCHAR(255),
    latitude        FLOAT,
    longitude       FLOAT,
    detected_at     TIMESTAMPTZ NOT NULL,
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
    data            JSONB,
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
    action          VARCHAR(100) NOT NULL,
    resource_type   VARCHAR(50),
    resource_id     UUID,
    details         JSONB,
    created_at      TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_audit_created ON audit_log(created_at DESC);
```

### SQLite (Edge Agent — Local State)

```sql
-- Edge Agent local persistence for offline operation

CREATE TABLE IF NOT EXISTS sync_state (
    key             TEXT PRIMARY KEY,
    value           TEXT,
    updated_at      TEXT DEFAULT (datetime('now'))
);
-- Known keys: last_sync_timestamp, device_id, api_key, backend_url, camera_mappings

-- Multi-photo support: PRIMARY KEY is embedding ID, indexed on person_id
CREATE TABLE IF NOT EXISTS cached_embeddings (
    id              TEXT PRIMARY KEY,                  -- Backend face_embeddings.id (UUID string)
    person_id       TEXT NOT NULL,                     -- Missing person UUID (supports multiple photos)
    person_name     TEXT,
    embedding_data  BLOB NOT NULL,                     -- 512-D float32 raw bytes (2048 bytes)
    photo_url       TEXT,
    synced_at       TEXT DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_cached_person ON cached_embeddings(person_id);

CREATE TABLE IF NOT EXISTS pending_sightings (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    person_id       TEXT NOT NULL,
    camera_id       TEXT NOT NULL,                     -- Backend camera UUID string (mapped on startup)
    similarity_score REAL NOT NULL,
    num_frames      INTEGER,
    face_crop_path  TEXT,
    full_frame_path TEXT,
    detected_at     TEXT NOT NULL,
    uploaded        INTEGER DEFAULT 0,
    retry_count     INTEGER DEFAULT 0,
    created_at      TEXT DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS track_log (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    track_id        INTEGER NOT NULL,
    camera_id       TEXT NOT NULL,
    person_id       TEXT,
    similarity_score REAL,
    frame_number    INTEGER,
    timestamp       TEXT DEFAULT (datetime('now'))
);
```

---

## 4. Dependencies

### Backend (`backend/requirements.txt`)

```
# ── Web Framework ──
fastapi>=0.110.0
uvicorn[standard]>=0.27.0
python-multipart>=0.0.6
sse-starlette>=1.6.0

# ── Database ──
sqlalchemy[asyncio]>=2.0.25
asyncpg>=0.29.0
alembic>=1.13.0
redis>=5.0.0                      # Native async support via redis.asyncio (aioredis is obsolete & removed)

# ── Auth & Security ──
firebase-admin>=6.3.0
python-jose>=3.3.0
cryptography>=42.0.0              # AES-GCM encryption for camera RTSP credentials

# ── Validation ──
pydantic>=2.5.0
pydantic-settings>=2.1.0

# ── AI / Face Processing ──
onnxruntime>=1.17.0
numpy>=1.26.0
opencv-python-headless>=4.9.0
insightface>=0.7.3
scikit-learn>=1.4.0

# ── File Handling ──
pillow>=10.2.0
aiofiles>=23.2.0

# ── Utilities ──
python-dotenv>=1.0.0
httpx>=0.26.0
bcrypt>=4.1.0
```

### Edge Agent (`edge_agent/requirements.txt`)

```
# ── GUI ──
PySide6>=6.6.0

# ── AI Pipeline ──
onnxruntime>=1.17.0
numpy>=1.26.0
opencv-python>=4.9.0
insightface>=0.7.3
faiss-cpu>=1.7.4
scipy>=1.12.0

# ── Tracking ──
lapx>=0.5.5                       # Maintained multi-platform LAP solver (pre-built Windows wheels)
filterpy>=1.4.5                   # Kalman Filter (patched for numpy>=1.26 np.float -> float, or vendored)

# ── Network ──
httpx>=0.26.0
sseclient-py>=1.8.0

# ── CCTV ──
onvif-zeep>=0.2.12
wsdiscovery>=2.0.0

# ── Utilities ──
pillow>=10.2.0
apscheduler>=3.10.4
psutil>=5.9.0

# ── Packaging ──
pyinstaller>=6.3.0
```

### Flutter (`pubspec.yaml` — dependencies)

```yaml
dependencies:
  flutter:
    sdk: flutter
  firebase_core: ^2.27.0
  firebase_auth: ^4.17.0
  google_sign_in: ^6.2.0
  firebase_messaging: ^14.7.0     # Primary notification channel for mobile background/terminated
  dio: ^5.4.0
  fetch_client: ^1.6.0            # Modern SSE / HTTP streaming client compatible with Dart 3
  flutter_riverpod: ^2.5.0
  cached_network_image: ^3.3.0
  image_picker: ^1.0.7
  photo_view: ^0.14.0
  shimmer: ^3.0.0
  flutter_map: ^6.1.0             # Map display for sighting locations & timelines
  latlong2: ^0.9.0                # Geo coordinates for flutter_map
  flutter_local_notifications: ^17.0.0
  flutter_localizations:
    sdk: flutter
  intl: ^0.19.0
  go_router: ^13.2.0
  shared_preferences: ^2.2.2
  uuid: ^4.3.0
  timeago: ^3.6.0
  url_launcher: ^6.2.0
```

---

## 5. Containerization / Runtime

### Backend `Dockerfile`

```dockerfile
FROM python:3.11-slim

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgl1 \
    libglib2.0-0 \
    libpq-dev \
    curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

# Download & initialize InsightFace buffalo_l models during build (det_10g + w600k_r50)
RUN python -c "from insightface.app import FaceAnalysis; app = FaceAnalysis(name='buffalo_l', root='./ai_models'); app.prepare(ctx_id=-1, det_size=(640, 640))"

EXPOSE 8000

COPY start.sh .
RUN chmod +x start.sh

CMD ["./start.sh"]
```

### `start.sh`

```bash
#!/bin/bash
set -e

echo "╔══════════════════════════════════════════════════╗"
echo "║  FindMissingPerson Backend Starting...           ║"
echo "╠══════════════════════════════════════════════════╣"
echo "║  APP_ENV:        ${APP_ENV:-development}        ║"
echo "║  PORT:           ${PORT:-8000}                  ║"
echo "║  WORKERS:        ${WORKERS:-1}                  ║"
echo "║  DB:             ${DATABASE_URL:0:40}...        ║"
echo "║  REDIS:          ${REDIS_URL:-not set}          ║"
echo "║  UPLOAD_DIR:     ${UPLOAD_DIR:-./uploads}       ║"
echo "║  ONNX_MODEL_DIR: ${ONNX_MODEL_DIR:-./ai_models}║"
echo "║  FIREBASE:       ${FIREBASE_PROJECT_ID:-not set}║"
echo "╚══════════════════════════════════════════════════╝"

echo "Running database migrations..."
alembic upgrade head

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

### Port Ownership Map

| Service | Port | Protocol | Owner |
|---|---|---|---|
| Backend API (FastAPI/Uvicorn) | 8000 | HTTP | Backend container |
| PostgreSQL | 5432 | TCP | postgres container |
| Redis | 6379 | TCP | redis container |
| Edge Agent | — | Outbound HTTP/SSE only | Desktop app |
| Flutter App | — | Outbound HTTP/SSE only | Mobile app |

---

## 6. Domain Config / AI Pipeline Architecture

> [!IMPORTANT]
> This is the **most critical section**. The AI pipeline inside the Edge Agent is the core research novelty — **temporal face matching** across multiple frames to reduce false positives.

### 6.1 Pipeline Flow (Per Camera)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    AI PIPELINE (per camera stream)                      │
│                                                                         │
│  RTSP Stream (25-30 FPS decoded via OpenCV DXVA2/D3D11)                 │
│      │                                                                  │
│      ▼                                                                  │
│  ┌──────────────────┐                                                   │
│  │ 1. Frame Sampler  │── Regulate to 10–15 FPS tracking feed             │
│  │    + Circ Buffer │   Maintain CircularFrameBuffer(maxlen=150) for clip│
│  └──────┬───────────┘                                                   │
│         ▼                                                               │
│  ┌──────────────────┐                                                   │
│  │ 2. Face Detector  │── SCRFD (det_10g or det_2.5g) via ONNX           │
│  │    (10–15 FPS)   │   Output: bboxes + 5 landmarks + scores           │
│  └──────┬───────────┘                                                   │
│         ▼                                                               │
│  ┌──────────────────┐                                                   │
│  │ 3. ByteTrack      │── Persistent Track IDs + facial landmarks         │
│  │    (Extended)    │   Maintains tracks across frames at 10–15 FPS     │
│  └──────┬───────────┘                                                   │
│         ▼                                                               │
│  ┌──────────────────┐                                                   │
│  │ 4. Quality Gate   │── Reject blurry (<100 Laplacian), small (<40px)  │
│  └──────┬───────────┘                                                   │
│         ▼                                                               │
│  ┌──────────────────┐                                                   │
│  │ 5. Throttle Gate  │── Only run recognition once per 1.0s per Track ID│
│  └──────┬───────────┘                                                   │
│         ▼                                                               │
│  ┌──────────────────┐                                                   │
│  │ 6. Face Aligner   │── 5 landmarks → 112×112 aligned crop             │
│  └──────┬───────────┘                                                   │
│         ▼                                                               │
│  ┌──────────────────┐                                                   │
│  │ 7. Face Encoder   │── ArcFace (w600k_r50) → 512-D L2-normalized vec  │
│  └──────┬───────────┘                                                   │
│         ▼                                                               │
│  ┌──────────────────┐                                                   │
│  │ 8. Vector Search  │── FAISS IndexFlatIP (thread-safe atomic swap)    │
│  │    (FAISS CPU)   │   Cutoff: similarity ≥ 0.50                       │
│  └──────┬───────────┘                                                   │
│         ▼                                                               │
│  ┌──────────────────┐                                                   │
│  │ 9. Deduplication  │── Deduplicate candidates: max(sim) per person/frm│
│  └──────┬───────────┘                                                   │
│         ▼                                                               │
│  ┌──────────────────┐                                                   │
│  │ 10. Temporal      │── Mean of 3 consecutive scores ≥ 0.60 → MATCH    │
│  │     Verifier     │   (KEY RESEARCH NOVELTY — eliminates false alarms)│
│  └──────┬───────────┘                                                   │
│         ▼                                                               │
│  ┌──────────────────┐                                                   │
│  │ 11. Evidence      │── Crop + full frame + 15 FPS clip from circ buff │
│  │     Collector    │   Save local + SQLite pending queue               │
│  └──────┬───────────┘                                                   │
│         ▼                                                               │
│     ALERT → Desktop notification + Backend upload + FCM push           │
└─────────────────────────────────────────────────────────────────────────┘
```

### 6.2 Face Detector: SCRFD

**File**: `edge_agent/ai/face_detector.py`

```python
class FaceDetector:
    """
    SCRFD face detector via ONNX Runtime / InsightFace model zoo.
    Input:  BGR frame (any resolution)
    Output: List[Detection] = [{bbox, score, landmarks}]
    
    Model Options:
      - det_10g.onnx (~16 MB): Standard high-accuracy detector
      - det_2.5g.onnx (~4 MB): Recommended for 4-camera CPU systems (~2.5x faster)
      - det_0.5g.onnx (~1.5 MB): Ultra-lightweight edge fallback
    
    Anchor decoding & NMS are handled via insightface.model_zoo.get_model()
    to guarantee proper stride 8/16/32 anchor decoding and multi-head post-processing.
    Runs at 10–15 FPS on tracking feed.
    """
    def __init__(self, model_path: str, threshold: float = 0.5):
        from insightface.model_zoo import get_model
        self.model = get_model(model_path, providers=['CPUExecutionProvider'])
        self.model.prepare(ctx_id=-1, input_size=(640, 640), det_thresh=threshold)
    
    def detect(self, frame: np.ndarray) -> List[Detection]:
        bboxes, kpss = self.model.detect(frame, max_num=0, metric='default')
        # Returns bounding boxes [x1, y1, x2, y2, score] and 5 landmarks [[x, y], ...]
        ...
```

### 6.3 Face Recognizer: ArcFace

**File**: `edge_agent/ai/face_recognizer.py`

```python
class FaceRecognizer:
    """
    ArcFace via ONNX Runtime.
    Input:  Aligned 112×112×3 RGB face
    Output: 512-D L2-normalized embedding (float32)
    
    Model: w600k_r50.onnx (~166 MB), trained on WebFace600K
    Similarity: Cosine similarity (dot product for L2-normalized vectors)
      - <0.50:       Different person (discarded)
      - 0.50–0.58:   Uncertain candidate (requires temporal accumulation)
      - 0.58–0.68:   Match threshold for temporal verification (FAR <= 10^-4)
      - 0.68+:       High confidence match
    Performance: ~8ms/face on i5-10400 CPU
    Throttled: Evaluated once per 1.0s per tracked person.
    """
    def __init__(self, model_path: str):
        from insightface.model_zoo import get_model
        self.model = get_model(model_path, providers=['CPUExecutionProvider'])
        self.model.prepare(ctx_id=-1)
    
    def get_embedding(self, aligned_face: np.ndarray) -> np.ndarray:
        return self.model.get_feat(aligned_face).flatten()
```

### 6.4 Face Aligner

**File**: `edge_agent/ai/face_aligner.py`

```python
class FaceAligner:
    """
    Affine alignment using 5 SCRFD landmarks → 112×112 crop.
    Reference landmarks (for 112×112):
        left_eye:    (38.2946, 51.6963)
        right_eye:   (73.5318, 51.5014)
        nose:        (56.0252, 71.7366)
        left_mouth:  (41.5493, 92.3655)
        right_mouth: (70.7299, 92.2041)
    
    Without alignment, ArcFace accuracy drops ~5–10%.
    """
    def align(self, frame: np.ndarray, landmarks: np.ndarray) -> Optional[np.ndarray]:
        if landmarks is None or len(landmarks) < 5:
            return None
        # cv2.estimateAffinePartial2D + cv2.warpAffine → 112×112
        ...
```

### 6.5 ByteTrack Tracker

**File**: `edge_agent/ai/tracker.py`

```python
class ExtendedTrack:
    """
    STrack extended with facial landmarks and recognition state.
    """
    def __init__(self, strack):
        self.track_id = strack.track_id
        self.bbox = strack.tlbr
        self.landmarks: Optional[np.ndarray] = None
        self.last_recognition_time: float = 0.0
        self.score_history: Dict[str, List[float]] = {}  # person_id -> scores

class ByteTracker:
    """
    Multi-object tracker running at 10–15 FPS.
    Maintains persistent IDs and preserves facial landmarks across frames.
    
    Uses lapx for high-performance Hungarian matching.
    Landmarks from matched detections are mapped directly to corresponding tracks.
    If a track is Kalman-predicted without detection, landmarks = None (recognition skipped).
    """
    def update(self, detections: List[Detection]) -> List[ExtendedTrack]:
        ...
```

### 6.6 Face Quality Gate

**File**: `edge_agent/ai/face_quality.py`

```python
class FaceQualityChecker:
    """
    Quality checks (in order, each can reject):
    1. MINIMUM SIZE:   face >= 40px (ArcFace needs 112×112 input)
    2. BLUR CHECK:     Laplacian variance >= 100 (motion blur kills features)
    3. ASPECT RATIO:   0.6–1.2 (extreme = occluded or profile)
    4. LANDMARK CHECK: All 5 landmarks inside bbox and not None
    """
    def is_quality_sufficient(self, frame, bbox, landmarks) -> Tuple[bool, str]:
        if landmarks is None:
            return False, "missing_landmarks"
        ...
```

### 6.7 Temporal Verifier (KEY RESEARCH CONTRIBUTION)

**File**: `edge_agent/ai/temporal_verifier.py`

```python
class TemporalVerifier:
    """
    ═══════════════════════════════════════════════════════════════════
    CORE RESEARCH NOVELTY: Multi-Frame Temporal Verification
    ═══════════════════════════════════════════════════════════════════
    
    Problem: Single-frame ArcFace matching under CCTV lighting produces
             15–25% false positives at threshold 0.42. Unrelated strangers
             regularly hit 0.40–0.48.
             
    Solution:
      1. Calibrate threshold to 0.60 (standard 1:N FAR <= 10^-4).
      2. Require 3 consecutive temporal observations (sampled at 1.0s intervals).
      3. A pedestrian crossing camera FOV (2–5s) easily yields 3 observations.
      4. Deduplicate candidates in each frame (max 1 score per person per frame).
    
    Example Walkthrough:
        t = 0.0s (Frame 1): track #42 → MP-102 similarity = 0.64
        t = 1.0s (Frame 15): track #42 → MP-102 similarity = 0.61
        t = 2.0s (Frame 30): track #42 → MP-102 similarity = 0.66
        Mean = 0.637 ≥ 0.60 → POSSIBLE MATCH CONFIRMED ✓
    
    Config: window_size=3, threshold=0.60, sample_interval=1.0s, cooldown=300s
    """
    def __init__(self, window_size: int = 3, threshold: float = 0.60, cooldown: float = 300.0):
        self.window_size = window_size
        self.threshold = threshold
        self.cooldown = cooldown
        self.history: Dict[Tuple[int, str], List[float]] = {}  # (track_id, person_id) -> scores
        self.last_alert_time: Dict[Tuple[int, str], float] = {}

    def check_match(self, track_id: int, person_id: str, similarity: float) -> Optional[MatchEvent]:
        key = (track_id, person_id)
        now = time.time()
        
        # Check cooldown
        if key in self.last_alert_time and (now - self.last_alert_time[key]) < self.cooldown:
            return None
            
        if key not in self.history:
            self.history[key] = []
        self.history[key].append(similarity)
        
        if len(self.history[key]) >= self.window_size:
            recent_scores = self.history[key][-self.window_size:]
            mean_score = float(np.mean(recent_scores))
            if mean_score >= self.threshold:
                self.last_alert_time[key] = now
                return MatchEvent(track_id=track_id, person_id=person_id, score=mean_score, frames=len(recent_scores))
        return None
```

### 6.8 Vector Search (FAISS) — Concurrency Safe

**File**: `edge_agent/ai/vector_search.py`

```python
import threading
import faiss
import numpy as np
from typing import List, Tuple

class VectorSearchEngine:
    """
    FAISS IndexFlatIP for cosine similarity search.
    Thread-Safe: Protects against C++ segmentation faults during concurrent
    reads (camera QThread workers) and writes (background SyncWorker)
    using double-buffering and threading.Lock.
    """
    def __init__(self, dimension: int = 512):
        self.lock = threading.Lock()
        self.index = faiss.IndexFlatIP(dimension)
        self.person_ids: List[str] = []
    
    def search(self, query: np.ndarray, top_k: int = 5, cutoff: float = 0.50) -> List[Tuple[str, float]]:
        query = query.reshape(1, -1).astype(np.float32)
        with self.lock:
            if self.index.ntotal == 0:
                return []
            k = min(top_k, self.index.ntotal)
            distances, indices = self.index.search(query, k)
            
            results = []
            for dist, idx in zip(distances[0], indices[0]):
                if idx != -1 and dist >= cutoff:
                    results.append((self.person_ids[idx], float(dist)))
            return results
    
    def rebuild_index(self, data: List[Tuple[str, np.ndarray]]) -> None:
        """
        Double buffering: Build new index offline, then atomically swap pointer.
        Prevents thread contention and segfaults.
        """
        new_index = faiss.IndexFlatIP(512)
        new_person_ids: List[str] = []
        if data:
            embeddings = np.vstack([emb for _, emb in data]).astype(np.float32)
            new_index.add(embeddings)
            new_person_ids = [pid for pid, _ in data]
        
        with self.lock:
            self.index = new_index
            self.person_ids = new_person_ids
```

### 6.9 Evidence Collector & Circular Buffer

**File**: `edge_agent/ai/evidence_collector.py` and `edge_agent/camera/circular_buffer.py`

```python
from collections import deque
import cv2

class CircularFrameBuffer:
    """
    Ring buffer storing 15 FPS decoded frames (~10 seconds = 150 frames).
    Allows dumping a smooth pre/post match video clip upon alert.
    """
    def __init__(self, maxlen: int = 150):
        self.buffer = deque(maxlen=maxlen)
    
    def append(self, frame: np.ndarray, timestamp: float):
        self.buffer.append((frame.copy(), timestamp))
    
    def dump_clip(self, output_path: str, fps: int = 15):
        if not self.buffer:
            return None
        h, w, _ = self.buffer[0][0].shape
        fourcc = cv2.VideoWriter_fourcc(*'mp4v')
        out = cv2.VideoWriter(output_path, fourcc, fps, (w, h))
        for frame, _ in list(self.buffer):
            out.write(frame)
        out.release()
        return output_path

class EvidenceCollector:
    """
    Captures:
      - Aligned face crop (112×112)
      - Full frame with bounding box and telemetry overlay
      - 5-second 15 FPS video clip from CircularFrameBuffer
      - Metadata: camera UUID, GPS coordinates, timestamp, similarity score
    """
    def capture(self, frame, track, person_id, similarity, timestamp, circ_buffer=None) -> Evidence:
        ...
```

---

## 7. Data Access Layer — Backend CRUD

> One file per table. One function per query. No business logic.

```python
# ── crud/user.py ──
async def create_user(db, firebase_uid, name, email=None, phone=None) -> User
async def get_user_by_firebase_uid(db, firebase_uid) -> Optional[User]
async def get_user_by_id(db, user_id) -> Optional[User]
async def update_user(db, user_id, **kwargs) -> User
async def update_fcm_token(db, user_id, fcm_token) -> None

# ── crud/missing_person.py ──
async def create_report(db, user_id, report: ReportCreate) -> MissingPerson
async def list_reports_for_user(db, user_id, status=None) -> List[MissingPerson]
async def get_report(db, report_id) -> Optional[MissingPerson]
async def update_report_status(db, report_id, status) -> MissingPerson
async def get_active_person_ids(db) -> List[UUID]

# ── crud/face_embedding.py ──
async def store_embedding(db, person_id, photo_id, embedding, quality_score) -> FaceEmbedding
async def get_active_embeddings(db) -> List[dict]  # JOINs with missing_persons WHERE status='ACTIVE' AND is_active=TRUE
async def get_embeddings_since(db, since: datetime) -> List[dict]  # WHERE updated_at >= since AND is_active=TRUE
async def get_deactivated_person_ids_since(db, since: datetime) -> List[str]  # Persons marked FOUND/CLOSED since timestamp

# ── crud/sighting.py ──
async def create_sighting(db, sighting: SightingCreate) -> Sighting
async def list_sightings_for_person(db, person_id, limit=50) -> List[Sighting]
async def get_sighting(db, sighting_id) -> Optional[Sighting]
async def confirm_sighting(db, sighting_id, reviewer_id, notes=None) -> Sighting
async def reject_sighting(db, sighting_id, reviewer_id, notes=None) -> Sighting
async def get_person_timeline(db, person_id) -> List[dict]  # "last seen" feature

# ── crud/notification.py ──
async def create_notification(db, user_id, type, title, body, data=None) -> Notification
async def list_notifications(db, user_id, unread_only=False, limit=50) -> List[Notification]
async def mark_read(db, notification_id) -> None
async def mark_all_read(db, user_id) -> int
async def get_unread_count(db, user_id) -> int

# ── crud/edge_agent.py ──
async def register_agent(db, device_id, name, location, api_key_hash) -> EdgeAgent
async def update_heartbeat(db, agent_id, camera_count=None) -> None
async def get_agent_by_api_key(db, api_key_hash) -> Optional[EdgeAgent]
async def list_agents(db) -> List[EdgeAgent]
async def map_camera_uuid(db, agent_id: UUID, local_channel_id: str, name: str, encrypted_rtsp: str) -> Camera
```

---

## 8. Action / Tool Layer — Backend Services

```python
# ── services/face_processing.py ──

async def process_uploaded_photo(photo_path, person_id, photo_id, db) -> dict:
    """
    Called when: User uploads photos for a report.
    Supports atomic creation: POST /api/reports/ accepts multipart metadata + photos
    in a single request, preventing orphaned reports.
    Steps: Load → SCRFD detect → pick largest face → align 112×112 →
           save crop → ArcFace embed → store in DB → mark photo SUCCESS →
           if all photos done → mark report ACTIVE
    Returns: {status, face_crop_path, quality_score}
    Cleanup Task: Background cron runs every 30m to delete reports stuck in 'PROCESSING'
                  with 0 photos older than 30 minutes.
    """

# ── services/embedding_sync.py ──

async def get_sync_package(db, since=None) -> dict:
    """
    Called when: Edge Agent requests embeddings.
    Full sync (since=None): all ACTIVE person embeddings.
    Incremental (since=timestamp):
      1. New/modified embeddings: WHERE updated_at >= since AND is_active = TRUE
      2. Removed persons: missing_persons marked FOUND/CLOSED since timestamp,
         or embeddings deactivated since timestamp.
    Returns: {
        "full_sync": bool,
        "persons": [{"person_id": str, "name": str, "embeddings": [bytes], "photo_url": str}],
        "removed_ids": [str],  # Edge agent purges these from SQLite and FAISS
        "sync_timestamp": str
    }
    """

# ── services/notification_service.py ──

async def send_sighting_notification(db, user_id, sighting) -> None:
    """
    Called when: New sighting created by Edge Agent.
    Delivery Strategy:
      - Primary channel: FCM Push notification (delivered even if mobile app is backgrounded/killed)
      - Auxiliary channel: SSE event stream (for instant UI state refresh if app is active in foreground)
    Steps: Get FCM token → build bilingual payload → send FCM (try/except) →
           store notification in DB → publish SSE event
    """

# ── services/sse_manager.py ──

class SSEManager:
    """
    Redis pub/sub → SSE stream per user.
    Events: new_sighting, sighting_confirmed, report_status_change
    Heartbeat ping every 30s to prevent proxy/browser timeout.
    """
    async def publish(self, user_id, event_type, data) -> None
    async def subscribe(self, user_id) -> AsyncGenerator
```

---

## 9. Orchestration / Edge Agent Main Loop

**File**: `edge_agent/ai/pipeline.py`

```python
import threading
import time
from typing import Dict

class AIPipeline:
    """
    One instance per camera. Runs in a dedicated QThread.
    
    Lifecycle: INIT → RUN → PAUSE → STOP
    Graceful Shutdown: Cooperative cancellation via self._stop_event.
                       UI closeEvent() sets the event and waits on worker thread.
    
    Offline: Queue sightings in SQLite, upload when backend returns.
    
    Signals (Qt): frame_processed, possible_match, error_occurred
    """
    def __init__(self, camera_id: str, detector, tracker, quality_checker, 
                 aligner, recognizer, vector_search, verifier, evidence_collector):
        self.camera_id = camera_id            # Backend Camera UUID
        self.detector = detector
        self.tracker = tracker
        self.quality_checker = quality_checker
        self.aligner = aligner
        self.recognizer = recognizer
        self.vector_search = vector_search
        self.verifier = verifier
        self.evidence = evidence_collector
        self.circ_buffer = CircularFrameBuffer(maxlen=150)  # 10s at 15 FPS
        self._stop_event = threading.Event()

    def stop(self):
        """Cooperative thread shutdown signal."""
        self._stop_event.set()

    def process_frame(self, frame, timestamp):
        """
        ═══ SEQUENCE IS CRITICAL — DO NOT REORDER ═══
        
        1. Buffer frame in 15 FPS ring buffer (for evidence video clips)
        2. Detect faces (SCRFD at 10–15 FPS)
        3. Update tracker (ByteTrack) — preserve bboxes & facial landmarks
        4. Quality filter — only clear, unblurred faces proceed
        5. For each quality-passed tracked face:
           a. Throttle: evaluate recognition at 1.0s intervals per Track ID
           b. Validate landmarks: skip if track was predicted without landmarks
           c. Align (5 landmarks → 112×112 crop)
           d. Embed (ArcFace → 512-D L2-normalized vector)
           e. Search FAISS (top-5 candidates, similarity >= 0.50)
           f. Deduplicate candidates: keep only MAX score per person_id in this frame
           g. Temporal verify: update history with deduplicated score (need 3 scores >= 0.60)
           h. If match confirmed → dump 5s clip from circ_buffer → capture evidence → alert!
        """
        if self._stop_event.is_set():
            return
            
        self.circ_buffer.append(frame, timestamp)
        
        # 10–15 FPS Face Detection
        detections = self.detector.detect(frame)
        tracks = self.tracker.update(detections)
        now = time.time()
        
        for track in tracks:
            # 1.0s Recognition Throttle per person (prevents CPU starvation & duplicate frame counts)
            if (now - track.last_recognition_time) < 1.0:
                continue
            if track.landmarks is None:
                continue
            if not self.quality_checker.is_quality_sufficient(frame, track.bbox, track.landmarks):
                continue
            
            track.last_recognition_time = now
            aligned = self.aligner.align(frame, track.landmarks)
            if aligned is None:
                continue
                
            embedding = self.recognizer.get_embedding(aligned)
            candidates = self.vector_search.search(embedding, top_k=5, cutoff=0.50)
            
            # Per-frame candidate deduplication (if person has multiple photos in FAISS)
            best_candidates: Dict[str, float] = {}
            for person_id, similarity in candidates:
                if person_id not in best_candidates or similarity > best_candidates[person_id]:
                    best_candidates[person_id] = similarity
            
            # Temporal verification
            for person_id, similarity in best_candidates.items():
                match = self.verifier.check_match(track.track_id, person_id, similarity)
                if match:
                    clip_path = self.circ_buffer.dump_clip(f"evidence/clips/{int(now)}_{person_id}.mp4")
                    evidence = self.evidence.capture(
                        frame, track, person_id, similarity, timestamp, video_clip=clip_path
                    )
                    self.on_possible_match(match, evidence)
```

---

## 10. API Layer — REST Endpoints

```
# ── Health (no auth) ──
GET    /health                              → {status, version}

# ── Static Media Mount ──
GET    /uploads/{path:path}                 → Static file mount (photos, face crops, evidence clips)

# ── Users ──
POST   /api/users/                          → Create/sync user
GET    /api/users/me                        → Get profile
PUT    /api/users/me                        → Update profile

# ── Reports ──
POST   /api/reports/                        → Create report (supports atomic multipart: metadata + photos)
GET    /api/reports/                        → List user's reports (?status=&page=&limit=)
GET    /api/reports/{id}                    → Get report detail
PUT    /api/reports/{id}                    → Update report
DELETE /api/reports/{id}                    → Close report (soft delete / triggers status=CLOSED)
POST   /api/reports/{id}/photos             → Upload additional photos (multipart, max 4, ≤10MB each)
GET    /api/reports/{id}/sightings          → List sightings for person
GET    /api/reports/{id}/timeline           → Sighting timeline (last-seen tracking)

# ── Edge Agents ──
POST   /api/agents/register                → Register device (Auth: X-Enrollment-Key) → returns API key
POST   /api/agents/{id}/heartbeat          → Heartbeat (Auth: X-API-Key)
GET    /api/agents/                         → List agents (admin only)
POST   /api/agents/{id}/cameras/sync       → Sync local camera channels (CAM-01) → returns backend UUIDs

# ── Cameras (auth: X-API-Key or Bearer) ──
POST   /api/cameras/                        → Register camera (encrypts RTSP URL at rest)
GET    /api/cameras/?agent_id={id}          → List cameras

# ── Embeddings (auth: X-API-Key) ──
GET    /api/embeddings/sync                 → Fetch embeddings (?since=ISO timestamp, returns active & removed_ids)

# ── Sightings (auth: X-API-Key) ──
POST   /api/sightings/                      → Report sighting (multipart + face crop, full frame, video clip)
GET    /api/sightings/{id}                  → Get sighting detail
PUT    /api/sightings/{id}/confirm          → Confirm sighting (operator action)
PUT    /api/sightings/{id}/reject           → Reject sighting (operator action)

# ── Notifications (auth: Firebase JWT) ──
GET    /api/notifications/                  → List (?unread_only=true&limit=50)
GET    /api/notifications/unread-count      → Unread count (badge)
PUT    /api/notifications/{id}/read         → Mark read
PUT    /api/notifications/read-all          → Mark all read

# ── Real-time Push & Streams ──
# Primary mobile delivery: Firebase Cloud Messaging (FCM) push
GET    /api/events/stream                   → SSE stream (?token=firebase_token) for in-app active UI refresh
```

---

## 11. Frontend / UI Spec

### Flutter Navigation

```
Splash → Login (Google + Email) → Main Shell (4 tabs)
  🏠 Home:    Dashboard stats, recent sightings, quick actions
  📋 Reports: List → Detail (person + photos + sightings + timeline) | + New Report form
  🔔 Notifs:  Inbox → Sighting Detail (evidence, map, similarity gauge)
  👤 Profile: User info, language toggle (EN/HI), logout
```

### Edge Agent GUI

```
╔══════════════════════════════════════════════════════════════════╗
║  FindMissingPerson — AI CCTV Agent        [_] [□] [×]          ║
╠══════════════════════════════════════════════════════════════════╣
║  [CAM-01 preview + bboxes]  [CAM-02 preview + bboxes]          ║
║  [CAM-03 preview + bboxes]  [CAM-04 preview + bboxes]          ║
╠══════════════════════════════════════════════════════════════════╣
║  ● AI Running │ Cases: 12 │ CPU: 45% │ Faces: 1,247 │ Sync: 2m║
╠══════════════════════════════════════════════════════════════════╣
║  📋 Alerts: 🚨 14:32 MP-102 (92%) CAM-07 [VIEW]     [Settings]║
╚══════════════════════════════════════════════════════════════════╝
```

### All Enums

```
Report Status:        PROCESSING | ACTIVE | FOUND | CLOSED
Photo Processing:     PENDING | SUCCESS | FAILED | NO_FACE
Gender:               male | female | other
Sighting Status:      PENDING | CONFIRMED | REJECTED
Confidence:           POSSIBLE | PROBABLE | CONFIRMED | REJECTED
Agent Status:         ONLINE | OFFLINE | ERROR
Camera Status:        ACTIVE | INACTIVE | ERROR
Notification Type:    SIGHTING | CONFIRMATION | STATUS_CHANGE
Audit Actor:          USER | AGENT | SYSTEM
Language:             en | hi
Theme:                dark | light
Pipeline State:       INIT | RUN | PAUSE | STOP
Track State:          TRACKED | LOST
```

---

## 12. Critical Architecture Rules — DO NOT DEVIATE

### Rule 1: Never alert from a single frame & use calibrated thresholds

```python
# ❌ WRONG — 15–25% false positive rate (0.42 threshold floods alarms in CCTV)
if similarity > 0.42: send_alert(person_id)

# ✅ CORRECT — multi-frame temporal verification with standard FAR threshold (0.60)
scores = track.get_recent_scores(person_id, window=3)
if len(scores) >= 3 and np.mean(scores) >= 0.60: send_alert(person_id)
```

### Rule 2: Track at 10–15 FPS, throttle recognition to 1.0s

```python
# ❌ WRONG — 1 FPS drops IoU to 0.0 (track IDs change every second; 5s throttle misses pedestrians)
tracks = tracker.update(sampled_1fps_detections)

# ✅ CORRECT — 10–15 FPS continuous tracking, 1.0s sampled recognition
tracks = tracker.update(detections_15fps)  # Persistent tracking across frames
for track in tracks:
    if track.time_since_last_recognition() < 1.0: continue
    if track.landmarks is None: continue
    embedding = recognizer.get_embedding(track.aligned_face)
```

### Rule 3: ONNX Runtime only — no PyTorch in Edge Agent

```python
# ❌ WRONG — 800MB+ memory, 3x slower
import torch; model = torch.load("arcface.pth")

# ✅ CORRECT — 50MB footprint, CPU-optimized
session = ort.InferenceSession("w600k_r50.onnx", providers=['CPUExecutionProvider'])
```

### Rule 4: Queue sightings offline

```python
# ❌ WRONG — sighting lost forever
except ConnectionError: pass

# ✅ CORRECT — SQLite queue + background retry
except ConnectionError: local_db.save_pending_sighting(sighting)
```

### Rule 5: Embeddings as bytes, not JSON

```python
# ❌ WRONG — 6KB, slow parse
json.dumps(embedding.tolist())

# ✅ CORRECT — 2KB, zero-copy
embedding.astype(np.float32).tobytes()
```

### Rule 6: RTSP auto-reconnect

```python
# ❌ WRONG — pipeline stops on network blip
if not ret: break

# ✅ CORRECT — exponential backoff reconnect
if not ret: self.reconnect(backoff=[1, 2, 5, 10, 30])
```

### Rule 7: FAISS index rebuild must be thread-safe (Double Buffering)

```python
# ❌ WRONG — Fatal C++ segmentation fault when camera QThreads search during reset()
self.index.reset(); self.person_ids.clear()
for pid, emb in data: self.index.add(emb); self.person_ids.append(pid)

# ✅ CORRECT — Double-buffering: build offline, swap pointer under lock
new_index = faiss.IndexFlatIP(512)
if data:
    embeddings = np.vstack([emb for _, emb in data]).astype(np.float32)
    new_index.add(embeddings)
with self.lock:
    self.index = new_index
    self.person_ids = [pid for pid, _ in data]
```

### Rule 8: GUI updates on main thread only

```python
# ❌ WRONG — segfault
self.parent.widget.setPixmap(frame)  # from QThread

# ✅ CORRECT — Qt signal/slot
self.frame_ready.emit(frame)  # signal crosses thread safely
```

### Rule 9: Deduplicate candidates per person per frame

```python
# ❌ WRONG — Person with 3 photos in FAISS gets 3 score entries in 1 frame
for person_id, sim in top_k_candidates:
    verifier.check_match(track_id, person_id, sim)  # Window satisfied prematurely!

# ✅ CORRECT — Keep only max similarity per person in each frame
best = {}
for pid, sim in top_k_candidates:
    if pid not in best or sim > best[pid]: best[pid] = sim
for pid, sim in best.items():
    verifier.check_match(track_id, pid, sim)
```

### Rule 10: Retain landmarks in ByteTrack ExtendedTrack

```python
# ❌ WRONG — STrack only has bounding boxes; landmarks missing or crashes aligner
aligned = aligner.align(frame, track.landmarks)  # AttributeError!

# ✅ CORRECT — Extend STrack to carry landmarks from matched Detection
class ExtendedTrack(STrack):
    landmarks: Optional[np.ndarray] = None
# If Kalman filter predicted track without detection in current frame, landmarks is None (skip recognition)
```

### Rule 11: Cooperative thread shutdown on application exit

```python
# ❌ WRONG — Terminating QThread forcibly causes QThread/C++ destruction crash
self.worker_thread.terminate()

# ✅ CORRECT — Signal stop event and wait
self.pipeline.stop()  # sets _stop_event
self.worker_thread.quit()
self.worker_thread.wait(timeout=3000)
```

---

## 13. Deployment

### Backend (Docker)

```bash
# 1. Clone: git clone <repo> && cd FIND-MISSING-PEP
# 2. Config: cp backend/.env.example backend/.env → edit
# 3. Firebase: cp firebase-sa.json backend/
# 4. Start: docker compose up -d --build
# 5. Verify: curl http://localhost:8000/health → {"status": "ok"}
# 6. Swagger: curl http://localhost:8000/docs
```

### Edge Agent (Windows)

```powershell
# 1. cd edge_agent && python -m venv .venv && .venv\Scripts\Activate.ps1
# 2. pip install -r requirements.txt
# 3. python ..\scripts\download_models.py --output ./models
# 4. Edit config.ini → set backend URL + API key
# 5. python main.py → GUI opens with login → camera config → monitoring
```

### Flutter App

```bash
# 1. cd flutter_app && flutter pub get
# 2. Place google-services.json + GoogleService-Info.plist
# 3. Edit .env → API_BASE_URL
# 4. flutter run → Splash → Login → Home
```

### Build Executable

```powershell
# PyInstaller packaging: Bundles models and UI resources into executable
pyinstaller --onedir --windowed --name "FindMissingPerson-Agent" `
    --add-data "models;models" --add-data "ui/resources;ui/resources" `
    --icon "ui/resources/icons/app.ico" main.py
```

> [!IMPORTANT]
> **PyInstaller Runtime Asset Resolver**: In packaged `.exe` mode, PyInstaller unpacks bundled resources into a temporary directory accessed via `sys._MEIPASS`. All relative paths to QSS stylesheets, icons, and ONNX models must be resolved through `edge_agent/utils/path_resolver.py`:
> ```python
> import os, sys
> def get_resource_path(relative_path: str) -> str:
>     base_path = getattr(sys, '_MEIPASS', os.path.abspath("."))
>     return os.path.join(base_path, relative_path)
> ```

---

## 14. Known Gotchas Table

| # | Symptom | Root Cause | Fix |
|---|---------|------------|-----|
| 1 | Docker build fails: `Package 'libgl1-mesa-glx' has no installation candidate` | Removed in Debian 12 (Bookworm) | Replace with `libgl1` + `libglib2.0-0` in Dockerfile |
| 2 | Backend crashes on startup: model download fails behind firewall | `FaceAnalysis()` does not download models without `.prepare()` | Call `app.prepare(ctx_id=-1, det_size=(640, 640))` during Docker build |
| 3 | `pip install` fails on Windows: `Cannot open include file: 'lap.h'` | `lap` is unmaintained with no Windows binary wheels on Python 3.10+ | Replace with `lapx>=0.5.5` in `edge_agent/requirements.txt` |
| 4 | Runtime crash: `AttributeError: module 'numpy' has no attribute 'float'` | `filterpy` uses deprecated `np.float` removed in `numpy>=1.24` | Patch `np.float = float` on startup or vendor modernized Kalman Filter |
| 5 | Agent desktop app crashes with `SIGSEGV` / `EXCEPTION_ACCESS_VIOLATION` | Concurrent FAISS read (camera QThread) and write (`SyncWorker`) | Wrap FAISS with double-buffering pointer swap and `threading.Lock` |
| 6 | Track IDs change every second / temporal match never triggers | 1 FPS drops IoU to 0.0, breaking ByteTrack Kalman Filter | Decouple: run ByteTrack at 10–15 FPS; throttle ArcFace to 1.0s per track |
| 7 | Hundreds of false alarms per hour in CCTV feed | ArcFace similarity threshold set to 0.42 (strangers regularly score 0.40–0.48) | Calibrate candidate cutoff to 0.50 and temporal threshold to 0.60 (FAR $\le 10^{-4}$) |
| 8 | SQLite error: `UNIQUE constraint failed: cached_embeddings.person_id` | Person has multiple registered photos/embeddings | Change PK to `id` (UUID string), add index on `person_id` |
| 9 | Deactivated/found persons continue to be alerted after sync | `face_embeddings` lacked `updated_at` / status tracking | Query `updated_at >= since` with soft deletes (`is_active = FALSE`) |
| 10 | PostgreSQL error: `foreign_key_violation` on report deletion | `sightings.person_id` lacked `ON DELETE CASCADE` | Add `ON DELETE CASCADE` to sightings foreign key |
| 11 | Backend rejects sightings: `camera_id` is invalid UUID | Agent sent local label (`CAM-01`) instead of backend UUID | Auto-sync cameras on agent startup (`POST /api/agents/{id}/cameras/sync`) |
| 12 | Report stuck in `PROCESSING` forever with 0 photos | Photo upload failed after report creation | Use atomic multipart `POST /api/reports/` + 30m cleanup worker |
| 13 | Flutter / browser receives `404 Not Found` for photo and evidence URLs | Backend missing static media mount | Mount `StaticFiles(directory=settings.UPLOAD_DIR)` at `/uploads` |
| 14 | Desktop app crashes on startup after PyInstaller packaging | Relative paths (`./models`, `./resources`) invalid in `.exe` | Wrap paths with `get_resource_path()` using `sys._MEIPASS` |
| 15 | CPU hits 100% and drops RTSP frames on 4 cameras | Software OpenCV decode + SCRFD-10G saturates CPU | Enable hardware decode (`cv2.CAP_FFMPEG` + DXVA2/D3D11) & use `det_2.5g.onnx` |
| 16 | Mobile app receives no notifications when screen is locked | SSE connection terminated by iOS/Android background manager | Use Firebase Cloud Messaging (FCM) as primary background delivery |
| 17 | Green/corrupted RTSP frames | Network congestion / buffer overflow | Set `CAP_PROP_BUFFERSIZE=1` and transport `?rtsp_transport=tcp` |
| 18 | PySide6 crash on application exit | QThread worker forcibly destroyed while loop is executing | Use `_stop_event` flag and `worker.wait(3000)` in `closeEvent()` |
| 19 | Firebase 401 errors | Expired ID token | Call `user.getIdToken(true)` + Dio retry interceptor |
| 20 | SSE connection drops after 60 seconds | Reverse proxy / load balancer idle timeout | Send 30s heartbeat ping from backend SSE manager |

---

## 15. Reference Data

```
SCRFD:        threshold 0.5 (models: det_10g.onnx [16MB], det_2.5g.onnx [4MB], det_0.5g.onnx [1.5MB])
ArcFace:      [-1, 0.50]=different (discard), [0.50, 0.58]=candidate review, [0.58, 0.68]=match (FAR 10^-4), [0.68+]=high confidence
Temporal:     window=3, threshold=0.60, sample_interval=1.0s, cooldown=300s
ByteTrack:    high=0.5, low=0.1, new=0.6, IoU=0.8, buffer=30, tracking_fps=15
Alignment:    left_eye(38.29,51.70) right_eye(73.53,51.50) nose(56.03,71.74)
              left_mouth(41.55,92.37) right_mouth(70.73,92.20)
Quality:      min_size=40px, blur=100(Laplacian), aspect=0.6–1.2
Buffer:       CircularFrameBuffer(maxlen=150) -> 10s clip at 15 FPS
```

---

## 16. Cost / Ops Reference

| Resource | Cost |
|----------|------|
| Backend VPS (2 vCPU, 4GB) | ~$20/mo |
| PostgreSQL (managed) | ~$15/mo or $0 on same VPS |
| Firebase Auth + FCM | Free tier |
| ONNX models | One-time ~180 MB download |
| Edge Agent hardware | Standard Windows PC, 4GB–8GB RAM, 4-core i5 (10th gen+) |
| Per-sighting storage | ~50 KB (photo crop + full frame) + ~1 MB (5s 15 FPS clip) |
| Edge Agent CPU (4 cam, 15 FPS tracking, 1.0s recognition) | ~30–50% of 4-core i5 (with DXVA2/D3D11 HW decode + `det_2.5g.onnx`) |
| **Typical monthly total** | **~$20** |

---

## 17. External API Integration Reference

### Firebase Auth — Verify Token

```python
from firebase_admin import auth, credentials, initialize_app
cred = credentials.Certificate("firebase-sa.json")
initialize_app(cred)
decoded = auth.verify_id_token(id_token)
uid = decoded['uid']
```

### Firebase Cloud Messaging — Send Push

```python
from firebase_admin import messaging
message = messaging.Message(
    notification=messaging.Notification(title="🚨 Possible Sighting", body="..."),
    data={"sighting_id": str(sid), "type": "SIGHTING"},
    token=user_fcm_token,
)
response = messaging.send(message)
```

### InsightFace — Model Loading

```python
from insightface.app import FaceAnalysis
app = FaceAnalysis(name='buffalo_l', root='./ai_models', providers=['CPUExecutionProvider'])
app.prepare(ctx_id=-1, det_size=(640, 640))
faces = app.get(frame)
# face.bbox, face.kps, face.embedding, face.det_score
```

### ONVIF Discovery

```python
from wsdiscovery import WSDiscovery
wsd = WSDiscovery(); wsd.start()
services = wsd.searchServices(timeout=5)
# Extract RTSP URLs from discovered services
wsd.stop()
```

### OpenCV RTSP

```python
cap = cv2.VideoCapture("rtsp://...?rtsp_transport=tcp")
cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)
ret, frame = cap.read()
```

### FAISS Search

```python
index = faiss.IndexFlatIP(512)
index.add(embeddings)  # (N, 512) float32
distances, indices = index.search(query.reshape(1, -1), k=5)
```

---

## 18. One-Time Setup Sequence (Post-Deploy)

```
 1. Start Docker: docker compose up -d
 2. Verify: curl http://localhost:8000/health
 3. Create Firebase project: https://console.firebase.google.com
 4. Enable Auth: Email/Password + Google Sign-In
 5. Download Firebase SA key → backend/firebase-sa.json
 6. Flutter Firebase: google-services.json + GoogleService-Info.plist
 7. Download models: python scripts/download_models.py
 8. Flutter: register first user
 9. Create test report with 2–3 photos
10. Verify: status changes PROCESSING → ACTIVE
11. Edge Agent: login + register device → get API key
12. Add cameras (ONVIF or manual RTSP)
13. Start monitoring → verify face detection (status bar)
14. Walk past camera → verify bounding boxes
15. Test with reported person's photo → verify alert popup
16. Confirm/reject from Edge Agent UI
17. Verify Flutter receives push notification
18. (Optional) Test offline: stop Docker → trigger match → restart → verify upload
```

---

## Appendix A: Development Phase Order

```mermaid
graph TD
    P1["Phase 1: Face Recognition Core<br/>Prove ArcFace on LFW dataset"]
    P2["Phase 2: CCTV Pipeline<br/>Detection + Tracking + Recognition"]
    P3["Phase 3: Edge Agent GUI<br/>PySide6 + camera integration"]
    P4["Phase 4: Backend API<br/>FastAPI + PostgreSQL"]
    P5["Phase 5: Flutter App<br/>Reports + notifications"]
    P6["Phase 6: Integration<br/>Connect all components"]
    P7["Phase 7: Research<br/>Experiments + paper"]
    
    P1 --> P2 --> P3 --> P4 --> P5 --> P6 --> P7
```

| Phase | Deliverable | Duration |
|-------|-------------|----------|
| 1 | Notebook: ArcFace on LFW | 1 week |
| 2 | Script: video → detect + track + recognize | 2 weeks |
| 3 | PySide6 desktop app + AI pipeline | 2–3 weeks |
| 4 | FastAPI + Docker + all endpoints | 2 weeks |
| 5 | Flutter app: login, reports, notifications | 2 weeks |
| 6 | End-to-end integration | 1–2 weeks |
| 7 | Experiments + paper draft | 2–3 weeks |
| **Total** | | **~12–15 weeks** |

---

## Appendix B: Research Paper Direction

**Title**: "Temporal Face Matching for Real-Time Missing-Person Detection in CCTV Networks"

| # | Experiment | Metric |
|---|-----------|--------|
| 1 | Single-frame vs temporal | Precision, Recall, F1, FAR, FRR |
| 2 | Window size (3 vs 5 vs 10) | F1, alert latency |
| 3 | Threshold tuning (0.3–0.6) | ROC, EER |
| 4 | SCRFD vs RetinaFace | mAP, FPS, CPU |
| 5 | Difficult conditions | Accuracy per condition |
| 6 | Camera scaling (1–8) | CPU% curve |
| 7 | Offline resilience | Zero-loss under 5min disconnect |
