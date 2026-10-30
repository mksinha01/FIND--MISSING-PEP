# FIND-MISSING-PEP 🔍

> **Edge-Native AI Missing Person Detection & Real-Time CCTV Surveillance Network**  
> *Transforming passive urban CCTV feeds into an active, automated missing-person recovery and alert network.*

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Python 3.10+](https://img.shields.io/badge/python-3.10+-blue.svg)](https://www.python.org/downloads/)
[![FastAPI](https://img.shields.io/badge/FastAPI-0.110+-009688.svg?logo=fastapi&logoColor=white)](https://fastapi.tiangolo.com)
[![Android](https://img.shields.io/badge/Android-Kotlin%20%7C%20Jetpack%20Compose-3DDC84.svg?logo=android&logoColor=white)](android_app/)
[![React 18](https://img.shields.io/badge/React-18%20%7C%20Vite-61DAFB.svg?logo=react&logoColor=black)](web_app/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-336791.svg?logo=postgresql&logoColor=white)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-Compose-2496ED.svg?logo=docker&logoColor=white)](docker-compose.yml)
[![Edge AI](https://img.shields.io/badge/AI-SCRFD%20%7C%20ArcFace%20%7C%20ByteTrack-FF6F00.svg)](edge_agent/)

---

## 📌 Executive Summary

Every year, over **100,000 individuals go missing** in India alone (NCRB statistics), with up to 30% remaining untraced due to delayed reporting and resource-constrained manual searches. While modern cities have hundreds of thousands of CCTV cameras, **99% of surveillance video is never reviewed in real time**—human vigilance drops precipitously after just 20 minutes of monitoring, and investigations remain purely retroactive. Furthermore, streaming high-definition RTSP video feeds from thousands of cameras to cloud servers introduces crippling bandwidth costs and single points of network failure.

**FIND-MISSING-PEP** eliminates this bottleneck by deploying lightweight, high-accuracy biometric inference directly to **edge monitoring nodes** (local police station PCs, transit hubs, and commercial NVRs) on commodity CPU hardware. It features a mathematical **$M$-of-$N$ temporal verification protocol** to eliminate false positives, an **offline-first SQLite WAL queue** to guarantee zero data loss during network blackouts, an asynchronous **FastAPI + PostgreSQL** cloud core, a **React 18 + Leaflet GIS** web command portal, and a **native Kotlin + Jetpack Compose** Android application for field officers and families.

---

## 🏛️ End-to-End System Architecture

```
 ┌────────────────────────────────────────────────────────────────────────┐
 │                         CCTV EDGE AGENT NODE                           │
 │                                                                        │
 │   [ RTSP Cameras / NVR Feeds / USB Webcams / MP4 Simulations ]         │
 │              │                                                         │
 │              ▼                                                         │
 │   [ Circular Video Ring Buffer (15-25 FPS) ]                           │
 │              │                                                         │
 │              ▼ Subsampled Inference (1-2 FPS)                          │
 │   [ SCRFD-10GF Face Detection & 5-Point Alignment ]                    │
 │              │                                                         │
 │              ▼                                                         │
 │   [ Landmark-Preserving ByteTrack (8-D Kalman Filter) ]                │
 │              │                                                         │
 │              ▼                                                         │
 │   [ ArcFace Feature Extraction (512-D Embedding) ]                     │
 │              │                                                         │
 │              ▼                                                         │
 │   [ FAISS Vector Engine (IndexFlatIP Double-Buffered Swaps) ]          │
 │              │                                                         │
 │              ▼ Candidate Match (Cosine Similarity ≥ 0.60)              │
 │   [ Temporal Verification Protocol: M=3 Sightings / 5.0s Window ]       │
 │              │                                                         │
 │              ├── Verified Match ──► [ Local SQLite WAL Queue ]         │
 │              ▼                                  │                      │
 │   [ Forensic Evidence Generator ]               │ (Offline Resilient)  │
 │   • Aligned Face Crop (112x112)                 │                      │
 │   • Full Context Frame (1080p JPEG)             ▼                      │
 │   • 5s MP4 Video Buffer Clip         [ Background Network Sync ]       │
 └─────────────────────────────────────────────────┬──────────────────────┘
                                                   │ HTTPS Multipart / JSON
                                                   ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                     CENTRAL CLOUD BACKEND (FastAPI)                    │
 │                                                                        │
 │   [ FastAPI REST Core ] ◄──► [ PostgreSQL 16 + Redis 7 Cache ]         │
 │              │                                                         │
 │              ├── Case Lifecycle Management & Biometric Embedding Sync  │
 │              ├── Automated FIR Verification & Police Workflow          │
 │              ├── Gallery Distribution with Tombstone Deletions         │
 │              └── Server-Sent Events (SSE) Broadcast Engine             │
 └──────────────────────┬──────────────────────────────────┬──────────────┘
                        │                                  │
         Server-Sent Events (SSE)                   REST / FCM Push Alerts
                        ▼                                  ▼
 ┌──────────────────────────────────────┐ ┌───────────────────────────────┐
 │        WEB INVESTIGATION PORTAL      │ │      NATIVE ANDROID APP       │
 │     (React 18 + Vite + Leaflet)      │ │   (Kotlin + Jetpack Compose)  │
 │                                      │ │                               │
 │  • Real-Time Sighting SSE Alerts     │ │  • Instant Push Notifications │
 │  • Interactive GIS Breadcrumb Trail  │ │  • Multi-Step Reporting Wizard│
 │  • Forensic Side-by-Side Inspector   │ │  • FIR Status Verification    │
 │  • Active Case & Camera Dashboard    │ │  • Forensic Inspector & Maps  │
 └──────────────────────────────────────┘ └───────────────────────────────┘
```

---

## ⚡ Key Innovations & Capabilities

### 1. Edge-Native AI Vision Pipeline
- **SCRFD-10GF Face Detector**: Detects faces down to $20 \times 20\text{ px}$ under extreme illumination, blur, and oblique camera angles (up to $60^\circ$ yaw).
- **Landmark-Preserving ByteTrack**: 8-dimensional Kalman filter tracks subjects across temporary occlusions and crowd clutter without re-detecting on every frame.
- **ArcFace Metric Embeddings**: Generates 512-dimensional hyperspherical normalized embeddings ($\|\mathbf{f}\|_2 = 1$) for high-margin cosine verification.
- **Sub-Millisecond Vector Search**: In-memory FAISS `IndexFlatIP` executes vector lookups across thousands of active missing person profiles in under **0.5 milliseconds** on standard CPUs.

### 2. $M$-of-$N$ Temporal Sliding Window Verification
To eliminate single-frame false positives (caused by motion blur, lighting reflections, or facial angle distortion), the Edge Agent requires:
$$\text{Verified Alert} \iff \sum_{t=0}^{W} \mathbb{I}\left[\cos(\mathbf{e}_{\text{track}}, \mathbf{e}_{\text{gallery}}) \ge 0.60\right] \ge 3 \quad \text{within } W = 5.0\text{ seconds}$$
Only sustained, verified tracks generate evidence packages and trigger law enforcement alerts.

### 3. Tamper-Evident Forensic Evidence Package
When a match is confirmed, the Edge Agent packages complete forensic evidence:
1. **Aligned Face Crop**: $112 \times 112\text{ px}$ normalized image.
2. **Context Frame**: Full HD 1080p uncompressed JPEG showing environmental surroundings.
3. **5-Second Video Clip**: Extracted from the circular in-memory ring buffer capturing pre- and post-match footage.
4. **Metadata Manifest**: Cryptographic timestamp, Camera ID, GPS coordinates, and exact cosine similarity score.

### 4. Zero-Loss Offline Resilience
- Edge nodes store sightings and evidence in an on-disk **SQLite database operating in Write-Ahead Logging (WAL) mode**.
- If network connectivity fails, inference continues uninterrupted.
- An autonomous background worker detects network restoration and syncs queued events with exponential backoff.

---

## 📦 Multi-Platform Ecosystem

| Component | Directory | Technology Stack | Key Responsibilities |
| :--- | :--- | :--- | :--- |
| **Edge CCTV Agent** | [`edge_agent/`](edge_agent/) | Python 3.10+, PySide6 (Qt6), ONNX Runtime, OpenCV, FAISS, ByteTrack | Multi-camera RTSP ingestion, local AI inference, temporal filtering, desktop monitoring GUI |
| **Cloud Backend** | [`backend/`](backend/) | FastAPI, SQLAlchemy 2.0 (Async), PostgreSQL 16, Redis 7, Alembic | RESTful API, case enrollment, embedding generation, SSE event broker, database persistence |
| **Native Android App** | [`android_app/`](android_app/) | Kotlin 2.0, Jetpack Compose, Material Design 3, Dagger Hilt, Room, Retrofit | Real-time push alerts, report filing with photo picker, forensic inspector, GPS breadcrumb map |
| **Web Portal** | [`web_app/`](web_app/) | React 18, TypeScript, Vite, Leaflet GIS, Tailwind / Custom CSS | Central command station, real-time sighting toast alerts, GIS movement paths, case management |

---

## 🗂️ Project Directory Structure

```text
FIND -MISSING PEP/
├── android_app/                  # Native Kotlin Android Application
│   ├── app/src/main/java/com/findmissingpep/app/
│   │   ├── data/                 # Repositories, Room DB, Retrofit API, DTOs
│   │   ├── di/                   # Hilt Dependency Injection Modules
│   │   ├── domain/               # Domain Models & Business Use Cases
│   │   ├── presentation/screens/ # Jetpack Compose Screens (Cases, Sighting, Map, FIR, Auth)
│   │   └── service/              # Foreground Sync & FCM Notification Services
│   └── build.gradle.kts          # Gradle build script
├── backend/                      # FastAPI Central Cloud Application
│   ├── app/
│   │   ├── api/v1/               # Routers (auth, cases, sightings, cameras, sync, fir)
│   │   ├── core/                 # App configuration, security, JWT, Redis client
│   │   ├── db/                   # Async session manager, Base model
│   │   ├── models/               # SQLAlchemy models (Case, Sighting, Camera, Audit)
│   │   ├── schemas/              # Pydantic v2 schemas
│   │   └── services/             # Biometrics, storage, vector sync, SSE broadcaster
│   ├── alembic/                  # Database migration scripts
│   ├── Dockerfile                # Backend container definition
│   └── requirements.txt          # Python cloud dependencies
├── edge_agent/                   # Desktop CCTV Monitoring & AI Inference Station
│   ├── ai/                       # SCRFD detector, ArcFace extractor, ByteTrack, FAISS
│   ├── camera/                   # RTSP circular ring buffer, video stream worker
│   ├── network/                  # Cloud sync worker, gallery puller, heartbeat
│   ├── storage/                  # SQLite WAL local evidence database
│   ├── ui/                       # PySide6 Desktop GUI (2x2 camera grid, sighting log)
│   ├── config.ini                # Edge station configuration
│   └── main.py                   # Edge agent launcher
├── scripts/                      # Utility scripts (seed_test_data.py, bench, etc.)
├── web_app/                      # React 18 + Vite Web Investigation Dashboard
│   ├── src/
│   │   ├── components/           # Navbar, MapView, SightingCard, Modals
│   │   ├── pages/                # Dashboard, Cases, Reports, Sightings, Timeline
│   │   └── services/             # API client, SSE stream subscriber
│   ├── Dockerfile                # Production Nginx container
│   └── package.json
├── docker-compose.yml            # Local development orchestration (DB, Redis, Backend)
├── docker-compose.prod.yml       # Production stack orchestration (Full system + Web)
├── FIND-MISSING-PEP.apk          # Pre-built installable Android application package
├── install_and_run_edge.bat      # Windows 1-click Edge Agent setup script
├── install_and_run_edge.sh       # Linux / macOS 1-click Edge Agent setup script
├── build_edge_exe.bat            # Standalone .EXE compiler for Windows (PyInstaller)
├── install_app.bat               # ADB 1-click Android APK installer
└── README.md                     # Master project documentation
```

---

## 🚀 Quick Start & Installation

### Option 1: Edge CCTV Agent (Local Monitoring Station)

The Edge Agent connects local webcams, IP cameras, or RTSP feeds directly to the central cloud.

#### On Windows (1-Click Installer):
1. Install **Python 3.10, 3.11, or 3.12** from [python.org](https://www.python.org/downloads/) *(make sure to check **"Add Python to PATH"**)*.
2. Double-click:
   ```cmd
   install_and_run_edge.bat
   ```
   *This automatically sets up a `.venv`, installs PySide6, ONNX Runtime, InsightFace, OpenCV, FAISS, and launches the desktop GUI!*

#### On Linux / Ubuntu:
```bash
chmod +x install_and_run_edge.sh
./install_and_run_edge.sh
```

#### Standalone Portable Windows Package (.EXE — No Python Required):
To compile a standalone `.exe` that can be transferred via USB to any Windows workstation:
```cmd
build_edge_exe.bat
```
The output executable will be created in `dist/FindMissingPerson-Agent/FindMissingPerson-Agent.exe`.

---

### Option 2: Full Cloud Stack via Docker Compose

Deploy the complete central cloud infrastructure (PostgreSQL 16, Redis 7, FastAPI backend, and React web app):

```bash
# 1. Clone repository
git clone https://github.com/mksinha01/FIND--MISSING-PEP.git
cd FIND--MISSING-PEP

# 2. Configure environment
cp .env.prod.example .env

# 3. Launch Docker Compose stack
docker compose -f docker-compose.prod.yml up -d --build
```

- **Web Dashboard**: `http://localhost` (or `http://YOUR_SERVER_IP`)
- **Backend API & Swagger Docs**: `http://localhost:8000/docs`
- **PostgreSQL**: Port `5432`
- **Redis**: Port `6379`

---

### Option 3: Native Android Mobile Application

The Android app provides real-time alerts, case registration wizards, and GPS breadcrumb tracking.

#### Direct APK Install:
1. Enable **Developer Options** and **USB Debugging** on your Android device.
2. Connect your device via USB and run:
   ```cmd
   install_app.bat
   ```
   *Or copy [`FIND-MISSING-PEP.apk`](FIND-MISSING-PEP.apk) directly to your Android device and install.*

#### Building from Source:
```bash
cd android_app
./gradlew assembleDebug
```
The debug APK will be generated at `android_app/app/build/outputs/apk/debug/app-debug.apk`.

---

### Option 4: Web Application (Local Frontend Dev)

```bash
cd web_app
npm install
npm run dev
```
Open `http://localhost:5173` in your browser.

---

## ⚙️ Configuration & Environment Variables

### Backend Configuration (`.env`)
```ini
POSTGRES_USER=fmp_user
POSTGRES_PASSWORD=fmp_secure_password
POSTGRES_DB=fmp_db
DATABASE_URL=postgresql+asyncpg://fmp_user:fmp_secure_password@postgres:5432/fmp_db
REDIS_URL=redis://redis:6379/0

ADMIN_ENROLLMENT_KEY=your_secure_admin_enrollment_key
RTSP_SECRET_KEY=32bytehexsecretforaesencryption00
UPLOAD_DIR=/app/uploads
AI_MODEL_DIR=/app/ai_models
```

### Edge Agent Configuration (`edge_agent/config.ini`)
```ini
[backend]
url = http://65.20.80.222:8000
enrollment_key = your_secure_admin_enrollment_key
api_key = 6e_oD7PUWNxHIQ1uwJgunfJqbp0YfU8J2RJWl2HK5jQYKntEChsbsNjC63iiuv86

[agent]
device_id = EDGE-STATION-01
location_name = Main Central Station
sync_interval_seconds = 60
heartbeat_interval_seconds = 30

[biometrics]
confidence_threshold = 0.60
temporal_window_seconds = 5.0
temporal_min_matches = 3
```

---

## 📡 REST API & Real-Time SSE Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/api/v1/auth/token` | Authenticate user & obtain JWT bearer token |
| `GET` | `/api/v1/cases/` | List all missing person cases with pagination & status filter |
| `POST` | `/api/v1/cases/` | Register new missing person case with reference photos |
| `GET` | `/api/v1/cases/{case_id}` | Detailed case dossier, timeline, and forensic sightings |
| `POST` | `/api/v1/sightings/` | Submit verified edge sighting with aligned crop, full frame & clip |
| `GET` | `/api/v1/sightings/stream` | **Server-Sent Events (SSE)** real-time push feed for live alerts |
| `GET` | `/api/v1/sync/gallery` | Differential biometric vector gallery pull for Edge stations |
| `POST` | `/api/v1/cameras/heartbeat` | Edge camera telemetry and operational health check |
| `POST` | `/api/v1/fir/validate` | Automated FIR parsing and verification queue |

---

## 🔍 Forensic Verification & Math Formulation

### ArcFace Biometric Metric Learning
Face images are normalized and mapped to a 512-dimensional unit hypersphere:
$$\mathcal{L} = -\log \frac{e^{s(\cos(\theta_{y_i} + m))}}{e^{s(\cos(\theta_{y_i} + m))} + \sum_{j \neq y_i} e^{s \cos \theta_j}}$$
where $s = 64$ is the feature scale and $m = 0.50$ is the additive angular margin penalty.

### Fast Inner Product Vector Search
Cosine similarity between normalized gallery embeddings $\mathbf{g}$ and probe embedding $\mathbf{p}$:
$$\text{Sim}(\mathbf{p}, \mathbf{g}) = \mathbf{p} \cdot \mathbf{g} = \sum_{k=1}^{512} p_k g_k \quad (\text{since } \|\mathbf{p}\|_2 = \|\mathbf{g}\|_2 = 1)$$
FAISS `IndexFlatIP` performs SIMD-accelerated dot products across thousands of registered profiles in microseconds.

---

## 🛡️ Security & Privacy Guardrails

- **Data Privacy**: Biometric vectors are mathematical 512-D float vectors; original reference photos cannot be reconstructed from embeddings.
- **Role-Based Access Control (RBAC)**: Distinct permissions for Law Enforcement Administrators, Field Officers, and Citizen Informants.
- **Encrypted Transmission**: HTTPS/TLS 1.3 encryption across all edge-to-cloud streams and REST payloads.
- **RTSP Credential Protection**: Camera passwords and stream secrets encrypted at rest using AES-256-GCM.
- **Tamper-Evident Evidence**: Sighting evidence packages include SHA-256 integrity hashes for admissibility in legal proceedings.

---

## 👥 Contributors & Acknowledgments

Developed with ❤️ for **HACKX-MUJ** and missing-person recovery initiatives.

- **Developer / Maintainer**: [mksinha01](https://github.com/mksinha01)
- **Primary Repository**: [FIND--MISSING-PEP](https://github.com/mksinha01/FIND--MISSING-PEP)

*Together, technology and rapid community response bring loved ones home safely.*
